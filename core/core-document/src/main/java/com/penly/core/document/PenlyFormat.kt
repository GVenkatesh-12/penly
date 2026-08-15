package com.penly.core.document

import com.penly.core.common.PenlyIds
import com.penly.core.geometry.Rect
import com.penly.core.geometry.Transform
import com.penly.core.model.InkObject
import com.penly.core.model.ObjectId
import com.penly.core.model.OpaqueObject
import com.penly.core.model.Page
import com.penly.core.model.PageObject
import com.penly.core.model.PageObjectType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Binary page-file container for Penly.
 *
 * Layout (all integers big-endian):
 * ```
 * magic "PNLY" (4 bytes) | version 0x01 | record count (4 bytes)
 * per object record:
 *   type byte   0x01=ink 0x02=text 0x03=image 0x04=shape 0x05=embedded 0x06=opaque
 *   objectId    2-byte BE length + UTF-8 bytes
 *   jsonLen     4-byte BE length + PageObject metadata JSON (payload excluded)
 *   payloadLen  4-byte BE length + payload bytes (0 allowed)
 * ```
 * Unknown record type bytes and unknown JSON class discriminators decode as [OpaqueObject]s
 * carrying the raw payload, so forward-compatible records survive re-saves losslessly.
 */
internal object PenlyFormat {
    /** Four ASCII bytes "PNLY". */
    val MAGIC: ByteArray = byteArrayOf(0x50, 0x4E, 0x4C, 0x59)

    /** Current page-file format version byte. */
    const val VERSION: Byte = 0x01

    /** Current Penly format version (also stamped in the manifest). */
    const val FORMAT_VERSION: Int = 1

    private const val TYPE_INK: Int = 0x01
    private const val TYPE_TEXT: Int = 0x02
    private const val TYPE_IMAGE: Int = 0x03
    private const val TYPE_SHAPE: Int = 0x04
    private const val TYPE_EMBEDDED: Int = 0x05
    private const val TYPE_OPAQUE: Int = 0x06
    private const val TYPE_UNKNOWN: Int = 0x00

    /** JSON class discriminators this reader understands. */
    val knownTypes: Set<String> = setOf("ink", "text", "image", "shape", "embedded", "opaque")

    /** Result of decoding a page file: the objects plus any non-fatal warnings. */
    data class DecodedPage(
        val objects: List<PageObject>,
        val warnings: List<String>,
    )

    /** Serializes [page] (metadata plus payloads) into the binary page-file format. */
    fun encodePage(
        page: Page,
        json: Json,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(VERSION.toInt())
        writeInt(out, page.objects.size)
        for (obj in page.objects) {
            out.write(typeByte(obj.type))
            val idBytes = obj.objectId.value.toByteArray(Charsets.UTF_8)
            writeShort(out, idBytes.size)
            out.write(idBytes)
            val jsonString = json.encodeToString(PageObject.serializer(), obj)
            val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)
            writeInt(out, jsonBytes.size)
            out.write(jsonBytes)
            val payload = obj.payload ?: ByteArray(0)
            writeInt(out, payload.size)
            out.write(payload)
        }
        return out.toByteArray()
    }

    /**
     * Decodes a page file. Unknown record types or unknown JSON class discriminators become
     * [OpaqueObject]s (recorded in the returned warnings) instead of failing the load.
     *
     * @throws IllegalArgumentException when [bytes] is not a valid page file.
     */
    fun decodePage(
        bytes: ByteArray,
        json: Json,
    ): DecodedPage {
        val input = ByteArrayInputStream(bytes)
        try {
            val magic = ByteArray(MAGIC.size)
            readFully(input, magic)
            if (!magic.contentEquals(MAGIC)) {
                throw IllegalArgumentException("bad magic: expected PNLY")
            }
            val version = input.read()
            if (version < 0) throw IOException("truncated")
            if (version != VERSION.toInt()) {
                val hex = version.toString(16).padStart(2, '0')
                throw IllegalArgumentException("unsupported format version 0x$hex")
            }
            val count = readInt(input)
            val objects = ArrayList<PageObject>(count)
            val warnings = ArrayList<String>()
            repeat(count) {
                val type = input.read()
                if (type < 0) throw IOException("truncated")
                val idBytes = ByteArray(readShort(input))
                readFully(input, idBytes)
                val objectId = ObjectId(String(idBytes, Charsets.UTF_8))
                val jsonBytes = ByteArray(readInt(input))
                readFully(input, jsonBytes)
                val payload = ByteArray(readInt(input))
                readFully(input, payload)
                objects.add(
                    decodeRecord(
                        type = type,
                        objectId = objectId,
                        jsonString = String(jsonBytes, Charsets.UTF_8),
                        payload = payload,
                        json = json,
                        warnings = warnings,
                    ),
                )
            }
            return DecodedPage(objects = objects, warnings = warnings)
        } catch (e: IOException) {
            throw IllegalArgumentException("truncated page file", e)
        }
    }

    private fun decodeRecord(
        type: Int,
        objectId: ObjectId,
        jsonString: String,
        payload: ByteArray,
        json: Json,
        warnings: MutableList<String>,
    ): PageObject {
        if (type in TYPE_INK..TYPE_OPAQUE) {
            val decoded =
                runCatching { json.decodeFromString(PageObject.serializer(), jsonString) }
                    .getOrNull()
            if (decoded != null) {
                return attachPayload(decoded, payload)
            }
        }
        val kind = discriminator(jsonString, json) ?: "unknown"
        warnings +=
            if (kind in knownTypes) {
                val hex = type.toString(16).padStart(2, '0')
                "object $objectId: unknown record type byte 0x$hex; preserved as OpaqueObject"
            } else {
                "object $objectId: unknown class discriminator '$kind'; preserved as OpaqueObject"
            }
        return opaqueObject(jsonString, json, kind, payload)
    }

    private fun attachPayload(
        obj: PageObject,
        payload: ByteArray,
    ): PageObject =
        when (obj) {
            is InkObject -> obj.copy(payload = payload)
            is OpaqueObject -> obj.copy(payload = payload)
            else -> obj
        }

    private fun discriminator(
        jsonString: String,
        json: Json,
    ): String? =
        runCatching {
            json
                .parseToJsonElement(jsonString)
                .jsonObject["type"]
                ?.jsonPrimitive
                ?.content
        }.getOrNull()

    private fun opaqueObject(
        jsonString: String,
        json: Json,
        kind: String,
        payload: ByteArray,
    ): OpaqueObject {
        val element = runCatching { json.parseToJsonElement(jsonString) }.getOrNull() as? JsonObject
        return OpaqueObject(
            objectId =
                element
                    ?.get("objectId")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.let(::ObjectId)
                    ?: ObjectId(PenlyIds.newId()),
            transform =
                field(element, json, "transform", Transform.serializer())
                    ?: Transform.IDENTITY,
            bounds =
                field(element, json, "bounds", Rect.serializer())
                    ?: Rect(0f, 0f, 0f, 0f),
            zIndex = element?.get("zIndex")?.jsonPrimitive?.intOrNull ?: 0,
            visibility = element?.get("visibility")?.jsonPrimitive?.booleanOrNull ?: true,
            createdAtMillis = element?.get("createdAtMillis")?.jsonPrimitive?.longOrNull ?: 0L,
            updatedAtMillis = element?.get("updatedAtMillis")?.jsonPrimitive?.longOrNull ?: 0L,
            revision = element?.get("revision")?.jsonPrimitive?.longOrNull ?: 0L,
            payloadRef = element?.get("payloadRef")?.jsonPrimitive?.contentOrNull,
            kind = kind,
            payload = payload,
        )
    }

    /** Decodes a common metadata field from unknown-object JSON, or null when absent/invalid. */
    private fun <T> field(
        element: JsonObject?,
        json: Json,
        key: String,
        serializer: KSerializer<T>,
    ): T? =
        element?.get(key)?.let {
            runCatching { json.decodeFromJsonElement(serializer, it) }.getOrNull()
        }

    private fun typeByte(type: PageObjectType): Int =
        when (type) {
            PageObjectType.INK -> TYPE_INK
            PageObjectType.TEXT -> TYPE_TEXT
            PageObjectType.IMAGE -> TYPE_IMAGE
            PageObjectType.SHAPE -> TYPE_SHAPE
            PageObjectType.EMBEDDED -> TYPE_EMBEDDED
            PageObjectType.OPAQUE -> TYPE_OPAQUE
        }

    private fun writeShort(
        out: ByteArrayOutputStream,
        value: Int,
    ) {
        out.write(value ushr 8 and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeInt(
        out: ByteArrayOutputStream,
        value: Int,
    ) {
        out.write(value ushr 24 and 0xFF)
        out.write(value ushr 16 and 0xFF)
        out.write(value ushr 8 and 0xFF)
        out.write(value and 0xFF)
    }

    private fun readShort(input: InputStream): Int {
        val hi = input.read()
        val lo = input.read()
        if (hi < 0 || lo < 0) throw IOException("truncated")
        return (hi shl 8) or lo
    }

    private fun readInt(input: InputStream): Int {
        var value = 0
        for (i in 0 until 4) {
            val b = input.read()
            if (b < 0) throw IOException("truncated")
            value = (value shl 8) or b
        }
        return value
    }

    private fun readFully(
        input: InputStream,
        bytes: ByteArray,
    ) {
        var offset = 0
        while (offset < bytes.size) {
            val count = input.read(bytes, offset, bytes.size - offset)
            if (count < 0) throw IOException("truncated")
            offset += count
        }
    }
}

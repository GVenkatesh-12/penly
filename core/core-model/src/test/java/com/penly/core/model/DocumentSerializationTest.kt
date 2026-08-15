package com.penly.core.model

import com.penly.core.common.PenlyIds
import com.penly.core.geometry.Rect
import com.penly.core.geometry.Transform
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun documentRoundTrip_preservesObjects_omitsPayloadFromJson() {
        val inkPayload = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val opaquePayload = byteArrayOf(0x0A, 0x0B)

        val ink =
            InkObject(
                objectId = ObjectId(PenlyIds.newId()),
                transform = Transform.IDENTITY,
                bounds = Rect(0f, 0f, 120f, 60f),
                zIndex = 1,
                createdAtMillis = 1000L,
                updatedAtMillis = 2000L,
                revision = 3,
                payloadRef = null,
                brushId = "PEN",
                colorArgb = 0xFF112233.toInt(),
                size = 5f,
                opacity = 1f,
                payload = inkPayload,
            )
        val text =
            TextObject(
                objectId = ObjectId(PenlyIds.newId()),
                bounds = Rect(10f, 10f, 210f, 60f),
                createdAtMillis = 1000L,
                updatedAtMillis = 2000L,
                text = "hello",
                fontSize = 24f,
                colorArgb = 0xFF445566.toInt(),
            )
        val opaque =
            OpaqueObject(
                objectId = ObjectId(PenlyIds.newId()),
                bounds = Rect(1f, 2f, 3f, 4f),
                createdAtMillis = 1000L,
                updatedAtMillis = 2000L,
                kind = "future-widget",
                payload = opaquePayload,
            )

        val documentId = DocumentId(PenlyIds.newId())
        val document =
            Document(
                documentId = documentId,
                title = "My Page",
                pages =
                    listOf(
                        Page(
                            pageId = PageId(PenlyIds.newId()),
                            documentId = documentId,
                            title = "Page 1",
                            objects = listOf(ink, text, opaque),
                            revision = 7,
                            createdAtMillis = 1000L,
                            updatedAtMillis = 2000L,
                        ),
                    ),
                revision = 9,
                createdAtMillis = 1000L,
                updatedAtMillis = 2000L,
            )

        val encoded = json.encodeToString(Document.serializer(), document)
        val decoded = json.decodeFromString(Document.serializer(), encoded)

        // Payload bytes must never enter the JSON; value-class IDs encode as plain strings.
        assertFalse(encoded.contains("\"payload\""))
        assertTrue(encoded.contains(document.documentId.value))

        // Structural equality of the decoded document vs the original.
        assertEquals(document.documentId, decoded.documentId)
        assertEquals(document.title, decoded.title)
        assertEquals(document.revision, decoded.revision)
        assertEquals(document.createdAtMillis, decoded.createdAtMillis)
        assertEquals(document.updatedAtMillis, decoded.updatedAtMillis)

        val originalPage = document.pages.single()
        val decodedPage = decoded.pages.single()
        assertEquals(originalPage.pageId, decodedPage.pageId)
        assertEquals(originalPage.documentId, decodedPage.documentId)
        assertEquals(originalPage.title, decodedPage.title)
        assertEquals(originalPage.revision, decodedPage.revision)
        assertEquals(originalPage.createdAtMillis, decodedPage.createdAtMillis)
        assertEquals(originalPage.updatedAtMillis, decodedPage.updatedAtMillis)
        assertEquals(originalPage.objects.size, decodedPage.objects.size)

        // Discriminators survive: each object decodes to its concrete type.
        assertTrue(decodedPage.objects[0] is InkObject)
        assertTrue(decodedPage.objects[1] is TextObject)
        assertTrue(decodedPage.objects[2] is OpaqueObject)

        // InkObject: full equality against the original with payload cleared.
        val decodedInk = decodedPage.objects[0] as InkObject
        assertEquals(ink.copy(payload = null), decodedInk)
        assertNull(decodedInk.payload)
        assertNull(decodedInk.payloadRef)

        // TextObject: no payload member at all, full data-class equality.
        val decodedText = decodedPage.objects[1] as TextObject
        assertEquals(text, decodedText)

        // OpaqueObject: kind preserved, payload transient.
        val decodedOpaque = decodedPage.objects[2] as OpaqueObject
        assertEquals(opaque.copy(payload = null), decodedOpaque)
        assertNull(decodedOpaque.payload)
    }

    @Test
    fun valueClassIdsRoundTripAsPlainStrings() {
        val document =
            Document(
                documentId = DocumentId(PenlyIds.newId()),
                title = "Untitled",
            )
        val encoded = json.encodeToString(Document.serializer(), document)
        val decoded = json.decodeFromString(Document.serializer(), encoded)

        assertEquals(document, decoded)
        assertTrue(encoded.contains("\"documentId\":\"${document.documentId.value}\""))
    }
}

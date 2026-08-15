@file:OptIn(ExperimentalInkCustomBrushApi::class)

package com.penly.core.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.ExperimentalInkCustomBrushApi
import androidx.ink.storage.BrushFamilySerialization
import androidx.ink.storage.StrokeInputBatchSerialization
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInputBatch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Binary codec for strokes, used to persist ink payloads out-of-band from page metadata.
 *
 * Payload framing (all integers big-endian):
 * ```
 * familyLen (4) | brush family bytes | batchLen (4) | input batch bytes
 * ```
 * The length prefixes are required: Ink's storage stream API is NOT self-delimiting —
 * a native decode of a message with trailing bytes fails ("Failed to parse ... proto").
 * Each message is therefore encoded into its own stream and framed explicitly.
 *
 * Decoding requires the caller-supplied [Brush] because the brush family alone cannot
 * rebuild the full brush (size/color/epsilon live in object metadata).
 */
object StrokeCodec {
    fun encode(stroke: Stroke): ByteArray {
        val familyBytes = encodeFamily(stroke.brush.family)
        val batchBytes = encodeBatch(stroke.inputs)
        val out = ByteArrayOutputStream(familyBytes.size + batchBytes.size + 8)
        writeInt(out, familyBytes.size)
        out.write(familyBytes)
        writeInt(out, batchBytes.size)
        out.write(batchBytes)
        return out.toByteArray()
    }

    /**
     * Decodes a codec payload. Payloads without the length-prefixed framing (written by an
     * earlier buggy version) are rejected with [IllegalArgumentException] so callers can skip
     * them instead of crashing.
     */
    fun decode(
        bytes: ByteArray,
        brush: Brush,
    ): Stroke {
        val input = ByteArrayInputStream(bytes)
        val familyBytes = ByteArray(readInt(input, bytes.size))
        readFully(input, familyBytes, bytes.size)
        BrushFamilySerialization.decode(ByteArrayInputStream(familyBytes))
        val batchBytes = ByteArray(readInt(input, bytes.size))
        readFully(input, batchBytes, bytes.size)
        val inputs = StrokeInputBatchSerialization.decode(ByteArrayInputStream(batchBytes))
        return Stroke(brush, inputs)
    }

    private fun encodeFamily(family: BrushFamily): ByteArray =
        ByteArrayOutputStream().also { out -> BrushFamilySerialization.encode(family, out) }.toByteArray()

    private fun encodeBatch(inputs: StrokeInputBatch): ByteArray =
        ByteArrayOutputStream().also { out -> StrokeInputBatchSerialization.encode(inputs, out) }.toByteArray()

    private fun writeInt(
        out: ByteArrayOutputStream,
        value: Int,
    ) {
        out.write(value ushr 24)
        out.write(value ushr 16)
        out.write(value ushr 8)
        out.write(value)
    }

    private fun readInt(
        input: InputStream,
        totalSize: Int,
    ): Int {
        val b0 = input.read()
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) {
            throw IllegalArgumentException("truncated stroke payload ($totalSize bytes)")
        }
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun readFully(
        input: InputStream,
        target: ByteArray,
        totalSize: Int,
    ) {
        var offset = 0
        while (offset < target.size) {
            val count = input.read(target, offset, target.size - offset)
            if (count < 0) {
                throw IllegalArgumentException("truncated stroke payload ($totalSize bytes)")
            }
            offset += count
        }
    }
}

@file:OptIn(ExperimentalInkCustomBrushApi::class)

package com.penly.core.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.ExperimentalInkCustomBrushApi
import androidx.ink.storage.BrushFamilySerialization
import androidx.ink.storage.StrokeInputBatchSerialization
import androidx.ink.strokes.Stroke
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Binary codec for strokes, used to persist ink payloads out-of-band from page metadata.
 *
 * The encoded payload is the brush family followed by the input batch. Decoding requires the
 * caller-supplied [Brush] because the brush family alone cannot rebuild the full brush
 * (size/color/epsilon live in object metadata).
 */
object StrokeCodec {
    fun encode(stroke: Stroke): ByteArray {
        val out = ByteArrayOutputStream()
        BrushFamilySerialization.encode(stroke.brush.family, out)
        StrokeInputBatchSerialization.encode(stroke.inputs, out)
        return out.toByteArray()
    }

    fun decode(
        bytes: ByteArray,
        brush: Brush,
    ): Stroke {
        val input = ByteArrayInputStream(bytes)
        BrushFamilySerialization.decode(input)
        val inputs = StrokeInputBatchSerialization.decode(input)
        return Stroke(brush, inputs)
    }
}

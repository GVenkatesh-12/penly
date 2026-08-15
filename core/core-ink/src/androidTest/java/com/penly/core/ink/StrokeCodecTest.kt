package com.penly.core.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.InProgressStroke
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.ink.strokes.StrokeInputBatch
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StrokeCodecTest {
    @Test
    fun roundTripPreservesInputsAndBrushFamily() {
        val brush = BrushFactory.createBrush(PenTool.PEN, 5f, PenTool.PEN.defaultColorArgb)
        val stroke = buildStroke(brush)
        val decoded = StrokeCodec.decode(StrokeCodec.encode(stroke), brush)

        assertBatchesEqual(stroke.inputs, decoded.inputs)
        assertEquals(stroke.brush.family, decoded.brush.family)
    }

    @Test
    fun decodeReencodesToIdenticalBytes() {
        val brush = BrushFactory.createBrush(PenTool.MARKER, 12f, 0xFF0077B6.toInt())
        val stroke = buildStroke(brush)
        val encoded = StrokeCodec.encode(stroke)
        val decoded = StrokeCodec.decode(encoded, brush)

        assertTrue(encoded.contentEquals(StrokeCodec.encode(decoded)))
    }

    private fun buildStroke(brush: Brush): Stroke {
        val stroke = InProgressStroke()
        stroke.start(brush)
        stroke.enqueueInputs(
            MutableStrokeInputBatch()
                .add(StrokeInput().apply { update(10f, 20f, 0L, InputToolType.STYLUS, 0.25f) })
                .add(StrokeInput().apply { update(30f, 40f, 16L, InputToolType.STYLUS, 0.5f) })
                .add(StrokeInput().apply { update(60f, 55f, 33L, InputToolType.STYLUS, 0.9f) }),
            MutableStrokeInputBatch(),
        )
        stroke.finishInput()
        stroke.updateShape()
        return stroke.toImmutable()
    }

    // StrokeInputBatch has no structural equals (JNI-backed, identity semantics), so compare
    // element-wise via StrokeInput's data-class equality.
    private fun assertBatchesEqual(
        expected: StrokeInputBatch,
        actual: StrokeInputBatch,
    ) {
        assertEquals("input count", expected.size, actual.size)
        for (index in 0 until expected.size) {
            assertEquals("input #$index", expected.get(index), actual.get(index))
        }
    }
}

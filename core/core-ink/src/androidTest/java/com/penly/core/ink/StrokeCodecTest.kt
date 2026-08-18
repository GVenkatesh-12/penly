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
import kotlin.math.abs
import kotlin.math.max

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
                .add(
                    StrokeInput().apply {
                        update(
                            x = 10f,
                            y = 20f,
                            elapsedTimeMillis = 0L,
                            toolType = InputToolType.STYLUS,
                            pressure = 0.25f,
                        )
                    },
                ).add(
                    StrokeInput().apply {
                        update(
                            x = 30f,
                            y = 40f,
                            elapsedTimeMillis = 16L,
                            toolType = InputToolType.STYLUS,
                            pressure = 0.5f,
                        )
                    },
                ).add(
                    StrokeInput().apply {
                        update(
                            x = 60f,
                            y = 55f,
                            elapsedTimeMillis = 33L,
                            toolType = InputToolType.STYLUS,
                            pressure = 0.9f,
                        )
                    },
                ),
            MutableStrokeInputBatch(),
        )
        stroke.finishInput()
        stroke.updateShape()
        return stroke.toImmutable()
    }

    // StrokeInputBatch has no structural equals (JNI-backed, identity semantics), so compare
    // element-wise. Ink's storage format stores inputs at Float16 precision, so float fields
    // are compared with a relative tolerance (half-precision ulp is ~2^-11 of the value).
    private fun assertBatchesEqual(
        expected: StrokeInputBatch,
        actual: StrokeInputBatch,
    ) {
        assertEquals("input count", expected.size, actual.size)
        for (index in 0 until expected.size) {
            val expectedInput = expected.get(index)
            val actualInput = actual.get(index)
            val message = "input #$index"
            assertEquals("$message elapsedTimeMillis", expectedInput.elapsedTimeMillis, actualInput.elapsedTimeMillis)
            assertEquals("$message toolType", expectedInput.toolType, actualInput.toolType)
            assertFloatApproximatelyEqual("$message x", expectedInput.x, actualInput.x)
            assertFloatApproximatelyEqual("$message y", expectedInput.y, actualInput.y)
            assertFloatApproximatelyEqual("$message strokeUnitLengthCm", expectedInput.strokeUnitLengthCm, actualInput.strokeUnitLengthCm)
            assertFloatApproximatelyEqual("$message pressure", expectedInput.pressure, actualInput.pressure)
            assertFloatApproximatelyEqual("$message tiltRadians", expectedInput.tiltRadians, actualInput.tiltRadians)
            assertFloatApproximatelyEqual("$message orientationRadians", expectedInput.orientationRadians, actualInput.orientationRadians)
        }
    }

    private fun assertFloatApproximatelyEqual(
        message: String,
        expected: Float,
        actual: Float,
    ) {
        val tolerance = max(1e-3f, abs(expected) * HALF_PRECISION_RELATIVE_TOLERANCE)
        assertEquals(message, expected, actual, tolerance)
    }
}

private const val HALF_PRECISION_RELATIVE_TOLERANCE = 0.001f

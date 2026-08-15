package com.penly.core.document

import android.graphics.RectF
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.InProgressStroke
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.ink.strokes.StrokeInputBatch
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.penly.core.geometry.Transform
import com.penly.core.ink.BrushFactory
import com.penly.core.ink.PenTool
import com.penly.core.ink.StrokeRecord
import com.penly.core.model.InkObject
import com.penly.core.model.ObjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max
import kotlin.math.min

@RunWith(AndroidJUnit4::class)
class InkObjectMapperTest {
    @Test
    fun strokeRoundTrip_preservesInputsBrushAndBounds() {
        val brush = BrushFactory.createBrush(PenTool.MARKER, 12f, 0xFF0077B6.toInt())
        val stroke = buildStroke(brush)
        val expectedBounds = boundsOf(stroke)

        val obj =
            InkObjectMapper.toInkObject(
                record = StrokeRecord(ObjectId("stroke-1"), stroke, expectedBounds),
                nowMillis = 1234L,
            )

        assertEquals("MARKER", obj.brushId)
        assertEquals(brush.colorIntArgb, obj.colorArgb)
        assertEquals(brush.size, obj.size, 0f)
        assertEquals(1f, obj.opacity, 0f)
        assertEquals(1234L, obj.createdAtMillis)
        assertEquals(1234L, obj.updatedAtMillis)
        assertEquals("stroke-1", obj.payloadRef)
        assertEquals(expectedBounds.left, obj.bounds.left, 0f)
        assertEquals(expectedBounds.top, obj.bounds.top, 0f)
        assertEquals(expectedBounds.right, obj.bounds.right, 0f)
        assertEquals(expectedBounds.bottom, obj.bounds.bottom, 0f)
        assertNotNull(obj.payload)

        val record = InkObjectMapper.toStrokeRecord(obj)
        assertNotNull(record)
        assertBatchesEqual(stroke.inputs, record!!.stroke.inputs)
        assertEquals(brush.family, record.stroke.brush.family)
        assertEquals(expectedBounds.left, record.bounds.left, 0.001f)
        assertEquals(expectedBounds.top, record.bounds.top, 0.001f)
        assertEquals(expectedBounds.right, record.bounds.right, 0.001f)
        assertEquals(expectedBounds.bottom, record.bounds.bottom, 0.001f)
    }

    @Test
    fun highlighterAndPen_roundTripBrushId() {
        val highlighterBrush =
            BrushFactory.createBrush(PenTool.HIGHLIGHTER, 24f, 0xFFFFC300.toInt())
        val highlighterStroke = buildStroke(highlighterBrush)
        val highlighterObj =
            InkObjectMapper.toInkObject(
                record = StrokeRecord(ObjectId("highlighter-1"), highlighterStroke, boundsOf(highlighterStroke)),
                nowMillis = 0L,
            )
        assertEquals("HIGHLIGHTER", highlighterObj.brushId)

        val penBrush = BrushFactory.createBrush(PenTool.PEN, 5f, 0xFF1B2A4A.toInt())
        val penStroke = buildStroke(penBrush)
        val penObj =
            InkObjectMapper.toInkObject(
                record = StrokeRecord(ObjectId("pen-1"), penStroke, boundsOf(penStroke)),
                nowMillis = 0L,
            )
        assertEquals("PEN", penObj.brushId)
    }

    @Test
    fun pencilBrush_mapsToPen_brushId() {
        val pencilBrush = BrushFactory.createBrush(PenTool.PENCIL, 8f, 0xFF37474F.toInt())
        val pencilStroke = buildStroke(pencilBrush)
        val obj =
            InkObjectMapper.toInkObject(
                record = StrokeRecord(ObjectId("pencil-1"), pencilStroke, boundsOf(pencilStroke)),
                nowMillis = 0L,
            )
        // Pencil shares the pressurePen family, so it cannot be distinguished from a pen.
        assertEquals("PEN", obj.brushId)
    }

    @Test
    fun toStrokeRecord_returnsNull_whenPayloadMissing() {
        val obj =
            InkObject(
                objectId = ObjectId("empty-1"),
                brushId = "PEN",
                colorArgb = 0xFF000000.toInt(),
                size = 5f,
                opacity = 1f,
            )
        assertNull(InkObjectMapper.toStrokeRecord(obj))
    }

    @Test
    fun transformRoundTrip_preservesObjectIdAndTranslation() {
        val brush = BrushFactory.createBrush(PenTool.PEN, 5f, 0xFF1B2A4A.toInt())
        val stroke = buildStroke(brush)
        val base = boundsOf(stroke)
        val moved =
            RectF(
                base.left + 50f,
                base.top + 25f,
                base.right + 50f,
                base.bottom + 25f,
            )
        val record =
            StrokeRecord(
                objectId = ObjectId("moved-1"),
                stroke = stroke,
                bounds = moved,
                transform = Transform(translationX = 50f, translationY = 25f),
            )

        val obj = InkObjectMapper.toInkObject(record = record, nowMillis = 0L)
        assertEquals("moved-1", obj.objectId.value)
        assertEquals(50f, obj.transform.translationX, 0f)
        assertEquals(25f, obj.transform.translationY, 0f)

        val restored = InkObjectMapper.toStrokeRecord(obj)
        assertNotNull(restored)
        assertEquals("moved-1", restored!!.objectId.value)
        assertEquals(50f, restored.transform.translationX, 0f)
        assertEquals(25f, restored.transform.translationY, 0f)
        // Effective bounds survive the round trip: base input bounds + stored translation.
        assertEquals(moved.left, restored.bounds.left, 0.001f)
        assertEquals(moved.top, restored.bounds.top, 0.001f)
        assertEquals(moved.right, restored.bounds.right, 0.001f)
        assertEquals(moved.bottom, restored.bounds.bottom, 0.001f)
        assertBatchesEqual(stroke.inputs, restored.stroke.inputs)
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

    private fun boundsOf(stroke: Stroke): RectF {
        val inputs = stroke.inputs
        var minX = inputs.get(0).x
        var minY = inputs.get(0).y
        var maxX = minX
        var maxY = minY
        for (index in 1 until inputs.size) {
            val input = inputs.get(index)
            minX = min(minX, input.x)
            minY = min(minY, input.y)
            maxX = max(maxX, input.x)
            maxY = max(maxY, input.y)
        }
        return RectF(minX, minY, maxX, maxY)
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

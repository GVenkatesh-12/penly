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
import com.penly.core.common.PenlyIds
import com.penly.core.ink.BrushFactory
import com.penly.core.ink.PenTool
import com.penly.core.ink.StrokeRecord
import com.penly.core.model.Document
import com.penly.core.model.DocumentId
import com.penly.core.model.InkObject
import com.penly.core.model.ObjectId
import com.penly.core.model.Page
import com.penly.core.model.PageId
import com.penly.core.storage.InMemoryContentStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max
import kotlin.math.min

/**
 * The Phase 2 exit-criterion proof: two real strokes are saved through [PenlyStore],
 * the in-memory objects are discarded, and reloading yields strokes with identical inputs —
 * same data means the same rendering, hence no visual drift.
 */
@RunWith(AndroidJUnit4::class)
class PenlyEndToEndTest {
    @Test
    fun saveDiscardLoad_restoresIdenticalStrokeInputs() {
        val penBrush = BrushFactory.createBrush(PenTool.PEN, 5f, PenTool.PEN.defaultColorArgb)
        val markerBrush = BrushFactory.createBrush(PenTool.MARKER, 14f, 0xFF0077B6.toInt())
        val penStroke = buildStroke(penBrush, PEN_INPUTS)
        val markerStroke = buildStroke(markerBrush, MARKER_INPUTS)
        val originalStrokes = listOf(penStroke, markerStroke)

        val now = 1000L
        val documentId = DocumentId(PenlyIds.newId())
        val page =
            Page(
                pageId = PageId(PenlyIds.newId()),
                documentId = documentId,
                title = "Page 1",
                objects =
                    originalStrokes.map { stroke ->
                        InkObjectMapper.toInkObject(
                            record = StrokeRecord(ObjectId(PenlyIds.newId()), stroke, boundsOf(stroke)),
                            nowMillis = now,
                        )
                    },
                revision = 1,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        val original = Document(documentId, "End to End", listOf(page), 1, now, now)

        val store = InMemoryContentStore()
        val penlyStore = PenlyStore(store)
        penlyStore.save(original)

        // Discard the in-memory objects: everything below comes back from the store.
        val result = penlyStore.load(documentId)
        assertTrue("expected Success, got $result", result is LoadResult.Success)
        val loaded = (result as LoadResult.Success).document

        val loadedObjects = loaded.pages.single().objects
        assertEquals(originalStrokes.size, loadedObjects.size)
        for (index in originalStrokes.indices) {
            val loadedInk = loadedObjects[index] as InkObject
            assertEquals(originalStrokes[index].brush.family, decodedBrush(loadedInk).family)
            val record = InkObjectMapper.toStrokeRecord(loadedInk)
            assertNotNull("object #$index lost its payload", record)
            assertBatchesEqual(originalStrokes[index].inputs, record!!.stroke.inputs)
        }
    }

    private fun decodedBrush(obj: InkObject): Brush {
        val tool = PenTool.valueOf(obj.brushId)
        return BrushFactory.createBrush(tool, obj.size, obj.colorArgb)
    }

    private fun buildStroke(
        brush: Brush,
        inputs: Array<StrokeInput>,
    ): Stroke {
        val batch = MutableStrokeInputBatch()
        for (input in inputs) {
            batch.add(input)
        }
        val stroke = InProgressStroke()
        stroke.start(brush)
        stroke.enqueueInputs(batch, MutableStrokeInputBatch())
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

    private companion object {
        val PEN_INPUTS =
            arrayOf(
                StrokeInput().apply {
                    update(
                        x = 10f,
                        y = 20f,
                        elapsedTimeMillis = 0L,
                        toolType = InputToolType.STYLUS,
                        pressure = 0.25f,
                    )
                },
                StrokeInput().apply {
                    update(
                        x = 30f,
                        y = 40f,
                        elapsedTimeMillis = 16L,
                        toolType = InputToolType.STYLUS,
                        pressure = 0.5f,
                    )
                },
                StrokeInput().apply {
                    update(
                        x = 60f,
                        y = 55f,
                        elapsedTimeMillis = 33L,
                        toolType = InputToolType.STYLUS,
                        pressure = 0.9f,
                    )
                },
                StrokeInput().apply {
                    update(
                        x = 90f,
                        y = 60f,
                        elapsedTimeMillis = 51L,
                        toolType = InputToolType.STYLUS,
                        pressure = 0.8f,
                    )
                },
            )
        val MARKER_INPUTS =
            arrayOf(
                StrokeInput().apply {
                    update(
                        x = 5f,
                        y = 200f,
                        elapsedTimeMillis = 0L,
                        toolType = InputToolType.STYLUS,
                        pressure = 1f,
                    )
                },
                StrokeInput().apply {
                    update(
                        x = 55f,
                        y = 210f,
                        elapsedTimeMillis = 12L,
                        toolType = InputToolType.STYLUS,
                        pressure = 0.9f,
                    )
                },
                StrokeInput().apply {
                    update(
                        x = 105f,
                        y = 190f,
                        elapsedTimeMillis = 24L,
                        toolType = InputToolType.STYLUS,
                        pressure = 1f,
                    )
                },
                StrokeInput().apply {
                    update(
                        x = 155f,
                        y = 170f,
                        elapsedTimeMillis = 37L,
                        toolType = InputToolType.STYLUS,
                        pressure = 0.7f,
                    )
                },
            )
    }
}

package com.penly.core.document

import android.graphics.RectF
import android.util.Log
import androidx.ink.brush.StockBrushes
import com.penly.core.geometry.Point
import com.penly.core.geometry.Rect
import com.penly.core.geometry.Transform
import com.penly.core.ink.BrushFactory
import com.penly.core.ink.PenTool
import com.penly.core.ink.StrokeCodec
import com.penly.core.ink.StrokeRecord
import com.penly.core.model.InkObject
import kotlin.math.max
import kotlin.math.min

/**
 * Adapter between committed ink strokes ([StrokeRecord]) and persisted [InkObject]s.
 */
object InkObjectMapper {
    /**
     * Maps a committed stroke to an [InkObject] whose payload holds the codec-encoded stroke.
     *
     * The object id and transform are taken from [record] (stable identity: ids survive saves,
     * unlike the Phase 2 behavior of minting a fresh id per save). The brush family is mapped
     * back to a [PenTool] by identity: pressure pen -> PEN, marker -> MARKER, highlighter ->
     * HIGHLIGHTER. A pencil stroke cannot be distinguished from a pen stroke because
     * [BrushFactory] maps [PenTool.PENCIL] to `StockBrushes.pressurePen()` (see AGENTS.md);
     * pencil ink therefore round-trips with brushId "PEN".
     */
    fun toInkObject(
        record: StrokeRecord,
        nowMillis: Long,
    ): InkObject {
        val brush = record.stroke.brush
        val tool =
            when (brush.family) {
                StockBrushes.marker() -> PenTool.MARKER
                StockBrushes.highlighter() -> PenTool.HIGHLIGHTER
                else -> PenTool.PEN
            }
        val bounds = record.bounds
        return InkObject(
            objectId = record.objectId,
            transform = record.transform,
            bounds = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom),
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            payloadRef = record.objectId.value,
            brushId = tool.name,
            colorArgb = brush.colorIntArgb,
            size = brush.size,
            opacity = 1f,
            payload = StrokeCodec.encode(record.stroke),
        )
    }

    /**
     * Rebuilds a [StrokeRecord] from [inkObject]; returns null when the payload is missing or
     * cannot be decoded (e.g. written by an older codec version). Undecodable objects are
     * skipped with a warning instead of failing the page load.
     *
     * The base bounds are the min/max of the decoded stroke's inputs; [inkObject.transform] is
     * then applied to make the record bounds EFFECTIVE page-space bounds (matching
     * [StrokeRecord.bounds]'s contract), so hit-testing works for moved strokes immediately
     * after load without waiting for a re-save. For the translation-only transforms Phase 3
     * produces, this round-trips exactly: save stores [StrokeRecord.bounds], reload recomputes
     * the same rectangle from inputs + transform.
     */
    fun toStrokeRecord(inkObject: InkObject): StrokeRecord? {
        val payload = inkObject.payload ?: return null
        val stroke =
            try {
                val tool = PenTool.valueOf(inkObject.brushId)
                val brush = BrushFactory.createBrush(tool, inkObject.size, inkObject.colorArgb)
                StrokeCodec.decode(payload, brush)
            } catch (exception: RuntimeException) {
                Log.w(TAG, "skipping undecodable ink object ${inkObject.objectId}", exception)
                return null
            }
        val inputs = stroke.inputs
        if (inputs.size == 0) {
            return StrokeRecord(
                objectId = inkObject.objectId,
                stroke = stroke,
                bounds = RectF(),
                transform = inkObject.transform,
            )
        }
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
        val effective = applyTransform(Rect(minX, minY, maxX, maxY), inkObject.transform)
        return StrokeRecord(
            objectId = inkObject.objectId,
            stroke = stroke,
            bounds = RectF(effective.left, effective.top, effective.right, effective.bottom),
            transform = inkObject.transform,
        )
    }

    /** Axis-aligned page-space bounds of [bounds] mapped through [transform]. */
    private fun applyTransform(
        bounds: Rect,
        transform: Transform,
    ): Rect {
        if (transform == Transform.IDENTITY) return bounds
        val corners =
            listOf(
                Point(bounds.left, bounds.top),
                Point(bounds.right, bounds.top),
                Point(bounds.left, bounds.bottom),
                Point(bounds.right, bounds.bottom),
            ).map(transform::apply)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (corner in corners) {
            minX = min(minX, corner.x)
            minY = min(minY, corner.y)
            maxX = max(maxX, corner.x)
            maxY = max(maxY, corner.y)
        }
        return Rect(minX, minY, maxX, maxY)
    }

    private const val TAG: String = "InkObjectMapper"
}

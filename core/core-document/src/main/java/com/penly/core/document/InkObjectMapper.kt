package com.penly.core.document

import android.graphics.RectF
import androidx.ink.brush.StockBrushes
import com.penly.core.common.PenlyIds
import com.penly.core.geometry.Rect
import com.penly.core.geometry.Transform
import com.penly.core.ink.BrushFactory
import com.penly.core.ink.PenTool
import com.penly.core.ink.StrokeCodec
import com.penly.core.ink.StrokeRecord
import com.penly.core.model.InkObject
import com.penly.core.model.ObjectId
import kotlin.math.max
import kotlin.math.min

/**
 * Adapter between committed ink strokes ([StrokeRecord]) and persisted [InkObject]s.
 */
object InkObjectMapper {
    /**
     * Maps a committed stroke to an [InkObject] whose payload holds the codec-encoded stroke.
     *
     * The brush family is mapped back to a [PenTool] by identity: pressure pen -> PEN, marker ->
     * MARKER, highlighter -> HIGHLIGHTER. A pencil stroke cannot be distinguished from a pen
     * stroke because [BrushFactory] maps [PenTool.PENCIL] to `StockBrushes.pressurePen()`
     * (see AGENTS.md); pencil ink therefore round-trips with brushId "PEN".
     */
    fun toInkObject(
        record: StrokeRecord,
        objectId: ObjectId = ObjectId(PenlyIds.newId()),
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
            objectId = objectId,
            transform = Transform.IDENTITY,
            bounds = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom),
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            payloadRef = objectId.value,
            brushId = tool.name,
            colorArgb = brush.colorIntArgb,
            size = brush.size,
            opacity = 1f,
            payload = StrokeCodec.encode(record.stroke),
        )
    }

    /**
     * Rebuilds a [StrokeRecord] from [inkObject]; returns null when the payload is missing.
     * The record bounds are recomputed as the min/max of the decoded stroke's inputs.
     */
    fun toStrokeRecord(inkObject: InkObject): StrokeRecord? {
        val payload = inkObject.payload ?: return null
        val tool = PenTool.valueOf(inkObject.brushId)
        val brush = BrushFactory.createBrush(tool, inkObject.size, inkObject.colorArgb)
        val stroke = StrokeCodec.decode(payload, brush)
        val inputs = stroke.inputs
        if (inputs.size == 0) return StrokeRecord(stroke, RectF())
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
        return StrokeRecord(stroke, RectF(minX, minY, maxX, maxY))
    }
}

package com.penly.core.renderer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.penly.core.model.PageTemplate
import kotlin.math.floor

/**
 * Data-driven page background patterns (plan §29). All geometry is in page units tiled from
 * the page origin, so the pattern pans/zooms coherently with the ink drawn above it — the
 * caller must invoke this under the SAME canvas transform as the strokes (the invariant from
 * commits c2ee869/2f94bc5). Blank draws nothing.
 */
object PageTemplates {
    /** Base grid spacing in page units; every template derives its density from this. */
    const val BASE_SPACING_PX: Float = 32f

    /** Light blue-gray rule/dot color. */
    const val LINE_COLOR_ARGB: Int = 0xFFC5CEDA.toInt()

    /** Dot radius for the DOTS template, page units. */
    const val DOT_RADIUS_PX: Float = 1.5f

    /** Y of the Cornell summary header rule, page units. */
    const val CORNELL_HEADER_Y_PX: Float = 96f
    private val linePaint =
        Paint().apply {
            isAntiAlias = true
            color = LINE_COLOR_ARGB
            strokeWidth = 1f
        }

    private val dotPaint =
        Paint().apply {
            isAntiAlias = true
            color = LINE_COLOR_ARGB
            style = Paint.Style.FILL
        }

    /** Paints [template] across [visible] (page-space rect), origin-aligned tiling. */
    fun drawBackground(
        canvas: Canvas,
        template: PageTemplate,
        visible: RectF,
    ) {
        when (template) {
            PageTemplate.BLANK -> Unit
            PageTemplate.RULED -> drawHorizontalLines(canvas, visible, BASE_SPACING_PX)
            PageTemplate.NARROW_RULED -> drawHorizontalLines(canvas, visible, BASE_SPACING_PX / 2f)
            PageTemplate.GRID -> {
                drawHorizontalLines(canvas, visible, BASE_SPACING_PX)
                drawVerticalLines(canvas, visible, BASE_SPACING_PX)
            }
            PageTemplate.DOTS -> drawDots(canvas, visible)
            PageTemplate.CORNELL -> drawCornell(canvas, visible)
        }
    }

    private fun drawHorizontalLines(
        canvas: Canvas,
        visible: RectF,
        spacing: Float,
    ) {
        val firstRow = floor(visible.top / spacing).toInt()
        val lastRow = (visible.bottom / spacing).toInt()
        for (row in firstRow..lastRow) {
            val y = row * spacing
            if (y < visible.top) continue
            canvas.drawLine(visible.left, y, visible.right, y, linePaint)
        }
    }

    private fun drawVerticalLines(
        canvas: Canvas,
        visible: RectF,
        spacing: Float,
    ) {
        val firstCol = floor(visible.left / spacing).toInt()
        val lastCol = (visible.right / spacing).toInt()
        for (col in firstCol..lastCol) {
            val x = col * spacing
            if (x < visible.left) continue
            canvas.drawLine(x, visible.top, x, visible.bottom, linePaint)
        }
    }

    private fun drawDots(
        canvas: Canvas,
        visible: RectF,
    ) {
        val firstRow = floor(visible.top / BASE_SPACING_PX).toInt()
        val lastRow = (visible.bottom / BASE_SPACING_PX).toInt()
        val firstCol = floor(visible.left / BASE_SPACING_PX).toInt()
        val lastCol = (visible.right / BASE_SPACING_PX).toInt()
        for (row in firstRow..lastRow) {
            val y = row * BASE_SPACING_PX
            if (y < visible.top) continue
            for (col in firstCol..lastCol) {
                val x = col * BASE_SPACING_PX
                if (x < visible.left) continue
                canvas.drawCircle(x, y, DOT_RADIUS_PX, dotPaint)
            }
        }
    }

    private fun drawCornell(
        canvas: Canvas,
        visible: RectF,
    ) {
        if (CORNELL_HEADER_Y_PX >= visible.top && CORNELL_HEADER_Y_PX <= visible.bottom) {
            canvas.drawLine(visible.left, CORNELL_HEADER_Y_PX, visible.right, CORNELL_HEADER_Y_PX, linePaint)
        }
        // Ruled notes area below the header, still origin-aligned on the base grid.
        val startY = maxOf(visible.top, CORNELL_HEADER_Y_PX + BASE_SPACING_PX)
        val below = RectF(visible.left, startY, visible.right, visible.bottom)
        drawHorizontalLines(canvas, below, BASE_SPACING_PX)
    }
}

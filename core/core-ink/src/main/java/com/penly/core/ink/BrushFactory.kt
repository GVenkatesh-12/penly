package com.penly.core.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes

object BrushFactory {
    private const val EPSILON: Float = 0.1f

    fun createBrush(
        tool: PenTool,
        size: Float,
        colorArgb: Int,
    ): Brush {
        val family =
            when (tool) {
                PenTool.PEN -> StockBrushes.pressurePen()
                PenTool.PENCIL -> StockBrushes.pressurePen()
                PenTool.MARKER -> StockBrushes.marker()
                PenTool.HIGHLIGHTER -> StockBrushes.highlighter()
                PenTool.ERASER -> error("Eraser is not a stroke tool")
            }
        return Brush.createWithColorIntArgb(
            family = family,
            colorIntArgb = colorArgb,
            size = size,
            epsilon = EPSILON,
        )
    }
}

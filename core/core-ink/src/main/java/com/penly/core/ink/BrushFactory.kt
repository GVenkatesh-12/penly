package com.penly.core.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes

object BrushFactory {
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
        return Brush
            .builder()
            .setFamily(family)
            .setSize(size)
            .setColorIntArgb(colorArgb)
            .build()
    }
}

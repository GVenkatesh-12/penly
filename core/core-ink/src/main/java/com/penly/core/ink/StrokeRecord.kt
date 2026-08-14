package com.penly.core.ink

import android.graphics.RectF
import androidx.ink.strokes.Stroke

data class StrokeRecord(
    val stroke: Stroke,
    val bounds: RectF,
)

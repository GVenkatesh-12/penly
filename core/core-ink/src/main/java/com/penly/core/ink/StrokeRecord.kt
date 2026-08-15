package com.penly.core.ink

import android.graphics.RectF
import androidx.ink.strokes.Stroke
import com.penly.core.geometry.Transform
import com.penly.core.model.ObjectId

/**
 * A committed stroke held by the canvas, with a stable identity and object transform.
 *
 * [bounds] are the EFFECTIVE page-space bounds: the min/max of the stroke's inputs transformed
 * by [transform]. For identity transforms they equal the raw input bounds; every mutation that
 * changes [transform] (move) also translates [bounds], so hit-testing can always use [bounds]
 * directly. Rendering composes the viewport matrix with [transform] (inputs stay untransformed).
 */
data class StrokeRecord(
    val objectId: ObjectId,
    val stroke: Stroke,
    val bounds: RectF,
    val transform: Transform = Transform.IDENTITY,
)

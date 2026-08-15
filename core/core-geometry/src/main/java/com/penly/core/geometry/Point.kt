package com.penly.core.geometry

import kotlinx.serialization.Serializable

/** A 2D point in float precision. */
@Serializable
data class Point(
    val x: Float,
    val y: Float,
) {
    operator fun plus(other: Point): Point = Point(x + other.x, y + other.y)

    operator fun minus(other: Point): Point = Point(x - other.x, y - other.y)

    operator fun times(scalar: Float): Point = Point(x * scalar, y * scalar)

    /** Returns a copy of this point shifted by [dx] along X and [dy] along Y. */
    fun translate(
        dx: Float,
        dy: Float,
    ): Point = Point(x + dx, y + dy)
}

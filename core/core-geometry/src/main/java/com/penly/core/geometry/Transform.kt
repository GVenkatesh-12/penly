package com.penly.core.geometry

import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin

/**
 * An affine transform: `p' = t + R(theta) * (S * p)`.
 *
 * [scaleX]/[scaleY] are applied first, then a counterclockwise rotation of [rotationDegrees]
 * degrees, then the [translationX]/[translationY] translation.
 *
 * [inverse] is exact when the scale is uniform ([scaleX] == [scaleY]) or the rotation is zero,
 * which covers all transforms Penly currently produces (pan/zoom viewports, identity object
 * transforms). Non-uniform scale combined with non-zero rotation is NOT closed under inversion
 * in this parameterization (the exact inverse of R*S is not expressible as R'*S'); if that
 * combination becomes reachable (future object manipulation), upgrade [Transform] to a full
 * affine matrix while keeping this API.
 */
@Serializable
data class Transform(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
) {
    /** Maps [p] through this transform: `p' = t + R(theta) * (S * p)`. */
    fun apply(p: Point): Point {
        val theta = radians(rotationDegrees)
        val cosTheta = cos(theta)
        val sinTheta = sin(theta)
        val scaledX = scaleX * p.x
        val scaledY = scaleY * p.y
        return Point(
            x = (scaledX * cosTheta - scaledY * sinTheta).toFloat() + translationX,
            y = (scaledX * sinTheta + scaledY * cosTheta).toFloat() + translationY,
        )
    }

    /**
     * Returns the transform that undoes this one: `scale' = (1/scaleX, 1/scaleY)`,
     * `rotation' = -rotationDegrees`, `translation' = -R(-theta) * S^-1 * t`.
     */
    fun inverse(): Transform {
        val theta = radians(rotationDegrees)
        val cosTheta = cos(theta)
        val sinTheta = sin(theta)
        val invScaleX = 1f / scaleX
        val invScaleY = 1f / scaleY
        val invTx = -(cosTheta * translationX * invScaleX + sinTheta * translationY * invScaleY)
        val invTy = sinTheta * translationX * invScaleX - cosTheta * translationY * invScaleY
        return Transform(
            translationX = invTx.toFloat(),
            translationY = invTy.toFloat(),
            scaleX = invScaleX,
            scaleY = invScaleY,
            rotationDegrees = -rotationDegrees,
        )
    }

    companion object {
        /** The identity transform: no translation, no rotation, unit scale. */
        val IDENTITY = Transform()
    }
}

private fun radians(degrees: Float): Double = degrees * (Math.PI / 180.0)

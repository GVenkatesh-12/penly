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
     * Returns this transform composed with a pure translation by ([dx], [dy]).
     *
     * Translation is applied after rotation/scale (`T(d) * this`), which is exactly an additive
     * shift of [translationX]/[translationY] for any scale or rotation — objects move without
     * distortion.
     */
    fun translate(
        dx: Float,
        dy: Float,
    ): Transform = copy(translationX = translationX + dx, translationY = translationY + dy)

    /**
     * Composes a page→screen viewport transform (uniform [scale] plus [offsetX]/[offsetY])
     * after this object transform: `p' = scale * this(p) + offset`.
     *
     * The result is exact because the viewport never rotates and scales uniformly, so the
     * composition stays inside this parameterization: object translation is scaled and shifted
     * by the viewport offset, object scale multiplies the viewport scale, and rotation is
     * preserved. Used by the canvas to render an object transform under pan/zoom in one matrix.
     */
    fun throughViewport(
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ): Transform =
        Transform(
            translationX = translationX * scale + offsetX,
            translationY = translationY * scale + offsetY,
            scaleX = scaleX * scale,
            scaleY = scaleY * scale,
            rotationDegrees = rotationDegrees,
        )

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

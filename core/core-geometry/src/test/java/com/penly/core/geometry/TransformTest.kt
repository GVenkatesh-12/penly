package com.penly.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

class TransformTest {
    private val testPoints =
        listOf(
            Point(0f, 0f),
            Point(1f, 1f),
            Point(-3.5f, 2.25f),
            Point(100f, -50f),
            Point(0.001f, 999.99f),
            Point(-42f, -42f),
            Point(3f, 4f),
            Point(12.5f, -0.25f),
        )

    @Test
    fun applyInverse_returnsOriginalPoint_panOnly() {
        val transform = Transform(translationX = 12.5f, translationY = -3.25f)
        assertInverseRoundTrips(transform)
    }

    @Test
    fun applyInverse_returnsOriginalPoint_zoomOnly() {
        val transform = Transform(scaleX = 3f, scaleY = 3f)
        assertInverseRoundTrips(transform)
    }

    @Test
    fun applyInverse_returnsOriginalPoint_rotateScaleTranslate() {
        val transform =
            Transform(
                translationX = 10f,
                translationY = -7f,
                scaleX = 1.5f,
                scaleY = 1.5f,
                rotationDegrees = 30f,
            )
        assertInverseRoundTrips(transform)
    }

    @Test
    fun identity_appliesPointToItself() {
        for (p in testPoints) {
            assertEquals(p, Transform.IDENTITY.apply(p))
        }
    }

    @Test
    fun apply_scaleTwoTranslateTen_mapsExactly() {
        val transform = Transform(translationX = 10f, translationY = 10f, scaleX = 2f, scaleY = 2f)
        val result = transform.apply(Point(3f, 4f))
        assertEquals(16f, result.x, 0f)
        assertEquals(18f, result.y, 0f)
    }

    private fun assertInverseRoundTrips(transform: Transform) {
        val inverse = transform.inverse()
        for (p in testPoints) {
            val roundTripped = inverse.apply(transform.apply(p))
            assertEquals("x drift for $p", p.x, roundTripped.x, 0.001f)
            assertEquals("y drift for $p", p.y, roundTripped.y, 0.001f)
        }
    }
}

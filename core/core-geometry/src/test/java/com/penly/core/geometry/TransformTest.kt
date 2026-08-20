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

    @Test
    fun throughViewport_matchesManualViewportComposition() {
        // Object moved by (20, 30) page units under a 3x zoom with offset (-410, -410):
        // page point p must land at 3 * (p + (20, 30)) + (-410, -410).
        val transform = Transform(translationX = 20f, translationY = 30f)
        val composed = transform.throughViewport(scale = 3f, offsetX = -410f, offsetY = -410f)
        for (p in testPoints) {
            val expected = Point((p.x + 20f) * 3f - 410f, (p.y + 30f) * 3f - 410f)
            assertEquals("x for $p", expected.x, composed.apply(p).x, 0.001f)
            assertEquals("y for $p", expected.y, composed.apply(p).y, 0.001f)
        }
    }

    @Test
    fun throughViewport_scalesObjectScaleAndShiftsTranslation() {
        // Resized object (scale 2x, translation 10) under viewport scale 0.5, offset (7, -3).
        val transform = Transform(translationX = 10f, translationY = 10f, scaleX = 2f, scaleY = 2f)
        val composed = transform.throughViewport(scale = 0.5f, offsetX = 7f, offsetY = -3f)
        val expected = Transform(translationX = 12f, translationY = 2f, scaleX = 1f, scaleY = 1f)
        assertEquals(expected, composed)
        val result = composed.apply(Point(3f, 4f))
        assertEquals(15f, result.x, 0.001f)
        assertEquals(6f, result.y, 0.001f)
    }

    @Test
    fun throughViewport_preservesRotation() {
        val transform = Transform(rotationDegrees = 90f)
        val composed = transform.throughViewport(scale = 2f, offsetX = 5f, offsetY = 0f)
        assertEquals(90f, composed.rotationDegrees, 0f)
        val result = composed.apply(Point(1f, 0f))
        // (1,0) rotated 90° -> (0,1), scaled 2x -> (0,2), offset -> (5,2).
        assertEquals(5f, result.x, 0.001f)
        assertEquals(2f, result.y, 0.001f)
    }

    @Test
    fun identityThroughViewport_isTheViewportTransform() {
        val viewport = Transform.IDENTITY.throughViewport(scale = 3f, offsetX = -410f, offsetY = 120f)
        for (p in testPoints) {
            assertEquals(3f * p.x - 410f, viewport.apply(p).x, 0.001f)
            assertEquals(3f * p.y + 120f, viewport.apply(p).y, 0.001f)
        }
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

package com.penly.core.ink

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputSanitizerTest {
    @Test
    fun acceptsStrictlyIncreasingTime() {
        val sanitizer = InputSanitizer()
        assertTrue(sanitizer.accept(0f, 0f, 10L))
        assertTrue(sanitizer.accept(1f, 1f, 20L))
        assertTrue(sanitizer.accept(2f, 2f, 30L))
    }

    @Test
    fun rejectsOutOfOrderTime() {
        val sanitizer = InputSanitizer()
        sanitizer.accept(0f, 0f, 20L)
        assertFalse(sanitizer.accept(1f, 1f, 10L))
    }

    @Test
    fun rejectsExactDuplicateInput() {
        val sanitizer = InputSanitizer()
        sanitizer.accept(5f, 5f, 10L)
        assertFalse(sanitizer.accept(5f, 5f, 10L))
    }

    @Test
    fun acceptsSameTimeDifferentPosition() {
        val sanitizer = InputSanitizer()
        sanitizer.accept(5f, 5f, 10L)
        assertTrue(sanitizer.accept(6f, 5f, 10L))
    }

    @Test
    fun reset_allowsReuse() {
        val sanitizer = InputSanitizer()
        sanitizer.accept(5f, 5f, 10L)
        sanitizer.reset()
        assertTrue(sanitizer.accept(5f, 5f, 10L))
    }
}

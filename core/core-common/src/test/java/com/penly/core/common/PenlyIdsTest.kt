package com.penly.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PenlyIdsTest {
    @Test
    fun newId_returnsDifferentIds() {
        val first = PenlyIds.newId()
        val second = PenlyIds.newId()
        assertNotEquals(first, second)
    }

    @Test
    fun newId_matchesUuidLength() {
        assertEquals(36, PenlyIds.newId().length)
    }
}

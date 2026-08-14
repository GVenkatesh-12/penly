package com.penly.core.ink

class InputSanitizer {
    private var lastElapsedMillis: Long = -1L
    private var lastX: Float = Float.NaN
    private var lastY: Float = Float.NaN

    fun reset() {
        lastElapsedMillis = -1L
        lastX = Float.NaN
        lastY = Float.NaN
    }

    fun accept(
        x: Float,
        y: Float,
        elapsedTimeMillis: Long,
    ): Boolean {
        if (elapsedTimeMillis < lastElapsedMillis) return false
        if (elapsedTimeMillis == lastElapsedMillis && x == lastX && y == lastY) return false
        lastElapsedMillis = elapsedTimeMillis
        lastX = x
        lastY = y
        return true
    }
}

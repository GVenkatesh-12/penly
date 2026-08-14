package com.penly.core.ink

data class CanvasViewport(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    fun pan(
        deltaX: Float,
        deltaY: Float,
    ): CanvasViewport = copy(offsetX = offsetX + deltaX, offsetY = offsetY + deltaY)

    fun zoomAt(
        focusX: Float,
        focusY: Float,
        factor: Float,
    ): CanvasViewport {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val effectiveFactor = newScale / scale
        return copy(
            scale = newScale,
            offsetX = focusX - (focusX - offsetX) * effectiveFactor,
            offsetY = focusY - (focusY - offsetY) * effectiveFactor,
        )
    }

    fun pageToScreenX(x: Float): Float = x * scale + offsetX

    fun pageToScreenY(y: Float): Float = y * scale + offsetY

    fun screenToPageX(x: Float): Float = (x - offsetX) / scale

    fun screenToPageY(y: Float): Float = (y - offsetY) / scale

    companion object {
        const val MIN_SCALE: Float = 0.25f
        const val MAX_SCALE: Float = 8f
        val INITIAL = CanvasViewport()
    }
}

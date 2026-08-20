package com.penly.core.ink

enum class PenTool(
    val label: String,
    val defaultSize: Float,
    val defaultColorArgb: Int,
) {
    PEN("Pen", 5f, 0xFF1B2A4A.toInt()),
    PENCIL("Pencil", 4f, 0xFF37474F.toInt()),
    MARKER("Marker", 14f, 0xFF0077B6.toInt()),
    HIGHLIGHTER("Highlighter", 24f, 0xFFFFC300.toInt()),
    ERASER("Eraser", 0f, 0),
    ;

    val isStrokeTool: Boolean
        get() = this != ERASER
}

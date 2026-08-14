package com.penly.editor.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun fpsOverlay(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val fps = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        var frames = 0L
        var windowNanos = 0L
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    windowNanos += frameNanos - lastFrameNanos
                    frames++
                    if (windowNanos >= REPORT_WINDOW_NANOS) {
                        fps.floatValue = frames * 1e9f / windowNanos
                        frames = 0L
                        windowNanos = 0L
                    }
                }
                lastFrameNanos = frameNanos
            }
        }
    }
    Canvas(
        modifier = modifier.size(width = 76.dp, height = 26.dp),
        onDraw = {
            fps.floatValue
            drawRect(color = Color(0x99000000))
            val paint =
                android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 14.dp.toPx()
                }
            drawIntoCanvas { canvas ->
                val label = String.format(Locale.US, "%.0f fps", fps.floatValue)
                canvas.nativeCanvas.drawText(label, 6.dp.toPx(), 18.dp.toPx(), paint)
            }
        },
    )
}

private const val REPORT_WINDOW_NANOS: Long = 500_000_000L

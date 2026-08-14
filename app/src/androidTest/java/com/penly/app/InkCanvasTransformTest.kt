package com.penly.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.ink.brush.InputToolType
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.InProgressStroke
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.StrokeInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.penly.core.ink.BrushFactory
import com.penly.core.ink.CanvasViewport
import com.penly.core.ink.PenTool
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasTransformTest {
    @Test
    fun strokeRendersAtScreenPositionAfterPan() {
        val viewport = CanvasViewport(scale = 1f, offsetX = 100f, offsetY = 0f)
        // page (50,50) must land at screen (150,50), not at raw page coords (50,50)
        assertStrokeRendersAt(viewport, pageX = 50f, pageY = 50f, expectedX = 150f, expectedY = 50f)
    }

    @Test
    fun strokeRendersAtScreenPositionAfterZoom() {
        val viewport = CanvasViewport(scale = 2f, offsetX = -200f, offsetY = 0f)
        // page (150,100) must land at screen (100,200), not at (150,100)
        assertStrokeRendersAt(viewport, pageX = 150f, pageY = 100f, expectedX = 100f, expectedY = 200f)
    }

    private fun assertStrokeRendersAt(
        viewport: CanvasViewport,
        pageX: Float,
        pageY: Float,
        expectedX: Float,
        expectedY: Float,
    ) {
        val stroke =
            InProgressStroke()
                .apply {
                    start(BrushFactory.createBrush(PenTool.PEN, PenTool.PEN.defaultSize, PenTool.PEN.defaultColorArgb))
                    enqueueInputs(
                        MutableStrokeInputBatch()
                            .add(
                                StrokeInput().apply { update(pageX, pageY, 0L, InputToolType.STYLUS, 1f) },
                            ),
                        MutableStrokeInputBatch(),
                    )
                    finishInput()
                    updateShape()
                }.toImmutable()

        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val matrix =
            Matrix().apply {
                setScale(viewport.scale, viewport.scale)
                postTranslate(viewport.offsetX, viewport.offsetY)
            }
        val renderer = CanvasStrokeRenderer.create()

        // Fixed pattern — canvas must carry the viewport transform (androidx Ink contract).
        canvas.save()
        canvas.concat(matrix)
        renderer.draw(canvas, stroke, matrix)
        canvas.restore()

        assertTrue(
            "ink expected near screen ($expectedX, $expectedY)",
            bitmap.hasInkNear(expectedX, expectedY),
        )
        assertFalse(
            "ink must not land at raw page coords ($pageX, $pageY) when viewport != identity",
            bitmap.hasInkNear(pageX, pageY),
        )
    }

    private fun Bitmap.hasInkNear(
        x: Float,
        y: Float,
        radius: Float = 12f,
    ): Boolean {
        val minX = (x - radius).toInt().coerceAtLeast(0)
        val maxX = (x + radius).toInt().coerceAtMost(width - 1)
        val minY = (y - radius).toInt().coerceAtLeast(0)
        val maxY = (y + radius).toInt().coerceAtMost(height - 1)
        for (py in minY..maxY) {
            for (px in minX..maxX) {
                if (Color.alpha(getPixel(px, py)) >= 0x80) return true
            }
        }
        return false
    }
}

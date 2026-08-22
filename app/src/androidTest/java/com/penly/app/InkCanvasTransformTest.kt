package com.penly.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.InProgressStroke
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.StrokeInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.penly.core.common.PenlyIds
import com.penly.core.geometry.Point
import com.penly.core.geometry.Transform
import com.penly.core.ink.BrushFactory
import com.penly.core.ink.CanvasViewport
import com.penly.core.ink.PenTool
import com.penly.core.ink.StrokeRecord
import com.penly.core.model.ObjectId
import com.penly.editor.canvas.INK_CANVAS_TAG
import com.penly.editor.canvas.InkCanvasState
import com.penly.editor.canvas.inkCanvas
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders the real [inkCanvas] composable and asserts committed ink lands at
 * `page * scale + offset` in screen pixels.
 *
 * Regression guard for the dropped-viewport-translation bug: [CanvasStrokeRenderer.draw]
 * applies its matrix argument modulo translation only, so the canvas itself must carry the
 * pan/zoom transform. Without that, ink ignores the viewport while images and text (drawn
 * under the canvas transform) follow it — and eraser/lasso hit-testing, which works in page
 * space, stops matching the visible pixels.
 */
@RunWith(AndroidJUnit4::class)
class InkCanvasTransformTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun strokeRendersAtScreenPositionAfterPan() {
        // page (50,50) must land at screen (150,50); offset-dropped rendering puts it at (50,50).
        assertStrokeRendersAt(
            viewport = CanvasViewport(scale = 1f, offsetX = 100f, offsetY = 0f),
            objectTransform = Transform.IDENTITY,
            page = Point(50f, 50f),
            expected = Point(150f, 50f),
            droppedTranslation = Point(50f, 50f),
        )
    }

    @Test
    fun strokeRendersAtScreenPositionAfterZoom() {
        // page (150,100) must land at screen (100,200);
        // offset-dropped rendering puts it at (300,200).
        assertStrokeRendersAt(
            viewport = CanvasViewport(scale = 2f, offsetX = -200f, offsetY = 0f),
            objectTransform = Transform.IDENTITY,
            page = Point(150f, 100f),
            expected = Point(100f, 200f),
            droppedTranslation = Point(300f, 200f),
        )
    }

    @Test
    fun movedStrokeRendersAtMovedScreenPosition() {
        // A lasso-moved stroke (object transform translate(30,40)) under the same viewport:
        // page (150,100) must land at screen ((150+30)*2-200, (100+40)*2) = (160,280).
        assertStrokeRendersAt(
            viewport = CanvasViewport(scale = 2f, offsetX = -200f, offsetY = 0f),
            objectTransform = Transform(translationX = 30f, translationY = 40f),
            page = Point(150f, 100f),
            expected = Point(160f, 280f),
            droppedTranslation = Point(100f, 200f),
        )
    }

    private fun assertStrokeRendersAt(
        viewport: CanvasViewport,
        objectTransform: Transform,
        page: Point,
        expected: Point,
        droppedTranslation: Point,
    ) {
        val record =
            StrokeRecord(
                objectId = ObjectId(PenlyIds.newId()),
                stroke = dotStroke(page.x, page.y),
                bounds = effectiveBounds(page, objectTransform),
                transform = objectTransform,
            )
        val state = InkCanvasState()
        state.loadRecords(listOf(record))
        applyViewport(state, viewport)

        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                inkCanvas(state = state, modifier = Modifier.fillMaxSize())
            }
        }
        val bitmap = composeRule.onNodeWithTag(INK_CANVAS_TAG).captureToImage().asAndroidBitmap()

        assertTrue(
            "ink expected near screen (${expected.x}, ${expected.y})",
            bitmap.hasInkNear(expected.x, expected.y),
        )
        assertFalse(
            "ink must not render near (${droppedTranslation.x}, ${droppedTranslation.y})",
            bitmap.hasInkNear(droppedTranslation.x, droppedTranslation.y),
        )
    }

    /** Reaches [target] through the exact-arithmetic pan/zoom API, no gestures needed. */
    private fun applyViewport(
        state: InkCanvasState,
        target: CanvasViewport,
    ) {
        if (target.scale != 1f) state.zoomAt(0f, 0f, target.scale)
        state.pan(target.offsetX, target.offsetY)
    }

    private fun dotStroke(
        x: Float,
        y: Float,
    ): androidx.ink.strokes.Stroke =
        InProgressStroke()
            .apply {
                start(BrushFactory.createBrush(PenTool.PEN, PenTool.PEN.defaultSize, PenTool.PEN.defaultColorArgb))
                enqueueInputs(inputsAt(x, y), MutableStrokeInputBatch())
                finishInput()
                updateShape()
            }.toImmutable()

    private fun inputsAt(
        x: Float,
        y: Float,
    ): MutableStrokeInputBatch =
        MutableStrokeInputBatch().add(
            StrokeInput().apply {
                update(x = x, y = y, elapsedTimeMillis = 0L, toolType = InputToolType.STYLUS, pressure = 1f)
            },
        )

    private fun effectiveBounds(
        page: Point,
        transform: Transform,
    ): RectF {
        val p = transform.apply(page)
        return RectF(p.x, p.y, p.x, p.y)
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
                if (isInk(getPixel(px, py))) return true
            }
        }
        return false
    }

    /**
     * The composited canvas is opaque white, so every screen pixel has alpha 0xFF and an
     * alpha check would match everything. Ink (dark navy) is detected as any pixel meaningfully
     * darker than the white background on any channel; antialiased edge blends qualify too.
     */
    private fun isInk(pixel: Int): Boolean =
        Color.red(pixel) < BACKGROUND_CHANNEL_MAX ||
            Color.green(pixel) < BACKGROUND_CHANNEL_MAX ||
            Color.blue(pixel) < BACKGROUND_CHANNEL_MAX

    private companion object {
        const val BACKGROUND_CHANNEL_MAX: Int = 0xE6
    }
}

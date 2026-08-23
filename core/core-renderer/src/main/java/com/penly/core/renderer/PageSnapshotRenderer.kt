package com.penly.core.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import com.penly.core.geometry.Rect
import com.penly.core.geometry.Transform
import com.penly.core.ink.StrokeRecord
import com.penly.core.model.ImageObject
import com.penly.core.model.ObjectId
import com.penly.core.model.PageObject
import com.penly.core.model.PageTemplate
import com.penly.core.model.TextObject
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Renders a page (template background + strokes + objects) into a bitmap for the thumbnail
 * cache, fit-to-content. Pure android.graphics — no Compose — so it can run on any thread.
 * Drawing is guarded: a renderer failure degrades to the partially drawn/blank bitmap and
 * must never crash the caller.
 */
object PageSnapshotRenderer {
    /** Empty-page canvas size in page units (3:4). */
    private const val EMPTY_PAGE_WIDTH_PX: Float = 768f
    private const val EMPTY_PAGE_HEIGHT_PX: Float = 1024f

    /** Thumbnail WebP quality (0-100). */
    private const val WEBP_QUALITY: Int = 80

    private val strokeRenderer: CanvasStrokeRenderer = CanvasStrokeRenderer.create()

    private val textPaint =
        Paint().apply {
            isAntiAlias = true
        }

    /**
     * Renders [strokes] + [objects] + template background fit-to-bounds into a bitmap exactly
     * [targetWidthPx] wide. An empty page renders as a blank 3:4 canvas.
     *
     * The composed page→bitmap matrix is built exactly like InkCanvas's transformToMatrix so
     * thumbnails match what the editor shows (CanvasStrokeRenderer applies its matrix argument
     * modulo translation only; the canvas carries the rest).
     */
    fun render(
        strokes: List<StrokeRecord>,
        objects: List<PageObject>,
        imageFor: (ObjectId) -> Bitmap?,
        template: PageTemplate,
        targetWidthPx: Int,
    ): Bitmap {
        val bounds = contentBounds(strokes, objects)
        val scale =
            if (bounds.isEmpty) {
                targetWidthPx / EMPTY_PAGE_WIDTH_PX
            } else {
                targetWidthPx / bounds.width
            }
        val widthPx = max(1, targetWidthPx)
        val heightPx =
            max(
                1,
                ((if (bounds.isEmpty) EMPTY_PAGE_HEIGHT_PX else bounds.height) * scale).toInt(),
            )
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        return try {
            canvas.drawColor(android.graphics.Color.WHITE)
            val fit =
                Transform(
                    translationX = -bounds.left * scale,
                    translationY = -bounds.top * scale,
                    scaleX = scale,
                    scaleY = scale,
                )
            val matrix = transformToMatrix(fit)
            PageTemplates.drawBackground(
                canvas,
                template,
                RectF(
                    bounds.left,
                    bounds.top,
                    if (bounds.isEmpty) bounds.left + EMPTY_PAGE_WIDTH_PX else bounds.right,
                    if (bounds.isEmpty) bounds.top + EMPTY_PAGE_HEIGHT_PX else bounds.bottom,
                ),
            )
            canvas.save()
            canvas.concat(matrix)
            try {
                for (record in strokes) {
                    drawStroke(canvas, record, fit, matrix)
                }
                for (obj in objects) {
                    drawObject(canvas, obj, imageFor)
                }
            } finally {
                canvas.restore()
            }
            bitmap
        } catch (error: RuntimeException) {
            bitmap
        }
    }

    /** Encodes a rendered page for the thumbnail cache (lossy WebP; legacy WebP below API 30). */
    fun encodeWebP(bitmap: Bitmap): ByteArray {
        val format =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        val out = ByteArrayOutputStream()
        bitmap.compress(format, WEBP_QUALITY, out)
        return out.toByteArray()
    }

    private fun contentBounds(
        strokes: List<StrokeRecord>,
        objects: List<PageObject>,
    ): Rect {
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY

        fun include(
            l: Float,
            t: Float,
            r: Float,
            b: Float,
        ) {
            left = min(left, l)
            top = min(top, t)
            right = max(right, r)
            bottom = max(bottom, b)
        }
        for (record in strokes) {
            include(record.bounds.left, record.bounds.top, record.bounds.right, record.bounds.bottom)
        }
        for (obj in objects) {
            val b = obj.bounds
            if (!b.isEmpty) include(b.left, b.top, b.right, b.bottom)
        }
        if (left == Float.POSITIVE_INFINITY) return Rect(0f, 0f, 0f, 0f)
        return Rect(left, top, right, bottom)
    }

    /** Mirrors InkCanvas's per-record composition for transformed strokes. */
    private fun drawStroke(
        canvas: Canvas,
        record: StrokeRecord,
        fit: Transform,
        fitMatrix: Matrix,
    ) {
        if (record.transform == Transform.IDENTITY) {
            strokeRenderer.draw(canvas, record.stroke, fitMatrix)
        } else {
            val objectMatrix = transformToMatrix(record.transform)
            val combined =
                transformToMatrix(
                    record.transform.throughViewport(fit.scaleX, fit.translationX, fit.translationY),
                )
            canvas.save()
            canvas.concat(objectMatrix)
            try {
                strokeRenderer.draw(canvas, record.stroke, combined)
            } finally {
                canvas.restore()
            }
        }
    }

    /** Mirrors InkCanvas.drawObject text/image semantics without selection overlays. */
    private fun drawObject(
        canvas: Canvas,
        obj: PageObject,
        imageFor: (ObjectId) -> Bitmap?,
    ) {
        when (obj) {
            is TextObject -> {
                textPaint.textSize = obj.fontSize
                textPaint.color = obj.colorArgb
                var baseline = obj.bounds.top - textPaint.fontMetrics.ascent
                val lineSpacing = textPaint.fontSpacing
                for (line in obj.text.split('\n')) {
                    canvas.drawText(line, obj.bounds.left, baseline, textPaint)
                    baseline += lineSpacing
                }
            }
            is ImageObject -> {
                val bitmap = imageFor(obj.objectId) ?: return
                canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(obj.bounds.left, obj.bounds.top, obj.bounds.right, obj.bounds.bottom),
                    null,
                )
            }
            else -> Unit
        }
    }

    /**
     * Converts a [Transform] (`p' = t + R * S * p`) into an Android [Matrix]
     * (`M = T * R * S`), identical to editor-canvas's helper (copied, not widened).
     */
    private fun transformToMatrix(transform: Transform): Matrix =
        Matrix().apply {
            setScale(transform.scaleX, transform.scaleY)
            postRotate(transform.rotationDegrees)
            postTranslate(transform.translationX, transform.translationY)
        }
}

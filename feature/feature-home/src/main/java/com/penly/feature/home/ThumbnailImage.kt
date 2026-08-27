package com.penly.feature.home

import android.graphics.BitmapFactory
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import com.penly.core.document.PenlyStore
import com.penly.core.model.DocumentId
import com.penly.core.model.PageId
import com.penly.core.model.PageTemplate
import com.penly.core.renderer.PageTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Process-wide thumbnail cache. Thumbnails are small (512px-wide WebP) so a fixed 24-entry
 * cache keeps the library grid smooth without an image-loader dependency.
 */
private val thumbnailCache = LruCache<String, ImageBitmap>(24)

/**
 * Shows the stored thumbnail for a page; while it is missing (never rendered yet) a
 * placeholder painted with the page's [template] pattern is shown, so tiles look correct
 * instantly and pan-consistently with the real background once opened in the editor.
 */
@Composable
fun thumbnailImage(
    store: PenlyStore,
    documentId: DocumentId,
    pageId: PageId,
    template: PageTemplate,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val key = "${documentId.value}/${pageId.value}"
    val bitmap by produceState<ImageBitmap?>(thumbnailCache.get(key), key) {
        if (value == null) {
            val decoded =
                withContext(Dispatchers.IO) {
                    runCatching {
                        store.openThumbnail(documentId, pageId)?.let { bytes ->
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }
                    }.getOrNull()
                }
            if (decoded != null) {
                thumbnailCache.put(key, decoded)
                value = decoded
            }
        }
    }
    val shown = bitmap
    if (shown != null) {
        Image(
            bitmap = shown,
            contentDescription = contentDescription,
            modifier = modifier.clipToBounds(),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier.background(Color.White)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas
                    nativeCanvas.save()
                    nativeCanvas.scale(size.width / THUMBNAIL_PREVIEW_UNITS, size.height / THUMBNAIL_PREVIEW_UNITS)
                    try {
                        PageTemplates.drawBackground(
                            nativeCanvas,
                            template,
                            RectF(0f, 0f, THUMBNAIL_PREVIEW_UNITS, THUMBNAIL_PREVIEW_UNITS),
                        )
                    } finally {
                        nativeCanvas.restore()
                    }
                }
            }
        }
    }
}

private const val THUMBNAIL_PREVIEW_UNITS: Float = 256f

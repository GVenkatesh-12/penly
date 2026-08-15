package com.penly.feature.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.penly.core.common.PenlyIds
import com.penly.core.document.InkObjectMapper
import com.penly.core.document.LoadResult
import com.penly.core.document.PenlyStore
import com.penly.core.geometry.Point
import com.penly.core.geometry.Rect
import com.penly.core.ink.StrokeRecord
import com.penly.core.model.Document
import com.penly.core.model.DocumentId
import com.penly.core.model.ImageObject
import com.penly.core.model.InkObject
import com.penly.core.model.ObjectId
import com.penly.core.model.Page
import com.penly.core.model.PageId
import com.penly.core.model.PageObject
import com.penly.editor.canvas.InkCanvasState
import com.penly.editor.canvas.fpsOverlay
import com.penly.editor.canvas.inkCanvas
import com.penly.editor.canvas.rememberInkCanvasState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun editorScreen(
    state: InkCanvasState,
    modifier: Modifier = Modifier,
    store: PenlyStore? = null,
    documentIdProvider: () -> DocumentId? = { null },
) {
    var showTextDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imagePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                val activeStore = store ?: return@rememberLauncherForActivityResult
                scope.launch {
                    val bytesAndMime =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val bytes =
                                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                        ?: error("cannot open picked image")
                                val mime = context.contentResolver.getType(uri) ?: "image/*"
                                bytes to mime
                            }.getOrNull()
                        }
                    if (bytesAndMime == null) {
                        Log.w(TAG, "failed to read picked image $uri")
                        return@launch
                    }
                    val (bytes, mime) = bytesAndMime
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap == null) {
                        Log.w(TAG, "failed to decode picked image $uri")
                        return@launch
                    }
                    val scaled = scaleDown(bitmap, MAX_IMAGE_WIDTH)
                    val center = viewportCenterPage(state)
                    val bounds =
                        Rect(
                            left = center.x - scaled.width / 2f,
                            top = center.y - scaled.height / 2f,
                            right = center.x + scaled.width / 2f,
                            bottom = center.y + scaled.height / 2f,
                        )
                    val documentId = documentIdProvider()
                    if (documentId == null) {
                        Log.w(TAG, "no document id yet; cannot store image asset")
                        return@launch
                    }
                    val name = "${PenlyIds.newId()}.img"
                    val payloadRef =
                        withContext(Dispatchers.IO) {
                            activeStore.putAsset(documentId, name, bytes)
                        }
                    val objectId = ObjectId(PenlyIds.newId())
                    state.insertImage(objectId, bounds, mime, payloadRef)
                    state.setImage(objectId, scaled)
                }
            },
        )
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Page 1") },
                actions = {
                    TextButton(onClick = state::undo, enabled = state.canUndo) {
                        Text("Undo")
                    }
                    TextButton(onClick = state::redo, enabled = state.canRedo) {
                        Text("Redo")
                    }
                    TextButton(onClick = state::clearAll) {
                        Text("Clear")
                    }
                    TextButton(onClick = { showTextDialog = true }) {
                        Text("Text")
                    }
                    if (store != null) {
                        TextButton(
                            onClick = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                        ) {
                            Text("Image")
                        }
                    }
                    if (state.selectionMode && state.selectionBounds != null) {
                        TextButton(onClick = state::deleteSelection) {
                            Text("Delete")
                        }
                        TextButton(onClick = state::copySelection) {
                            Text("Copy")
                        }
                    }
                },
            )
        },
        bottomBar = {
            brushBar(
                tool = state.tool,
                onToolSelected = state::selectTool,
                selectionMode = state.selectionMode,
                onSelectionModeChange = state::setSelectionMode,
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            inkCanvas(state = state, modifier = Modifier.fillMaxSize())
            fpsOverlay(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
            )
        }
    }
    if (showTextDialog) {
        var textInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add text") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Text") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            state.insertText(
                                text = textInput,
                                fontSize = TEXT_FONT_SIZE,
                                colorArgb = TEXT_COLOR,
                                at = viewportCenterPage(state),
                            )
                        }
                        showTextDialog = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/**
 * Editor backed by a [PenlyStore]: loads the most recently written document on open and
 * persists the full page after every mutation (stroke commit, undo, redo, erase, clear,
 * text/image insert, move). Saves run off the main thread and are serialized so rapid edits
 * never interleave on disk. When no document exists yet one is materialized in memory so
 * image assets always have a stable document id.
 */
@Composable
fun editorScreen(
    store: PenlyStore,
    modifier: Modifier = Modifier,
) {
    val state = rememberInkCanvasState()
    val scope = rememberCoroutineScope()
    val saveMutex = remember { Mutex() }
    var document by remember { mutableStateOf<Document?>(null) }
    val currentDocument by rememberUpdatedState(document)

    LaunchedEffect(store) {
        val loaded =
            withContext(Dispatchers.IO) {
                runCatching {
                    val latestId = store.listDocuments().lastOrNull() ?: return@withContext null
                    when (val result = store.load(latestId)) {
                        is LoadResult.Success -> result.document
                        is LoadResult.Failure -> {
                            Log.w(TAG, "failed to load document $latestId: ${result.reason}")
                            null
                        }
                    }
                }.getOrElse { error ->
                    Log.w(TAG, "failed to load latest document", error)
                    null
                }
            }
        val page = loaded?.pages?.firstOrNull()
        if (page != null) {
            val loadedContent =
                withContext(Dispatchers.IO) {
                    loadPageContent(store, loaded.documentId, page)
                }
            state.loadRecords(loadedContent.records)
            state.loadObjects(loadedContent.objects)
            for ((objectId, bitmap) in loadedContent.images) {
                state.setImage(objectId, bitmap)
            }
            document = loaded
        } else {
            document = createFreshDocument()
        }
    }

    SideEffect {
        state.onStrokesChanged = { records ->
            scope.launch {
                saveMutex.withLock {
                    val current = currentDocument
                    val saved =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val updated = buildDocument(current, records, state.objects)
                                store.save(updated)
                                updated
                            }.getOrElse { error ->
                                Log.w(TAG, "failed to save page", error)
                                null
                            }
                        }
                    if (saved != null) {
                        document = saved
                    }
                }
            }
        }
    }

    editorScreen(
        state = state,
        modifier = modifier,
        store = store,
        documentIdProvider = { document?.documentId },
    )
}

@Composable
fun editorScreen(modifier: Modifier = Modifier) {
    val state = rememberInkCanvasState()
    editorScreen(state = state, modifier = modifier)
}

private class LoadedPageContent(
    val records: List<StrokeRecord>,
    val objects: List<PageObject>,
    val images: Map<ObjectId, Bitmap>,
)

/** Splits a loaded page into stroke records, non-ink objects, and decoded image bitmaps. */
private fun loadPageContent(
    store: PenlyStore,
    documentId: DocumentId,
    page: Page,
): LoadedPageContent {
    val records = mutableListOf<StrokeRecord>()
    val objects = mutableListOf<PageObject>()
    val images = mutableMapOf<ObjectId, Bitmap>()
    for (obj in page.objects) {
        when (obj) {
            is InkObject -> InkObjectMapper.toStrokeRecord(obj)?.let { records += it }
            is ImageObject -> {
                objects += obj
                val payloadRef = obj.payloadRef
                if (payloadRef != null) {
                    val bytes =
                        runCatching { store.openAsset(documentId, payloadRef) }
                            .getOrNull()
                    if (bytes != null) {
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            images[obj.objectId] = bitmap
                        } else {
                            Log.w(TAG, "corrupt image asset for ${obj.objectId}: $payloadRef")
                        }
                    } else {
                        Log.w(TAG, "missing image asset for ${obj.objectId}: $payloadRef")
                    }
                }
            }
            else -> objects += obj
        }
    }
    return LoadedPageContent(records, objects, images)
}

private fun buildDocument(
    current: Document?,
    records: List<StrokeRecord>,
    objects: List<PageObject>,
): Document {
    val now = System.currentTimeMillis()
    val base =
        current ?: Document(
            documentId = DocumentId(PenlyIds.newId()),
            title = "Untitled",
            createdAtMillis = now,
            updatedAtMillis = now,
        )
    val existingPage = base.pages.firstOrNull()
    val page =
        existingPage ?: Page(
            pageId = PageId(PenlyIds.newId()),
            documentId = base.documentId,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
    val pageObjects =
        records.map { record -> InkObjectMapper.toInkObject(record, nowMillis = now) } + objects
    val updatedPage =
        page.copy(
            objects = pageObjects,
            revision = page.revision + 1,
            updatedAtMillis = now,
        )
    return base.copy(
        pages = listOf(updatedPage),
        revision = base.revision + 1,
        updatedAtMillis = now,
    )
}

private fun createFreshDocument(): Document {
    val now = System.currentTimeMillis()
    val documentId = DocumentId(PenlyIds.newId())
    return Document(
        documentId = documentId,
        title = "Untitled",
        pages =
            listOf(
                Page(
                    pageId = PageId(PenlyIds.newId()),
                    documentId = documentId,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            ),
        createdAtMillis = now,
        updatedAtMillis = now,
    )
}

/** The canvas center in page coordinates, from the last measured canvas size. */
private fun viewportCenterPage(state: InkCanvasState): Point {
    val size = state.lastCanvasSize
    return Point(
        x = state.viewport.screenToPageX(size.width / 2f),
        y = state.viewport.screenToPageY(size.height / 2f),
    )
}

/** Downscales [bitmap] so its width is at most [maxWidth], preserving aspect ratio. */
private fun scaleDown(
    bitmap: Bitmap,
    maxWidth: Float,
): Bitmap {
    if (bitmap.width <= maxWidth) return bitmap
    val scale = maxWidth / bitmap.width
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1),
        true,
    )
}

private const val TAG: String = "EditorScreen"
private const val TEXT_FONT_SIZE: Float = 32f
private const val TEXT_COLOR: Int = 0xFF1B2A4A.toInt()
private const val MAX_IMAGE_WIDTH: Float = 400f

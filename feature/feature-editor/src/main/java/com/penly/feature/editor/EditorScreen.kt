package com.penly.feature.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.penly.core.renderer.PageSnapshotRenderer
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
    recovered: Boolean = false,
    documentIdProvider: () -> DocumentId? = { null },
    title: String = "Page 1",
    /** 0-based index of the shown page; the pager row is hidden when null or single-page. */
    pageIndex: Int? = null,
    pageCount: Int = 0,
    onNavigateBack: (() -> Unit)? = null,
    onPrevPage: (() -> Unit)? = null,
    onNextPage: (() -> Unit)? = null,
) {
    var showTextDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
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
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = {
                    if (pageIndex == null || pageCount <= 1) {
                        Text(title)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(title, style = MaterialTheme.typography.titleLarge)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onPrevPage?.invoke() },
                                    enabled = pageIndex > 0,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Previous page",
                                    )
                                }
                                Text(
                                    "Page ${pageIndex + 1} of $pageCount",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                IconButton(
                                    onClick = { onNextPage?.invoke() },
                                    enabled = pageIndex < pageCount - 1,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Next page",
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = state::undo, enabled = state.canUndo) {
                            Text("Undo")
                        }
                        TextButton(onClick = state::redo, enabled = state.canRedo) {
                            Text("Redo")
                        }
                        if (state.selectionMode && state.selectionBounds != null) {
                            TextButton(onClick = state::deleteSelection) {
                                Text("Delete")
                            }
                            TextButton(onClick = state::cutSelection) {
                                Text("Cut")
                            }
                            TextButton(onClick = state::copySelection) {
                                Text("Copy")
                            }
                            TextButton(onClick = { state.duplicateSelection() }) {
                                Text("Duplicate")
                            }
                        } else {
                            TextButton(onClick = { showClearDialog = true }) {
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
                        }
                        if (state.canPaste) {
                            TextButton(onClick = { state.paste() }) {
                                Text("Paste")
                            }
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
            if (recovered) {
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = "Recovered unsaved changes",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
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
                    singleLine = false,
                    maxLines = 5,
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
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear page") },
            text = { Text("Are you sure you want to clear all strokes and objects on this page?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.clearAll()
                        showClearDialog = false
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/**
 * Document-scoped editor: loads [documentId] (navigating back when it cannot be opened),
 * shows one page at a time with a page switcher, persists ONLY the current page after every
 * mutation, and regenerates that page's thumbnail on each successful save. Saves are
 * serialized through a mutex so rapid edits never interleave on disk; thumbnail failures
 * are logged and never fail the save. Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z drive undo/redo from
 * a hardware keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun editorDocumentScreen(
    store: PenlyStore,
    documentId: DocumentId,
    initialPageId: PageId?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberInkCanvasState()
    val scope = rememberCoroutineScope()
    val saveMutex = remember { Mutex() }
    val focusRequester = remember { FocusRequester() }
    var document by remember { mutableStateOf<Document?>(null) }
    var currentPageId by remember { mutableStateOf<PageId?>(null) }
    var recovered by remember { mutableStateOf(false) }

    LaunchedEffect(documentId) {
        val result =
            withContext(Dispatchers.IO) {
                runCatching { store.load(documentId) }.getOrElse { error ->
                    Log.w(TAG, "failed to load document $documentId", error)
                    null
                }
            }
        val loaded = result as? LoadResult.Success
        if (loaded == null) {
            if (result is LoadResult.Failure) {
                Log.w(TAG, "failed to load document $documentId: ${result.reason}")
            }
            onNavigateBack()
            return@LaunchedEffect
        }
        val pages = loaded.document.pages
        val target =
            initialPageId?.takeIf { id -> pages.any { it.pageId == id } }
                ?: pages.lastOrNull()?.pageId
        val page = target?.let { id -> pages.firstOrNull { it.pageId == id } }
        if (page == null) {
            onNavigateBack()
            return@LaunchedEffect
        }
        showPage(store, state, documentId, page)
        document = loaded.document
        currentPageId = page.pageId
        recovered = loaded.recovered
    }

    SideEffect {
        state.onStrokesChanged = { records ->
            scope.launch {
                saveMutex.withLock {
                    persistCurrentPage(
                        store,
                        document,
                        currentPageId,
                        records.toList(),
                        state.objects.toList(),
                    )?.let { saved ->
                        document = saved
                        writeThumbnail(store, state, documentId, currentPageId)
                    }
                }
            }
        }
    }

    fun switchPage(target: PageId) {
        if (target == currentPageId) return
        scope.launch {
            saveMutex.withLock {
                persistCurrentPage(
                    store,
                    document,
                    currentPageId,
                    state.strokes.toList(),
                    state.objects.toList(),
                )?.let { saved ->
                    document = saved
                    writeThumbnail(store, state, documentId, currentPageId)
                }
                val doc = document ?: return@withLock
                val page = doc.pages.firstOrNull { it.pageId == target } ?: return@withLock
                showPage(store, state, doc.documentId, page)
                currentPageId = target
            }
        }
    }

    val pages = document?.pages.orEmpty()
    val shownIndex = pages.indexOfFirst { it.pageId == currentPageId }.takeIf { it >= 0 }
    val keyEventHandler =
        Modifier.onPreviewKeyEvent { event ->
            // Only act once per physical press: ACTION_UP would otherwise repeat the edit.
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val undoRequested =
                event.isCtrlPressed && event.key == Key.Z && !event.isShiftPressed
            val redoRequested =
                event.isCtrlPressed &&
                    (event.key == Key.Y || (event.key == Key.Z && event.isShiftPressed))
            when {
                undoRequested -> {
                    state.undo()
                    true
                }
                redoRequested -> {
                    state.redo()
                    true
                }
                else -> false
            }
        }
    Box(
        modifier =
            modifier
                .focusRequester(focusRequester)
                .focusable()
                .then(keyEventHandler),
    ) {
        editorScreen(
            state = state,
            store = store,
            recovered = recovered,
            documentIdProvider = { document?.documentId },
            title = document?.title ?: "Untitled",
            pageIndex = shownIndex,
            pageCount = pages.size,
            onNavigateBack = onNavigateBack,
            onPrevPage = {
                shownIndex?.let { index ->
                    pages.getOrNull(index - 1)?.pageId?.let(::switchPage)
                }
            },
            onNextPage = {
                shownIndex?.let { index ->
                    pages.getOrNull(index + 1)?.pageId?.let(::switchPage)
                }
            },
        )
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
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

/** Loads [page]'s content into [state]: stroke records, non-ink objects, images, template. */
private suspend fun showPage(
    store: PenlyStore,
    state: InkCanvasState,
    documentId: DocumentId,
    page: Page,
) {
    val content =
        withContext(Dispatchers.IO) {
            loadPageContent(store, documentId, page)
        }
    state.loadRecords(content.records)
    state.loadObjects(content.objects)
    for ((objectId, bitmap) in content.images) {
        state.setImage(objectId, bitmap)
    }
    state.setPageTemplate(page.template)
}

/**
 * Rebuilds ONLY [currentPageId] from [records] + [objects] and saves the document. Returns
 * the saved [Document] (with bumped revisions) or null when the save failed. Callers hold
 * the editor's save mutex.
 */
private suspend fun persistCurrentPage(
    store: PenlyStore,
    document: Document?,
    currentPageId: PageId?,
    records: List<StrokeRecord>,
    objects: List<PageObject>,
): Document? {
    val base = document ?: return null
    val pageId = currentPageId ?: return null
    val now = System.currentTimeMillis()
    val pageObjects =
        records.map { record -> InkObjectMapper.toInkObject(record, nowMillis = now) } + objects
    val updated =
        base.copy(
            pages =
                base.pages.map { page ->
                    if (page.pageId == pageId) {
                        page.copy(
                            objects = pageObjects,
                            revision = page.revision + 1,
                            updatedAtMillis = now,
                        )
                    } else {
                        page
                    }
                },
            revision = base.revision + 1,
            updatedAtMillis = now,
        )
    val saved =
        withContext(Dispatchers.IO) {
            runCatching { store.save(updated) }.getOrElse { error ->
                Log.w(TAG, "failed to save page", error)
                null
            }
        }
    return if (saved != null) updated else null
}

/**
 * Regenerates the current page's cached thumbnail from live canvas state. Best-effort:
 * any failure is logged and never propagates to the save path.
 */
private suspend fun writeThumbnail(
    store: PenlyStore,
    state: InkCanvasState,
    documentId: DocumentId,
    pageId: PageId?,
) {
    if (pageId == null) return
    try {
        val strokes = state.strokes.toList()
        val objects = state.objects.toList()
        val template = state.pageTemplate
        val bytes =
            withContext(Dispatchers.Default) {
                PageSnapshotRenderer.encodeWebP(
                    PageSnapshotRenderer.render(
                        strokes,
                        objects,
                        { objectId -> state.images[objectId] },
                        template,
                        THUMBNAIL_WIDTH_PX,
                    ),
                )
            }
        withContext(Dispatchers.IO) { store.putThumbnail(documentId, pageId, bytes) }
    } catch (error: RuntimeException) {
        Log.w(TAG, "thumbnail regeneration failed", error)
    }
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
private const val THUMBNAIL_WIDTH_PX: Int = 512

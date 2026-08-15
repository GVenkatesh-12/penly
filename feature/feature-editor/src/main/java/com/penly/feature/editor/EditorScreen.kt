package com.penly.feature.editor

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import com.penly.core.common.PenlyIds
import com.penly.core.document.InkObjectMapper
import com.penly.core.document.LoadResult
import com.penly.core.document.PaperForgeStore
import com.penly.core.ink.StrokeRecord
import com.penly.core.model.Document
import com.penly.core.model.DocumentId
import com.penly.core.model.InkObject
import com.penly.core.model.Page
import com.penly.core.model.PageId
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
) {
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
                },
            )
        },
        bottomBar = {
            brushBar(tool = state.tool, onToolSelected = state::selectTool)
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
}

/**
 * Editor backed by a [PaperForgeStore]: loads the most recently written document on open and
 * persists the full page after every mutation (stroke commit, undo, redo, erase, clear).
 * Saves run off the main thread and are serialized so rapid edits never interleave on disk.
 */
@Composable
fun editorScreen(
    store: PaperForgeStore,
    modifier: Modifier = Modifier,
) {
    val state = rememberInkCanvasState()
    val scope = rememberCoroutineScope()
    val saveMutex = remember { Mutex() }
    var document by remember { mutableStateOf<Document?>(null) }
    val currentDocument by rememberUpdatedState(document)

    LaunchedEffect(store) {
        val latestId = store.listDocuments().lastOrNull()
        if (latestId != null) {
            when (val result = store.load(latestId)) {
                is LoadResult.Success -> {
                    val page = result.document.pages.firstOrNull()
                    if (page != null) {
                        val records =
                            page.objects.mapNotNull { obj ->
                                if (obj is InkObject) InkObjectMapper.toStrokeRecord(obj) else null
                            }
                        state.loadRecords(records)
                        document = result.document
                    }
                }
                is LoadResult.Failure ->
                    Log.w(TAG, "failed to load document $latestId: ${result.reason}")
            }
        }
    }

    SideEffect {
        state.onStrokesChanged = { records ->
            scope.launch {
                saveMutex.withLock {
                    val saved = buildDocument(currentDocument, records)
                    withContext(Dispatchers.IO) { store.save(saved) }
                    document = saved
                }
            }
        }
    }

    editorScreen(state = state, modifier = modifier)
}

private fun buildDocument(
    current: Document?,
    records: List<StrokeRecord>,
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
    val objects = records.map { record -> InkObjectMapper.toInkObject(record, nowMillis = now) }
    val updatedPage =
        page.copy(
            objects = objects,
            revision = page.revision + 1,
            updatedAtMillis = now,
        )
    return base.copy(
        pages = listOf(updatedPage),
        revision = base.revision + 1,
        updatedAtMillis = now,
    )
}

@Composable
fun editorScreen(modifier: Modifier = Modifier) {
    val state = rememberInkCanvasState()
    editorScreen(state = state, modifier = modifier)
}

private const val TAG: String = "EditorScreen"

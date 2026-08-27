package com.penly.feature.notebook

import android.graphics.BitmapFactory
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.penly.core.common.PenlyIds
import com.penly.core.document.LoadResult
import com.penly.core.document.PenlyStore
import com.penly.core.model.Document
import com.penly.core.model.DocumentId
import com.penly.core.model.Page
import com.penly.core.model.PageId
import com.penly.core.model.PageTemplate
import com.penly.core.renderer.PageTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREVIEW_PAGE_UNITS: Float = 256f
private val pageThumbnailCache = LruCache<String, ImageBitmap>(36)

/**
 * Notebook-level page browser. It always loads a single document and makes metadata/page-list
 * mutations through [PenlyStore.save], retaining its crash-safe journal protocol for every
 * rename, page creation and delete. Opening a tile delegates page editing to feature-editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun notebookScreen(
    store: PenlyStore,
    documentId: DocumentId,
    defaultTemplate: PageTemplate,
    onOpenPage: (PageId) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var refresh by remember { mutableIntStateOf(0) }
    val result by produceState<LoadResult?>(null, store, documentId.value, refresh) {
        value = withContext(Dispatchers.IO) { runCatching { store.load(documentId) }.getOrNull() }
    }
    val document = (result as? LoadResult.Success)?.document
    val scope = rememberCoroutineScope()
    var showCreatePage by remember { mutableStateOf(false) }
    var showRenameNotebook by remember { mutableStateOf(false) }
    var pendingRename by remember { mutableStateOf<Page?>(null) }
    var pendingDelete by remember { mutableStateOf<Page?>(null) }

    LaunchedEffect(result) {
        if (result != null && document == null) onNavigateBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(document?.title ?: "Notebook")
                        document?.let {
                            Text(
                                text = pageCountLabel(it.pages.size),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showRenameNotebook = true },
                        enabled = document != null,
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Rename notebook")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreatePage = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New page") },
                expanded = true,
            )
        },
    ) { innerPadding ->
        val pages = document?.pages.orEmpty()
        if (document == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Loading notebook…")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(pages, key = { it.pageId.value }) { page ->
                    pageCard(
                        store = store,
                        documentId = documentId,
                        page = page,
                        onOpen = { onOpenPage(page.pageId) },
                        onRequestRename = { pendingRename = page },
                        onRequestDelete = { pendingDelete = page },
                    )
                }
            }
        }
    }

    document?.let { loaded ->
        if (showCreatePage) {
            createPageDialog(
                defaultTemplate = defaultTemplate,
                onDismiss = { showCreatePage = false },
                onCreate = { title, template ->
                    showCreatePage = false
                    val now = System.currentTimeMillis()
                    val page =
                        Page(
                            pageId = PageId(PenlyIds.newId()),
                            documentId = loaded.documentId,
                            title = title.ifBlank { "Page ${loaded.pages.size + 1}" },
                            template = template,
                            createdAtMillis = now,
                            updatedAtMillis = now,
                        )
                    scope.launch {
                        val didSave =
                            persistPage(store, loaded) {
                                it.copy(
                                    pages = it.pages + page,
                                    revision = it.revision + 1,
                                    updatedAtMillis = now,
                                )
                            }
                        if (didSave) {
                            refresh += 1
                            onOpenPage(page.pageId)
                        }
                    }
                },
            )
        }
        if (showRenameNotebook) {
            renameDialog(
                dialogTitle = "Rename notebook",
                title = loaded.title,
                onDismiss = { showRenameNotebook = false },
                onRename = { title ->
                    showRenameNotebook = false
                    val now = System.currentTimeMillis()
                    scope.launch {
                        val didSave =
                            persistPage(store, loaded) {
                                it.copy(
                                    title = title,
                                    revision = it.revision + 1,
                                    updatedAtMillis = now,
                                )
                            }
                        if (didSave) refresh += 1
                    }
                },
            )
        }
        pendingRename?.let { page ->
            renameDialog(
                dialogTitle = "Rename page",
                title = page.title,
                onDismiss = { pendingRename = null },
                onRename = { title ->
                    pendingRename = null
                    val now = System.currentTimeMillis()
                    scope.launch {
                        val didSave =
                            persistPage(store, loaded) {
                                it.copy(
                                    pages =
                                        it.pages.map { candidate ->
                                            if (candidate.pageId == page.pageId) {
                                                candidate.copy(title = title, updatedAtMillis = now)
                                            } else {
                                                candidate
                                            }
                                        },
                                    revision = it.revision + 1,
                                    updatedAtMillis = now,
                                )
                            }
                        if (didSave) refresh += 1
                    }
                },
            )
        }
        pendingDelete?.let { page ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Delete page?") },
                text = {
                    Text(
                        if (loaded.pages.size == 1) {
                            "A notebook must contain at least one page. Create another page first."
                        } else {
                            "\"${page.title}\" and all of its ink and objects will be deleted."
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = loaded.pages.size > 1,
                        onClick = {
                            pendingDelete = null
                            val now = System.currentTimeMillis()
                            scope.launch {
                                val didSave =
                                    persistPage(store, loaded) {
                                        it.copy(
                                            pages =
                                                it.pages.filterNot { candidate ->
                                                    candidate.pageId == page.pageId
                                                },
                                            revision = it.revision + 1,
                                            updatedAtMillis = now,
                                        )
                                    }
                                if (didSave) refresh += 1
                            }
                        },
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

/** Applies [transform] to [document] and persists it through the journal-safe save protocol. */
private suspend fun persistPage(
    store: PenlyStore,
    document: Document,
    transform: (Document) -> Document,
): Boolean =
    withContext(Dispatchers.IO) {
        runCatching { store.save(transform(document)) }.isSuccess
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun pageCard(
    store: PenlyStore,
    documentId: DocumentId,
    page: Page,
    onOpen: () -> Unit,
    onRequestRename: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
    ) {
        Column {
            pageThumbnail(store, documentId, page, Modifier.fillMaxWidth().height(144.dp))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = page.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Page options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename page") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRequestRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete page") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRequestDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun pageThumbnail(
    store: PenlyStore,
    documentId: DocumentId,
    page: Page,
    modifier: Modifier,
) {
    val key = "${documentId.value}/${page.pageId.value}"
    val bitmap by produceState<ImageBitmap?>(pageThumbnailCache.get(key), key) {
        if (value == null) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                        store.openThumbnail(documentId, page.pageId)?.let { bytes ->
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }
                    }.getOrNull()
                }?.also { pageThumbnailCache.put(key, it) }
        }
    }
    val shown = bitmap
    if (shown != null) {
        Image(
            bitmap = shown,
            contentDescription = "Thumbnail of ${page.title}",
            modifier = modifier.clipToBounds(),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier.background(Color.White)) {
            Canvas(Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas
                    nativeCanvas.save()
                    nativeCanvas.scale(
                        size.width / PREVIEW_PAGE_UNITS,
                        size.height / PREVIEW_PAGE_UNITS,
                    )
                    try {
                        PageTemplates.drawBackground(
                            nativeCanvas,
                            page.template,
                            RectF(0f, 0f, PREVIEW_PAGE_UNITS, PREVIEW_PAGE_UNITS),
                        )
                    } finally {
                        nativeCanvas.restore()
                    }
                }
            }
        }
    }
}

@Composable
private fun createPageDialog(
    defaultTemplate: PageTemplate,
    onDismiss: () -> Unit,
    onCreate: (String, PageTemplate) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var template by remember(defaultTemplate) { mutableStateOf(defaultTemplate) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New page") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Page title (optional)") },
                    singleLine = true,
                )
                PageTemplate.entries.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { template = option },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = template == option, onClick = null)
                        Text(option.label(), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title.trim(), template) }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun renameDialog(
    dialogTitle: String,
    title: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var input by remember(title) { mutableStateOf(title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Title") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(input.trim()) }, enabled = input.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun PageTemplate.label(): String =
    when (this) {
        PageTemplate.BLANK -> "Blank"
        PageTemplate.RULED -> "Ruled"
        PageTemplate.NARROW_RULED -> "Narrow ruled"
        PageTemplate.GRID -> "Grid"
        PageTemplate.DOTS -> "Dots"
        PageTemplate.CORNELL -> "Cornell"
    }

private fun pageCountLabel(count: Int): String = if (count == 1) "1 page" else "$count pages"

package com.penly.feature.home

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.penly.core.common.PenlyIds
import com.penly.core.document.DocumentSummary
import com.penly.core.document.LoadResult
import com.penly.core.document.PenlyStore
import com.penly.core.model.Document
import com.penly.core.model.DocumentId
import com.penly.core.model.Page
import com.penly.core.model.PageId
import com.penly.core.model.PageTemplate
import com.penly.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Library tabs; order matches the flat All/Recent/Favorites/Trash model of plan §28. */
private enum class LibraryTab(
    val label: String,
) {
    ALL("All"),
    RECENT("Recent"),
    FAVORITES("Favorites"),
    TRASH("Trash"),
}

/**
 * The Penly home screen: a flat notebook library (All / Recent / Favorites / Trash) built
 * exclusively from [PenlyStore.listSummaries] so opening the library never decodes page
 * payloads. Mutations load the full document, apply a copy() transform and re-save through
 * the journal-safe path. The grid adapts 2/3/4 columns to compact/medium/expanded widths.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3Api::class)
@Composable
fun libraryScreen(
    store: PenlyStore,
    settings: SettingsRepository,
    onOpenNotebook: (DocumentId) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val summaries = remember { mutableStateListOf<DocumentSummary>() }
    val scope = rememberCoroutineScope()
    var refreshTick by remember { mutableIntStateOf(0) }
    val defaultTemplate by settings.defaultTemplate.collectAsState()

    LaunchedEffect(refreshTick) {
        val loaded =
            withContext(Dispatchers.IO) {
                runCatching { store.listSummaries() }.getOrDefault(emptyList())
            }
        summaries.clear()
        summaries.addAll(loaded)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshTick += 1
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as Activity)
    val columns =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Expanded -> 4
            WindowWidthSizeClass.Medium -> 3
            else -> 2
        }
    var selectedTab by remember { mutableStateOf(LibraryTab.ALL) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DocumentSummary?>(null) }

    fun refresh() {
        refreshTick += 1
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Penly") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New notebook") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                LibraryTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) },
                    )
                }
            }
            val visible = filterFor(selectedTab, summaries)
            if (visible.isEmpty()) {
                emptyState(
                    tab = selectedTab,
                    onCreateNotebook = { showCreateDialog = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visible, key = { it.documentId.value }) { summary ->
                        notebookCard(
                            store = store,
                            summary = summary,
                            inTrash = selectedTab == LibraryTab.TRASH,
                            sections = distinctSections(summaries),
                            onOpen = { onOpenNotebook(summary.documentId) },
                            onChanged = ::refresh,
                            onRequestDeleteForever = { pendingDelete = summary },
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        createNotebookDialog(
            sections = distinctSections(summaries),
            onDismiss = { showCreateDialog = false },
            onCreate = { title, section ->
                showCreateDialog = false
                scope.launch {
                    val documentId = createNotebook(store, title, section, defaultTemplate)
                    refresh()
                    documentId?.let(onOpenNotebook)
                }
            },
        )
    }
    pendingDelete?.let { doomed ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete forever?") },
            text = {
                Text(
                    "\"${doomed.title}\" and all its pages will be permanently deleted. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = doomed
                        pendingDelete = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                runCatching { store.deleteDocument(target.documentId) }
                            }
                            refresh()
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

private fun filterFor(
    tab: LibraryTab,
    summaries: List<DocumentSummary>,
): List<DocumentSummary> =
    when (tab) {
        LibraryTab.ALL -> summaries.filter { !it.trashed }
        LibraryTab.RECENT -> summaries.filter { !it.trashed }.take(RECENT_LIMIT)
        LibraryTab.FAVORITES -> summaries.filter { it.favorite && !it.trashed }
        LibraryTab.TRASH -> summaries.filter { it.trashed }
    }

private fun distinctSections(summaries: List<DocumentSummary>): List<String> = summaries.mapNotNull { it.section }.distinct().sorted()

@Composable
private fun emptyState(
    tab: LibraryTab,
    onCreateNotebook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (tab == LibraryTab.TRASH) Icons.Filled.Delete else Icons.Filled.Star,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(16.dp))
        val heading =
            when (tab) {
                LibraryTab.RECENT -> "No recent notebooks"
                LibraryTab.FAVORITES -> "No favorites yet"
                LibraryTab.TRASH -> "Trash is empty"
                LibraryTab.ALL -> "No notebooks yet"
            }
        Text(heading, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text =
                if (tab == LibraryTab.ALL) {
                    "Everything you write lands here first."
                } else {
                    "Notebooks you add will show up in this tab."
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tab == LibraryTab.ALL || tab == LibraryTab.RECENT) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onCreateNotebook) {
                Text("Create your first notebook")
            }
        }
    }
}

/** Loads the full document behind [summary], applies [transform] and saves it. */
internal suspend fun mutateDocument(
    store: PenlyStore,
    summary: DocumentSummary,
    transform: (Document) -> Document,
): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            when (val result = store.load(summary.documentId)) {
                is LoadResult.Success -> {
                    store.save(transform(result.document))
                    true
                }
                is LoadResult.Failure -> false
            }
        }.getOrDefault(false)
    }

/** Creates a one-page notebook and returns its id, or null when saving failed. */
private suspend fun createNotebook(
    store: PenlyStore,
    title: String,
    section: String?,
    template: PageTemplate,
): DocumentId? =
    withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val documentId = DocumentId(PenlyIds.newId())
            val document =
                Document(
                    documentId = documentId,
                    title = title.ifBlank { "Untitled" },
                    section = section?.takeIf(String::isNotBlank),
                    pages =
                        listOf(
                            Page(
                                pageId = PageId(PenlyIds.newId()),
                                documentId = documentId,
                                title = "Page 1",
                                template = template,
                                createdAtMillis = now,
                                updatedAtMillis = now,
                            ),
                        ),
                    createdAtMillis = now,
                    updatedAtMillis = now,
                )
            store.save(document)
            documentId
        }.getOrNull()
    }

@Composable
private fun createNotebookDialog(
    sections: List<String>,
    onDismiss: () -> Unit,
    onCreate: (title: String, section: String?) -> Unit,
) {
    var titleInput by remember { mutableStateOf("") }
    var sectionInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New notebook") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Title") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = sectionInput,
                    onValueChange = { sectionInput = it },
                    label = { Text(sectionLabel(sections)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(titleInput, sectionInput) }) {
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

private fun sectionLabel(sections: List<String>): String =
    if (sections.isEmpty()) {
        "Section (optional)"
    } else {
        "Section (existing: ${sections.joinToString()})"
    }

private const val RECENT_LIMIT: Int = 20

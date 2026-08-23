package com.penly.app.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.penly.core.common.PenlyIds
import com.penly.core.document.PenlyStore
import com.penly.core.model.Document
import com.penly.core.model.DocumentId
import com.penly.core.model.Page
import com.penly.core.model.PageId
import com.penly.core.storage.FileContentStore
import com.penly.feature.editor.editorDocumentScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Interim app host for Phase 5 Milestone C: opens the most recent notebook, materializing an
 * empty "Untitled" one when the store has none. Replaced wholesale by the library NavHost in
 * Milestone G.
 */
@Composable
fun penlyApp(
    context: Context = LocalContext.current.applicationContext,
    store: PenlyStore? = null,
    modifier: Modifier = Modifier,
) {
    val contentStore =
        remember(context, store) {
            store ?: PenlyStore(FileContentStore(File(context.filesDir, "penly")))
        }
    var documentId by remember(contentStore) { mutableStateOf<DocumentId?>(null) }
    LaunchedEffect(contentStore) {
        documentId =
            withContext(Dispatchers.IO) {
                runCatching { openOrCreateDocument(contentStore) }.getOrNull()
            }
    }
    MaterialTheme {
        val id = documentId
        if (id != null) {
            editorDocumentScreen(
                store = contentStore,
                documentId = id,
                initialPageId = null,
                onNavigateBack = {},
                modifier = modifier,
            )
        }
    }
}

/** Latest stored document id; creates a blank single-page notebook when the store is empty. */
private fun openOrCreateDocument(store: PenlyStore): DocumentId {
    store.listDocuments().lastOrNull()?.let { return it }
    val now = System.currentTimeMillis()
    val documentId = DocumentId(PenlyIds.newId())
    val document =
        Document(
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
    store.save(document)
    return documentId
}

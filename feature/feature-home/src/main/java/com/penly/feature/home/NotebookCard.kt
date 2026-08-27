package com.penly.feature.home

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.penly.core.document.DocumentSummary
import com.penly.core.document.PenlyStore
import com.penly.core.model.Document
import com.penly.core.model.PageTemplate
import kotlinx.coroutines.launch

/**
 * One library tile: first-page thumbnail, title, relative updated time, section chip,
 * favorite marker and an overflow menu whose mutations go through [mutateDocument]
 * (load full document → copy() transform → journal-safe save).
 */
@Composable
internal fun notebookCard(
    store: PenlyStore,
    summary: DocumentSummary,
    inTrash: Boolean,
    sections: List<String>,
    onOpen: () -> Unit,
    onChanged: () -> Unit,
    onRequestDeleteForever: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showMoveToSection by remember { mutableStateOf(false) }

    fun perform(transform: (Document) -> Document) {
        scope.launch {
            mutateDocument(store, summary, transform)
            onChanged()
        }
    }

    val menuItems: @Composable () -> Unit = {
        if (inTrash) {
            DropdownMenuItem(
                text = { Text("Restore") },
                onClick = {
                    menuOpen = false
                    perform { it.copy(trashed = false) }
                },
            )
            DropdownMenuItem(
                text = { Text("Delete forever") },
                onClick = {
                    menuOpen = false
                    onRequestDeleteForever()
                },
            )
        } else {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    menuOpen = false
                    showRename = true
                },
            )
            DropdownMenuItem(
                text = { Text(if (summary.favorite) "Unfavorite" else "Favorite") },
                onClick = {
                    menuOpen = false
                    perform { it.copy(favorite = !it.favorite) }
                },
            )
            DropdownMenuItem(
                text = { Text("Move to section") },
                onClick = {
                    menuOpen = false
                    showMoveToSection = true
                },
            )
            DropdownMenuItem(
                text = { Text("Move to Trash") },
                onClick = {
                    menuOpen = false
                    perform { it.copy(trashed = true) }
                },
            )
        }
    }

    if (inTrash) {
        Card(modifier = Modifier.fillMaxWidth()) {
            cardContent(store, summary, inTrash, menuOpen, { menuOpen = true }, { menuOpen = false }, menuItems)
        }
    } else {
        Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            cardContent(store, summary, inTrash, menuOpen, { menuOpen = true }, { menuOpen = false }, menuItems)
        }
    }

    if (showRename) {
        renameDialog(
            currentTitle = summary.title,
            onDismiss = { showRename = false },
            onRename = { newTitle ->
                showRename = false
                perform { it.copy(title = newTitle) }
            },
        )
    }
    if (showMoveToSection) {
        moveToSectionDialog(
            currentSection = summary.section,
            sections = sections,
            onDismiss = { showMoveToSection = false },
            onMove = { section ->
                showMoveToSection = false
                perform { it.copy(section = section?.takeIf(String::isNotBlank)) }
            },
        )
    }
}

@Composable
private fun cardContent(
    store: PenlyStore,
    summary: DocumentSummary,
    inTrash: Boolean,
    menuOpen: Boolean,
    onMenuToggle: () -> Unit,
    onMenuClose: () -> Unit,
    menu: @Composable () -> Unit,
) {
    Column {
        val pageId = summary.firstPageId
        if (pageId != null) {
            thumbnailImage(
                store = store,
                documentId = summary.documentId,
                pageId = pageId,
                template = PageTemplate.BLANK,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                contentDescription = "Thumbnail of ${summary.title}",
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = DateUtils.getRelativeTimeSpanString(summary.updatedAtMillis).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (summary.favorite && !inTrash) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Favorite",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onMenuToggle) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuClose) {
                menu()
            }
        }
        summary.section?.let { section ->
            AssistChip(
                onClick = {},
                label = { Text(section, maxLines = 1) },
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun renameDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var titleInput by remember { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename notebook") },
        text = {
            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                label = { Text("Title") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(titleInput.trim()) },
                enabled = titleInput.isNotBlank(),
            ) {
                Text("Rename")
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
private fun moveToSectionDialog(
    currentSection: String?,
    sections: List<String>,
    onDismiss: () -> Unit,
    onMove: (String?) -> Unit,
) {
    var customInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to section") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (sections.isEmpty()) {
                    Text(
                        "No sections yet. Type a name to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it },
                    label = { Text("New or existing section") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                sectionChoiceRow(
                    selected = currentSection == null && customInput.isBlank(),
                    label = "No section",
                    onClick = {
                        customInput = ""
                        onMove(null)
                    },
                )
                sections.forEach { section ->
                    sectionChoiceRow(
                        selected = currentSection == section && customInput.isBlank(),
                        label = section,
                        onClick = {
                            customInput = ""
                            onMove(section)
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onMove(customInput.trim()) },
                enabled = customInput.isNotBlank(),
            ) {
                Text("Move")
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
private fun sectionChoiceRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, maxLines = 1)
    }
}

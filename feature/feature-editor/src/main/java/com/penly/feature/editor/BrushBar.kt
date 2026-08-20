package com.penly.feature.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.penly.core.ink.PenTool

@Composable
fun brushBar(
    tool: PenTool,
    onToolSelected: (PenTool) -> Unit,
    selectionMode: Boolean = false,
    onSelectionModeChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PenTool.entries.forEach { entry ->
            FilterChip(
                selected = !selectionMode && tool == entry,
                onClick = {
                    onToolSelected(entry)
                    if (selectionMode) {
                        onSelectionModeChange(false)
                    }
                },
                label = { Text(entry.label) },
            )
        }
        FilterChip(
            selected = selectionMode,
            onClick = { onSelectionModeChange(!selectionMode) },
            label = { Text("Select") },
        )
    }
}

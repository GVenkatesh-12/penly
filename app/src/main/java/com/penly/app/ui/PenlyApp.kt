package com.penly.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.penly.feature.editor.editorScreen

@Composable
fun penlyApp(modifier: Modifier = Modifier) {
    MaterialTheme {
        editorScreen(modifier = modifier)
    }
}

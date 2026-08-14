package com.penly.feature.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.penly.editor.canvas.InkCanvasState
import com.penly.editor.canvas.fpsOverlay
import com.penly.editor.canvas.inkCanvas
import com.penly.editor.canvas.rememberInkCanvasState

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

@Composable
fun editorScreen(modifier: Modifier = Modifier) {
    val state = rememberInkCanvasState()
    editorScreen(state = state, modifier = modifier)
}

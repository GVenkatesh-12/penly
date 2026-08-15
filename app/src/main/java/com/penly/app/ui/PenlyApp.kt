package com.penly.app.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.penly.core.document.PenlyStore
import com.penly.core.storage.FileContentStore
import com.penly.feature.editor.editorScreen
import java.io.File

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
    MaterialTheme {
        editorScreen(store = contentStore, modifier = modifier)
    }
}

package com.penly.app.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.penly.core.document.PenlyStore
import com.penly.core.model.DocumentId
import com.penly.core.model.PageId
import com.penly.core.settings.SettingsRepository
import com.penly.core.settings.ThemeMode
import com.penly.core.storage.FileContentStore
import com.penly.feature.editor.editorDocumentScreen
import com.penly.feature.home.libraryScreen
import com.penly.feature.notebook.notebookScreen
import com.penly.feature.settings.settingsScreen
import java.io.File

private const val LIBRARY_ROUTE = "library"
private const val SETTINGS_ROUTE = "settings"
private const val NOTEBOOK_ID_ARGUMENT = "documentId"
private const val PAGE_ID_ARGUMENT = "pageId"
private const val NOTEBOOK_ROUTE = "notebook/{$NOTEBOOK_ID_ARGUMENT}"
private const val EDITOR_ROUTE = "editor/{$NOTEBOOK_ID_ARGUMENT}/{$PAGE_ID_ARGUMENT}"

/**
 * Application navigation host for the Phase 5 library flow:
 * library → notebook pages → editor, with settings reachable from the library. A single
 * [PenlyStore] and [SettingsRepository] are retained for the composition lifetime so each
 * destination observes the same durable data and theme preference.
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
    val scope = rememberCoroutineScope()
    val settings = remember(context) { SettingsRepository(context, scope) }
    val themeMode by settings.themeMode.collectAsState()
    val defaultTemplate by settings.defaultTemplate.collectAsState()
    val darkTheme =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val navController = rememberNavController()

    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        NavHost(
            navController = navController,
            startDestination = LIBRARY_ROUTE,
            modifier = modifier,
        ) {
            composable(LIBRARY_ROUTE) {
                libraryScreen(
                    store = contentStore,
                    settings = settings,
                    onOpenNotebook = { documentId ->
                        navController.navigate(notebookRoute(documentId))
                    },
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                )
            }
            composable(
                route = NOTEBOOK_ROUTE,
                arguments = listOf(navArgument(NOTEBOOK_ID_ARGUMENT) { type = NavType.StringType }),
            ) { entry ->
                val documentId = entry.documentIdArgument() ?: return@composable
                notebookScreen(
                    store = contentStore,
                    documentId = documentId,
                    defaultTemplate = defaultTemplate,
                    onOpenPage = { pageId ->
                        navController.navigate(editorRoute(documentId, pageId))
                    },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(
                route = EDITOR_ROUTE,
                arguments =
                    listOf(
                        navArgument(NOTEBOOK_ID_ARGUMENT) { type = NavType.StringType },
                        navArgument(PAGE_ID_ARGUMENT) { type = NavType.StringType },
                    ),
            ) { entry ->
                val documentId = entry.documentIdArgument() ?: return@composable
                val pageId = entry.pageIdArgument() ?: return@composable
                editorDocumentScreen(
                    store = contentStore,
                    documentId = documentId,
                    initialPageId = pageId,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(SETTINGS_ROUTE) {
                settingsScreen(
                    settings = settings,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun notebookRoute(documentId: DocumentId): String = "notebook/${documentId.value}"

private fun editorRoute(
    documentId: DocumentId,
    pageId: PageId,
): String = "editor/${documentId.value}/${pageId.value}"

private fun NavBackStackEntry.documentIdArgument(): DocumentId? = arguments?.getString(NOTEBOOK_ID_ARGUMENT)?.let(::DocumentId)

private fun NavBackStackEntry.pageIdArgument(): PageId? = arguments?.getString(PAGE_ID_ARGUMENT)?.let(::PageId)

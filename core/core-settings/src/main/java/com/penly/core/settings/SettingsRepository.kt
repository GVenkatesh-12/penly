package com.penly.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.penly.core.model.PageTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/** User-selectable appearance. SYSTEM follows the OS dark-mode setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "penly_settings")

/**
 * Key/value user settings backed by Jetpack DataStore ("penly_settings"). Writes are
 * serialized through [edit]; reads expose hot [StateFlow]s so the UI can react to changes.
 * Constructed by the app layer (`remember {}`) — no DI framework in this phase.
 */
class SettingsRepository(
    context: Context,
    private val scope: CoroutineScope,
) {
    private data class Keys(
        val themeMode: Preferences.Key<String>,
        val defaultTemplate: Preferences.Key<String>,
    )

    private val dataStore = context.applicationContext.settingsDataStore

    private val keys =
        Keys(
            themeMode = stringPreferencesKey("theme_mode"),
            defaultTemplate = stringPreferencesKey("default_template"),
        )

    val themeMode: StateFlow<ThemeMode> =
        dataStore.data
            .catch { failure ->
                if (failure is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw failure
                }
            }.map { preferences ->
                when (preferences[keys.themeMode]) {
                    ThemeMode.LIGHT.name -> ThemeMode.LIGHT
                    ThemeMode.DARK.name -> ThemeMode.DARK
                    else -> ThemeMode.SYSTEM
                }
            }.stateIn(scope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val defaultTemplate: StateFlow<PageTemplate> =
        dataStore.data
            .catch { failure ->
                if (failure is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw failure
                }
            }.map { preferences ->
                templateFromName(preferences[keys.defaultTemplate])
            }.stateIn(scope, SharingStarted.Eagerly, PageTemplate.BLANK)

    fun setThemeMode(mode: ThemeMode) {
        scope.launch { dataStore.edit { it[keys.themeMode] = mode.name } }
    }

    fun setDefaultTemplate(template: PageTemplate) {
        scope.launch { dataStore.edit { it[keys.defaultTemplate] = template.name } }
    }

    private fun templateFromName(name: String?): PageTemplate = PageTemplate.entries.firstOrNull { it.name == name } ?: PageTemplate.BLANK
}

package com.penly.feature.settings

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.penly.core.model.PageTemplate
import com.penly.core.renderer.PageTemplates
import com.penly.core.settings.SettingsRepository
import com.penly.core.settings.ThemeMode

/** Page units covered by the template mini-previews (a square crop of the page origin). */
private const val PREVIEW_PAGE_UNITS: Float = 200f

/**
 * Appearance settings: theme mode and the default page template for new pages/notebooks.
 * Standard Material controls keep TalkBack, mouse and keyboard support for free (plan §27).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun settingsScreen(
    settings: SettingsRepository,
    onNavigateBack: () -> Unit,
) {
    val themeMode by settings.themeMode.collectAsState()
    val defaultTemplate by settings.defaultTemplate.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
        ) {
            settingsHeader("Appearance")
            ThemeMode.entries.forEach { mode ->
                radioRow(
                    selected = themeMode == mode,
                    label =
                        when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        },
                    onClick = { settings.setThemeMode(mode) },
                )
            }
            HorizontalDivider()
            settingsHeader("Default page template")
            PageTemplate.entries.forEach { template ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { settings.setDefaultTemplate(template) }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    templatePreview(template)
                    Text(template.displayName(), modifier = Modifier.padding(start = 12.dp))
                    RadioButton(selected = defaultTemplate == template, onClick = null)
                }
            }
        }
    }
}

private fun PageTemplate.displayName(): String =
    when (this) {
        PageTemplate.BLANK -> "Blank"
        PageTemplate.RULED -> "Ruled"
        PageTemplate.NARROW_RULED -> "Narrow ruled"
        PageTemplate.GRID -> "Grid"
        PageTemplate.DOTS -> "Dots"
        PageTemplate.CORNELL -> "Cornell"
    }

@Composable
private fun settingsHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun radioRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

/** 48dp square preview painted with the real [PageTemplates] pattern over 200x200 page units. */
@Composable
private fun templatePreview(template: PageTemplate) {
    Canvas(modifier = Modifier.size(48.dp).clipToBounds()) {
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            nativeCanvas.save()
            nativeCanvas.scale(size.width / PREVIEW_PAGE_UNITS, size.height / PREVIEW_PAGE_UNITS)
            try {
                PageTemplates.drawBackground(
                    nativeCanvas,
                    template,
                    RectF(0f, 0f, PREVIEW_PAGE_UNITS, PREVIEW_PAGE_UNITS),
                )
            } finally {
                nativeCanvas.restore()
            }
        }
    }
}

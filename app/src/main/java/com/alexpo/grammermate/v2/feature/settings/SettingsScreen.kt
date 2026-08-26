package com.alexpo.grammermate.v2.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alexpo.grammermate.domain.model.ThemeMode
import com.alexpo.grammermate.v2.core.ui.collectState

/** Стабильные test-теги настроек. */
object SettingsTestTags {
    const val THEME_SYSTEM = "settings_theme_system"
    const val THEME_LIGHT = "settings_theme_light"
    const val THEME_DARK = "settings_theme_dark"
}

/**
 * Экран настроек (Фаза 7: последний Placeholder заменён реальным экраном) —
 * режим темы; изменение применяется немедленно (AppViewModel реактивен).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state = collectState(viewModel.state)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Настройки", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Тема оформления", style = MaterialTheme.typography.titleMedium)
            when (val s = state) {
                is SettingsViewState.Content -> androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeChip("Системная", ThemeMode.SYSTEM, SettingsTestTags.THEME_SYSTEM, s, viewModel)
                    ThemeChip("Светлая", ThemeMode.LIGHT, SettingsTestTags.THEME_LIGHT, s, viewModel)
                    ThemeChip("Тёмная", ThemeMode.DARK, SettingsTestTags.THEME_DARK, s, viewModel)
                }

                SettingsViewState.Loading -> Text("Загрузка…")

                is SettingsViewState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    mode: ThemeMode,
    tag: String,
    state: SettingsViewState.Content,
    viewModel: SettingsViewModel,
) {
    FilterChip(
        selected = state.themeMode == mode,
        onClick = { viewModel.setThemeMode(mode) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(0f).testTag(tag),
    )
}

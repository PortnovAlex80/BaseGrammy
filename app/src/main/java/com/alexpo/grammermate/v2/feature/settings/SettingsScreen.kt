package com.alexpo.grammermate.v2.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    const val SESSION_SIZE_DECREASE = "settings_session_size_decrease"
    const val SESSION_SIZE_VALUE = "settings_session_size_value"
    const val SESSION_SIZE_INCREASE = "settings_session_size_increase"
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
                is SettingsViewState.Content -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip("Системная", ThemeMode.SYSTEM, SettingsTestTags.THEME_SYSTEM, s, viewModel)
                        ThemeChip("Светлая", ThemeMode.LIGHT, SettingsTestTags.THEME_LIGHT, s, viewModel)
                        ThemeChip("Тёмная", ThemeMode.DARK, SettingsTestTags.THEME_DARK, s, viewModel)
                    }
                    Text("Карточек в уроке", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        IconButton(
                            onClick = { viewModel.setSessionSize(s.sessionSize - 1) },
                            enabled = s.sessionSize > MIN_SESSION_SIZE,
                            modifier = Modifier.testTag(SettingsTestTags.SESSION_SIZE_DECREASE),
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Уменьшить")
                        }
                        Spacer(Modifier.width(16.dp))
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .testTag(SettingsTestTags.SESSION_SIZE_VALUE),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = s.sessionSize.toString(),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        IconButton(
                            onClick = { viewModel.setSessionSize(s.sessionSize + 1) },
                            enabled = s.sessionSize < MAX_SESSION_SIZE,
                            modifier = Modifier.testTag(SettingsTestTags.SESSION_SIZE_INCREASE),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Увеличить")
                        }
                    }
                }

                SettingsViewState.Loading -> Text("Загрузка…")

                is SettingsViewState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private const val MIN_SESSION_SIZE = 3
private const val MAX_SESSION_SIZE = 1000

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
        modifier = Modifier.testTag(tag),
    )
}

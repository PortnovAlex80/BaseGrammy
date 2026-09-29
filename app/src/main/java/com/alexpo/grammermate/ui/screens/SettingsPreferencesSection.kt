package com.alexpo.grammermate.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.ThemeMode
import com.alexpo.grammermate.shared.AuditLogger

internal data class SettingsPreferencesValues(
    val uiLanguage: String,
    val testMode: Boolean,
    val themeMode: ThemeMode,
    val hintLevel: HintLevel,
    val clickableWordHints: Boolean,
    val vocabSprintLimit: Int,
    val sessionSize: Int,
    val ttsSpeed: Float
)

@Composable
internal fun ColumnScope.SettingsPreferencesSection(
    values: SettingsPreferencesValues,
    onSetUiLanguage: (String) -> Unit,
    onToggleTestMode: () -> Unit,
    onOpenLadder: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetHintLevel: (HintLevel) -> Unit,
    onSetClickableWordHints: (Boolean) -> Unit,
    onUpdateVocabLimit: (Int) -> Unit,
    onSetSessionSize: (Int) -> Unit,
    onSetTtsSpeed: (Float) -> Unit
) {
    var vocabLimitText by remember(values.vocabSprintLimit) { mutableStateOf(values.vocabSprintLimit.toString()) }
    var sessionSizeText by remember(values.sessionSize) { mutableStateOf(values.sessionSize.toString()) }
    // Interface language selector (separate from learning content language)
    Text(
        text = stringResource(R.string.settings_interface_language),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val languageOptions = listOf(
            "system" to stringResource(R.string.settings_language_system),
            "en" to stringResource(R.string.settings_language_en),
            "ru" to stringResource(R.string.settings_language_ru)
        )
        languageOptions.forEach { (code, label) ->
            FilterChip(
                selected = values.uiLanguage == code,
                onClick = {
                    AuditLogger.getInstanceOrNull()?.settingsChange("uiLanguage", code)
                    onSetUiLanguage(code)
                },
                label = { Text(text = label) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    Text(
        text = stringResource(R.string.settings_service_mode),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = stringResource(R.string.settings_test_mode), style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = values.testMode,
            onCheckedChange = {
                AuditLogger.getInstanceOrNull()?.settingsChange("testMode", (!values.testMode).toString())
                onToggleTestMode()
            }
        )
    }
    Text(
        text = stringResource(R.string.settings_test_mode_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
    OutlinedButton(
        onClick = onOpenLadder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Insights, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(R.string.settings_show_ladder))
    }

    // Appearance / Theme selector
    Text(
        text = stringResource(R.string.settings_appearance),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        com.alexpo.grammermate.data.ThemeMode.entries.forEach { mode ->
            val isSelected = values.themeMode == mode
            val label = when (mode) {
                com.alexpo.grammermate.data.ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                com.alexpo.grammermate.data.ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                com.alexpo.grammermate.data.ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
            }
            FilterChip(
                selected = isSelected,
                onClick = {
                    AuditLogger.getInstanceOrNull()?.settingsChange("themeMode", mode.name)
                    onSetThemeMode(mode)
                },
                label = { Text(text = label) },
                modifier = Modifier.weight(1f)
            )
        }
    }
    Text(
        text = stringResource(R.string.settings_theme_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )

    // Difficulty / Hint level selector
    Text(
        text = stringResource(R.string.settings_difficulty),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HintLevel.entries.forEach { level ->
            val isSelected = values.hintLevel == level
            val label = when (level) {
                HintLevel.EASY -> stringResource(R.string.settings_easy)
                HintLevel.MEDIUM -> stringResource(R.string.settings_medium)
                HintLevel.HARD -> stringResource(R.string.settings_hard)
            }
            FilterChip(
                selected = isSelected,
                onClick = {
                    AuditLogger.getInstanceOrNull()?.settingsChange("hintLevel", level.name)
                    onSetHintLevel(level)
                },
                label = { Text(text = label) },
                modifier = Modifier.weight(1f)
            )
        }
    }
    Text(
        text = when (values.hintLevel) {
            HintLevel.EASY ->
                stringResource(R.string.settings_easy_description)
            HintLevel.MEDIUM ->
                stringResource(R.string.settings_medium_description)
            HintLevel.HARD ->
                stringResource(R.string.settings_hard_description)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )

    // Clickable word hints toggle
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Clickable word hints",
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(
            checked = values.clickableWordHints,
            onCheckedChange = {
                AuditLogger.getInstanceOrNull()?.settingsChange("clickableWordHints", it.toString())
                onSetClickableWordHints(it)
            }
        )
    }
    Text(
        text = "Show word translations and ranks when viewing answers",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = vocabLimitText,
        onValueChange = { next ->
            val cleaned = next.filter { it.isDigit() }
            vocabLimitText = cleaned
            val parsed = cleaned.toIntOrNull()
            if (parsed != null) {
                AuditLogger.getInstanceOrNull()?.settingsChange("vocabLimit", parsed.toString())
                onUpdateVocabLimit(parsed)
            } else if (cleaned.isEmpty()) {
                AuditLogger.getInstanceOrNull()?.settingsChange("vocabLimit", "0")
                onUpdateVocabLimit(0)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = stringResource(R.string.settings_vocab_sprint_limit)) }
    )
    Text(
        text = stringResource(R.string.settings_vocab_limit_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
    OutlinedTextField(
        value = sessionSizeText,
        onValueChange = { next ->
            val cleaned = next.filter { it.isDigit() }
            sessionSizeText = cleaned
            val parsed = cleaned.toIntOrNull()
            if (parsed != null) {
                AuditLogger.getInstanceOrNull()?.settingsChange("sessionSize", parsed.toString())
                onSetSessionSize(parsed)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = stringResource(R.string.settings_session_size)) }
    )
    Text(
        text = stringResource(R.string.settings_session_size_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )

    // TTS speed control
    Text(
        text = stringResource(R.string.settings_pronunciation_speed),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "0.5x", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = values.ttsSpeed,
            onValueChange = {
                AuditLogger.getInstanceOrNull()?.settingsChange("ttsSpeed", it.toString())
                onSetTtsSpeed(it)
            },
            valueRange = 0.5f..1.5f,
            steps = 3,
            modifier = Modifier.weight(1f)
        )
        Text(text = "1.5x", style = MaterialTheme.typography.bodySmall)
    }
    Text(
        text = String.format("%.2fx", values.ttsSpeed),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

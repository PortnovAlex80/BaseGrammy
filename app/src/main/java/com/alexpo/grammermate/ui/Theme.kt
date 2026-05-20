package com.alexpo.grammermate.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// =====================================================================
// GrammarMateColors — semantic color palette with light/dark variants
// =====================================================================

/**
 * Semantic color tokens for GrammarMate screens.
 * Light and dark variants are provided via [LocalGrammarMateColors].
 * Screen files should migrate to `LocalGrammarMateColors.current.xxx`
 * (TASK-013/014). Top-level vals below remain for backward compat.
 */
@Stable
data class GrammarMateColors(
    // Correctness feedback
    val correctGreen: Color,
    val incorrectRed: Color,
    // Drill mode (TrainingScreen)
    val drillBackgroundGreen: Color,
    val drillTenseLabelGreen: Color,
    val drillPromptGreen: Color,
    // Mastery / progress (HomeScreen, SessionProgressIndicator)
    val masteryGreen: Color,
    val progressGreen: Color,
    val progressTrackGreen: Color,
    val progressFastGreen: Color,
    // Speed indicator (SessionProgressIndicator)
    val speedSlowRed: Color,
    val speedMediumYellow: Color,
    val progressLabelWhite: Color,
    val progressTrackGray: Color,
    // SRS rating backgrounds and text (DailyPracticeScreen, VocabDrillScreen)
    val srsAgainBackground: Color,
    val srsAgainText: Color,
    val srsHardBackground: Color,
    val srsHardText: Color,
    val srsGoodBackground: Color,
    val srsGoodText: Color,
    val srsEasyBackground: Color,
    val srsEasyText: Color,
    // Vocab drill (VocabDrillScreen)
    val vocabIntervalOrange: Color,
    val vocabCorrectBackground: Color,
    val vocabIncorrectBackground: Color,
    // Boss rewards (GrammarMateApp, LessonRoadmapScreen)
    val bossBronze: Color,
    val bossSilver: Color,
    val bossGold: Color,
    // Destructive actions (SettingsScreen)
    val destructiveRed: Color
)

/** Light-mode semantic colors (matches original hardcoded vals). */
val LightGrammarMateColors = GrammarMateColors(
    correctGreen = Color(0xFF2E7D32),
    incorrectRed = Color(0xFFC62828),
    drillBackgroundGreen = Color(0xFFE8F5E9),
    drillTenseLabelGreen = Color(0xFF388E3C),
    drillPromptGreen = Color(0xFF2E7D32),
    masteryGreen = Color(0xFF2E7D32),
    progressGreen = Color(0xFF4CAF50),
    progressTrackGreen = Color(0xFFC8E6C9),
    progressFastGreen = Color(0xFF43A047),
    speedSlowRed = Color(0xFFE53935),
    speedMediumYellow = Color(0xFFFDD835),
    progressLabelWhite = Color.White,
    progressTrackGray = Color(0xFFE0E0E0),
    srsAgainBackground = Color(0xFFFFEBEE),
    srsAgainText = Color(0xFFE53935),
    srsHardBackground = Color(0xFFFFF3E0),
    srsHardText = Color(0xFFFF9800),
    srsGoodBackground = Color(0xFFE8F5E9),
    srsGoodText = Color(0xFF4CAF50),
    srsEasyBackground = Color(0xFFE3F2FD),
    srsEasyText = Color(0xFF2196F3),
    vocabIntervalOrange = Color(0xFFE65100),
    vocabCorrectBackground = Color(0xFFE8F5E9),
    vocabIncorrectBackground = Color(0xFFFFEBEE),
    bossBronze = Color(0xFFCD7F32),
    bossSilver = Color(0xFFC0C0C0),
    bossGold = Color(0xFFFFD700),
    destructiveRed = Color(0xFFB00020)
)

/** Dark-mode semantic colors — muted backgrounds, brighter foregrounds. */
val DarkGrammarMateColors = GrammarMateColors(
    correctGreen = Color(0xFF66BB6A),
    incorrectRed = Color(0xFFEF5350),
    drillBackgroundGreen = Color(0xFF1B3A1D),
    drillTenseLabelGreen = Color(0xFF81C784),
    drillPromptGreen = Color(0xFF66BB6A),
    masteryGreen = Color(0xFF66BB6A),
    progressGreen = Color(0xFF66BB6A),
    progressTrackGreen = Color(0xFF2E4A2F),
    progressFastGreen = Color(0xFF66BB6A),
    speedSlowRed = Color(0xFFEF5350),
    speedMediumYellow = Color(0xFFFFEE58),
    progressLabelWhite = Color.White,
    progressTrackGray = Color(0xFF3A3A3A),
    srsAgainBackground = Color(0xFF3A1B1B),
    srsAgainText = Color(0xFFEF5350),
    srsHardBackground = Color(0xFF3A2E1B),
    srsHardText = Color(0xFFFF8A65),
    srsGoodBackground = Color(0xFF1B3A1D),
    srsGoodText = Color(0xFF66BB6A),
    srsEasyBackground = Color(0xFF1A2E3A),
    srsEasyText = Color(0xFF64B5F6),
    vocabIntervalOrange = Color(0xFFFF8A65),
    vocabCorrectBackground = Color(0xFF1B3A1D),
    vocabIncorrectBackground = Color(0xFF3A1B1B),
    bossBronze = Color(0xFFCD7F32),
    bossSilver = Color(0xFFC0C0C0),
    bossGold = Color(0xFFFFD700),
    destructiveRed = Color(0xFFCF6679)
)

/** CompositionLocal that provides the current [GrammarMateColors]. */
val LocalGrammarMateColors = staticCompositionLocalOf { LightGrammarMateColors }

// =====================================================================
// Top-level color constants — backward compat (light-mode defaults)
// =====================================================================
// These remain so that existing imports in screen files still compile.
// Screen files will migrate to LocalGrammarMateColors.current.xxx
// in TASK-013/014. At that point these vals can be deprecated/removed.

// Correctness feedback
val CorrectGreen: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.correctGreen
val IncorrectRed: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.incorrectRed

// Drill mode (TrainingScreen)
val DrillBackgroundGreen: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.drillBackgroundGreen
val DrillTenseLabelGreen: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.drillTenseLabelGreen
val DrillPromptGreen: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.drillPromptGreen

// Mastery / progress (HomeScreen, SessionProgressIndicator)
val MasteryGreen: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.masteryGreen
val ProgressGreen: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.progressGreen
val ProgressTrackGreen: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.progressTrackGreen
val ProgressFastGreen: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.progressFastGreen

// Speed indicator (SessionProgressIndicator)
val SpeedSlowRed: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.speedSlowRed
val SpeedMediumYellow: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.speedMediumYellow
val ProgressLabelWhite: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.progressLabelWhite
val ProgressTrackGray: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.progressTrackGray

// SRS rating backgrounds and text (DailyPracticeScreen, VocabDrillScreen)
val SrsAgainBackground: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.srsAgainBackground
val SrsAgainText: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.srsAgainText
val SrsHardBackground: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.srsHardBackground
val SrsHardText: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.srsHardText
val SrsGoodBackground: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.srsGoodBackground
val SrsGoodText: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.srsGoodText
val SrsEasyBackground: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.srsEasyBackground
val SrsEasyText: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.srsEasyText

// Vocab drill (VocabDrillScreen)
val VocabIntervalOrange: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.vocabIntervalOrange
val VocabCorrectBackground: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.vocabCorrectBackground
val VocabIncorrectBackground: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.vocabIncorrectBackground

// Boss rewards (GrammarMateApp, LessonRoadmapScreen)
val BossBronze: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.bossBronze
val BossSilver: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.bossSilver
val BossGold: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.bossGold

// Destructive actions (SettingsScreen)
val DestructiveRed: Color
    @Composable @ReadOnlyComposable get() = LocalGrammarMateColors.current.destructiveRed

// --- Light color scheme (unchanged from original) ---

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5D62),
    onPrimary = Color.White,
    secondary = Color(0xFF5E8B7E),
    onSecondary = Color.White,
    background = Color(0xFFF7F4F1),
    onBackground = Color(0xFF1F1F1F),
    surface = Color.White,
    onSurface = Color(0xFF1F1F1F)
)

// --- Dark color scheme ---

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80CBC4),
    onPrimary = Color(0xFF003731),
    secondary = Color(0xFF80B5A9),
    onSecondary = Color(0xFF00332B),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E1DF),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E1DF),
    surfaceVariant = Color(0xFF2C2E30),
    onSurfaceVariant = Color(0xFFC3C7C5),
    error = Color(0xFFCF6679),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF690005),
    onErrorContainer = Color(0xFFCF6679),
    primaryContainer = Color(0xFF004D46),
    onPrimaryContainer = Color(0xFF9CF0E4),
    secondaryContainer = Color(0xFF004A42),
    onSecondaryContainer = Color(0xFFA5F0E0),
    outline = Color(0xFF8D9190),
    outlineVariant = Color(0xFF424746)
)

@Composable
fun GrammarMateTheme(
    themeMode: com.alexpo.grammermate.data.ThemeMode = com.alexpo.grammermate.data.ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        com.alexpo.grammermate.data.ThemeMode.LIGHT -> false
        com.alexpo.grammermate.data.ThemeMode.DARK -> true
        com.alexpo.grammermate.data.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        useDarkTheme -> DarkColors
        else -> LightColors
    }
    val grammarMateColors = when {
        useDarkTheme -> DarkGrammarMateColors
        else -> LightGrammarMateColors
    }
    CompositionLocalProvider(LocalGrammarMateColors provides grammarMateColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

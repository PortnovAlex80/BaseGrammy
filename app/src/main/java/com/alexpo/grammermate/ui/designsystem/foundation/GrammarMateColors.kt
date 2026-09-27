package com.alexpo.grammermate.ui.designsystem.foundation

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
 * (TASK-013/014). Top-level compat getters in `ui/Theme.kt` remain for
 * backward compat.
 */
@Stable
data class GrammarMateColors(
    // Correctness feedback
    val correctGreen: Color,
    val incorrectRed: Color,
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
    val destructiveRed: Color,
    // Glass token — elevated translucent surface for glassmorphism cards (API 31+ blur target)
    val surfaceElevated: Color
)

/** Light-mode semantic colors — refreshed warm palette, emerald primary. */
val LightGrammarMateColors = GrammarMateColors(
    correctGreen = Color(0xFF0F7C66),
    incorrectRed = Color(0xFFC62828),
    masteryGreen = Color(0xFF0F7C66),
    progressGreen = Color(0xFF0F7C66),
    progressTrackGreen = Color(0xFFCFEFE5),
    progressFastGreen = Color(0xFF0F7C66),
    speedSlowRed = Color(0xFFE53935),
    speedMediumYellow = Color(0xFFF2A900),
    progressLabelWhite = Color.White,
    progressTrackGray = Color(0xFFE0E0E0),
    srsAgainBackground = Color(0xFFFCE4E4),
    srsAgainText = Color(0xFFC62828),
    srsHardBackground = Color(0xFFFFF1DC),
    srsHardText = Color(0xFFB26A00),
    srsGoodBackground = Color(0xFFDCF2EC),
    srsGoodText = Color(0xFF0F7C66),
    srsEasyBackground = Color(0xFFE4EDF7),
    srsEasyText = Color(0xFF1E6FB8),
    vocabIntervalOrange = Color(0xFFB26A00),
    vocabCorrectBackground = Color(0xFFDCF2EC),
    vocabIncorrectBackground = Color(0xFFFCE4E4),
    bossBronze = Color(0xFFCD7F32),
    bossSilver = Color(0xFF8A938C),
    bossGold = Color(0xFFB8860B),
    destructiveRed = Color(0xFFB00020),
    surfaceElevated = Color(0xFFFFFFFF)
)

/** Dark-mode semantic colors — Premium Dark / Emerald. Muted glass backgrounds, brighter foregrounds. */
val DarkGrammarMateColors = GrammarMateColors(
    correctGreen = Color(0xFF34D6B4),
    incorrectRed = Color(0xFFFF8A80),
    masteryGreen = Color(0xFF34D6B4),
    progressGreen = Color(0xFF34D6B4),
    progressTrackGreen = Color(0xFF1E2B25),
    progressFastGreen = Color(0xFF5BE8CC),
    speedSlowRed = Color(0xFFFF8A80),
    speedMediumYellow = Color(0xFFFFD166),
    progressLabelWhite = Color.White,
    progressTrackGray = Color(0xFF243329),
    srsAgainBackground = Color(0xFF33201C),
    srsAgainText = Color(0xFFFF8A80),
    srsHardBackground = Color(0xFF332A1C),
    srsHardText = Color(0xFFFFB74D),
    srsGoodBackground = Color(0xFF1B3A2E),
    srsGoodText = Color(0xFF34D6B4),
    srsEasyBackground = Color(0xFF1C2A33),
    srsEasyText = Color(0xFF7BC8F0),
    vocabIntervalOrange = Color(0xFFFFB74D),
    vocabCorrectBackground = Color(0xFF1B3A2E),
    vocabIncorrectBackground = Color(0xFF33201C),
    bossBronze = Color(0xFFCD7F32),
    bossSilver = Color(0xFFB8C2BC),
    bossGold = Color(0xFFFFD166),
    destructiveRed = Color(0xFFFF8A80),
    surfaceElevated = Color(0xFF243329)
)

/** CompositionLocal that provides the current [GrammarMateColors]. */
val LocalGrammarMateColors = staticCompositionLocalOf { LightGrammarMateColors }

// =====================================================================
// Material 3 color schemes (consumed by the app theme facade `GrammarMateTheme`)
// =====================================================================
// Values are moved verbatim from the former private `LightColors`/`DarkColors`
// in ui/Theme.kt; `internal` only because the facade now lives in another package.

// --- Light color scheme — refreshed warm palette ---

internal val LightGrammarMateColorScheme = lightColorScheme(
    primary = Color(0xFF0F7C66),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFEFE5),
    onPrimaryContainer = Color(0xFF00382B),
    secondary = Color(0xFF5E8B7E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE9E3),
    onSecondaryContainer = Color(0xFF1B3B31),
    background = Color(0xFFF4F1EC),
    onBackground = Color(0xFF1B2420),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B2420),
    surfaceVariant = Color(0xFFE8E4DD),
    onSurfaceVariant = Color(0xFF5A6B63),
    outline = Color(0xFFC9C2B6),
    outlineVariant = Color(0xFFDED8CC),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

// --- Dark color scheme — Premium Dark / Emerald ---

internal val DarkGrammarMateColorScheme = darkColorScheme(
    primary = Color(0xFF34D6B4),
    onPrimary = Color(0xFF042822),
    primaryContainer = Color(0xFF0B4A3C),
    onPrimaryContainer = Color(0xFF7FEFD8),
    secondary = Color(0xFF5E8B7E),
    onSecondary = Color(0xFF04130F),
    secondaryContainer = Color(0xFF1E2B25),
    onSecondaryContainer = Color(0xFFCFE6DC),
    background = Color(0xFF0E1512),
    onBackground = Color(0xFFE8EFEB),
    surface = Color(0xFF15201B),
    onSurface = Color(0xFFE8EFEB),
    surfaceVariant = Color(0xFF1E2B25),
    onSurfaceVariant = Color(0xFF9DB0A6),
    outline = Color(0xFF3D5247),
    outlineVariant = Color(0xFF2A3A33),
    error = Color(0xFFEF5350),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF3A1B1B),
    onErrorContainer = Color(0xFFFFB4AB)
)

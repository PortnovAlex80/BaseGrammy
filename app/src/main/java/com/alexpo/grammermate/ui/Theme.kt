package com.alexpo.grammermate.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.alexpo.grammermate.ui.components.AppBackground
import com.alexpo.grammermate.ui.designsystem.foundation.DarkGrammarMateColorScheme
import com.alexpo.grammermate.ui.designsystem.foundation.DarkGrammarMateColors
import com.alexpo.grammermate.ui.designsystem.foundation.GrammarMateShapes
import com.alexpo.grammermate.ui.designsystem.foundation.GrammarMateTypography
import com.alexpo.grammermate.ui.designsystem.foundation.LightGrammarMateColorScheme
import com.alexpo.grammermate.ui.designsystem.foundation.LightGrammarMateColors
import com.alexpo.grammermate.ui.designsystem.foundation.LocalGrammarMateColors

// =====================================================================
// GrammarMateTheme — public app theme facade
// =====================================================================
// Foundational declarations (semantic palettes, Material color schemes,
// typography, shapes, text-scale policy) live in
// `ui/designsystem/foundation/` and must not import components, features,
// navigation, or stores. This file stays the composition facade: it resolves
// the active scheme, provides [LocalGrammarMateColors], wraps MaterialTheme,
// and keeps applying `AppBackground` (gradient + safeDrawing insets) to all
// content exactly as before the split.
//
// The top-level compat color getters below intentionally remain in this file
// (package `com.alexpo.grammermate.ui`): screens/components import them from
// this path, or resolve them same-package without an import. They each
// delegate to [LocalGrammarMateColors] — single source in foundation.
// Owner: theme facade; removal: P5+ after call sites migrate to
// `LocalGrammarMateColors.current.*`.

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
        useDarkTheme -> DarkGrammarMateColorScheme
        else -> LightGrammarMateColorScheme
    }
    val grammarMateColors = when {
        useDarkTheme -> DarkGrammarMateColors
        else -> LightGrammarMateColors
    }
    CompositionLocalProvider(LocalGrammarMateColors provides grammarMateColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = GrammarMateShapes,
            typography = GrammarMateTypography,
            content = {
                AppBackground(useDarkTheme = useDarkTheme) {
                    content()
                }
            }
        )
    }
}

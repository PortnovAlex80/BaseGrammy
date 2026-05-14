package com.alexpo.grammermate.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// --- Semantic color constants used across screens ---
// Extracted from hardcoded Color(0xFF...) literals found in UI files.
// Screens should import these from Theme.kt instead of using raw Color literals.
// Replacement in screen files is a separate task.

// Correctness feedback
val CorrectGreen = Color(0xFF2E7D32)
val IncorrectRed = Color(0xFFC62828)

// Drill mode (TrainingScreen)
val DrillBackgroundGreen = Color(0xFFE8F5E9)
val DrillTenseLabelGreen = Color(0xFF388E3C)
val DrillPromptGreen = Color(0xFF2E7D32)

// Mix challenge (TrainingScreen)
val MixChallengeSurface = Color(0xFFE3F2FD)
val MixChallengeText = Color(0xFF1565C0)

// Mastery / progress (HomeScreen, SessionProgressIndicator)
val MasteryGreen = Color(0xFF2E7D32)
val ProgressGreen = Color(0xFF4CAF50)
val ProgressTrackGreen = Color(0xFFC8E6C9)
val ProgressFastGreen = Color(0xFF43A047)

// Speed indicator (SessionProgressIndicator)
val SpeedSlowRed = Color(0xFFE53935)
val SpeedMediumYellow = Color(0xFFFDD835)
val ProgressLabelWhite = Color.White
val ProgressTrackGray = Color(0xFFE0E0E0)

// SRS rating backgrounds and text (DailyPracticeScreen, VocabDrillScreen)
val SrsAgainBackground = Color(0xFFFFEBEE)
val SrsAgainText = Color(0xFFE53935)
val SrsHardBackground = Color(0xFFFFF3E0)
val SrsHardText = Color(0xFFFF9800)
val SrsGoodBackground = Color(0xFFE8F5E9)
val SrsGoodText = Color(0xFF4CAF50)
val SrsEasyBackground = Color(0xFFE3F2FD)
val SrsEasyText = Color(0xFF2196F3)

// Vocab drill (VocabDrillScreen)
val VocabIntervalOrange = Color(0xFFE65100)
val VocabCorrectBackground = Color(0xFFE8F5E9)
val VocabIncorrectBackground = Color(0xFFFFEBEE)

// Boss rewards (GrammarMateApp, LessonRoadmapScreen)
val BossBronze = Color(0xFFCD7F32)
val BossSilver = Color(0xFFC0C0C0)
val BossGold = Color(0xFFFFD700)

// Destructive actions (SettingsScreen)
val DestructiveRed = Color(0xFFB00020)

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
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

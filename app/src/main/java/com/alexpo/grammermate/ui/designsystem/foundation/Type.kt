package com.alexpo.grammermate.ui.designsystem.foundation

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// =====================================================================
// Typography — M3 scale retuned: tighter headings, medium body
// =====================================================================
// Moved verbatim from ui/Theme.kt (was private); `internal` only because the
// app theme facade (`GrammarMateTheme` in ui/Theme.kt) now lives in another
// package. Screens read styles via MaterialTheme.typography, not this val.

internal val GrammarMateTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontWeight = FontWeight.Medium),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp)
)

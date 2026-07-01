package com.alexpo.grammermate.v2.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material 3 type scale для GrammarMate v2.
 *
 * Использует системный sans-serif (без кастомных шрифтов — меньше APK, нулевой
 * риск лицензий/missing-asset). Веса и размеры — дефолтный Material 3 baseline,
 * tweaked для читаемости промптов языкового обучения (чуть крупнее body).
 */

/** Базовые настройки шрифта, разделяемые всеми стилями. */
private val DefaultFontFamily = FontFamily.Default

/** Display — крупный заголовок hero-секции (например, приветствие на Home). */
private val displayLarge = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 57.sp,
    lineHeight = 64.sp,
    letterSpacing = (-0.25).sp,
)

private val displayMedium = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 45.sp,
    lineHeight = 52.sp,
)

private val displaySmall = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 36.sp,
    lineHeight = 44.sp,
)

/** Headline — заголовки секций/экранов. */
private val headlineLarge = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 32.sp,
    lineHeight = 40.sp,
)

private val headlineMedium = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 36.sp,
)

private val headlineSmall = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 24.sp,
    lineHeight = 32.sp,
)

/** Title — заголовки карточек. */
private val titleLarge = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
)

private val titleMedium = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.15.sp,
)

private val titleSmall = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp,
)

/** Body — основной читаемый текст (промпты карточек, описания). */
private val bodyLarge = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 18.sp, // чуть крупнее baseline (16) — для удобства чтения промптов
    lineHeight = 26.sp,
    letterSpacing = 0.5.sp,
)

private val bodyMedium = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.25.sp,
)

private val bodySmall = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.4.sp,
)

/** Label — кнопки, чипы, капсулы. */
private val labelLarge = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp,
)

private val labelMedium = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp,
)

private val labelSmall = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp,
)

/**
 * Полная M3 type scale, собранная из стилей выше.
 *
 * Используется как `Typography`-аргумент [androidx.compose.material3.MaterialTheme].
 */
val GrammarMateTypography = Typography(
    displayLarge = displayLarge,
    displayMedium = displayMedium,
    displaySmall = displaySmall,
    headlineLarge = headlineLarge,
    headlineMedium = headlineMedium,
    headlineSmall = headlineSmall,
    titleLarge = titleLarge,
    titleMedium = titleMedium,
    titleSmall = titleSmall,
    bodyLarge = bodyLarge,
    bodyMedium = bodyMedium,
    bodySmall = bodySmall,
    labelLarge = labelLarge,
    labelMedium = labelMedium,
    labelSmall = labelSmall,
)

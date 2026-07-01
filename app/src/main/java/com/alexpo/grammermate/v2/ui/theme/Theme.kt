package com.alexpo.grammermate.v2.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.alexpo.grammermate.v2.core.domain.model.ThemeMode

/**
 * Тема приложения GrammarMate v2 — presentation-слой.
 *
 * Здесь определены две брендовые цветовые схемы (light/dark) поверх палитры из
 * [Color.kt] и собран `@Composable GrammarMateTheme`, который:
 *  1. разрешает тёмную/светлую тему по [ThemeMode] из [AppConfig];
 *  2. подключает Material You dynamic color на Android 12+ (если разрешено);
 *  3. подкрашивает системные бары под схему (edge-to-edge).
 */

// ── Контрастные on-* цвета (текст/иконки поверх заливочных цветов) ──────────────
// Хранятся рядом со схемой, т.к. существуют только для обслуживания colorScheme.

private val OnTealPrimary = Color(0xFFFFFFFF)
private val OnTealContainerLight = Color(0xFF002020)
private val OnTealContainerDark = Color(0xFF83F8F0)
private val OnAmber = Color(0xFFFFFFFF)
private val OnAmberContainerLight = Color(0xFF2C1600)
private val OnAmberContainerDark = Color(0xFFFFDCBE)
private val OnLavender = Color(0xFFFFFFFF)
private val OnLavenderContainerLight = Color(0xFF271336)
private val OnLavenderContainerDark = Color(0xFFE9D4F6)
private val OnError = Color(0xFFFFFFFF)
private val OnErrorContainerLight = Color(0xFF410002)
private val OnBgLight = Color(0xFF191C1C)
private val OnBgDark = Color(0xFFE0E3E2)
private val OnSurfaceLight = Color(0xFF191C1C)
private val OnSurfaceDark = Color(0xFFE0E3E2)
private val OnSurfaceVariantLight = Color(0xFF3F4946)
private val OnSurfaceVariantDark = Color(0xFFBEC9C6)

/**
 * Светлая цветовая схема бренда GrammarMate (зелёно-бирюзовый teal + amber).
 *
 * Используется по умолчанию на Android < 12 или когда dynamic color отключён.
 */
private val GrammarMateLightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = OnTealPrimary,
    primaryContainer = TealContainer,
    onPrimaryContainer = OnTealContainerLight,
    secondary = AmberSecondary,
    onSecondary = OnAmber,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainerLight,
    tertiary = LavenderTertiary,
    onTertiary = OnLavender,
    tertiaryContainer = LavenderContainer,
    onTertiaryContainer = OnLavenderContainerLight,
    error = ErrorLight,
    onError = OnError,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = NeutralBgLight,
    onBackground = OnBgLight,
    surface = NeutralSurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = NeutralSurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = NeutralOutlineLight,
)

/**
 * Тёмная цветовая схема бренда GrammarMate.
 */
private val GrammarMateDarkColorScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = OnTealPrimary,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = OnTealContainerDark,
    secondary = AmberSecondaryDark,
    onSecondary = OnAmber,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = OnAmberContainerDark,
    tertiary = LavenderTertiaryDark,
    onTertiary = OnLavender,
    tertiaryContainer = LavenderContainerDark,
    onTertiaryContainer = OnLavenderContainerDark,
    error = ErrorDark,
    onError = OnError,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerLight,
    background = NeutralBgDark,
    onBackground = OnBgDark,
    surface = NeutralSurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = NeutralSurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = NeutralOutlineDark,
)

/**
 * Тема приложения GrammarMate v2.
 *
 * Композиция:
 *  1. **Тёмная/светлая развилка** определяется [themeMode]:
 *     - [ThemeMode.SYSTEM] — следует системной настройке ([isSystemInDarkTheme]);
 *     - [ThemeMode.LIGHT]/[ThemeMode.DARK] — явное переопределение.
 *  2. **Dynamic color** (Material You): при [dynamicColor] = true на Android 12+
 *     ([Build.VERSION_CODES.S]) схема берётся из обоев пользователя через
 *     [dynamicLightColorScheme]/[dynamicDarkColorScheme]. На Android < 12 или при
 *     `dynamicColor = false` используется брендовая палитра GrammarMate.
 *  3. **Edge-to-edge**: системные бары подкрашиваются под схему (через
 *     [WindowCompat]), чтобы status bar не «выпадал» из дизайна.
 *
 * @param darkTheme    форсировать тёмную тему (по умолчанию — системная).
 * @param dynamicColor включить Material You (Android 12+). По умолчанию true.
 * @param themeMode     пользовательский режим темы из [AppConfig.themeMode].
 *                      Если `null`, трактуется как [ThemeMode.SYSTEM].
 * @param content       содержимое темы.
 */
@Composable
fun GrammarMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeMode: ThemeMode? = null,
    content: @Composable () -> Unit,
) {
    // 1. Разрешаем фактическую тёмную/светлую тему с учётом пользовательского режима.
    val resolvedDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        null, ThemeMode.SYSTEM -> darkTheme
    }

    val context = LocalContext.current

    // 2. Цветовая схема: dynamic (Android 12+) либо брендовая палитра.
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (resolvedDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        resolvedDark -> GrammarMateDarkColorScheme
        else -> GrammarMateLightColorScheme
    }

    // 3. Подкрашиваем системные бары под схему (edge-to-edge уже включён в Activity).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !resolvedDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GrammarMateTypography,
    ) {
        // Предоставляем шкалу отступов всем composable под темой.
        CompositionLocalProvider(LocalSpacing provides Spacing()) {
            content()
        }
    }
}

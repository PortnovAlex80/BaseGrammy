package com.alexpo.grammermate.v2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Унифицированная шкала отступов для GrammarMate v2.
 *
 * Один источник истины для padding'ов/расстояний во всём UI — вместо россыпи
 * магических `8.dp`/`16.dp` в каждом composable. Доступ через
 * `MaterialTheme.spacing.<level>` (см. расширение ниже).
 *
 * Шкала — 4-px grid (Material baseline), удвоения:
 *   xs=4 · small=8 · medium=16 · large=24 · xl=32 · xxl=48.
 *
 * @property xs     микро-отступ (плотные списки, иконка↔текст).
 * @property small  плотный межэлементный отступ.
 * @property medium стандартный padding карточки/строки.
 * @property large  padding секции/экрана.
 * @property xl     крупные блоки.
 * @property xxl    hero-отступы.
 */
data class Spacing(
    val xs: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
)

/**
 * CompositionLocal, хранящий активную шкалу [Spacing].
 *
 * По умолчанию — базовая шкала GrammarMate. Переопределяется в теме, если позже
 * понадобится адаптивный интервал (например, просторнее на планшетах).
 */
val LocalSpacing = staticCompositionLocalOf { Spacing() }

/**
 * Доступ к шкале отступов из любого composable: `MaterialTheme.spacing.medium`.
 */
val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current

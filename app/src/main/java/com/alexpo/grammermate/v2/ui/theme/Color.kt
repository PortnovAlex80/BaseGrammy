package com.alexpo.grammermate.v2.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Палитра Material 3 для GrammarMate v2.
 *
 * Концепция: тёплые, образовательные тона. Primary — зелёно-бирюзовый (Teal):
 * спокойствие, фокус, ассоциация с ростом и обучением. Акценты — янтарный
 * (терракотовое тепло) и лавандовый (контраст для third-level actions).
 *
 * Схема — вручную подобранная тон-в-тон для light/dark (а не автоматическая
 * генерация), чтобы сохранить узнаваемый бренд-характер приложения.
 */

// ── Brand primary (Teal / зелёно-бирюзовый) ────────────────────────────────────

/** Основной teal-цвет бренда — насыщенный, но не кислотный. */
val TealPrimary = Color(0xFF006A6A)

/** Более светлый teal для тёмной темы (читается на тёмном фоне). */
val TealPrimaryDark = Color(0xFF4EDADA)

/** Контейнер teal (заливка карточек/чипов) — light. */
val TealContainer = Color(0xFF6FF7F0)

/** Контейнер teal — dark. */
val TealContainerDark = Color(0xFF004F50)

// ── Secondary (Янтарный / Amber) — тёплый акцент ───────────────────────────────

val AmberSecondary = Color(0xFF8A5000)
val AmberSecondaryDark = Color(0xFFFFB864)
val AmberContainer = Color(0xFFFFDCBE)
val AmberContainerDark = Color(0xFF553900)

// ── Tertiary (Лавандовый) — third-level контраст ───────────────────────────────

val LavenderTertiary = Color(0xFF6B5778)
val LavenderTertiaryDark = Color(0xFFD7BEE6)
val LavenderContainer = Color(0xFFF2DAFF)
val LavenderContainerDark = Color(0xFF523F5F)

// ── Error ──────────────────────────────────────────────────────────────────────

val ErrorLight = Color(0xFFBA1A1A)
val ErrorDark = Color(0xFFFFB4AB)
val ErrorContainerLight = Color(0xFFFFDAD6)
val ErrorContainerDark = Color(0xFF93000A)

// ── Neutral-фон (наш искусственный тон, не чисто серый) ────────────────────────

/** Фон-базовый свет — тёплый off-white. */
val NeutralBgLight = Color(0xFFFAFDFC)

/** Поверхность — чуть приподнятый тон. */
val NeutralSurfaceLight = Color(0xFFF0F4F3)
val NeutralSurfaceVariantLight = Color(0xFFDAE5E2)
val NeutralOutlineLight = Color(0xFF707875)

val NeutralBgDark = Color(0xFF0F1414)
val NeutralSurfaceDark = Color(0xFF1A2020)
val NeutralSurfaceVariantDark = Color(0xFF3F4946)
val NeutralOutlineDark = Color(0xFF899390)

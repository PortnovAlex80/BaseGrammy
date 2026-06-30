package com.alexpo.grammermate.v2.core.domain.srs

/**
 * Оценка ответа пользователя в алгоритме FSRS v6.
 *
 * Это псевдоним канонического enum'а домена
 * [com.alexpo.grammermate.v2.core.domain.model.SrsRating]
 * (`AGAIN, HARD, GOOD, EASY`), вынесенный в пакет `srs` для удобства:
 * внутри SRS-формул используется короткое имя `SrsRating` без полных
 * import'ов. Не плодит новый enum — единый источник правды остаётся в `model`.
 *
 * Числовые значения рейтинга, принятые в FSRS (используются в формулах):
 *  - AGAIN = 1 (полное забывание),
 *  - HARD  = 2,
 *  - GOOD  = 3 (опорное значение: формулы симметричны относительно GOOD),
 *  - EASY  = 4.
 */
typealias SrsRating = com.alexpo.grammermate.v2.core.domain.model.SrsRating

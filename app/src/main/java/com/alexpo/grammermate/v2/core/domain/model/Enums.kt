package com.alexpo.grammermate.v2.core.domain.model

/**
 * Перечисления домена — чистый Kotlin, без Android-зависимостей.
 *
 * Сюда вынесены все замкнутые множества значений (статусы, режимы, типы),
 * используемые моделями контента, прогресса и сессий. Enum'ы — это
 * стабильный контракт домена, на который опираются data- и ui-слои.
 */

/** Режим тренировки внутри пака. */
enum class TrainingMode { LESSON, ALL_SEQUENTIAL, ALL_MIXED }

/** Жизненный цикл сессии тренировки. */
enum class SessionStatus { ACTIVE, PAUSED, COMPLETED }

/** Текущее состояние шага сессии (активно / показана подсказка). */
enum class SessionState { ACTIVE, HINT_SHOWN }

/** Способ ввода ответа пользователем. */
enum class InputMode { VOICE, KEYBOARD, WORD_BANK }

/** Тип «босса» — контрольной точки в обучении. */
enum class BossType { LESSON, MEGA, ELITE }

/**
 * Награда за босса. [pct] — порог правильных ответов (в процентах),
 * необходимый для получения данного уровня.
 */
enum class BossReward(val pct: Int) { BRONZE(30), SILVER(60), GOLD(90) }

/** Уровень сложности подсказки. */
enum class HintLevel { EASY, MEDIUM, HARD }

/** Режим оформления приложения. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Тип практического упражнения. */
enum class PracticeType { TRANSLATION, VOCAB, VERB }

/** Оценка в алгоритме интервального повторения (SRS). */
enum class SrsRating { AGAIN, HARD, GOOD, EASY }

/** Оценка сложности конкретной карточки пользователем. */
enum class CardDifficultyRating { AGAIN, HARD, GOOD, EASY }

/** Тип блока в дневной норме. */
enum class DailyBlockType { TRANSLATE, VOCAB, VERBS }

/** Фаза истории (сторителлинга): начало / завершение урока. */
enum class StoryPhase { CHECK_IN, CHECK_OUT }

/**
 * Пресет помодоро-таймера. [minutes] — длительность фокуса в минутах.
 */
enum class PomodoroPreset(val minutes: Int) { QUICK(5), FOCUS(15), CLASSIC(20) }

/** Пометка фонового словарного слова. */
enum class BgVocabMark { NONE, GREEN, RED }

/** Направление drill-тренировки словаря. */
enum class VocabDrillDirection { IT_TO_RU, RU_TO_IT }

/** Тип карточки. */
enum class CardType { SENTENCE, VERB_DRILL, AUX_DRILL }

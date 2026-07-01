package com.alexpo.grammermate.v2.core.domain.model

/**
 * Модели геймификации — boss-битвы, elite-тренировки, pomodoro-таймер и
 * SRS-лестница уроков.
 *
 * Чистый Kotlin, без Room-аннотаций и Android-зависимостей. Визуальные «цветки»
 * ([FlowerState], [FlowerVisual], [PackFlowerVisual]) объявлены в [Flower.kt];
 * boss/elite/pomodoro — изменяемые во времени состояния сессий. Enum'ы
 * ([BossType], [BossReward], [HintLevel], [CardDifficultyRating]) импортируются
 * из [Enums].
 */

/**
 * Состояние boss-битвы — контрольной точки обучения.
 *
 * Накапливает прогресс по карточкам, итоговую награду и историю прошлых битв.
 * [savedHintLevel] восстанавливается при выходе из boss-режима.
 *
 * @property bossActive          активна ли сейчас boss-сессия.
 * @property bossType            тип босса (LESSON/MEGA/ELITE), либо null.
 * @property bossTotal           всего карточек в boss-сессии.
 * @property bossProgress        пройдено карточек в boss-сессии.
 * @property bossReward          итоговая награда за текущую/последнюю битву, либо null.
 * @property bossRewardMessage   сообщение о награде для UI, либо null.
 * @property bossFinishedToken   токен завершения (инкрементируется при финише).
 * @property bossLastType        тип последнего завершённого босса, либо null.
 * @property bossErrorMessage    сообщение об ошибке boss-сессии, либо null.
 * @property bossLessonRewards   награды по урокам (lessonId → [BossReward]).
 * @property bossMegaRewards     награды мега-боссов по ключу → [BossReward].
 * @property savedHintLevel      уровень подсказок до старта boss (восстанавливается при выходе).
 */
data class BossState(
    val bossActive: Boolean = false,
    val bossType: BossType? = null,
    val bossTotal: Int = 0,
    val bossProgress: Int = 0,
    val bossReward: BossReward? = null,
    val bossRewardMessage: String? = null,
    val bossFinishedToken: Int = 0,
    val bossLastType: BossType? = null,
    val bossErrorMessage: String? = null,
    val bossLessonRewards: Map<String, BossReward> = emptyMap(),
    val bossMegaRewards: Map<String, BossReward> = emptyMap(),
    val savedHintLevel: HintLevel = HintLevel.EASY,
)

/**
 * Elite-тренировка состояние — скоростной режим с увеличенным объёмом.
 *
 * @property eliteActive          активна ли сейчас elite-сессия.
 * @property eliteStepIndex       индекс шага в elite-сессии.
 * @property eliteBestSpeeds      лучшие темпы (слов/мин) по шагам/сессиям.
 * @property eliteFinishedToken   токен завершения (инкрементируется при финише).
 * @property eliteUnlocked        разблокирован ли elite-режим.
 * @property eliteSizeMultiplier  множитель размера elite-сессии (больше обычной).
 */
data class EliteState(
    val eliteActive: Boolean = false,
    val eliteStepIndex: Int = 0,
    val eliteBestSpeeds: List<Double> = emptyList(),
    val eliteFinishedToken: Int = 0,
    val eliteUnlocked: Boolean = false,
    val eliteSizeMultiplier: Double = 1.25,
)

/**
 * Статистика pomodoro-сессии — собранные за фокус-интервал метрики.
 *
 * @property cardsShown         показано карточек.
 * @property cardsCorrect       правильных ответов.
 * @property cardsIncorrect     неправильных ответов.
 * @property difficultyRatings  распределение оценок сложности (рейтинг → сколько раз).
 * @property wordsPerMinute     темп набора (слов/мин).
 * @property durationMinutes    плановая длительность фокуса в минутах.
 * @property completedAtMs      epoch-мс завершения сессии.
 */
data class PomodoroSessionStats(
    val cardsShown: Int = 0,
    val cardsCorrect: Int = 0,
    val cardsIncorrect: Int = 0,
    val difficultyRatings: Map<CardDifficultyRating, Int> = emptyMap(),
    val wordsPerMinute: Double = 0.0,
    val durationMinutes: Int = 0,
    val completedAtMs: Long = 0,
)

/**
 * Состояние pomodoro-таймера.
 *
 * [baselineCorrect]/[baselineIncorrect] — счётчики на момент старта помодоро,
 * чтобы статистика считалась как прирост за фокус-интервал.
 *
 * @property isActive                 запущен ли таймер.
 * @property isPaused                 приостановлен ли таймер.
 * @property isComplete               завершён ли фокус-интервал.
 * @property selectedDurationMinutes  выбранная длительность фокуса (мин).
 * @property remainingSeconds         сколько секунд осталось.
 * @property totalSeconds             полная длительность фокуса в секундах.
 * @property stats                    накопленная статистика сессии.
 * @property showRatingPrompt         показать ли промпт оценки сложности по завершении.
 * @property showExitConfirm          показать ли диалог подтверждения выхода.
 * @property baselineCorrect          счётчик правильных на момент старта помодоро.
 * @property baselineIncorrect        счётчик неправильных на момент старта помодоро.
 */
data class PomodoroState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val isComplete: Boolean = false,
    val selectedDurationMinutes: Int = 20,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val stats: PomodoroSessionStats = PomodoroSessionStats(),
    val showRatingPrompt: Boolean = false,
    val showExitConfirm: Boolean = false,
    val baselineCorrect: Int = 0,
    val baselineIncorrect: Int = 0,
)

/**
 * Строка таблицы SRS-лестницы урока — для экрана Ladder.
 *
 * @property index            порядковый номер строки (для нумерации UI).
 * @property lessonId         урок строки.
 * @property title            заголовок урока.
 * @property uniqueCardShows сколько уникальных карточек показано (null — не начат).
 * @property daysSinceLastShow дней с последнего показа (null — не начат).
 * @property intervalLabel    метка интервала/просрочки (null — не начат).
 */
data class LessonLadderRow(
    val index: Int,
    val lessonId: LessonId,
    val title: String,
    val uniqueCardShows: Int?,
    val daysSinceLastShow: Int?,
    val intervalLabel: String?,
)

/**
 * Метрики SRS для одного урока (computed) — без UI-атрибутов (заголовка/индекса).
 *
 * @property uniqueCardShows  сколько уникальных карточек показано (null — урок не начат).
 * @property daysSinceLastShow дней с последнего показа (null — урок не начат).
 * @property intervalLabel    метка интервала («N-M») либо просрочки («Просрочка+K»), null — не начат.
 */
data class LessonLadderMetrics(
    val uniqueCardShows: Int?,
    val daysSinceLastShow: Int?,
    val intervalLabel: String?,
)

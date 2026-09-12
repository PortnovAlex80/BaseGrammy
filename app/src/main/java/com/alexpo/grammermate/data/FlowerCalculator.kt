package com.alexpo.grammermate.data

/**
 * Калькулятор визуального состояния цветка на основе данных освоения урока.
 *
 * Логика:
 * - masteryPercent: процент уникальных показов от порога закрепления, где порог =
 *   min(totalCardsInLesson, MASTERY_THRESHOLD). Короткий урок (12 карточек)
 *   достигает 100% за 12 показов; длинный — как раньше, максимум за 150.
 *   Без информации о размере урока (0) порог = MASTERY_THRESHOLD.
 * - healthPercent: здоровье по кривой забывания (50-100%)
 * - scaleMultiplier: масштаб иконки = masteryPercent * healthPercent (50-100%)
 */
object FlowerCalculator {

    /**
     * Рассчитать визуальное состояние цветка.
     *
     * @param mastery данные освоения урока (null = урок не начат)
     * @param totalCardsInLesson общее количество карточек в уроке (для определения завершённости)
     * @return визуальное представление цветка
     */
    fun calculate(mastery: LessonMasteryState?, totalCardsInLesson: Int = 0): FlowerVisual {
        // Урок не начат - показываем SEED (готов к началу), а не LOCKED
        // LOCKED используется только в UI для заблокированных уроков
        if (mastery == null || mastery.uniqueCardShows == 0) {
            return FlowerVisual(
                state = FlowerState.SEED,
                masteryPercent = 0f,
                healthPercent = 1f,
                scaleMultiplier = 0.5f
            )
        }

        // Процент закрепления (0-100%). Порог нормируется на размер урока:
        // иначе короткий урок (11-14 карточек) никогда не превышает ~8%
        // и визуальный прогресс не виден (арх. аудит §2.4).
        val masteryThreshold = if (totalCardsInLesson > 0) {
            minOf(totalCardsInLesson, SpacedRepetitionConfig.MASTERY_THRESHOLD)
        } else {
            SpacedRepetitionConfig.MASTERY_THRESHOLD
        }
        val masteryPercent = (mastery.uniqueCardShows.toFloat() / masteryThreshold)
            .coerceIn(0f, 1f)

        // Дней с последнего показа
        val daysSinceLastShow = calculateDaysSince(mastery.lastShowDateMs)

        // Проверка на исчезновение (> 90 дней)
        if (daysSinceLastShow > SpacedRepetitionConfig.GONE_THRESHOLD_DAYS) {
            return FlowerVisual(
                state = FlowerState.GONE,
                masteryPercent = 0f,
                healthPercent = 0f,
                scaleMultiplier = 0.5f
            )
        }

        // Здоровье цветка по кривой забывания
        val healthPercent = SpacedRepetitionConfig.calculateHealthPercent(
            daysSinceLastShow = daysSinceLastShow,
            intervalStepIndex = mastery.intervalStepIndex
        )

        // Определение состояния цветка
        val state = determineFlowerState(masteryPercent, healthPercent, mastery)

        // Масштаб = процент закрепления * здоровье (минимум 50%)
        val scale = (masteryPercent * healthPercent).coerceIn(0.5f, 1.0f)

        return FlowerVisual(
            state = state,
            masteryPercent = masteryPercent,
            healthPercent = healthPercent,
            scaleMultiplier = scale
        )
    }

    /**
     * Определить состояние цветка на основе метрик.
     */
    private fun determineFlowerState(
        masteryPercent: Float,
        healthPercent: Float,
        mastery: LessonMasteryState
    ): FlowerState {
        return when {
            // Если здоровье упало ниже порога увядания
            // Используем небольшой эпсилон т.к. формула гарантирует health >= WILTED_THRESHOLD
            healthPercent <= SpacedRepetitionConfig.WILTED_THRESHOLD + 0.01f -> FlowerState.WILTED

            // Если здоровье между 50% и 100% - увядает
            healthPercent < 1.0f -> FlowerState.WILTING

            // Определяем по проценту закрепления
            masteryPercent < 0.33f -> FlowerState.SEED
            masteryPercent < 0.66f -> FlowerState.SPROUT
            else -> FlowerState.BLOOM
        }
    }

    /**
     * Рассчитать количество дней с указанной даты.
     */
    private fun calculateDaysSince(timestampMs: Long): Int {
        if (timestampMs <= 0L) return 0
        val now = System.currentTimeMillis()
        val diffMs = now - timestampMs
        return (diffMs / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
    }

    /**
     * Получить emoji для состояния цветка.
     */
    fun getEmoji(state: FlowerState): String {
        return when (state) {
            FlowerState.LOCKED -> "\uD83D\uDD12"  // 🔒
            FlowerState.SEED -> "\uD83C\uDF31"    // 🌱
            FlowerState.SPROUT -> "\uD83C\uDF3F"  // 🌿
            FlowerState.BLOOM -> "\uD83C\uDF38"   // 🌸
            FlowerState.WILTING -> "\uD83E\uDD40" // 🥀
            FlowerState.WILTED -> "\uD83C\uDF42"  // 🍂
            FlowerState.GONE -> "\u26AB"          // ⚫
        }
    }

    /**
     * Получить emoji с учётом масштаба (для отображения).
     * Масштаб влияет на размер шрифта в UI.
     */
    fun getEmojiWithScale(flower: FlowerVisual): Pair<String, Float> {
        return getEmoji(flower.state) to flower.scaleMultiplier
    }
}

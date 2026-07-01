package com.alexpo.grammermate.v2.core.domain.model

/**
 * Визуальные модели «цветка» прогресса — чистый Kotlin, ноль Android.
 *
 * Перенесены из v1 `data/Models.kt:206` (FlowerState) и `data/Models.kt:219`
 * (FlowerVisual), плюс `feature/progress/PackProgressCalculator.kt:22`
 * (PackFlowerVisual). Вынесены в model-пакет, т.к. это доменные value-типы,
 * на которые опираются калькуляторы прогресса и UI.
 */

/**
 * Состояние цветка для отображения в UI.
 *
 * Перенесено 1:1 из v1 `data/Models.kt:206-214`. Семантика переходов задаётся
 * в [com.alexpo.grammermate.v2.core.domain.progress.FlowerCalculator].
 */
enum class FlowerState {
    LOCKED,
    SEED,
    SPROUT,
    BLOOM,
    WILTING,
    WILTED,
    GONE,
}

/**
 * Визуальное представление цветка одного урока.
 *
 * Перенесено 1:1 из v1 `data/Models.kt:219-224`.
 *
 * @property state           состояние цветка.
 * @property masteryPercent  процент закрепления 0..1 (uniqueCardShows / MASTERY_THRESHOLD).
 * @property healthPercent   здоровье по кривой забывания Эббингауза, 0..1.
 * @property scaleMultiplier масштаб иконки = masteryPercent * healthPercent, clamp [0.5, 1.0].
 */
data class FlowerVisual(
    val state: FlowerState,
    val masteryPercent: Float,
    val healthPercent: Float,
    val scaleMultiplier: Float,
)

/**
 * Визуальное представление цветка пака (агрегат по урокам).
 *
 * Перенесено 1:1 из v1 `feature/progress/PackProgressCalculator.kt:22-29`.
 *
 * @property flowerState     общее состояние цветка пака.
 * @property depth           средний процент освоения по начатым урокам, 0..1.
 * @property healthPercent   здоровье по кривой Эббингауза, 0..1.
 * @property scaleMultiplier depth * health, clamp [0.5, 1.0].
 * @property completedLessons число уроков с completedAtMs != null.
 * @property totalLessons    всего уроков в паке.
 */
data class PackFlowerVisual(
    val flowerState: FlowerState,
    val depth: Float,
    val healthPercent: Float,
    val scaleMultiplier: Float,
    val completedLessons: Int,
    val totalLessons: Int,
)

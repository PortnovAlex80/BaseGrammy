package com.alexpo.grammermate.v2.core.domain.model

/**
 * Результат проверки ответа пользователя — чистый доменный value-тип.
 *
 * Перенесено из v1 `data/CardSessionContract.kt:63` (`data class AnswerResult`).
 * В v2 живёт в model-пакете core/domain (ноль Android, ноль feature-зависимостей),
 * т.к. на него опирается чистый [com.alexpo.grammermate.v2.core.domain.training.CardSessionStateMachine].
 *
 * Примечание: в feature-слое (`feature/training/TrainingViewState.kt`) есть
 * упрощённый `AnswerResult(val correct: Boolean)` для UI-state — это РАЗНЫЙ тип;
 * доменный — канонический (с displayAnswer/hintShown), feature-тип — view-projection.
 *
 * @property correct       true — ответ принят; false — неверный.
 * @property displayAnswer ответ для отображения (первый принимаемый ответ карточки).
 * @property hintShown     была ли показана подсказка при этом результате.
 */
data class AnswerResult(
    val correct: Boolean,
    val displayAnswer: String,
    val hintShown: Boolean = false,
)

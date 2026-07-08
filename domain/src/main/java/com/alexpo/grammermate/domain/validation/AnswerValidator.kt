package com.alexpo.grammermate.domain.validation

import com.alexpo.grammermate.domain.TrainingConfig
import com.alexpo.grammermate.domain.model.InputMode

/**
 * Результат валидации ответа пользователя против принимаемых ответов.
 *
 * @property isCorrect       Принят ли нормализованный ввод (совпал с ответом).
 * @property normalizedInput Ввод после нормализации (trim, lowercase, снятие
 *                           диакритик, удаление пунктуации).
 * @property hintShown       Достигнут ли порог подсказки (3 неудачные попытки).
 * @property hintText        Текст подсказки (первый принимаемый ответ), либо null.
 */
data class AnswerValidationResult(
    val isCorrect: Boolean,
    val normalizedInput: String,
    val hintShown: Boolean = false,
    val hintText: String? = null,
)

/**
 * Чистая-Kotlin валидация ответа.
 *
 * Перенесено из v1
 * `app/legacy-src/java/com/alexpo/grammermate/feature/training/AnswerValidator.kt`.
 *
 * Нормализует ввод через [Normalizer], затем сравнивает с каждым принимаемым
 * ответом (каждая строка ответа может содержать альтернативы, разделённые `+`,
 * как в CSV-формате уроков).
 *
 * Ноль Android-зависимостей — тестируется на чистой JVM.
 *
 * @param normalizer [Normalizer] для нормализации текста. По умолчанию синглтон
 *                   [Normalizer], но инъектируется для тестов.
 */
class AnswerValidator(
    private val normalizer: Normalizer = Normalizer,
) {

    companion object {
        /** Число неправильных попыток до автоматического показа подсказки. */
        const val HINT_THRESHOLD: Int = TrainingConfig.HINT_THRESHOLD
    }

    /**
     * Проверить [input] против [acceptedAnswers].
     *
     * Каждая запись [acceptedAnswers] может содержать несколько альтернатив,
     * разделённых `+` (CSV multi-answer, напр. "ciao+salve"). Каждая альтернатива
     * нормализуется и сравнивается с нормализованным вводом.
     *
     * В режиме VOICE используется [Normalizer.normalizeForVoice], иначе
     * [Normalizer.normalize] — точно как в v1 (строки 69–85).
     *
     * При [testMode]=true ответ принимается всегда (correct), что повторяет
     * поведение v1 `TrainingViewModel.submitAnswer`.
     *
     * @param input           сырой ввод пользователя.
     * @param acceptedAnswers список принимаемых ответов (могут содержать `+`).
     * @param testMode        если true — сравнение пропускается, ответ верный.
     * @param inputMode       режим ввода (VOICE → normalizeForVoice).
     * @return [AnswerValidationResult] с результатом.
     */
    fun validate(
        input: String,
        acceptedAnswers: List<String>,
        testMode: Boolean = false,
        inputMode: InputMode = InputMode.KEYBOARD,
    ): AnswerValidationResult {
        val normalizedInput = if (inputMode == InputMode.VOICE) {
            normalizer.normalizeForVoice(input)
        } else {
            normalizer.normalize(input)
        }

        val isCorrect = testMode || acceptedAnswers.any { answer ->
            // Сплит по `+` для CSV multi-answer-формата ("ciao+salve").
            answer.split("+").any { alternative ->
                val normalizedAnswer = if (inputMode == InputMode.VOICE) {
                    normalizer.normalizeForVoice(alternative)
                } else {
                    normalizer.normalize(alternative)
                }
                normalizedAnswer == normalizedInput
            }
        }

        return AnswerValidationResult(
            isCorrect = isCorrect,
            normalizedInput = normalizedInput,
            hintShown = false,
            hintText = null,
        )
    }

    /**
     * Показывать ли подсказку по числу последовательных неправильных попыток.
     *
     * Перенесено 1:1 из v1 `AnswerValidator.shouldShowHint`, строки 102–104.
     *
     * @param incorrectAttempts число неудачных попыток на текущей карточке.
     * @return true, если [incorrectAttempts] >= [HINT_THRESHOLD] (по умолчанию 3).
     */
    fun shouldShowHint(incorrectAttempts: Int): Boolean {
        return incorrectAttempts >= HINT_THRESHOLD
    }

    /**
     * Построить текст подсказки из списка принимаемых ответов.
     *
     * Возвращает первый принимаемый ответ, повторяя поведение v1
     * `AnswerValidator.getHintText`, строки 117–119 (SessionRunner и
     * DailyPracticeSessionProvider показывают `card.acceptedAnswers.first()`).
     *
     * @param acceptedAnswers список принимаемых ответов.
     * @return первый ответ, либо пустая строка, если список пуст.
     */
    fun getHintText(acceptedAnswers: List<String>): String {
        return acceptedAnswers.firstOrNull()?.ifBlank { "" } ?: ""
    }
}

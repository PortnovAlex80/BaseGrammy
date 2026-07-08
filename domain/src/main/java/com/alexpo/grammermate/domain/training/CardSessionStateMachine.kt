package com.alexpo.grammermate.domain.training

import com.alexpo.grammermate.domain.TrainingConfig
import com.alexpo.grammermate.domain.model.AnswerResult
import com.alexpo.grammermate.domain.model.InputMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Результат [CardSessionStateMachine.onSubmit] — sealed interface, чтобы вызывающая
 * сторона различала correct/wrong/hint без повторной проверки булевых флагов.
 *
 * Перенесено из v1
 * `app/legacy-src/java/com/alexpo/grammermate/feature/training/CardSessionStateMachine.kt:77`
 * (`sealed class OnSubmitResult`). В v2 — sealed interface (идиоматичнее); имена
 * полей приведены к терминологии задачи ([incorrectAttempts]/[remainingAttempts]).
 */
sealed interface OnSubmitResult {
    /** Ответ верный. [answerResult] содержит отображаемый ответ. */
    data class Correct(val answerResult: AnswerResult) : OnSubmitResult

    /** Ответ неверный, но попытки остались. */
    data class Wrong(val incorrectAttempts: Int, val remainingAttempts: Int) : OnSubmitResult

    /** Достигнут максимум попыток — подсказка (ответ) показана. */
    data class HintShown(val answer: String) : OnSubmitResult
}

/**
 * Переиспользуемый держатель состояния машины retry/hint для провайдеров сессий
 * карточек (VerbDrill, DailyPractice и т. д.).
 *
 * Перенесено из v1
 * `app/legacy-src/java/com/alexpo/grammermate/feature/training/CardSessionStateMachine.kt`.
 * Использует `kotlinx.coroutines.flow.MutableStateFlow` (а не Compose
 * `mutableStateOf`) для JUnit-тестируемости на чистой JVM.
 *
 * Инкапсулирует общий паттерн:
 *  - верный ответ → сброс состояния, вернуть результат;
 *  - неверный ответ → инкремент попыток, inline-фидбек либо авто-подсказка;
 *  - [maxAttempts] неверных → авто-показ ответа как подсказки (инпут остаётся видимым);
 *  - ручной «Показать ответ» → форс-подсказка;
 *  - пользователь печатает после подсказки → очистить подсказку, сбросить попытки.
 *
 * **Адаптация v2:** в v1 `answerProvider` принимал `SessionCard` (полный объект).
 * В v2 чистый домен оперирует только ID, поэтому [answerProvider] принимает
 * `String` (cardId) и возвращает первый принимаемый ответ карточки. Это делает
 * стейт-машину независимой от контент-модели — вызывающая сторона resolves
 * cardId → answer через репозиторий/маппинг.
 *
 * Ноль Android-зависимостей (только kotlinx.coroutines.flow из stdlib-coroutines).
 *
 * @param maxAttempts     максимум неверных попыток до авто-подсказки (по умолчанию
 *                        [TrainingConfig.HINT_THRESHOLD]).
 * @param answerProvider  функция cardId → первый принимаемый ответ (для подсказки).
 */
class CardSessionStateMachine(
    val maxAttempts: Int = TrainingConfig.HINT_THRESHOLD,
    private val answerProvider: (String) -> String,
) {

    private val _incorrectAttempts = MutableStateFlow(0)
    /** Последовательные неверные попытки на текущей карточке. */
    var incorrectAttempts: Int
        get() = _incorrectAttempts.value
        private set(value) { _incorrectAttempts.value = value }
    val incorrectAttemptsFlow: StateFlow<Int> = _incorrectAttempts.asStateFlow()

    private val _hintAnswer = MutableStateFlow<String?>(null)
    /** Не null — ответ показывается как подсказка (авто или вручную). */
    var hintAnswer: String?
        get() = _hintAnswer.value
        private set(value) { _hintAnswer.value = value }
    val hintAnswerFlow: StateFlow<String?> = _hintAnswer.asStateFlow()

    private val _showIncorrectFeedback = MutableStateFlow(false)
    /** true — показать inline «Неверно» в инпут-контролах (попытка < max). */
    var showIncorrectFeedback: Boolean
        get() = _showIncorrectFeedback.value
        private set(value) { _showIncorrectFeedback.value = value }
    val showIncorrectFeedbackFlow: StateFlow<Boolean> = _showIncorrectFeedback.asStateFlow()

    private val _remainingAttempts = MutableStateFlow(maxAttempts)
    /** Оставшиеся попытки до авто-подсказки. */
    var remainingAttempts: Int
        get() = _remainingAttempts.value
        private set(value) { _remainingAttempts.value = value }
    val remainingAttemptsFlow: StateFlow<Int> = _remainingAttempts.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    /** Пауза сессии (ответ показан, ждём продолжения от пользователя). */
    var isPaused: Boolean
        get() = _isPaused.value
        private set(value) { _isPaused.value = value }
    val isPausedFlow: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _voiceTriggerToken = MutableStateFlow(0)
    /** Токен, инкрементируемый для запуска авто-распознавания голоса. */
    var voiceTriggerToken: Int
        get() = _voiceTriggerToken.value
        private set(value) { _voiceTriggerToken.value = value }
    val voiceTriggerTokenFlow: StateFlow<Int> = _voiceTriggerToken.asStateFlow()

    // ── Действия ─────────────────────────────────────────────────────────

    /**
     * Обработать отправленный ответ. Возвращает соответствующий [OnSubmitResult].
     *
     * Перенесено 1:1 из v1 `CardSessionStateMachine.onSubmit`, строки 99–137
     * (адаптировано: [cardId] вместо `SessionCard`; [answerProvider] по cardId).
     *
     * Логика:
     *  - если подсказка уже показана (hintAnswer != null) → игнор, вернуть Wrong;
     *  - верно → onCorrect(), сброс состояния, [OnSubmitResult.Correct];
     *  - неверно → onWrong(), incorrectAttempts++, remainingAttempts=max−incorrect:
     *    - если incorrect >= max → показать ответ (hintAnswer), пауза, [OnSubmitResult.HintShown];
     *    - иначе inline-фидбек, и при VOICE — инкремент voiceTriggerToken (авто-retry),
     *      [OnSubmitResult.Wrong].
     *
     * @param isCorrect нормализованно-верный ли ответ.
     * @param cardId    ID текущей карточки (для извлечения ответа-подсказки).
     * @param inputMode режим ввода (VOICE → авто-retry).
     * @param onCorrect колбэк side-эффектов верного ответа (напр. запись времени).
     * @param onWrong   колбэк side-эффектов неверного ответа.
     */
    fun onSubmit(
        isCorrect: Boolean,
        cardId: String,
        inputMode: InputMode = InputMode.KEYBOARD,
        onCorrect: () -> Unit = {},
        onWrong: () -> Unit = {},
    ): OnSubmitResult {
        // Подсказка уже показана — игнорируем дальнейшие отправки.
        if (hintAnswer != null) return OnSubmitResult.Wrong(incorrectAttempts, remainingAttempts)

        if (isCorrect) {
            val answer = answerProvider(cardId)
            onCorrect()
            incorrectAttempts = 0
            showIncorrectFeedback = false
            remainingAttempts = maxAttempts
            return OnSubmitResult.Correct(AnswerResult(correct = true, displayAnswer = answer))
        } else {
            onWrong()
            incorrectAttempts++
            remainingAttempts = maxAttempts - incorrectAttempts
            if (incorrectAttempts >= maxAttempts) {
                // Авто-показ ответа после max неверных попыток.
                val answer = answerProvider(cardId)
                hintAnswer = answer
                showIncorrectFeedback = false
                isPaused = true
                return OnSubmitResult.HintShown(answer)
            } else {
                // Inline «Неверно» для попыток < max.
                showIncorrectFeedback = true
                // Авто-запуск распознавания голоса в VOICE-режиме для retry.
                if (inputMode == InputMode.VOICE) {
                    voiceTriggerToken++
                }
                return OnSubmitResult.Wrong(incorrectAttempts, remainingAttempts)
            }
        }
    }

    /**
     * Обработать изменение текста ввода. Сбрасывает подсказку и попытки, когда
     * пользователь начинает печатать после показа ответа.
     *
     * Перенесено 1:1 из v1 `CardSessionStateMachine.onInputChanged`, строки 143–150.
     */
    fun onInputChanged(text: String) {
        if (text.isNotBlank() && hintAnswer != null) {
            hintAnswer = null
            isPaused = false
            incorrectAttempts = 0
            remainingAttempts = maxAttempts
        }
    }

    /**
     * Форс-показ ответа как подсказки (ручная кнопка «глаз»).
     *
     * Перенесено 1:1 из v1 `CardSessionStateMachine.showAnswer`, строки 156–164
     * (адаптировано: [cardId] вместо `SessionCard`). Возвращает строку-ответ.
     */
    fun showAnswer(cardId: String): String {
        val answer = answerProvider(cardId)
        hintAnswer = answer
        incorrectAttempts = maxAttempts
        showIncorrectFeedback = false
        remainingAttempts = 0
        isPaused = true
        return answer
    }

    /**
     * Сбросить inline-фидбек «Неверно», когда пользователь печатает новую попытку.
     *
     * Перенесено 1:1 из v1 `CardSessionStateMachine.clearIncorrectFeedback`, строки 169–171.
     */
    fun clearIncorrectFeedback() {
        showIncorrectFeedback = false
    }

    /**
     * Сбросить ВСЁ состояние retry/hint к начальным значениям.
     *
     * Перенесено 1:1 из v1 `CardSessionStateMachine.reset`, строки 177–183.
     * Вызывается при переходе к следующей карточке, возврате назад или рестарте.
     */
    fun reset() {
        incorrectAttempts = 0
        hintAnswer = null
        showIncorrectFeedback = false
        remainingAttempts = maxAttempts
        isPaused = false
    }

    /**
     * Инкремент voice-триггер-токена (напр. при переключении в VOICE или смене карты).
     *
     * Перенесено 1:1 из v1 `CardSessionStateMachine.triggerVoice`, строки 188–190.
     */
    fun triggerVoice() {
        voiceTriggerToken++
    }

    /**
     * Установить паузу без показа подсказки (togglePause пользователем).
     *
     * Перенесено 1:1 из v1 `CardSessionStateMachine.pause`, строки 196–198.
     */
    fun pause() {
        isPaused = true
    }

    /**
     * Возобновить после ручной паузы БЕЗ сброса прочего состояния.
     *
     * Перенесено 1:1 из v1 `CardSessionStateMachine.resume`, строки 204–206.
     * В отличие от [reset], сохраняет hint/попытки/прочие поля.
     */
    fun resume() {
        isPaused = false
    }
}

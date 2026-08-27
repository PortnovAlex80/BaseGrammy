package com.alexpo.grammermate.domain.training

import com.alexpo.grammermate.domain.model.InputMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Регрессионные тесты для [CardSessionStateMachine] — машины retry/hint.
 *
 * Инварианты (перенесённые из v1):
 *  - onSubmit верно → Correct + reset;
 *  - неверный 1..max-1 → Wrong (remaining уменьшается);
 *  - неверный max (3) → HintShown + пауза;
 *  - hintAnswer показан → повторный submit → Wrong (не коррект);
 *  - showAnswer (ручной «глаз») → форс-подсказка;
 *  - onInputChanged очищает подсказку;
 *  - reset сбрасывает всё.
 *
 * StateFlow-based, но значения читаются синхронно через публичные свойства;
 * один тест использует runTest для проверки flow.
 */
class CardSessionStateMachineTest {

    private val cardId = "card_1"
    private val answer = "ciao"

    private fun newStateMachine(maxAttempts: Int = 3): CardSessionStateMachine =
        CardSessionStateMachine(maxAttempts = maxAttempts, answerProvider = { _ -> answer })

    // ── onSubmit: верный ответ ─────────────────────────────────────────────

    @Test
    fun `onSubmit correct returns Correct and resets state`() {
        val sm = newStateMachine()
        // Сначала сделаем одну неверную, чтобы было что сбрасывать.
        sm.onSubmit(isCorrect = false, cardId = cardId)

        val result = sm.onSubmit(isCorrect = true, cardId = cardId)

        assertThat(result).isInstanceOf(OnSubmitResult.Correct::class.java)
        assertThat(sm.incorrectAttempts).isEqualTo(0)
        assertThat(sm.remainingAttempts).isEqualTo(3)
        assertThat(sm.showIncorrectFeedback).isFalse()
        assertThat(sm.hintAnswer).isNull()
        val correct = result as OnSubmitResult.Correct
        assertThat(correct.answerResult.correct).isTrue()
        assertThat(correct.answerResult.displayAnswer).isEqualTo(answer)
    }

    @Test
    fun `onSubmit correct invokes onCorrect callback`() {
        val sm = newStateMachine()
        var called = false
        sm.onSubmit(isCorrect = true, cardId = cardId, onCorrect = { called = true })
        assertThat(called).isTrue()
    }

    // ── onSubmit: неверный 1..max-1 → Wrong ───────────────────────────────

    @Test
    fun `onSubmit wrong first attempt returns Wrong with remaining decremented`() {
        val sm = newStateMachine(maxAttempts = 3)
        val result = sm.onSubmit(isCorrect = false, cardId = cardId)
        assertThat(result).isInstanceOf(OnSubmitResult.Wrong::class.java)
        val wrong = result as OnSubmitResult.Wrong
        assertThat(wrong.incorrectAttempts).isEqualTo(1)
        assertThat(wrong.remainingAttempts).isEqualTo(2) // 3-1
        assertThat(sm.showIncorrectFeedback).isTrue()
        assertThat(sm.hintAnswer).isNull()
        assertThat(sm.isPaused).isFalse()
    }

    @Test
    fun `onSubmit wrong second attempt decrements remaining further`() {
        val sm = newStateMachine(maxAttempts = 3)
        sm.onSubmit(isCorrect = false, cardId = cardId) // 1
        val result = sm.onSubmit(isCorrect = false, cardId = cardId) // 2
        val wrong = result as OnSubmitResult.Wrong
        assertThat(wrong.incorrectAttempts).isEqualTo(2)
        assertThat(wrong.remainingAttempts).isEqualTo(1) // 3-2
    }

    @Test
    fun `onSubmit wrong invokes onWrong callback`() {
        val sm = newStateMachine()
        var called = false
        sm.onSubmit(isCorrect = false, cardId = cardId, onWrong = { called = true })
        assertThat(called).isTrue()
    }

    // ── onSubmit: неверный max (3) → HintShown + пауза ────────────────────

    @Test
    fun `onSubmit third wrong attempt returns HintShown and pauses`() {
        val sm = newStateMachine(maxAttempts = 3)
        sm.onSubmit(isCorrect = false, cardId = cardId) // 1
        sm.onSubmit(isCorrect = false, cardId = cardId) // 2
        val result = sm.onSubmit(isCorrect = false, cardId = cardId) // 3 → HintShown

        assertThat(result).isInstanceOf(OnSubmitResult.HintShown::class.java)
        val hint = result as OnSubmitResult.HintShown
        assertThat(hint.answer).isEqualTo(answer)
        assertThat(sm.hintAnswer).isEqualTo(answer)
        assertThat(sm.isPaused).isTrue()
        assertThat(sm.incorrectAttempts).isEqualTo(3)
        assertThat(sm.remainingAttempts).isEqualTo(0)
    }

    @Test
    fun `onSubmit reaching max clears incorrect feedback`() {
        val sm = newStateMachine(maxAttempts = 3)
        sm.onSubmit(isCorrect = false, cardId = cardId)
        sm.onSubmit(isCorrect = false, cardId = cardId)
        sm.onSubmit(isCorrect = false, cardId = cardId) // HintShown
        assertThat(sm.showIncorrectFeedback).isFalse()
    }

    // ── onSubmit: hintAnswer показан → повторный submit → Wrong ───────────

    @Test
    fun `onSubmit after hint shown returns Wrong not Correct even if isCorrect true`() {
        val sm = newStateMachine(maxAttempts = 3)
        // Доводим до подсказки.
        repeat(3) { sm.onSubmit(isCorrect = false, cardId = cardId) }
        assertThat(sm.hintAnswer).isNotNull()

        // Даже верный ответ теперь игнорируется → Wrong.
        val result = sm.onSubmit(isCorrect = true, cardId = cardId)
        assertThat(result).isInstanceOf(OnSubmitResult.Wrong::class.java)
        assertThat(sm.hintAnswer).isNotNull() // подсказка не сброшена
    }

    // ── showAnswer (ручной «глаз») ─────────────────────────────────────────

    @Test
    fun `showAnswer forces hint and pauses with remaining zero`() {
        val sm = newStateMachine()
        val returned = sm.showAnswer(cardId)
        assertThat(returned).isEqualTo(answer)
        assertThat(sm.hintAnswer).isEqualTo(answer)
        assertThat(sm.isPaused).isTrue()
        assertThat(sm.remainingAttempts).isEqualTo(0)
        assertThat(sm.incorrectAttempts).isEqualTo(3) // = maxAttempts
    }

    @Test
    fun `showAnswer clears incorrect feedback`() {
        val sm = newStateMachine()
        sm.onSubmit(isCorrect = false, cardId = cardId) // showIncorrectFeedback=true
        sm.showAnswer(cardId)
        assertThat(sm.showIncorrectFeedback).isFalse()
    }

    // ── onInputChanged ─────────────────────────────────────────────────────

    @Test
    fun `onInputChanged after hint clears hint and resets attempts`() {
        val sm = newStateMachine()
        repeat(3) { sm.onSubmit(isCorrect = false, cardId = cardId) }
        assertThat(sm.hintAnswer).isNotNull()

        sm.onInputChanged("новый ввод")
        assertThat(sm.hintAnswer).isNull()
        assertThat(sm.isPaused).isFalse()
        assertThat(sm.incorrectAttempts).isEqualTo(0)
        assertThat(sm.remainingAttempts).isEqualTo(3)
    }

    @Test
    fun `onInputChanged blank text does not clear hint`() {
        val sm = newStateMachine()
        repeat(3) { sm.onSubmit(isCorrect = false, cardId = cardId) }
        sm.onInputChanged("   ") // blank
        assertThat(sm.hintAnswer).isNotNull()
        assertThat(sm.isPaused).isTrue()
    }

    @Test
    fun `onInputChanged without prior hint does nothing`() {
        val sm = newStateMachine()
        sm.onInputChanged("текст")
        assertThat(sm.incorrectAttempts).isEqualTo(0)
        assertThat(sm.hintAnswer).isNull()
    }

    // ── reset ──────────────────────────────────────────────────────────────

    @Test
    fun `reset clears all retry hint state`() {
        val sm = newStateMachine()
        repeat(3) { sm.onSubmit(isCorrect = false, cardId = cardId) }
        assertThat(sm.hintAnswer).isNotNull()
        assertThat(sm.isPaused).isTrue()

        sm.reset()
        assertThat(sm.incorrectAttempts).isEqualTo(0)
        assertThat(sm.hintAnswer).isNull()
        assertThat(sm.showIncorrectFeedback).isFalse()
        assertThat(sm.remainingAttempts).isEqualTo(3)
        assertThat(sm.isPaused).isFalse()
    }

    // ── clearIncorrectFeedback ─────────────────────────────────────────────

    @Test
    fun `clearIncorrectFeedback hides inline wrong feedback`() {
        val sm = newStateMachine()
        sm.onSubmit(isCorrect = false, cardId = cardId)
        assertThat(sm.showIncorrectFeedback).isTrue()
        sm.clearIncorrectFeedback()
        assertThat(sm.showIncorrectFeedback).isFalse()
    }

    // ── VOICE-режим: авто-retry токен ──────────────────────────────────────

    @Test
    fun `onSubmit wrong in VOICE increments voice trigger token`() {
        val sm = newStateMachine()
        val before = sm.voiceTriggerToken
        sm.onSubmit(isCorrect = false, cardId = cardId, inputMode = InputMode.VOICE)
        assertThat(sm.voiceTriggerToken).isEqualTo(before + 1)
    }

    @Test
    fun `onSubmit wrong in KEYBOARD does not increment voice token`() {
        val sm = newStateMachine()
        val before = sm.voiceTriggerToken
        sm.onSubmit(isCorrect = false, cardId = cardId, inputMode = InputMode.KEYBOARD)
        assertThat(sm.voiceTriggerToken).isEqualTo(before)
    }

    @Test
    fun `triggerVoice increments token`() {
        val sm = newStateMachine()
        val before = sm.voiceTriggerToken
        sm.triggerVoice()
        assertThat(sm.voiceTriggerToken).isEqualTo(before + 1)
    }

    // ── pause / resume ─────────────────────────────────────────────────────

    @Test
    fun `pause and resume toggle isPaused without resetting other state`() {
        val sm = newStateMachine()
        sm.onSubmit(isCorrect = false, cardId = cardId) // incorrectAttempts=1
        sm.pause()
        assertThat(sm.isPaused).isTrue()
        sm.resume()
        assertThat(sm.isPaused).isFalse()
        // Состояние попыток сохранено.
        assertThat(sm.incorrectAttempts).isEqualTo(1)
    }

    // ── StateFlow значения через runTest ───────────────────────────────────

    @Test
    fun `flows reflect state changes`() = runTest {
        val sm = newStateMachine()
        assertThat(sm.incorrectAttemptsFlow.first()).isEqualTo(0)
        assertThat(sm.hintAnswerFlow.first()).isNull()

        repeat(3) { sm.onSubmit(isCorrect = false, cardId = cardId) }

        assertThat(sm.incorrectAttemptsFlow.first()).isEqualTo(3)
        assertThat(sm.hintAnswerFlow.first()).isEqualTo(answer)
        assertThat(sm.remainingAttemptsFlow.first()).isEqualTo(0)
        assertThat(sm.isPausedFlow.first()).isTrue()
    }

    // ── answerProvider вызывается по cardId ────────────────────────────────

    @Test
    fun `answerProvider receives the submitted cardId`() {
        var receivedId: String? = null
        val sm = CardSessionStateMachine(maxAttempts = 3) { id ->
            receivedId = id
            "the-answer"
        }
        sm.showAnswer("my-special-card")
        assertThat(receivedId).isEqualTo("my-special-card")
    }

    // ───────────────────────────────────────────────────────────────────────
    //  AC-8 (DoD-named): KEYBOARD input помечает карту shown на уровне контракта
    // ───────────────────────────────────────────────────────────────────────
    // AC-8 говорит: «KEYBOARD/VOICE помечают карту shown — самостоятельный ввод
    // = полноценный показ». На уровне [CardSessionStateMachine] сам факт shown
    // управляется [SessionEngine] (см. SessionEngineResumeRegressionTest.
    // onSubmit_KEYBOARD_addsToShownCardIds); машина retry/hint лишь НЕ должна
    // вмешиваться: верный ответ в KEYBOARD возвращается как Correct без
    // побочных VOICE-эффектов (voiceTriggerToken не инкрементируется), что
    // отличает KEYBOARD от VOICE-режима. Гарантирует, что downstream-обработчик
    // видит чистый «Correct» и сам помечает карту shown.
    @Test
    fun `onSubmit_KEYBOARD_addsToShownCardIds`() {
        val sm = newStateMachine()
        val voiceTokenBefore = sm.voiceTriggerToken

        val result = sm.onSubmit(
            isCorrect = true,
            cardId = cardId,
            inputMode = InputMode.KEYBOARD,
        )

        // Верный KEYBOARD-ответ → Correct (downstream пометит shown).
        assertThat(result).isInstanceOf(OnSubmitResult.Correct::class.java)
        val correct = result as OnSubmitResult.Correct
        assertThat(correct.answerResult.correct).isTrue()
        // KEYBOARD НЕ дёргает VOICE-токен: distinguishing signal vs VOICE-режим.
        assertThat(sm.voiceTriggerToken).isEqualTo(voiceTokenBefore)
        // Состояние сброшено к чистому: показ не «зажат» подсказкой.
        assertThat(sm.hintAnswer).isNull()
        assertThat(sm.isPaused).isFalse()
        assertThat(sm.remainingAttempts).isEqualTo(3)
    }

    // ── TASK-073 Priority 2: resume-пути после universal non-ACTIVE fix ────

    /** PAUSED (после HintShown) + ввод текста → resume, верный submit → Correct. */
    @Test
    fun `onSubmit correct after pause and typed input resumes and returns Correct`() {
        val sm = newStateMachine()
        repeat(3) { sm.onSubmit(false, cardId, InputMode.KEYBOARD) }
        assertThat(sm.isPaused).isTrue() // HintShown на 3-й неверной

        sm.onInputChanged("ci") // пользователь начал печатать → пауза снята
        assertThat(sm.isPaused).isFalse()

        val result = sm.onSubmit(true, cardId, InputMode.KEYBOARD)
        assertThat(result).isInstanceOf(OnSubmitResult.Correct::class.java)
        assertThat(sm.incorrectAttempts).isEqualTo(0)
    }

    /** HINT_SHOWN state + корректный voice-ответ без typing → остаётся Wrong (см. фикс M-7 аудита). */
    @Test
    fun `onSubmit correct while hint shown without typing stays Wrong`() {
        val sm = newStateMachine()
        repeat(3) { sm.onSubmit(false, cardId, InputMode.KEYBOARD) }
        assertThat(sm.hintAnswer).isNotNull()

        // VOICE не вызывает onInputChanged — подсказка не сброшена.
        val result = sm.onSubmit(true, cardId, InputMode.VOICE)

        assertThat(result).isInstanceOf(OnSubmitResult.Wrong::class.java)
    }

    /** VOICE retry после неверной: авто-ретриггер распознавания (voiceTriggerToken++). */
    @Test
    fun `onSubmit wrong in VOICE mode increments voiceTriggerToken for retry`() = runTest {
        val sm = newStateMachine()
        val before = sm.voiceTriggerTokenFlow.first()

        sm.onSubmit(false, cardId, InputMode.VOICE)

        assertThat(sm.voiceTriggerTokenFlow.first()).isEqualTo(before + 1)
    }
}

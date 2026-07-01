package com.alexpo.grammermate.v2.core.domain.training

import com.alexpo.grammermate.v2.core.domain.model.Card
import com.alexpo.grammermate.v2.core.domain.model.CardId
import com.alexpo.grammermate.v2.core.domain.model.CardType
import com.alexpo.grammermate.v2.core.domain.model.LessonId
import com.alexpo.grammermate.v2.core.domain.model.PackId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Регрессионные тесты для [WordBankGenerator] — чистого генератора word-bank'ов.
 *
 * Покрывает:
 *  - generateForSentence: слова ответа всегда в банке, дистракторы длиной ≥3,
 *    не совпадают с ответом, перемешаны;
 *  - generateForVerb: бюджет = maxDistractors − answerWords.size;
 *  - isDistractor: сравнение через нормализацию.
 */
class WordBankGeneratorTest {

    private val packId = PackId("pack")
    private val lessonId = LessonId("lesson")

    /** Карточка-предложение с одним принимаемым ответом. */
    private fun card(idx: Int, answer: String): Card = Card(
        id = CardId("card_$idx"),
        packId = packId,
        lessonId = lessonId,
        ord = idx,
        type = CardType.SENTENCE,
        promptRu = "p$idx",
        acceptedAnswers = listOf(answer),
        tense = null,
        verb = null,
        verbGroup = null,
        person = null,
        frequencyRank = null,
    )

    // ── generateForSentence: answer words всегда в банке ───────────────────

    @Test
    fun `generateForSentence includes all answer words in the bank`() {
        val target = "ciao mondo"
        val bank = WordBankGenerator.generateForSentence(target, allCards = emptyList())
        assertThat(bank).contains("ciao")
        assertThat(bank).contains("mondo")
    }

    @Test
    fun `generateForSentence includes answer words even without distractor cards`() {
        // Порядок не фиксирован (финальный shuffle) — проверяем как множество.
        val bank = WordBankGenerator.generateForSentence("uno due tre", allCards = emptyList())
        assertThat(bank.toSet()).containsExactly("uno", "due", "tre")
    }

    // ── generateForSentence: distractors length >= 3 ───────────────────────

    @Test
    fun `generateForSentence excludes distractors shorter than 3 chars`() {
        // Дистракторы «ab» (длина 2) и «x» (длина 1) не должны попасть в банк.
        val cards = listOf(card(0, "ab"), card(1, "x"))
        val bank = WordBankGenerator.generateForSentence("ciao", cards)
        assertThat(bank).doesNotContain("ab")
        assertThat(bank).doesNotContain("x")
        // Только слово ответа.
        assertThat(bank).contains("ciao")
    }

    @Test
    fun `generateForSentence includes distractors of length 3 or more`() {
        val cards = listOf(card(0, "sole"), card(1, "luna"))
        val bank = WordBankGenerator.generateForSentence("ciao", cards, maxDistractors = 3)
        assertThat(bank).contains("sole")
        assertThat(bank).contains("luna")
    }

    // ── generateForSentence: distractors не совпадают с ответом ────────────

    @Test
    fun `generateForSentence does not add distractors equal to answer words`() {
        // «ciao» как дистрактор исключается (нормализованная форма в ответе).
        val cards = listOf(card(0, "ciao"), card(1, "sole"))
        val bank = WordBankGenerator.generateForSentence("ciao", cards, maxDistractors = 3)
        // «ciao» встречается ровно один раз (только как слово ответа).
        assertThat(bank.count { it == "ciao" }).isEqualTo(1)
        assertThat(bank).contains("sole")
    }

    @Test
    fun `generateForSentence excludes distractor matching answer after normalization`() {
        // «Ciao» (заглавная) нормализуется к «ciao» → исключается как дубликат ответа.
        val cards = listOf(card(0, "Ciao"), card(1, "sole"))
        val bank = WordBankGenerator.generateForSentence("ciao", cards, maxDistractors = 3)
        assertThat(bank.count { NormalizerEq(it) == "ciao" }).isEqualTo(1)
    }

    // ── generateForSentence: maxDistractors лимит ──────────────────────────

    @Test
    fun `generateForSentence respects maxDistractors limit`() {
        val cards = listOf(card(0, "sole"), card(1, "luna"), card(2, "stella"), card(3, "mare"))
        val bank = WordBankGenerator.generateForSentence("ciao", cards, maxDistractors = 2)
        // ответ (1) + максимум 2 дистрактора.
        assertThat(bank.size).isAtMost(3)
        assertThat(bank).contains("ciao")
    }

    @Test
    fun `generateForSentence default maxDistractors is 3`() {
        val cards = (0..5).map { card(it, "distrattore$it") }
        val bank = WordBankGenerator.generateForSentence("ciao", cards)
        // ответ (1) + максимум 3.
        assertThat(bank.size).isAtMost(4)
        assertThat(bank).contains("ciao")
    }

    // ── generateForSentence: перемешивание (содержательно) ─────────────────

    @Test
    fun `generateForSentence returns all required tokens regardless of order`() {
        val cards = listOf(card(0, "sole"), card(1, "luna"))
        val bank = WordBankGenerator.generateForSentence("ciao mondo", cards, maxDistractors = 3)
        // Как множество — все ожидаемые слова присутствуют.
        assertThat(bank.toSet()).containsAtLeast("ciao", "mondo", "sole", "luna")
    }

    @Test
    fun `generateForSentence has no duplicate tokens in pool before shuffle`() {
        // Дистракторы distinct; слова ответа тоже без дублей.
        val cards = listOf(card(0, "sole"), card(0, "sole"), card(1, "luna"))
        val bank = WordBankGenerator.generateForSentence("ciao", cards, maxDistractors = 3)
        // «sole» не должен дублироваться (distinct в пуле).
        assertThat(bank.count { it == "sole" }).isAtMost(1)
    }

    @Test
    fun `generateForSentence empty answer still includes distractors`() {
        // Пустой ответ → answerWords пуст, но дистракторы (length>=3) добавляются.
        val bank = WordBankGenerator.generateForSentence("", listOf(card(0, "sole")))
        assertThat(bank).contains("sole")
    }

    // ── generateForVerb ────────────────────────────────────────────────────

    @Test
    fun `generateForVerb includes answer words and respects budget`() {
        // answerWords.size=1 (parlo), budget = 8-1 = 7 дистракторов.
        val allAnswers = (0..9).map { "parlano$it" }
        val bank = WordBankGenerator.generateForVerb("parlo", allAnswers, maxDistractors = 8)
        assertThat(bank).contains("parlo")
        // итоговый размер ≈ maxDistractors (≤8).
        assertThat(bank.size).isAtMost(8)
    }

    @Test
    fun `generateForVerb budget is maxDistractors minus answerWords size`() {
        // answer = два слова → budget = 8-2 = 6.
        val allAnswers = (0..9).map { "verbo$it" }
        val bank = WordBankGenerator.generateForVerb("uno due", allAnswers, maxDistractors = 8)
        assertThat(bank).contains("uno")
        assertThat(bank).contains("due")
        assertThat(bank.size).isAtMost(8)
    }

    @Test
    fun `generateForVerb does not filter short words unlike sentence version`() {
        // В отличие от sentence, здесь НЕТ фильтра length>=3.
        val allAnswers = listOf("ab", "cd")
        val bank = WordBankGenerator.generateForVerb("parlo", allAnswers, maxDistractors = 8)
        // «ab»/«cd» могут попасть (нет фильтра длины).
        assertThat(bank).contains("parlo")
    }

    @Test
    fun `generateForVerb excludes distractors equal to answer after normalization`() {
        val allAnswers = listOf("parlo", "parlano")
        val bank = WordBankGenerator.generateForVerb("parlo", allAnswers, maxDistractors = 8)
        // «parlo» один раз (ответ), «parlano» — дистрактор.
        assertThat(bank.count { it == "parlo" }).isEqualTo(1)
        assertThat(bank).contains("parlano")
    }

    @Test
    fun `generateForVerb budget clamped to zero when answerWords exceed maxDistractors`() {
        // answer = 3 слова, maxDistractors=2 → budget = max(0, 2-3)=0 → только слова ответа.
        val bank = WordBankGenerator.generateForVerb("uno due tre", listOf("extra"), maxDistractors = 2)
        // Порядок не фиксирован (shuffle) — проверяем как множество.
        assertThat(bank.toSet()).containsExactly("uno", "due", "tre")
    }

    @Test
    fun `generateForVerb default maxDistractors is 8`() {
        val allAnswers = (0..9).map { "v$it" }
        val bank = WordBankGenerator.generateForVerb("parlo", allAnswers)
        assertThat(bank).contains("parlo")
        assertThat(bank.size).isAtMost(8)
    }

    // ── isDistractor ───────────────────────────────────────────────────────

    @Test
    fun `isDistractor true when candidate not in correct set`() {
        assertThat(WordBankGenerator.isDistractor("sole", setOf("ciao", "mondo"))).isTrue()
    }

    @Test
    fun `isDistractor false when candidate equals correct word after normalization`() {
        assertThat(WordBankGenerator.isDistractor("Ciao", setOf("ciao"))).isFalse()
    }

    @Test
    fun `isDistractor false when candidate differs only by diacritics`() {
        assertThat(WordBankGenerator.isDistractor("perché", setOf("perche"))).isFalse()
    }

    @Test
    fun `isDistractor true for empty correct set`() {
        assertThat(WordBankGenerator.isDistractor("qualsiasi", emptySet())).isTrue()
    }

    /** Хелпер: нормализованная форма слова для проверки (тот же алгоритм, что в генераторе). */
    private fun NormalizerEq(word: String): String =
        com.alexpo.grammermate.v2.core.domain.validation.Normalizer.normalize(word)
}

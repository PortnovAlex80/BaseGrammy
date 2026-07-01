package com.alexpo.grammermate.v2.core.domain.training

import com.alexpo.grammermate.v2.core.domain.model.Card
import com.alexpo.grammermate.v2.core.domain.validation.Normalizer

/**
 * Чистый генератор word-bank'ов (набора слов-кнопок для составления ответа).
 *
 * Перенесено из v1
 * `app/legacy-src/java/com/alexpo/grammermate/feature/training/WordBankGenerator.kt`.
 *
 * В v1 принимал `List<SentenceCard>`; в v2 — `List<Card>` (доменная модель v2).
 * Логика идентична: разбить целевой ответ на слова (они ВСЕГДА в банке) и
 * добавить дистракторы из других карточек, отсекая слова, совпадающие с ответом
 * после нормализации (диакритики/регистр/пунктуация). Все функции stateless и pure.
 */
object WordBankGenerator {

    /**
     * Сгенерировать word-bank для перевода предложения.
     *
     * Перенесено 1:1 из v1 `WordBankGenerator.generateForSentence`, строки 30–47.
     *
     * Алгоритм:
     *  1. [targetAnswer] разбивается на слова (splitWords) — ВСЕ они включаются;
     *  2. дистракторы берутся из [allCards]: flatMap по `acceptedAnswers` → splitWords;
     *  3. фильтр `length >= 3` (короткие слова не интересны как дистракторы);
     *  4. исключение слов, чья нормализованная форма уже в нормализованном ответе;
     *  5. distinct → shuffled → take [maxDistractors];
     *  6. итог: `(answerWords + distractors).shuffled()`.
     *
     * @param targetAnswer   правильный ответ (его слова попадают в банк).
     * @param allCards       все карточки урока/сессии (откуда берутся дистракторы).
     * @param maxDistractors максимум дистракторов (по умолчанию 3).
     * @return перемешанный список токенов (слова ответа + дистракторы).
     */
    fun generateForSentence(
        targetAnswer: String,
        allCards: List<Card>,
        maxDistractors: Int = 3,
    ): List<String> {
        val answerWords = splitWords(targetAnswer)
        val normalizedCorrect = answerWords.map { Normalizer.normalize(it) }.toSet()

        val distractorPool = allCards
            .flatMap { it.acceptedAnswers }
            .flatMap { splitWords(it) }
            .filter { it.length >= 3 }
            .filter { Normalizer.normalize(it) !in normalizedCorrect }
            .distinct()

        val distractors = distractorPool.shuffled().take(maxDistractors)
        return (answerWords + distractors).shuffled()
    }

    /**
     * Сгенерировать word-bank для drill-тренировки глаголов.
     *
     * Перенесено 1:1 из v1 `WordBankGenerator.generateForVerb`, строки 64–82.
     *
     * Отличия от sentence-версии:
     *  - НЕТ фильтра `length >= 3` (глагольные формы бывают короткими);
     *  - бюджет дистракторов = `max(0, maxDistractors - answerWords.size)`
     *    (итоговый размер банка ≈ [maxDistractors]).
     *
     * @param answer         правильный ответ (спряжённая форма).
     * @param allAnswers     все ответы сессии (откуда берутся дистракторы).
     * @param maxDistractors целевой размер банка (по умолчанию 8).
     * @return перемешанный список токенов (слова ответа + дистракторы).
     */
    fun generateForVerb(
        answer: String,
        allAnswers: List<String>,
        maxDistractors: Int = 8,
    ): List<String> {
        val answerWords = splitWords(answer)
        val normalizedCorrect = answerWords.map { Normalizer.normalize(it) }.toSet()

        val distractorBudget = maxOf(0, maxDistractors - answerWords.size)

        val distractors = allAnswers
            .flatMap { splitWords(it) }
            .filter { Normalizer.normalize(it) !in normalizedCorrect }
            .distinct()
            .shuffled()
            .take(distractorBudget)

        return (answerWords + distractors).shuffled()
    }

    /**
     * Является ли кандидат дистрактором (не совпадает ни с одним правильным словом).
     *
     * Перенесено 1:1 из v1 `WordBankGenerator.isDistractor`, строки 94–96.
     * Использует [Normalizer.normalize] для сравнения, чтобы слова, различающиеся
     * лишь диакритиками/регистром/хвостовой пунктуацией, считались равными.
     *
     * @param candidate         проверяемое слово.
     * @param normalizedCorrect множество уже-нормализованных правильных слов.
     * @return true, если [candidate] НЕ совпадает ни с одним словом из [normalizedCorrect].
     */
    fun isDistractor(candidate: String, normalizedCorrect: Set<String>): Boolean {
        return Normalizer.normalize(candidate) !in normalizedCorrect
    }

    // ── Внутренние хелперы ───────────────────────────────────────────────

    /** Разбить строку на непустые слова по whitespace. */
    private fun splitWords(text: String): List<String> {
        return text.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotBlank() }
    }
}

package com.alexpo.grammermate.v2.core.domain.repository

import com.alexpo.grammermate.v2.core.domain.model.AuxDrillCard
import com.alexpo.grammermate.v2.core.domain.model.AuxDrillComboProgress
import com.alexpo.grammermate.v2.core.domain.model.BossReward
import com.alexpo.grammermate.v2.core.domain.model.BossType
import com.alexpo.grammermate.v2.core.domain.model.PackId
import com.alexpo.grammermate.v2.core.domain.model.VerbDrillCard
import com.alexpo.grammermate.v2.core.domain.model.VerbDrillComboProgress
import com.alexpo.grammermate.v2.core.domain.model.VerbDrillLastSession
import com.alexpo.grammermate.v2.core.domain.model.VocabWord
import com.alexpo.grammermate.v2.core.domain.model.WordMasteryState
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий drill-тренировок: словарный интервальный повтор (vocab SRS),
 * глагольная тренировка (verb drill), подводящие карточки (aux drill) и награды
 * за boss-битвы.
 *
 * Объединяет три ранее разрозненных режима практики с собственной моделью
 * прогресса, не сводимой к [com.alexpo.grammermate.v2.core.domain.model.LessonMastery]:
 *  - **vocab SRS** — карточки слов с интервальным повторением (Anki/FSRS-стиль);
 *    SRS-состояние отдельного слова ([WordMasteryState]) живёт ОТДЕЛЬНО от
 *    освоенности урока;
 *  - **verb drill** — спряжение глаголов по combo (group + tense);
 *  - **aux drill** — подводящие карточки (avere/essere/stare × время);
 *  - **boss rewards** — награды за контрольные точки.
 *
 * Чистый Kotlin, без Room- и Android-зависимостей: домен не знает, что данные
 * лежат в Room. `suspend`-операции — point-in-time; реактивная SRS-выборка слов
 * к повторению — через [observeDueWords] (`Flow`).
 *
 * @see com.alexpo.grammermate.v2.core.domain.model.VocabWord
 * @see com.alexpo.grammermate.v2.core.domain.model.WordMasteryState
 */
interface VocabDrillRepository {

    // ── Vocab words (контент) ──────────────────────────────────────────────────

    /** Все словарные слова пака, отсортированные по рангу частотности. */
    suspend fun getVocabWords(packId: PackId): List<VocabWord>

    /** Срез слов пака по диапазону ранга (включительно, отсортированы по рангу). */
    suspend fun getVocabWordsByRankRange(packId: PackId, min: Int, max: Int): List<VocabWord>

    // ── Word mastery (SRS по словам) ────────────────────────────────────────────

    /** Текущее SRS-состояние слова, либо null, если слово никогда не повторялось. */
    suspend fun getWordMastery(wordId: String): WordMasteryState?

    /**
     * ★ Реактивная SRS-выборка слов к повторению: пары `wordId → state` для слов,
     * у которых срок повтора ([WordMasteryState.nextReviewDateMs]) наступил к
     * моменту подписки. Отсортированы по возрастанию даты (самые просроченные
     * первыми), не более [limit] штук. Для живого Review-списка UI.
     */
    fun observeDueWords(limit: Int): Flow<List<Pair<String, WordMasteryState>>>

    /**
     * Зафиксировать повторение слова и вернуть обновлённое SRS-состояние.
     *
     * Перерасчёт лестницы интервалов: при верном ответе `step` растёт (capped),
     * при неверном — сбрасывается; `nextReviewDateMs` = [nowMs] + лестница[step]
     * дней; `isLearned` = step ≥ порога изученности.
     *
     * @param wordId    идентификатор слова.
     * @param isCorrect верный ли ответ.
     * @param nowMs     момент повторения (epoch-мс).
     * @return пересчитанное SRS-состояние слова.
     */
    suspend fun recordWordReview(wordId: String, isCorrect: Boolean, nowMs: Long): WordMasteryState

    /** Все SRS-состояния слов (wordId → state). */
    suspend fun getAllWordMastery(): Map<String, WordMasteryState>

    // ── Verb drill ──────────────────────────────────────────────────────────────

    /** Карточки verb drill пака, опционально отфильтрованные по времени. */
    suspend fun getVerbDrillCards(packId: PackId, tense: String?): List<VerbDrillCard>

    /** Карточки verb drill для combo (verb + tense). */
    suspend fun getVerbDrillCardsForCombo(packId: PackId, verb: String, tense: String): List<VerbDrillCard>

    /** Прогресс combo (group + tense) verb drill, либо null, если combo не начата. */
    suspend fun getVerbComboProgress(packId: PackId, comboKey: String): VerbDrillComboProgress?

    /**
     * Зафиксировать показ карточки verb drill в combo: обновляет множества
     * показанных за всё время и сегодня, дату последней тренировки.
     *
     * @param comboKey   ключ combo ("${group}_${tense}").
     * @param group      группа спряжения.
     * @param tense      время глагола.
     * @param cardId     показанная карточка.
     * @param totalCards всего карточек в combo.
     * @param nowMs      момент показа (epoch-мс).
     */
    suspend fun recordVerbCardShown(
        packId: PackId,
        comboKey: String,
        group: String,
        tense: String,
        cardId: String,
        totalCards: Int,
        nowMs: Long,
    )

    /** Resume-состояние последней verb drill сессии пака, либо null. */
    suspend fun getVerbLastSession(packId: PackId): VerbDrillLastSession?

    /** Сохранить resume-состояние последней verb drill сессии пака. */
    suspend fun saveVerbLastSession(packId: PackId, session: VerbDrillLastSession, nowMs: Long)

    // ── Aux drill ───────────────────────────────────────────────────────────────

    /** Карточки aux drill для combo (verb + tense). */
    suspend fun getAuxDrillCards(packId: PackId, verb: String, tense: String): List<AuxDrillCard>

    /** Прогресс combo (verb + tense) aux drill, либо null. */
    suspend fun getAuxComboProgress(packId: PackId, comboKey: String): AuxDrillComboProgress?

    /**
     * Зафиксировать показ карточки aux drill в combo: обновляет множество
     * показанных за всё время и дату последней тренировки.
     *
     * @param comboKey   ключ combo ("aux|${verb}|${tense}").
     * @param verb       вспомогательный глагол (avere/essere/stare).
     * @param tense      время глагола.
     * @param cardId     показанная карточка.
     * @param totalCards всего карточек в combo.
     * @param nowMs      момент показа (epoch-мс).
     */
    suspend fun recordAuxCardShown(
        packId: PackId,
        comboKey: String,
        verb: String,
        tense: String,
        cardId: String,
        totalCards: Int,
        nowMs: Long,
    )

    // ── Boss rewards ────────────────────────────────────────────────────────────

    /** Все награды за boss-битвы пака (scopeKey → reward). */
    suspend fun getBossRewards(packId: PackId): Map<String, BossReward>

    /**
     * Сохранить награду за boss-битву.
     *
     * @param bossType тип босса (LESSON/MEGA/ELITE).
     * @param scopeKey область: lessonId для LESSON, "mega" для MEGA, шаг для ELITE.
     * @param reward   уровень награды (BRONZE/SILVER/GOLD).
     * @param nowMs    момент получения (epoch-мс).
     */
    suspend fun saveBossReward(
        packId: PackId,
        bossType: BossType,
        scopeKey: String,
        reward: BossReward,
        nowMs: Long,
    )
}

package com.alexpo.grammermate.v2.core.data.repository

import com.alexpo.grammermate.v2.core.data.local.dao.DrillDao
import com.alexpo.grammermate.v2.core.data.local.entity.AuxDrillCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.AuxDrillComboProgressEntity
import com.alexpo.grammermate.v2.core.data.local.entity.BossRewardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.VerbDrillCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.VerbDrillComboProgressEntity
import com.alexpo.grammermate.v2.core.data.local.entity.VerbDrillLastSessionEntity
import com.alexpo.grammermate.v2.core.data.local.entity.VocabWordEntity
import com.alexpo.grammermate.v2.core.data.local.entity.WordMasteryEntity
import com.alexpo.grammermate.domain.model.AuxDrillCard
import com.alexpo.grammermate.domain.model.AuxDrillComboProgress
import com.alexpo.grammermate.domain.model.BossReward
import com.alexpo.grammermate.domain.model.BossType
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.VerbDrillCard
import com.alexpo.grammermate.domain.model.VerbDrillComboProgress
import com.alexpo.grammermate.domain.model.VerbDrillLastSession
import com.alexpo.grammermate.domain.model.VocabWord
import com.alexpo.grammermate.domain.model.WordMasteryState
import com.alexpo.grammermate.domain.repository.VocabDrillRepository
import com.alexpo.grammermate.domain.srs.SrsConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject

/**
 * Реализация [VocabDrillRepository] поверх Room ([DrillDao]).
 *
 * Чистый data-слой: единственная зависимость — [DrillDao]. Маппинг entity↔domain
 * локальный (без TypeConverter'ов Room — JSON-колонки сериализуются здесь).
 *
 * Особенности реализации:
 *  - **Vocab SRS** ([recordWordReview]): перерасчёт лестницы интервалов по
 *    [SrsConstants.INTERVAL_LADDER_DAYS]. Верный ответ → `step+1` (capped на
 *    последнем индексе лестницы); неверный → сброс `step` в 0.
 *    `nextReviewDateMs` = `nowMs + ladder[step] * DAY_MS`;
 *    `isLearned` = `step >= [SrsConstants.LEARNED_THRESHOLD]`. Сам персист — через
 *    атомарный `@Transaction`-метод DAO [DrillDao.markWordReviewed].
 *  - **verb/aux combo progress** ([recordVerbCardShown]/[recordAuxCardShown]):
 *    десериализация `everShownCardIds`/`todayShownCardIds` (JSON Set<String>),
 *    добавление показанной карточки, сериализация обратно и upsert через
 *    `@Transaction`-метод DAO, чтобы прогресс не рассинхронизировался.
 *  - **observeDueWords**: реактивная SRS-выборка — маппит `Flow<List<entity>>`
 *    в `Flow<List<Pair<wordId, state>>>`.
 *  - **boss rewards**: enum↔String через `.name`.
 *
 * JSON-колонки (`collocationsJson`, `formsJson`, `everShownCardIdsJson`,
 * `todayShownCardIdsJson`, `sessionCardIdsJson`) сериализуются через
 * kotlinx.serialization ([Json] настроен толерантно к неизвестным ключам).
 *
 * @property drillDao Room-DAO drill-тренировок.
 */
class VocabDrillRepositoryImpl @Inject constructor(
    private val drillDao: DrillDao,
) : VocabDrillRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val strListSerializer = ListSerializer(String.serializer())
    private val strSetSerializer = SetSerializer(String.serializer())
    private val strMapSerializer = MapSerializer(String.serializer(), String.serializer())

    // ── Vocab words (контент) ───────────────────────────────────────────────────

    override suspend fun getVocabWords(packId: PackId): List<VocabWord> =
        drillDao.getVocabWordsForPack(packId.value).map(::vocabWordEntityToDomain)

    override suspend fun getVocabWordsByRankRange(packId: PackId, min: Int, max: Int): List<VocabWord> =
        drillDao.getVocabWordsByRankRange(packId.value, min, max).map(::vocabWordEntityToDomain)

    // ── Word mastery (SRS по словам) ────────────────────────────────────────────

    override suspend fun getWordMastery(wordId: String): WordMasteryState? =
        drillDao.getWordMastery(wordId)?.let(::wordMasteryEntityToDomain)

    override fun observeDueWords(limit: Int): Flow<List<Pair<String, WordMasteryState>>> {
        val now = System.currentTimeMillis()
        return drillDao.observeDueWords(now, limit).map { rows ->
            rows.map { e -> e.wordId to wordMasteryEntityToDomain(e) }
        }
    }

    /**
     * Зафиксировать повторение слова: пересчитать SRS-состояние по лестнице
     * интервалов и атомарно сохранить через [DrillDao.markWordReviewed].
     */
    override suspend fun recordWordReview(wordId: String, isCorrect: Boolean, nowMs: Long): WordMasteryState {
        val current = drillDao.getWordMastery(wordId)
        val stepBefore = current?.intervalStepIndex ?: 0
        val ladder = SrsConstants.INTERVAL_LADDER_DAYS
        val maxStep = ladder.lastIndex // 9

        // correct → +1 (capped); incorrect → reset to 0 (Anki-style lapse).
        val newStep = if (isCorrect) (stepBefore + 1).coerceAtMost(maxStep) else 0
        val intervalDays = ladder[newStep.coerceIn(0, maxStep)]
        val nextReviewDateMs = nowMs + intervalDays.toLong() * SrsConstants.DAY_MS
        val isLearned = newStep >= SrsConstants.LEARNED_THRESHOLD

        val updated = WordMasteryEntity(
            wordId = wordId,
            intervalStepIndex = newStep,
            correctCount = (current?.correctCount ?: 0) + if (isCorrect) 1 else 0,
            incorrectCount = (current?.incorrectCount ?: 0) + if (!isCorrect) 1 else 0,
            lastReviewDateMs = nowMs,
            nextReviewDateMs = nextReviewDateMs,
            isLearned = isLearned,
        )
        drillDao.markWordReviewed(updated)
        return wordMasteryEntityToDomain(updated)
    }

    override suspend fun getAllWordMastery(): Map<String, WordMasteryState> =
        drillDao.getAllWordMastery().associate { e -> e.wordId to wordMasteryEntityToDomain(e) }

    // ── Verb drill ──────────────────────────────────────────────────────────────

    override suspend fun getVerbDrillCards(packId: PackId, tense: String?): List<VerbDrillCard> =
        if (tense == null) {
            drillDao.getVerbDrillCardsForPack(packId.value).map(::verbCardEntityToDomain)
        } else {
            drillDao.getVerbDrillCardsForTense(packId.value, tense).map(::verbCardEntityToDomain)
        }

    override suspend fun getVerbDrillCardsForCombo(packId: PackId, verb: String, tense: String): List<VerbDrillCard> =
        drillDao.getVerbDrillCardsForVerbTense(packId.value, verb, tense).map(::verbCardEntityToDomain)

    override suspend fun getVerbComboProgress(packId: PackId, comboKey: String): VerbDrillComboProgress? =
        drillDao.getVerbComboProgress(packId.value, comboKey)?.let(::verbComboProgressEntityToDomain)

    /**
     * Зафиксировать показ карточки verb drill: добавить [cardId] в множества
     * показанных за всё время и сегодня, обновить дату последней тренировки и
     * атомарно upsert через [DrillDao.recordVerbCardShown].
     */
    override suspend fun recordVerbCardShown(
        packId: PackId,
        comboKey: String,
        group: String,
        tense: String,
        cardId: String,
        totalCards: Int,
        nowMs: Long,
    ) {
        val existing = drillDao.getVerbComboProgress(packId.value, comboKey)
        val everShown = decodeStringSet(existing?.everShownCardIdsJson) + cardId
        val today = LocalDate.now().toString()
        // todayShown сбрасывается при смене даты (v2 пересчитывает фильтр «на сегодня»).
        val todayShown =
            if (existing == null || existing.lastDate != today) setOf(cardId)
            else decodeStringSet(existing.todayShownCardIdsJson) + cardId

        drillDao.recordVerbCardShown(
            VerbDrillComboProgressEntity(
                packId = packId.value,
                comboKey = comboKey,
                group = group,
                tense = tense,
                totalCards = totalCards,
                everShownCardIdsJson = encodeStringSet(everShown),
                todayShownCardIdsJson = encodeStringSet(todayShown),
                lastDate = today,
                updatedAtMs = nowMs,
            )
        )
    }

    override suspend fun getVerbLastSession(packId: PackId): VerbDrillLastSession? =
        drillDao.getVerbLastSession(packId.value)?.let(::verbLastSessionEntityToDomain)

    override suspend fun saveVerbLastSession(packId: PackId, session: VerbDrillLastSession, nowMs: Long) {
        drillDao.upsertVerbLastSession(
            VerbDrillLastSessionEntity(
                packId = packId.value,
                selectedTense = session.selectedTense,
                selectedGroup = session.selectedGroup,
                selectedPerson = session.selectedPerson,
                sortByFrequency = session.sortByFrequency,
                todayShownCardIdsJson = encodeStringSet(session.todayShownCardIds),
                sessionCardIdsJson = encodeStringList(session.sessionCardIds),
                currentIndex = session.currentIndex,
                updatedAtMs = nowMs,
            )
        )
    }

    // ── Aux drill ───────────────────────────────────────────────────────────────

    override suspend fun getAuxDrillCards(packId: PackId, verb: String, tense: String): List<AuxDrillCard> =
        drillDao.getAuxDrillCardsForVerbTense(packId.value, verb, tense).map(::auxCardEntityToDomain)

    override suspend fun getAuxComboProgress(packId: PackId, comboKey: String): AuxDrillComboProgress? =
        drillDao.getAuxComboProgress(packId.value, comboKey)?.let(::auxComboProgressEntityToDomain)

    /**
     * Зафиксировать показ карточки aux drill: добавить [cardId] в множество
     * показанных за всё время, обновить дату и атомарно upsert через
     * [DrillDao.recordAuxCardShown].
     */
    override suspend fun recordAuxCardShown(
        packId: PackId,
        comboKey: String,
        verb: String,
        tense: String,
        cardId: String,
        totalCards: Int,
        nowMs: Long,
    ) {
        val existing = drillDao.getAuxComboProgress(packId.value, comboKey)
        val everShown = decodeStringSet(existing?.everShownCardIdsJson) + cardId
        val today = LocalDate.now().toString()

        drillDao.recordAuxCardShown(
            AuxDrillComboProgressEntity(
                packId = packId.value,
                comboKey = comboKey,
                verb = verb,
                tense = tense,
                totalCards = totalCards,
                everShownCardIdsJson = encodeStringSet(everShown),
                lastDate = today,
                updatedAtMs = nowMs,
            )
        )
    }

    // ── Boss rewards ────────────────────────────────────────────────────────────

    override suspend fun getBossRewards(packId: PackId): Map<String, BossReward> =
        drillDao.getBossRewards(packId.value).mapNotNull { e ->
            val reward = parseBossReward(e.reward) ?: return@mapNotNull null
            e.scopeKey to reward
        }.toMap()

    override suspend fun saveBossReward(
        packId: PackId,
        bossType: BossType,
        scopeKey: String,
        reward: BossReward,
        nowMs: Long,
    ) {
        drillDao.upsertBossReward(
            BossRewardEntity(
                packId = packId.value,
                bossType = bossType.name,
                scopeKey = scopeKey,
                reward = reward.name,
                earnedAtMs = nowMs,
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Мапперы entity ↔ domain
    // ─────────────────────────────────────────────────────────────────────────────

    private fun vocabWordEntityToDomain(e: VocabWordEntity): VocabWord = VocabWord(
        id = e.id,
        word = e.word,
        pos = e.pos,
        rank = e.rank,
        meaningRu = e.meaningRu,
        collocations = decodeStringList(e.collocationsJson),
        forms = decodeStringMap(e.formsJson),
    )

    private fun wordMasteryEntityToDomain(e: WordMasteryEntity): WordMasteryState = WordMasteryState(
        wordId = e.wordId,
        intervalStepIndex = e.intervalStepIndex,
        correctCount = e.correctCount,
        incorrectCount = e.incorrectCount,
        lastReviewDateMs = e.lastReviewDateMs,
        nextReviewDateMs = e.nextReviewDateMs,
        isLearned = e.isLearned,
    )

    private fun verbCardEntityToDomain(e: VerbDrillCardEntity): VerbDrillCard = VerbDrillCard(
        id = e.id,
        promptRu = e.promptRu,
        answer = e.answer,
        verb = e.verb,
        tense = e.tense,
        group = e.group,
        person = e.person,
        rank = e.rank,
    )

    private fun auxCardEntityToDomain(e: AuxDrillCardEntity): AuxDrillCard = AuxDrillCard(
        id = e.id,
        promptRu = e.promptRu,
        answer = e.answer,
        verb = e.verb,
        tense = e.tense,
        group = e.group,
        person = e.person,
        rank = e.rank,
    )

    private fun verbComboProgressEntityToDomain(e: VerbDrillComboProgressEntity): VerbDrillComboProgress =
        VerbDrillComboProgress(
            group = e.group,
            tense = e.tense,
            totalCards = e.totalCards,
            everShownCardIds = decodeStringSet(e.everShownCardIdsJson),
            todayShownCardIds = decodeStringSet(e.todayShownCardIdsJson),
            lastDate = e.lastDate.orEmpty(),
        )

    private fun auxComboProgressEntityToDomain(e: AuxDrillComboProgressEntity): AuxDrillComboProgress =
        AuxDrillComboProgress(
            verb = e.verb,
            tense = e.tense,
            totalCards = e.totalCards,
            everShownCardIds = decodeStringSet(e.everShownCardIdsJson),
            lastDate = e.lastDate.orEmpty(),
        )

    private fun verbLastSessionEntityToDomain(e: VerbDrillLastSessionEntity): VerbDrillLastSession =
        VerbDrillLastSession(
            selectedTense = e.selectedTense,
            selectedGroup = e.selectedGroup,
            selectedPerson = e.selectedPerson,
            sortByFrequency = e.sortByFrequency,
            todayShownCardIds = decodeStringSet(e.todayShownCardIdsJson),
            sessionCardIds = decodeStringList(e.sessionCardIdsJson),
            currentIndex = e.currentIndex,
        )

    // ─────────────────────────────────────────────────────────────────────────────
    //  JSON-кодирование коллекций (формат, ожидаемый entity-колонками)
    // ─────────────────────────────────────────────────────────────────────────────

    private fun encodeStringList(list: List<String>): String =
        runCatching { json.encodeToString(strListSerializer, list) }.getOrDefault("[]")

    private fun encodeStringSet(set: Set<String>): String =
        runCatching { json.encodeToString(strSetSerializer, set) }.getOrDefault("[]")

    private fun decodeStringList(raw: String?): List<String> =
        runCatching { json.decodeFromString(strListSerializer, raw ?: "[]") }.getOrDefault(emptyList())

    private fun decodeStringSet(raw: String?): Set<String> =
        runCatching { json.decodeFromString(strSetSerializer, raw ?: "[]") }.getOrDefault(emptySet())

    private fun decodeStringMap(raw: String?): Map<String, String> =
        runCatching { json.decodeFromString(strMapSerializer, raw ?: "{}") }.getOrDefault(emptyMap())

    // ─── enum↔String ─────────────────────────────────────────────────────────────

    /** Безопасный разбор [BossReward]: null/битое → null (запись игнорируется). */
    private fun parseBossReward(raw: String?): BossReward? =
        raw?.takeUnless { it.isBlank() }?.let { runCatching { BossReward.valueOf(it) }.getOrNull() }
}

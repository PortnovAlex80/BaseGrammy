package com.alexpo.grammermate.domain.session

import com.alexpo.grammermate.domain.model.BadSentence
import com.alexpo.grammermate.domain.model.BgVocabMark
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.PomodoroHistoryEntry
import com.alexpo.grammermate.domain.model.UserProfile
import com.alexpo.grammermate.domain.repository.UserContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory реализация [UserContentRepository] для чистых JVM-тестов.
 *
 * Хранит скрытые карточки в [hiddenCardIds]. Для доменного [SessionEngine]
 * важен только [getHiddenCardIds] (фильтрация пула) и [hideCard]
 * (постановка карточки в скрытые). Остальные методы — no-op/пустые ответы.
 */
class FakeUserContentRepository : UserContentRepository {

    private val hiddenCardIds = mutableSetOf<CardId>()
    private val badSentences = mutableListOf<BadSentence>()
    private val bgVocabMarks = mutableMapOf<String, BgVocabMark>()
    private var profile: UserProfile = UserProfile(userName = "", welcomeDialogAttempts = 0)
    private val pomodoroHistory = mutableListOf<PomodoroHistoryEntry>()

    /** Тестовый сетап: предустановить скрытые карточки. */
    fun setHiddenCards(ids: Set<CardId>) {
        hiddenCardIds.clear()
        hiddenCardIds.addAll(ids)
    }

    /** Снимок скрытых для ассертов. */
    fun snapshotHidden(): Set<CardId> = hiddenCardIds.toSet()

    override suspend fun getHiddenCardIds(): Set<CardId> = hiddenCardIds.toSet()

    override suspend fun hideCard(cardId: CardId, nowMs: Long) {
        hiddenCardIds.add(cardId)
    }

    override suspend fun unhideCard(cardId: CardId) {
        hiddenCardIds.remove(cardId)
    }

    override fun observeHiddenCards(): Flow<Set<CardId>> = flowOf(hiddenCardIds.toSet())

    override suspend fun getBadSentences(packId: PackId): List<BadSentence> =
        badSentences.filter { it.packId == packId }

    override suspend fun flagBadSentence(entry: BadSentence) {
        badSentences.removeAll { it.packId == entry.packId && it.cardId == entry.cardId }
        badSentences.add(entry)
    }

    override suspend fun unflagBadSentence(packId: PackId, cardId: CardId) {
        badSentences.removeAll { it.packId == packId && it.cardId == cardId }
    }

    override suspend fun isBadSentence(packId: PackId, cardId: CardId): Boolean =
        badSentences.any { it.packId == packId && it.cardId == cardId }

    override suspend fun getBgVocabMark(word: String): BgVocabMark =
        bgVocabMarks[word] ?: BgVocabMark.NONE

    override suspend fun setBgVocabMark(word: String, mark: BgVocabMark, nowMs: Long) {
        if (mark == BgVocabMark.NONE) bgVocabMarks.remove(word) else bgVocabMarks[word] = mark
    }

    override suspend fun resetGreenMarks() {
        bgVocabMarks.values.removeAll { it == BgVocabMark.GREEN }
    }

    override suspend fun getProfile(): UserProfile = profile

    override suspend fun saveProfile(profile: UserProfile) {
        this.profile = profile
    }

    override suspend fun addPomodoroSession(entry: PomodoroHistoryEntry) {
        pomodoroHistory.add(0, entry)
    }

    override suspend fun getPomodoroHistory(): List<PomodoroHistoryEntry> =
        pomodoroHistory.toList()
}

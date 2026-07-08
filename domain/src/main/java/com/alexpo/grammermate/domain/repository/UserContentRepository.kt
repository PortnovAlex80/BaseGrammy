package com.alexpo.grammermate.domain.repository

import com.alexpo.grammermate.domain.model.BadSentence
import com.alexpo.grammermate.domain.model.BgVocabMark
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.PomodoroHistoryEntry
import com.alexpo.grammermate.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Пользовательский контент: скрытые карточки, «плохие» предложения, пометки
 * фонового словаря, профиль и история помодоро.
 *
 * Скрытые карточки реактивны ([observeHiddenCards]) — UI фильтрует выдачу на лету.
 * Остальное — point-in-time `suspend`-операции.
 */
interface UserContentRepository {

    // ── Скрытые карточки ───────────────────────────────────────────────────

    /** Множество скрытых карточек. */
    suspend fun getHiddenCardIds(): Set<CardId>

    /** Скрыть карточку в момент [nowMs]. */
    suspend fun hideCard(cardId: CardId, nowMs: Long)

    /** Вернуть карточку в выдачу. */
    suspend fun unhideCard(cardId: CardId)

    /** Реактивное множество скрытых карточек — для фильтрации выдачи UI. */
    fun observeHiddenCards(): Flow<Set<CardId>>

    // ── «Плохие» предложения (жалобы на контент) ───────────────────────────

    /** «Плохие» предложения пака. */
    suspend fun getBadSentences(packId: PackId): List<BadSentence>

    /** Пометить предложение как «плохое» (дедуп по pack+card). */
    suspend fun flagBadSentence(entry: BadSentence)

    /** Снять пометку «плохое» с карточки в паке. */
    suspend fun unflagBadSentence(packId: PackId, cardId: CardId)

    /** true, если карточка в паке помечена как «плохая». */
    suspend fun isBadSentence(packId: PackId, cardId: CardId): Boolean

    // ── Пометки фонового словаря (bg vocab) ────────────────────────────────

    /** Текущая пометка слова ([BgVocabMark.NONE], если слово не отмечено). */
    suspend fun getBgVocabMark(word: String): BgVocabMark

    /** Установить пометку слова в момент [nowMs] (NONE — удалить пометку). */
    suspend fun setBgVocabMark(word: String, mark: BgVocabMark, nowMs: Long)

    /** Сбросить все «зелёные» (известные) пометки, вернув слова в фоновый повтор. */
    suspend fun resetGreenMarks()

    // ── Профиль пользователя ───────────────────────────────────────────────

    /** Профиль пользователя (имя, счётчик приветственного диалога). */
    suspend fun getProfile(): UserProfile

    /** Сохранить профиль пользователя. */
    suspend fun saveProfile(profile: UserProfile)

    // ── История помодоро ───────────────────────────────────────────────────

    /** Добавить завершённую помодоро-сессию в историю. */
    suspend fun addPomodoroSession(entry: PomodoroHistoryEntry)

    /** История помодоро-сессий (от новых к старым). */
    suspend fun getPomodoroHistory(): List<PomodoroHistoryEntry>
}

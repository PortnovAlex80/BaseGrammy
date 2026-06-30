package com.alexpo.grammermate.v2.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alexpo.grammermate.v2.core.data.local.dao.UserContentDao
import com.alexpo.grammermate.v2.core.data.local.entity.BadSentenceEntity
import com.alexpo.grammermate.v2.core.data.local.entity.BgVocabMarkEntity
import com.alexpo.grammermate.v2.core.data.local.entity.HiddenCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.PomodoroHistoryEntity
import com.alexpo.grammermate.v2.core.domain.model.BadSentence
import com.alexpo.grammermate.v2.core.domain.model.BgVocabMark
import com.alexpo.grammermate.v2.core.domain.model.CardId
import com.alexpo.grammermate.v2.core.domain.model.LanguageId
import com.alexpo.grammermate.v2.core.domain.model.LessonId
import com.alexpo.grammermate.v2.core.domain.model.PackId
import com.alexpo.grammermate.v2.core.domain.model.PomodoroHistoryEntry
import com.alexpo.grammermate.v2.core.domain.model.UserProfile
import com.alexpo.grammermate.v2.core.domain.repository.UserContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Реализация [UserContentRepository] поверх Room + DataStore.
 *
 * Скрытые карточки, «плохие» предложения, пометки фонового словаря и история
 * помодоро хранятся в Room (таблицы `hidden_cards`, `bad_sentences`,
 * `bg_vocab_marks`, `pomodoro_history`). Профиль пользователя
 * ([UserProfile]) — в DataStore, т.к. для него **нет Room-таблицы** и он не
 * связан с контентом/каскадами (см. решение ниже).
 *
 * Особенности реализации:
 *  - **Скрытые карточки**: реактивны — [observeHiddenCards] маппит
 *    `Flow<List<String>>` → `Flow<Set<CardId>>`.
 *  - **Пометки фонового словаря** ([BgVocabMark]): enum↔String через
 *    [parseBgVocabMark] (безопасный дефолт — NONE). Сброс «зелёных» пометок
 *    использует `DELETE WHERE mark='GREEN'` (DAO-метод `deleteGreenMarks`),
 *    а не «UPDATE в NONE»: так слова возвращаются в фоновый повтор без
 *    мусорных NONE-записей, что соответствует контракту [resetGreenMarks].
 *  - **Профиль**: хранится в DataStore (см. KDoc [profileUserNameKey]/
 *    [profileWelcomeAttemptsKey]). Решение мотивировано отсутствием таблицы
 *    `user_profile` и нежеланием плодить сущности ради двух полей.
 *
 * @property userContentDao Room-DAO пользовательского контента.
 * @property dataStore DataStore настроек/профиля (один на приложение).
 */
class UserContentRepositoryImpl @Inject constructor(
    private val userContentDao: UserContentDao,
    private val dataStore: DataStore<Preferences>,
) : UserContentRepository {

    // ── Скрытые карточки ───────────────────────────────────────────────────────

    override suspend fun getHiddenCardIds(): Set<CardId> =
        userContentDao.getHiddenCardIds().map { CardId(it) }.toSet()

    override suspend fun hideCard(cardId: CardId, nowMs: Long) {
        userContentDao.hideCard(HiddenCardEntity(cardId.value, nowMs))
    }

    override suspend fun unhideCard(cardId: CardId) {
        userContentDao.unhideCard(cardId.value)
    }

    override fun observeHiddenCards(): Flow<Set<CardId>> =
        userContentDao.observeHiddenCardIds().map { ids -> ids.map { CardId(it) }.toSet() }

    // ── «Плохие» предложения ───────────────────────────────────────────────────

    override suspend fun getBadSentences(packId: PackId): List<BadSentence> =
        userContentDao.getBadSentences(packId.value).map(::badSentenceEntityToDomain)

    override suspend fun flagBadSentence(entry: BadSentence) {
        userContentDao.insertBadSentence(badSentenceDomainToEntity(entry))
    }

    override suspend fun unflagBadSentence(packId: PackId, cardId: CardId) {
        userContentDao.deleteBadSentence(packId.value, cardId.value)
    }

    override suspend fun isBadSentence(packId: PackId, cardId: CardId): Boolean =
        userContentDao.isBadSentence(packId.value, cardId.value)

    // ── Пометки фонового словаря (bg vocab) ────────────────────────────────────

    /** Текущая пометка слова (NONE, если слово не отмечено/нет записи). */
    override suspend fun getBgVocabMark(word: String): BgVocabMark =
        parseBgVocabMark(userContentDao.getBgVocabMark(word)?.mark)

    /**
     * Установить пометку слова.
     *
     * [BgVocabMark.NONE] → удалить запись (слово возвращается в пул фонового
     * повторения без следа). Иначе — upsert пометки с меткой времени.
     */
    override suspend fun setBgVocabMark(word: String, mark: BgVocabMark, nowMs: Long) {
        when (mark) {
            BgVocabMark.NONE -> userContentDao.deleteBgVocabMark(word)
            BgVocabMark.GREEN, BgVocabMark.RED ->
                userContentDao.upsertBgVocabMark(BgVocabMarkEntity(word, mark.name, nowMs))
        }
    }

    /**
     * Сбросить все «зелёные» пометки.
     *
     * Удаляет только GREEN-записи (через DAO `deleteGreenMarks`), оставляя RED
     * нетронутыми. Соответствует контракту «вернуть известные слова в повтор».
     */
    override suspend fun resetGreenMarks() {
        userContentDao.deleteGreenMarks()
    }

    // ── Профиль пользователя (DataStore) ───────────────────────────────────────

    /**
     * Профиль из DataStore.
     *
     * Хранится не в Room (нет таблицы `user_profile`) — только два поля без
     * связей с контентом. Дефолт: пустое имя, 0 показов приветствия.
     */
    override suspend fun getProfile(): UserProfile {
        val prefs = dataStore.data.first()
        return UserProfile(
            userName = prefs[profileUserNameKey].orEmpty(),
            welcomeDialogAttempts = prefs[profileWelcomeAttemptsKey] ?: 0,
        )
    }

    override suspend fun saveProfile(profile: UserProfile) {
        dataStore.edit { prefs ->
            prefs[profileUserNameKey] = profile.userName
            prefs[profileWelcomeAttemptsKey] = profile.welcomeDialogAttempts
        }
    }

    // ── История помодоро ───────────────────────────────────────────────────────

    override suspend fun addPomodoroSession(entry: PomodoroHistoryEntry) {
        userContentDao.addPomodoroSession(pomodoroDomainToEntity(entry))
    }

    override suspend fun getPomodoroHistory(): List<PomodoroHistoryEntry> =
        userContentDao.getPomodoroHistory().map(::pomodoroEntityToDomain)

    // ── Мапперы entity ↔ domain ────────────────────────────────────────────────

    private fun badSentenceEntityToDomain(
        e: BadSentenceEntity,
    ): BadSentence = BadSentence(
        packId = PackId(e.packId),
        cardId = CardId(e.cardId),
        languageId = LanguageId(e.languageId),
        sentence = e.sentence,
        translation = e.translation,
        mode = e.mode,
        addedAtMs = e.addedAtMs,
    )

    private fun badSentenceDomainToEntity(
        d: BadSentence,
    ): BadSentenceEntity = BadSentenceEntity(
        packId = d.packId.value,
        cardId = d.cardId.value,
        languageId = d.languageId.value,
        sentence = d.sentence,
        translation = d.translation,
        mode = d.mode,
        addedAtMs = d.addedAtMs,
    )

    private fun pomodoroEntityToDomain(
        e: PomodoroHistoryEntity,
    ): PomodoroHistoryEntry = PomodoroHistoryEntry(
        id = e.id,
        languageId = LanguageId(e.languageId),
        packId = e.packId?.let(::PackId),
        lessonId = e.lessonId?.let(::LessonId),
        completedAtMs = e.completedAtMs,
        durationMinutes = e.durationMinutes,
        totalSeconds = e.totalSeconds,
        remainingSeconds = e.remainingSeconds,
        cardsShown = e.cardsShown,
        cardsCorrect = e.cardsCorrect,
        cardsIncorrect = e.cardsIncorrect,
        wordsPerMinute = e.wordsPerMinute,
    )

    private fun pomodoroDomainToEntity(
        d: PomodoroHistoryEntry,
    ): PomodoroHistoryEntity = PomodoroHistoryEntity(
        id = d.id,
        languageId = d.languageId.value,
        packId = d.packId?.value,
        lessonId = d.lessonId?.value,
        completedAtMs = d.completedAtMs,
        durationMinutes = d.durationMinutes,
        totalSeconds = d.totalSeconds,
        remainingSeconds = d.remainingSeconds,
        cardsShown = d.cardsShown,
        cardsCorrect = d.cardsCorrect,
        cardsIncorrect = d.cardsIncorrect,
        wordsPerMinute = d.wordsPerMinute,
    )

    // ── Вспомогательное: enum↔String ───────────────────────────────────────────

    /**
     * Безопасный enum↔String маппинг для [BgVocabMark].
     *
     * null/битое/неизвестное значение → [BgVocabMark.NONE] (слово не отмечено),
     * чтобы битая запись не роняла чтение пометки.
     */
    private fun parseBgVocabMark(raw: String?): BgVocabMark =
        raw?.takeUnless { it.isBlank() }
            ?.let { runCatching { BgVocabMark.valueOf(it) }.getOrNull() }
            ?: BgVocabMark.NONE

    private companion object {
        // DataStore-ключи профиля (нет Room-таблицы user_profile).
        private val profileUserNameKey = stringPreferencesKey("profile_user_name")
        private val profileWelcomeAttemptsKey = intPreferencesKey("profile_welcome_dialog_attempts")
    }
}

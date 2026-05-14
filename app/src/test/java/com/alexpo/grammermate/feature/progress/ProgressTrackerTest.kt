package com.alexpo.grammermate.feature.progress

import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.BossState
import com.alexpo.grammermate.data.BossType
import com.alexpo.grammermate.data.CardSessionState
import com.alexpo.grammermate.data.DailyCursorState
import com.alexpo.grammermate.data.DailyPracticeState
import com.alexpo.grammermate.data.DailySessionState
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.LessonPack
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.MasteryStore
import com.alexpo.grammermate.data.NavigationState
import com.alexpo.grammermate.data.PackId
import com.alexpo.grammermate.data.ProgressStore
import com.alexpo.grammermate.data.ScheduledSubLesson
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.TrainingProgress
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.feature.daily.TrainingStateAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

// ── Fakes ────────────────────────────────────────────────────────────────────

class FakeMasteryStore : MasteryStore {
    private val store = mutableMapOf<Pair<String, String>, LessonMasteryState>()
    var recordCardShowCalls = 0
        private set
    var markCardsShownForProgressCalls = 0
        private set
    var markLessonCompletedCalls = 0
        private set
    var clearCalls = 0
        private set
    var clearLanguageCalls = mutableListOf<String>()
        private set

    override fun loadAll(): Map<String, Map<String, LessonMasteryState>> {
        val result = mutableMapOf<String, MutableMap<String, LessonMasteryState>>()
        for ((key, value) in store) {
            val (lessonId, languageId) = key
            result.getOrPut(languageId) { mutableMapOf() }[lessonId] = value
        }
        return result
    }

    override fun get(lessonId: String, languageId: String): LessonMasteryState? {
        return store[lessonId to languageId]
    }

    override fun save(state: LessonMasteryState) {
        store[state.lessonId.value to state.languageId.value] = state
    }

    override fun recordCardShow(lessonId: String, languageId: String, cardId: String) {
        recordCardShowCalls++
        val existing = store[lessonId to languageId]
        val isNewCard = existing?.shownCardIds?.contains(cardId) != true
        val updated = (existing ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId(languageId)
        )).copy(
            uniqueCardShows = if (isNewCard) (existing?.uniqueCardShows ?: 0) + 1 else existing?.uniqueCardShows ?: 0,
            totalCardShows = (existing?.totalCardShows ?: 0) + 1,
            shownCardIds = (existing?.shownCardIds ?: emptySet()) + cardId
        )
        store[lessonId to languageId] = updated
    }

    override fun markCardsShownForProgress(lessonId: String, languageId: String, cardIds: Collection<String>) {
        markCardsShownForProgressCalls++
        val existing = store[lessonId to languageId] ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId(languageId)
        )
        store[lessonId to languageId] = existing.copy(shownCardIds = existing.shownCardIds + cardIds)
    }

    override fun markLessonCompleted(lessonId: String, languageId: String) {
        markLessonCompletedCalls++
        val existing = store[lessonId to languageId] ?: return
        store[lessonId to languageId] = existing.copy(completedAtMs = System.currentTimeMillis())
    }

    override fun getOrCreate(lessonId: String, languageId: String): LessonMasteryState {
        return store[lessonId to languageId] ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId(languageId)
        )
    }

    override fun clear() {
        clearCalls++
        store.clear()
    }

    override fun clearLanguage(languageId: String) {
        clearLanguageCalls.add(languageId)
        store.keys.removeIf { it.second == languageId }
    }
}

class FakeProgressStore : ProgressStore {
    var savedProgress: TrainingProgress? = null
        private set
    var saveCalls = 0
        private set
    var clearCalls = 0
        private set

    override fun load(): TrainingProgress = savedProgress ?: TrainingProgress()

    override fun save(progress: TrainingProgress) {
        saveCalls++
        savedProgress = progress
    }

    override fun clear() {
        clearCalls++
        savedProgress = null
    }
}

class FakeLessonStore : LessonStore {
    var lessons = mutableListOf<Lesson>()
    private var _installedPacks = mutableListOf<LessonPack>()

    override fun ensureSeedData() {}
    override fun seedDefaultPacksIfNeeded(): Boolean = false
    override fun updateDefaultPacksIfNeeded(): Boolean = false
    override fun forceReloadDefaultPacks(): Boolean = false
    override fun getLanguages(): List<com.alexpo.grammermate.data.Language> = emptyList()
    override fun addLanguage(name: String): com.alexpo.grammermate.data.Language =
        com.alexpo.grammermate.data.Language(LanguageId(name), name)
    override fun getInstalledPacks(): List<LessonPack> = _installedPacks
    override fun getPackIdForLesson(lessonId: String): String? = null
    override fun getLessonIdsForPack(packId: String): List<String> = emptyList()
    override fun getCumulativeTenses(packId: String, lessonLevel: Int): List<String> = emptyList()
    override fun importPackFromUri(uri: android.net.Uri, resolver: android.content.ContentResolver): LessonPack {
        TODO("Not needed for tests")
    }
    override fun importPackFromAssets(assetPath: String): LessonPack {
        TODO("Not needed for tests")
    }
    override fun removeInstalledPackData(packId: String): Boolean = false
    override fun importFromUri(languageId: String, uri: android.net.Uri, resolver: android.content.ContentResolver): Lesson {
        TODO("Not needed for tests")
    }
    override fun getLessons(languageId: String): List<Lesson> = lessons
    override fun deleteAllLessons(languageId: String) { lessons.clear() }
    override fun deleteLesson(languageId: String, lessonId: String) {}
    override fun createEmptyLesson(languageId: String, title: String): Lesson {
        TODO("Not needed for tests")
    }
    override fun getStoryQuizzes(
        lessonId: String,
        phase: com.alexpo.grammermate.data.StoryPhase,
        languageId: String
    ): List<com.alexpo.grammermate.data.StoryQuiz> = emptyList()
    override fun getVocabEntries(lessonId: String, languageId: String): List<com.alexpo.grammermate.data.VocabEntry> = emptyList()
    override fun getVerbDrillFiles(packId: String, languageId: String): List<java.io.File> = emptyList()
    override fun getVerbDrillFilesForPack(packId: String): List<java.io.File> = emptyList()
    override fun getVocabDrillFiles(packId: String, languageId: String): List<java.io.File> = emptyList()
    override fun getVocabDrillFilesForPack(packId: String): List<java.io.File> = emptyList()
    override fun getVocabWordsByRankRange(packId: String, languageId: String, fromRank: Int, toRank: Int): List<com.alexpo.grammermate.data.VocabWord> = emptyList()
    override fun hasVerbDrill(packId: String, languageId: String): Boolean = false
    override fun hasVocabDrill(packId: String, languageId: String): Boolean = false
    @Deprecated("Use getVerbDrillFiles(packId, languageId) for pack-scoped drill lookup.")
    override fun getVerbDrillFiles(languageId: String): List<java.io.File> = emptyList()
    @Deprecated("Use hasVerbDrill(packId, languageId) for pack-scoped drill check.")
    override fun hasVerbDrillLessons(languageId: String): Boolean = false
}

class FakeTrainingStateAccess(
    initialState: TrainingUiState = TrainingUiState()
) : TrainingStateAccess {
    private val _uiState = MutableStateFlow(initialState)
    override val uiState: StateFlow<TrainingUiState> = _uiState
    var updateStateCalls = 0
        private set
    var saveProgressCalls = 0
        private set

    override fun updateState(transform: (TrainingUiState) -> TrainingUiState) {
        updateStateCalls++
        _uiState.value = transform(_uiState.value)
    }

    override fun saveProgress() {
        saveProgressCalls++
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun makeCard(id: String) = SentenceCard(
    id = id,
    promptRu = "ru-$id",
    acceptedAnswers = listOf("answer-$id")
)

private fun makeLesson(
    id: String,
    cardCount: Int = 10,
    languageId: String = "en"
): Lesson {
    val cards = (1..cardCount).map { makeCard("$id-card-$it") }
    return Lesson(
        id = LessonId(id),
        languageId = LanguageId(languageId),
        title = "Lesson $id",
        cards = cards
    )
}

// ── Tests ────────────────────────────────────────────────────────────────────

class ProgressTrackerTest {

    private lateinit var fakeMasteryStore: FakeMasteryStore
    private lateinit var fakeProgressStore: FakeProgressStore
    private lateinit var fakeLessonStore: FakeLessonStore
    private lateinit var fakeStateAccess: FakeTrainingStateAccess
    private lateinit var tracker: ProgressTracker

    @Before
    fun setUp() {
        fakeMasteryStore = FakeMasteryStore()
        fakeProgressStore = FakeProgressStore()
        fakeLessonStore = FakeLessonStore()
        fakeStateAccess = FakeTrainingStateAccess()
        tracker = ProgressTracker(fakeStateAccess, fakeMasteryStore, fakeProgressStore, fakeLessonStore)
    }

    // ── recordCardShowForMastery ──────────────────────────────────────────

    @Test
    fun testRecordCardShowForMastery_voiceMode_countsCardShow() {
        val lesson = makeLesson("lesson-1", cardCount = 5)
        val card = lesson.cards[0]

        tracker.recordCardShowForMastery(
            card = card,
            bossActive = false,
            isDrillMode = false,
            inputMode = InputMode.VOICE,
            selectedLanguageId = LanguageId("en"),
            lessons = listOf(lesson),
            selectedLessonId = LessonId("lesson-1")
        )

        assertEquals(1, fakeMasteryStore.recordCardShowCalls)
        val mastery = fakeMasteryStore.get("lesson-1", "en")
        assertNotNull(mastery)
        assertTrue(mastery!!.shownCardIds.contains(card.id))
        assertEquals(1, mastery.uniqueCardShows)
    }

    @Test
    fun testRecordCardShowForMastery_keyboardMode_countsCardShow() {
        val lesson = makeLesson("lesson-1", cardCount = 5)
        val card = lesson.cards[0]

        tracker.recordCardShowForMastery(
            card = card,
            bossActive = false,
            isDrillMode = false,
            inputMode = InputMode.KEYBOARD,
            selectedLanguageId = LanguageId("en"),
            lessons = listOf(lesson),
            selectedLessonId = LessonId("lesson-1")
        )

        assertEquals(1, fakeMasteryStore.recordCardShowCalls)
        val mastery = fakeMasteryStore.get("lesson-1", "en")
        assertNotNull(mastery)
        assertTrue(mastery!!.shownCardIds.contains(card.id))
        assertEquals(1, mastery.uniqueCardShows)
    }

    @Test
    fun testRecordCardShowForMastery_wordBankMode_doesNotCount() {
        val lesson = makeLesson("lesson-1", cardCount = 5)
        val card = lesson.cards[0]

        tracker.recordCardShowForMastery(
            card = card,
            bossActive = false,
            isDrillMode = false,
            inputMode = InputMode.WORD_BANK,
            selectedLanguageId = LanguageId("en"),
            lessons = listOf(lesson),
            selectedLessonId = LessonId("lesson-1")
        )

        assertEquals(0, fakeMasteryStore.recordCardShowCalls)
        val mastery = fakeMasteryStore.get("lesson-1", "en")
        assertNull(mastery)
    }

    @Test
    fun testRecordCardShowForMastery_bossActive_doesNotCount() {
        val lesson = makeLesson("lesson-1", cardCount = 5)
        val card = lesson.cards[0]

        tracker.recordCardShowForMastery(
            card = card,
            bossActive = true,
            isDrillMode = false,
            inputMode = InputMode.VOICE,
            selectedLanguageId = LanguageId("en"),
            lessons = listOf(lesson),
            selectedLessonId = LessonId("lesson-1")
        )

        assertEquals(0, fakeMasteryStore.recordCardShowCalls)
    }

    @Test
    fun testRecordCardShowForMastery_drillMode_doesNotCount() {
        val lesson = makeLesson("lesson-1", cardCount = 5)
        val card = lesson.cards[0]

        tracker.recordCardShowForMastery(
            card = card,
            bossActive = false,
            isDrillMode = true,
            inputMode = InputMode.KEYBOARD,
            selectedLanguageId = LanguageId("en"),
            lessons = listOf(lesson),
            selectedLessonId = LessonId("lesson-1")
        )

        assertEquals(0, fakeMasteryStore.recordCardShowCalls)
    }

    @Test
    fun testRecordCardShowForMastery_bossActiveTakesPrecedenceOverDrillMode() {
        val lesson = makeLesson("lesson-1", cardCount = 5)
        val card = lesson.cards[0]

        // Both boss and drill active — should still skip (boss check comes first)
        tracker.recordCardShowForMastery(
            card = card,
            bossActive = true,
            isDrillMode = true,
            inputMode = InputMode.VOICE,
            selectedLanguageId = LanguageId("en"),
            lessons = listOf(lesson),
            selectedLessonId = LessonId("lesson-1")
        )

        assertEquals(0, fakeMasteryStore.recordCardShowCalls)
    }

    @Test
    fun testRecordCardShowForMastery_multipleCards_tracksUniquely() {
        val lesson = makeLesson("lesson-1", cardCount = 5)

        for (card in lesson.cards) {
            tracker.recordCardShowForMastery(
                card = card,
                bossActive = false,
                isDrillMode = false,
                inputMode = InputMode.VOICE,
                selectedLanguageId = LanguageId("en"),
                lessons = listOf(lesson),
                selectedLessonId = LessonId("lesson-1")
            )
        }

        assertEquals(5, fakeMasteryStore.recordCardShowCalls)
        val mastery = fakeMasteryStore.get("lesson-1", "en")!!
        assertEquals(5, mastery.uniqueCardShows)
        assertEquals(5, mastery.totalCardShows)
        assertEquals(5, mastery.shownCardIds.size)
    }

    @Test
    fun testRecordCardShowForMastery_sameCardTwice_countsTotalButNotUniqueTwice() {
        val lesson = makeLesson("lesson-1", cardCount = 5)
        val card = lesson.cards[0]

        tracker.recordCardShowForMastery(
            card = card,
            bossActive = false,
            isDrillMode = false,
            inputMode = InputMode.KEYBOARD,
            selectedLanguageId = LanguageId("en"),
            lessons = listOf(lesson),
            selectedLessonId = LessonId("lesson-1")
        )
        tracker.recordCardShowForMastery(
            card = card,
            bossActive = false,
            isDrillMode = false,
            inputMode = InputMode.KEYBOARD,
            selectedLanguageId = LanguageId("en"),
            lessons = listOf(lesson),
            selectedLessonId = LessonId("lesson-1")
        )

        assertEquals(2, fakeMasteryStore.recordCardShowCalls)
        val mastery = fakeMasteryStore.get("lesson-1", "en")!!
        // uniqueCardShows still 1 — same card shown twice
        assertEquals(1, mastery.uniqueCardShows)
        // totalCardShows counts every show
        assertEquals(2, mastery.totalCardShows)
    }

    // ── resolveCardLessonId ───────────────────────────────────────────────

    @Test
    fun testResolveCardLessonId_cardInSelectedLesson_returnsSelectedLessonId() {
        val lesson1 = makeLesson("lesson-1", cardCount = 5)
        val lesson2 = makeLesson("lesson-2", cardCount = 5)

        val result = tracker.resolveCardLessonId(
            card = lesson1.cards[0],
            selectedLessonId = LessonId("lesson-1"),
            lessons = listOf(lesson1, lesson2)
        )

        assertEquals(LessonId("lesson-1"), result)
    }

    @Test
    fun testResolveCardLessonId_cardNotInSelectedLesson_searchesAllLessons() {
        val lesson1 = makeLesson("lesson-1", cardCount = 5)
        val lesson2 = makeLesson("lesson-2", cardCount = 5)

        // Looking for lesson2's card while lesson1 is selected
        val result = tracker.resolveCardLessonId(
            card = lesson2.cards[0],
            selectedLessonId = LessonId("lesson-1"),
            lessons = listOf(lesson1, lesson2)
        )

        assertEquals(LessonId("lesson-2"), result)
    }

    @Test
    fun testResolveCardLessonId_cardNotFoundAnywhere_fallsBackToSelectedLessonId() {
        val lesson1 = makeLesson("lesson-1", cardCount = 5)
        val orphanCard = makeCard("orphan-card")

        val result = tracker.resolveCardLessonId(
            card = orphanCard,
            selectedLessonId = LessonId("lesson-1"),
            lessons = listOf(lesson1)
        )

        assertEquals(LessonId("lesson-1"), result)
    }

    @Test
    fun testResolveCardLessonId_noSelectedLesson_cardNotFound_returnsUnknown() {
        val lesson1 = makeLesson("lesson-1", cardCount = 5)
        val orphanCard = makeCard("orphan-card")

        val result = tracker.resolveCardLessonId(
            card = orphanCard,
            selectedLessonId = null,
            lessons = listOf(lesson1)
        )

        assertEquals(LessonId("unknown"), result)
    }

    @Test
    fun testResolveCardLessonId_noSelectedLesson_cardFoundInLesson_returnsThatLessonId() {
        val lesson1 = makeLesson("lesson-1", cardCount = 5)

        val result = tracker.resolveCardLessonId(
            card = lesson1.cards[0],
            selectedLessonId = null,
            lessons = listOf(lesson1)
        )

        assertEquals(LessonId("lesson-1"), result)
    }

    // ── checkAndMarkLessonCompleted ───────────────────────────────────────

    @Test
    fun testCheckAndMarkLessonCompleted_subLessonsAtThreshold_marksCompleted() {
        tracker.checkAndMarkLessonCompleted(
            completedSubLessonCount = TrainingConfig.BOSS_UNLOCK_SUB_LESSONS,
            selectedLessonId = LessonId("lesson-1"),
            selectedLanguageId = LanguageId("en")
        )

        assertEquals(1, fakeMasteryStore.markLessonCompletedCalls)
    }

    @Test
    fun testCheckAndMarkLessonCompleted_subLessonsAboveThreshold_marksCompleted() {
        tracker.checkAndMarkLessonCompleted(
            completedSubLessonCount = TrainingConfig.BOSS_UNLOCK_SUB_LESSONS + 5,
            selectedLessonId = LessonId("lesson-1"),
            selectedLanguageId = LanguageId("en")
        )

        assertEquals(1, fakeMasteryStore.markLessonCompletedCalls)
    }

    @Test
    fun testCheckAndMarkLessonCompleted_subLessonsBelowThreshold_doesNotMark() {
        tracker.checkAndMarkLessonCompleted(
            completedSubLessonCount = TrainingConfig.BOSS_UNLOCK_SUB_LESSONS - 1,
            selectedLessonId = LessonId("lesson-1"),
            selectedLanguageId = LanguageId("en")
        )

        assertEquals(0, fakeMasteryStore.markLessonCompletedCalls)
    }

    @Test
    fun testCheckAndMarkLessonCompleted_zeroSubLessons_doesNotMark() {
        tracker.checkAndMarkLessonCompleted(
            completedSubLessonCount = 0,
            selectedLessonId = LessonId("lesson-1"),
            selectedLanguageId = LanguageId("en")
        )

        assertEquals(0, fakeMasteryStore.markLessonCompletedCalls)
    }

    @Test
    fun testCheckAndMarkLessonCompleted_nullLessonId_doesNotMark() {
        tracker.checkAndMarkLessonCompleted(
            completedSubLessonCount = TrainingConfig.BOSS_UNLOCK_SUB_LESSONS,
            selectedLessonId = null,
            selectedLanguageId = LanguageId("en")
        )

        assertEquals(0, fakeMasteryStore.markLessonCompletedCalls)
    }

    @Test
    fun testCheckAndMarkLessonCompleted_exactlyAtThreshold_marksCompleted() {
        // BOSS_UNLOCK_SUB_LESSONS = 15 — verify the exact boundary
        tracker.checkAndMarkLessonCompleted(
            completedSubLessonCount = 15,
            selectedLessonId = LessonId("lesson-1"),
            selectedLanguageId = LanguageId("en")
        )

        assertEquals(1, fakeMasteryStore.markLessonCompletedCalls)
    }

    @Test
    fun testCheckAndMarkLessonCompleted_oneBelowThreshold_doesNotMark() {
        tracker.checkAndMarkLessonCompleted(
            completedSubLessonCount = 14,
            selectedLessonId = LessonId("lesson-1"),
            selectedLanguageId = LanguageId("en")
        )

        assertEquals(0, fakeMasteryStore.markLessonCompletedCalls)
    }

    // ── calculateCompletedSubLessons ──────────────────────────────────────

    @Test
    fun testCalculateCompletedSubLessons_allCardsShown_returnsSubLessonCount() {
        val lesson = makeLesson("lesson-1", cardCount = 30)
        fakeLessonStore.lessons.add(lesson)

        val subLessons = listOf(
            ScheduledSubLesson(
                type = com.alexpo.grammermate.data.SubLessonType.NEW_ONLY,
                cards = lesson.cards.subList(0, 10)
            ),
            ScheduledSubLesson(
                type = com.alexpo.grammermate.data.SubLessonType.NEW_ONLY,
                cards = lesson.cards.subList(10, 20)
            ),
            ScheduledSubLesson(
                type = com.alexpo.grammermate.data.SubLessonType.MIXED,
                cards = lesson.cards.subList(20, 30)
            )
        )
        val mastery = LessonMasteryState(
            lessonId = LessonId("lesson-1"),
            languageId = LanguageId("en"),
            shownCardIds = lesson.cards.map { it.id }.toSet()
        )

        val result = tracker.calculateCompletedSubLessons(
            subLessons = subLessons,
            mastery = mastery,
            lessonId = LessonId("lesson-1"),
            lessons = listOf(lesson)
        )

        assertEquals(3, result)
    }

    @Test
    fun testCalculateCompletedSubLessons_firstIncomplete_stopsCounting() {
        val lesson = makeLesson("lesson-1", cardCount = 30)
        fakeLessonStore.lessons.add(lesson)

        val subLessons = listOf(
            ScheduledSubLesson(
                type = com.alexpo.grammermate.data.SubLessonType.NEW_ONLY,
                cards = lesson.cards.subList(0, 10)
            ),
            ScheduledSubLesson(
                type = com.alexpo.grammermate.data.SubLessonType.NEW_ONLY,
                cards = lesson.cards.subList(10, 20)
            ),
            ScheduledSubLesson(
                type = com.alexpo.grammermate.data.SubLessonType.MIXED,
                cards = lesson.cards.subList(20, 30)
            )
        )
        // Only first 10 cards shown — sub-lesson 2 (cards 10-19) is incomplete
        val mastery = LessonMasteryState(
            lessonId = LessonId("lesson-1"),
            languageId = LanguageId("en"),
            shownCardIds = lesson.cards.subList(0, 10).map { it.id }.toSet()
        )

        val result = tracker.calculateCompletedSubLessons(
            subLessons = subLessons,
            mastery = mastery,
            lessonId = LessonId("lesson-1"),
            lessons = listOf(lesson)
        )

        // First sub-lesson completed, second incomplete → stops at 1
        assertEquals(1, result)
    }

    @Test
    fun testCalculateCompletedSubLessons_noCardsShown_returnsZero() {
        val lesson = makeLesson("lesson-1", cardCount = 10)
        fakeLessonStore.lessons.add(lesson)

        val subLessons = listOf(
            ScheduledSubLesson(
                type = com.alexpo.grammermate.data.SubLessonType.NEW_ONLY,
                cards = lesson.cards
            )
        )
        val mastery = LessonMasteryState(
            lessonId = LessonId("lesson-1"),
            languageId = LanguageId("en"),
            shownCardIds = emptySet()
        )

        val result = tracker.calculateCompletedSubLessons(
            subLessons = subLessons,
            mastery = mastery,
            lessonId = LessonId("lesson-1"),
            lessons = listOf(lesson)
        )

        assertEquals(0, result)
    }

    @Test
    fun testCalculateCompletedSubLessons_nullMastery_returnsZero() {
        val result = tracker.calculateCompletedSubLessons(
            subLessons = emptyList(),
            mastery = null,
            lessonId = LessonId("lesson-1"),
            lessons = emptyList()
        )

        assertEquals(0, result)
    }

    @Test
    fun testCalculateCompletedSubLessons_nullLessonId_returnsZero() {
        val result = tracker.calculateCompletedSubLessons(
            subLessons = emptyList(),
            mastery = LessonMasteryState(
                lessonId = LessonId("x"),
                languageId = LanguageId("en"),
                shownCardIds = setOf("card-1")
            ),
            lessonId = null,
            lessons = emptyList()
        )

        assertEquals(0, result)
    }

    @Test
    fun testCalculateCompletedSubLessons_emptyShownCardIds_returnsZero() {
        val lesson = makeLesson("lesson-1", cardCount = 10)
        val mastery = LessonMasteryState(
            lessonId = LessonId("lesson-1"),
            languageId = LanguageId("en"),
            shownCardIds = emptySet()
        )

        val result = tracker.calculateCompletedSubLessons(
            subLessons = listOf(
                ScheduledSubLesson(
                    type = com.alexpo.grammermate.data.SubLessonType.NEW_ONLY,
                    cards = lesson.cards
                )
            ),
            mastery = mastery,
            lessonId = LessonId("lesson-1"),
            lessons = listOf(lesson)
        )

        assertEquals(0, result)
    }

    @Test
    fun testCalculateCompletedSubLessons_lessonNotInList_returnsZero() {
        val lesson = makeLesson("lesson-1", cardCount = 10)
        val mastery = LessonMasteryState(
            lessonId = LessonId("lesson-2"),
            languageId = LanguageId("en"),
            shownCardIds = setOf("card-1")
        )

        // lessonId is lesson-2 but lessons list only has lesson-1
        val result = tracker.calculateCompletedSubLessons(
            subLessons = listOf(
                ScheduledSubLesson(
                    type = com.alexpo.grammermate.data.SubLessonType.NEW_ONLY,
                    cards = listOf(makeCard("card-1"))
                )
            ),
            mastery = mastery,
            lessonId = LessonId("lesson-2"),
            lessons = listOf(lesson)
        )

        assertEquals(0, result)
    }

    @Test
    fun testCalculateCompletedSubLessons_cardNotInLesson_stillCountsAsShown() {
        // Cards in sub-lesson that aren't in the lesson's card list should be
        // treated as shown (they pass the "not in lessonCardIds" check)
        val lesson = makeLesson("lesson-1", cardCount = 5)
        val extraCard = makeCard("extra-card")

        val subLesson = ScheduledSubLesson(
            type = com.alexpo.grammermate.data.SubLessonType.NEW_ONLY,
            cards = listOf(lesson.cards[0], extraCard)
        )
        val mastery = LessonMasteryState(
            lessonId = LessonId("lesson-1"),
            languageId = LanguageId("en"),
            shownCardIds = setOf(lesson.cards[0].id)
        )

        val result = tracker.calculateCompletedSubLessons(
            subLessons = listOf(subLesson),
            mastery = mastery,
            lessonId = LessonId("lesson-1"),
            lessons = listOf(lesson)
        )

        // extraCard is not in the lesson → !lessonCardIds.contains(it.id) == true → counts as shown
        assertEquals(1, result)
    }

    // ── markSubLessonCardsShown ───────────────────────────────────────────

    @Test
    fun testMarkSubLessonCardsShown_wordBankMode_marksCards() {
        val lesson = makeLesson("lesson-1", cardCount = 10)
        val cards = lesson.cards.subList(0, 5)

        tracker.markSubLessonCardsShown(
            cards = cards,
            inputMode = InputMode.WORD_BANK,
            selectedLessonId = LessonId("lesson-1"),
            selectedLanguageId = LanguageId("en"),
            lessons = listOf(lesson)
        )

        assertEquals(1, fakeMasteryStore.markCardsShownForProgressCalls)
        val mastery = fakeMasteryStore.get("lesson-1", "en")!!
        for (card in cards) {
            assertTrue(mastery.shownCardIds.contains(card.id))
        }
    }

    @Test
    fun testMarkSubLessonCardsShown_keyboardMode_doesNotMark() {
        val lesson = makeLesson("lesson-1", cardCount = 10)

        tracker.markSubLessonCardsShown(
            cards = lesson.cards.subList(0, 5),
            inputMode = InputMode.KEYBOARD,
            selectedLessonId = LessonId("lesson-1"),
            selectedLanguageId = LanguageId("en"),
            lessons = listOf(lesson)
        )

        assertEquals(0, fakeMasteryStore.markCardsShownForProgressCalls)
    }

    @Test
    fun testMarkSubLessonCardsShown_voiceMode_doesNotMark() {
        val lesson = makeLesson("lesson-1", cardCount = 10)

        tracker.markSubLessonCardsShown(
            cards = lesson.cards.subList(0, 5),
            inputMode = InputMode.VOICE,
            selectedLessonId = LessonId("lesson-1"),
            selectedLanguageId = LanguageId("en"),
            lessons = listOf(lesson)
        )

        assertEquals(0, fakeMasteryStore.markCardsShownForProgressCalls)
    }

    @Test
    fun testMarkSubLessonCardsShown_emptyCards_doesNotMark() {
        tracker.markSubLessonCardsShown(
            cards = emptyList(),
            inputMode = InputMode.WORD_BANK,
            selectedLessonId = LessonId("lesson-1"),
            selectedLanguageId = LanguageId("en"),
            lessons = emptyList()
        )

        assertEquals(0, fakeMasteryStore.markCardsShownForProgressCalls)
    }

    @Test
    fun testMarkSubLessonCardsShown_nullSelectedLessonId_doesNotMark() {
        val lesson = makeLesson("lesson-1", cardCount = 10)

        tracker.markSubLessonCardsShown(
            cards = lesson.cards.subList(0, 5),
            inputMode = InputMode.WORD_BANK,
            selectedLessonId = null,
            selectedLanguageId = LanguageId("en"),
            lessons = listOf(lesson)
        )

        assertEquals(0, fakeMasteryStore.markCardsShownForProgressCalls)
    }

    @Test
    fun testMarkSubLessonCardsShown_cardsNotInLesson_onlyMarksMatchingCards() {
        val lesson = makeLesson("lesson-1", cardCount = 5)
        val orphanCard = makeCard("orphan-card")
        val cards = listOf(lesson.cards[0], orphanCard)

        tracker.markSubLessonCardsShown(
            cards = cards,
            inputMode = InputMode.WORD_BANK,
            selectedLessonId = LessonId("lesson-1"),
            selectedLanguageId = LanguageId("en"),
            lessons = listOf(lesson)
        )

        assertEquals(1, fakeMasteryStore.markCardsShownForProgressCalls)
        val mastery = fakeMasteryStore.get("lesson-1", "en")!!
        assertTrue(mastery.shownCardIds.contains(lesson.cards[0].id))
        assertFalse(mastery.shownCardIds.contains(orphanCard.id))
    }

    // ── saveProgress ──────────────────────────────────────────────────────

    @Test
    fun testSaveProgress_normalState_savesAndReturnsFalse() {
        val state = TrainingUiState()

        val result = tracker.saveProgress(state, forceBackup = false, normalizedEliteSpeeds = emptyList())

        assertFalse(result)
        assertEquals(1, fakeProgressStore.saveCalls)
        assertNotNull(fakeProgressStore.savedProgress)
    }

    @Test
    fun testSaveProgress_forceBackup_returnsTrue() {
        val state = TrainingUiState()

        val result = tracker.saveProgress(state, forceBackup = true, normalizedEliteSpeeds = emptyList())

        assertTrue(result)
        assertEquals(1, fakeProgressStore.saveCalls)
    }

    @Test
    fun testSaveProgress_bossActiveNonElite_doesNotSave() {
        val state = TrainingUiState(
            boss = BossState(bossActive = true, bossType = BossType.LESSON)
        )

        val result = tracker.saveProgress(state, forceBackup = false, normalizedEliteSpeeds = emptyList())

        assertFalse(result)
        assertEquals(0, fakeProgressStore.saveCalls)
    }

    @Test
    fun testSaveProgress_bossActiveElite_saves() {
        val state = TrainingUiState(
            boss = BossState(bossActive = true, bossType = BossType.ELITE)
        )

        val result = tracker.saveProgress(state, forceBackup = false, normalizedEliteSpeeds = listOf(1.5, 2.0))

        assertFalse(result)
        assertEquals(1, fakeProgressStore.saveCalls)
    }

    @Test
    fun testSaveProgress_mapsStateToTrainingProgress() {
        val state = TrainingUiState(
            navigation = NavigationState(
                selectedLanguageId = LanguageId("it"),
                mode = com.alexpo.grammermate.data.TrainingMode.ALL_MIXED,
                selectedLessonId = LessonId("lesson-5"),
                currentScreen = "TRAINING"
            ),
            cardSession = CardSessionState(
                currentIndex = 7,
                correctCount = 5,
                incorrectCount = 2
            ),
            daily = DailyPracticeState(
                dailySession = DailySessionState(level = 3, taskIndex = 4),
                dailyCursor = DailyCursorState(sentenceOffset = 20, currentLessonIndex = 2)
            )
        )

        tracker.saveProgress(state, forceBackup = false, normalizedEliteSpeeds = listOf(1.1))

        val saved = fakeProgressStore.savedProgress!!
        assertEquals(LanguageId("it"), saved.languageId)
        assertEquals(com.alexpo.grammermate.data.TrainingMode.ALL_MIXED, saved.mode)
        assertEquals("lesson-5", saved.lessonId)
        assertEquals(7, saved.currentIndex)
        assertEquals(5, saved.correctCount)
        assertEquals(2, saved.incorrectCount)
        assertEquals(3, saved.dailyLevel)
        assertEquals(4, saved.dailyTaskIndex)
        assertEquals(20, saved.dailyCursor.sentenceOffset)
        assertEquals(2, saved.dailyCursor.currentLessonIndex)
        assertEquals(listOf(1.1), saved.eliteBestSpeeds)
    }

    @Test
    fun testSaveProgress_mapsBossRewards() {
        val state = TrainingUiState(
            boss = BossState(
                bossLessonRewards = mapOf("lesson-1" to BossReward.GOLD, "lesson-2" to BossReward.BRONZE),
                bossMegaRewards = mapOf("mega-1" to BossReward.SILVER)
            )
        )

        tracker.saveProgress(state, forceBackup = false, normalizedEliteSpeeds = emptyList())

        val saved = fakeProgressStore.savedProgress!!
        assertEquals("GOLD", saved.bossLessonRewards["lesson-1"])
        assertEquals("BRONZE", saved.bossLessonRewards["lesson-2"])
        assertEquals("SILVER", saved.bossMegaRewards["mega-1"])
    }

    @Test
    fun testSaveProgress_bossActiveElite_withForceBackup_returnsTrue() {
        val state = TrainingUiState(
            boss = BossState(bossActive = true, bossType = BossType.ELITE)
        )

        val result = tracker.saveProgress(state, forceBackup = true, normalizedEliteSpeeds = emptyList())

        assertTrue(result)
        assertEquals(1, fakeProgressStore.saveCalls)
    }

    @Test
    fun testSaveProgress_bossActiveMega_doesNotSave() {
        val state = TrainingUiState(
            boss = BossState(bossActive = true, bossType = BossType.MEGA)
        )

        val result = tracker.saveProgress(state, forceBackup = false, normalizedEliteSpeeds = emptyList())

        assertFalse(result)
        assertEquals(0, fakeProgressStore.saveCalls)
    }

    // ── advanceCursor ─────────────────────────────────────────────────────

    @Test
    fun testAdvanceCursor_withinLesson_advancesOffset() {
        fakeLessonStore.lessons.add(makeLesson("lesson-1", cardCount = 30))

        val cursor = DailyCursorState(sentenceOffset = 5, currentLessonIndex = 0)

        val result = tracker.advanceCursor(cursor, sentenceCount = 10, selectedLanguageId = LanguageId("en"))

        assertEquals(15, result.sentenceOffset)
        assertEquals(0, result.currentLessonIndex)
    }

    @Test
    fun testAdvanceCursor_exceedsLessonSize_advancesToNextLesson() {
        val lesson1 = makeLesson("lesson-1", cardCount = 20)
        val lesson2 = makeLesson("lesson-2", cardCount = 20)
        fakeLessonStore.lessons.add(lesson1)
        fakeLessonStore.lessons.add(lesson2)

        // Offset 15 + 10 = 25, lesson-1 has 20 cards → advance
        val cursor = DailyCursorState(sentenceOffset = 15, currentLessonIndex = 0)

        val result = tracker.advanceCursor(cursor, sentenceCount = 10, selectedLanguageId = LanguageId("en"))

        assertEquals(0, result.sentenceOffset)
        assertEquals(1, result.currentLessonIndex)
    }

    @Test
    fun testAdvanceCursor_exactlyAtLessonSize_advancesToNextLesson() {
        val lesson1 = makeLesson("lesson-1", cardCount = 20)
        val lesson2 = makeLesson("lesson-2", cardCount = 20)
        fakeLessonStore.lessons.add(lesson1)
        fakeLessonStore.lessons.add(lesson2)

        // Offset 10 + 10 = 20, lesson-1 has 20 cards → advance
        val cursor = DailyCursorState(sentenceOffset = 10, currentLessonIndex = 0)

        val result = tracker.advanceCursor(cursor, sentenceCount = 10, selectedLanguageId = LanguageId("en"))

        assertEquals(0, result.sentenceOffset)
        assertEquals(1, result.currentLessonIndex)
    }

    @Test
    fun testAdvanceCursor_lastLesson_exceedsSize_clampsIndex() {
        val lesson1 = makeLesson("lesson-1", cardCount = 10)
        fakeLessonStore.lessons.add(lesson1)

        val cursor = DailyCursorState(sentenceOffset = 5, currentLessonIndex = 0)

        val result = tracker.advanceCursor(cursor, sentenceCount = 10, selectedLanguageId = LanguageId("en"))

        // Only one lesson — clamp to index 0, reset offset
        assertEquals(0, result.sentenceOffset)
        assertEquals(0, result.currentLessonIndex)
    }

    @Test
    fun testAdvanceCursor_noLessons_justAdvancesOffset() {
        // No lessons loaded, lessonSize = 0, condition `lessonSize > 0` is false
        val cursor = DailyCursorState(sentenceOffset = 0, currentLessonIndex = 0)

        val result = tracker.advanceCursor(cursor, sentenceCount = 10, selectedLanguageId = LanguageId("en"))

        assertEquals(10, result.sentenceOffset)
        assertEquals(0, result.currentLessonIndex)
    }

    // ── storeFirstSessionCardIds ──────────────────────────────────────────

    @Test
    fun testStoreFirstSessionCardIds_setsDateAndIds() {
        val cursor = DailyCursorState()

        val result = tracker.storeFirstSessionCardIds(
            currentCursor = cursor,
            sentenceIds = listOf("s1", "s2", "s3"),
            verbIds = listOf("v1", "v2")
        )

        assertEquals(listOf("s1", "s2", "s3"), result.firstSessionSentenceCardIds)
        assertEquals(listOf("v1", "v2"), result.firstSessionVerbCardIds)
        // Date should be set to today's date in ISO format
        assertFalse(result.firstSessionDate.isEmpty())
    }

    @Test
    fun testStoreFirstSessionCardIds_emptyIds_stillSetsDate() {
        val cursor = DailyCursorState()

        val result = tracker.storeFirstSessionCardIds(
            currentCursor = cursor,
            sentenceIds = emptyList(),
            verbIds = emptyList()
        )

        assertTrue(result.firstSessionSentenceCardIds.isEmpty())
        assertTrue(result.firstSessionVerbCardIds.isEmpty())
        assertFalse(result.firstSessionDate.isEmpty())
    }

    // ── resolveProgressLessonInfo ─────────────────────────────────────────

    @Test
    fun testResolveProgressLessonInfo_noProgress_returnsFirstLesson() {
        fakeMasteryStore // empty by default
        val lesson1 = makeLesson("lesson-1", cardCount = 10)
        val lesson2 = makeLesson("lesson-2", cardCount = 10)

        val result = tracker.resolveProgressLessonInfo(
            activePackId = PackId("pack-1"),
            selectedLanguageId = LanguageId("en"),
            activePackLessonIds = listOf("lesson-1", "lesson-2"),
            lessons = listOf(lesson1, lesson2),
            dailyCursor = DailyCursorState()
        )

        assertNotNull(result)
        assertEquals("lesson-1", result!!.first)
        assertEquals(1, result.second)
    }

    @Test
    fun testResolveProgressLessonInfo_nullPackId_returnsNull() {
        val result = tracker.resolveProgressLessonInfo(
            activePackId = null,
            selectedLanguageId = LanguageId("en"),
            activePackLessonIds = listOf("lesson-1"),
            lessons = emptyList(),
            dailyCursor = DailyCursorState()
        )

        assertNull(result)
    }

    @Test
    fun testResolveProgressLessonInfo_nullLessonIds_returnsNull() {
        val result = tracker.resolveProgressLessonInfo(
            activePackId = PackId("pack-1"),
            selectedLanguageId = LanguageId("en"),
            activePackLessonIds = null,
            lessons = emptyList(),
            dailyCursor = DailyCursorState()
        )

        assertNull(result)
    }

    @Test
    fun testResolveProgressLessonInfo_emptyLessonIds_returnsNull() {
        val result = tracker.resolveProgressLessonInfo(
            activePackId = PackId("pack-1"),
            selectedLanguageId = LanguageId("en"),
            activePackLessonIds = emptyList(),
            lessons = emptyList(),
            dailyCursor = DailyCursorState()
        )

        assertNull(result)
    }

    @Test
    fun testResolveProgressLessonInfo_withProgress_returnsNextLesson() {
        val lesson1 = makeLesson("lesson-1", cardCount = 10)
        val lesson2 = makeLesson("lesson-2", cardCount = 10)
        val lesson3 = makeLesson("lesson-3", cardCount = 10)

        // Simulate mastery for lesson-1 (uniqueCardShows > 0)
        fakeMasteryStore.save(
            LessonMasteryState(
                lessonId = LessonId("lesson-1"),
                languageId = LanguageId("en"),
                uniqueCardShows = 5,
                shownCardIds = setOf("lesson-1-card-1", "lesson-1-card-2", "lesson-1-card-3", "lesson-1-card-4", "lesson-1-card-5")
            )
        )

        val result = tracker.resolveProgressLessonInfo(
            activePackId = PackId("pack-1"),
            selectedLanguageId = LanguageId("en"),
            activePackLessonIds = listOf("lesson-1", "lesson-2", "lesson-3"),
            lessons = listOf(lesson1, lesson2, lesson3),
            dailyCursor = DailyCursorState()
        )

        assertNotNull(result)
        assertEquals("lesson-2", result!!.first)
        assertEquals(2, result.second)
    }

    @Test
    fun testResolveProgressLessonInfo_cursorAdvancesBeyondProgress_usesCursorPosition() {
        val lesson1 = makeLesson("lesson-1", cardCount = 10)
        val lesson2 = makeLesson("lesson-2", cardCount = 10)
        val lesson3 = makeLesson("lesson-3", cardCount = 10)

        // Lesson-1 has progress, but cursor is already at lesson-3 (index 2)
        fakeMasteryStore.save(
            LessonMasteryState(
                lessonId = LessonId("lesson-1"),
                languageId = LanguageId("en"),
                uniqueCardShows = 5,
                shownCardIds = setOf("lesson-1-card-1", "lesson-1-card-2", "lesson-1-card-3", "lesson-1-card-4", "lesson-1-card-5")
            )
        )

        val result = tracker.resolveProgressLessonInfo(
            activePackId = PackId("pack-1"),
            selectedLanguageId = LanguageId("en"),
            activePackLessonIds = listOf("lesson-1", "lesson-2", "lesson-3"),
            lessons = listOf(lesson1, lesson2, lesson3),
            dailyCursor = DailyCursorState(currentLessonIndex = 2)
        )

        assertNotNull(result)
        // Cursor at index 2 wins over progress-based index 1
        assertEquals("lesson-3", result!!.first)
        assertEquals(3, result.second)
    }

    @Test
    fun testResolveProgressLessonInfo_lastLessonWithProgress_isLastLesson_clampsToLast() {
        val lesson1 = makeLesson("lesson-1", cardCount = 10)
        val lesson2 = makeLesson("lesson-2", cardCount = 10)

        // Both lessons have progress, cursor at 0
        fakeMasteryStore.save(
            LessonMasteryState(
                lessonId = LessonId("lesson-1"),
                languageId = LanguageId("en"),
                uniqueCardShows = 3,
                shownCardIds = setOf("lesson-1-card-1", "lesson-1-card-2", "lesson-1-card-3")
            )
        )
        fakeMasteryStore.save(
            LessonMasteryState(
                lessonId = LessonId("lesson-2"),
                languageId = LanguageId("en"),
                uniqueCardShows = 3,
                shownCardIds = setOf("lesson-2-card-1", "lesson-2-card-2", "lesson-2-card-3")
            )
        )

        val result = tracker.resolveProgressLessonInfo(
            activePackId = PackId("pack-1"),
            selectedLanguageId = LanguageId("en"),
            activePackLessonIds = listOf("lesson-1", "lesson-2"),
            lessons = listOf(lesson1, lesson2),
            dailyCursor = DailyCursorState(currentLessonIndex = 0)
        )

        assertNotNull(result)
        // Next after lesson-2 would be index 2, but clamped to last (index 1)
        assertEquals("lesson-2", result!!.first)
        assertEquals(2, result.second)
    }

    @Test
    fun testResolveProgressLessonInfo_lessonIdsNotInLessons_returnsNull() {
        val result = tracker.resolveProgressLessonInfo(
            activePackId = PackId("pack-1"),
            selectedLanguageId = LanguageId("en"),
            activePackLessonIds = listOf("nonexistent-lesson"),
            lessons = emptyList(),
            dailyCursor = DailyCursorState()
        )

        assertNull(result)
    }

    // ── getProgressLessonLevel ────────────────────────────────────────────

    @Test
    fun testGetProgressLessonLevel_noPack_returns1() {
        val level = tracker.getProgressLessonLevel(
            activePackId = null,
            selectedLanguageId = LanguageId("en"),
            activePackLessonIds = null,
            lessons = emptyList(),
            dailyCursor = DailyCursorState()
        )

        assertEquals(1, level)
    }

    @Test
    fun testGetProgressLessonLevel_withProgress_returnsCorrectLevel() {
        val lesson1 = makeLesson("lesson-1", cardCount = 10)
        val lesson2 = makeLesson("lesson-2", cardCount = 10)

        fakeMasteryStore.save(
            LessonMasteryState(
                lessonId = LessonId("lesson-1"),
                languageId = LanguageId("en"),
                uniqueCardShows = 5,
                shownCardIds = setOf("lesson-1-card-1", "lesson-1-card-2", "lesson-1-card-3", "lesson-1-card-4", "lesson-1-card-5")
            )
        )

        val level = tracker.getProgressLessonLevel(
            activePackId = PackId("pack-1"),
            selectedLanguageId = LanguageId("en"),
            activePackLessonIds = listOf("lesson-1", "lesson-2"),
            lessons = listOf(lesson1, lesson2),
            dailyCursor = DailyCursorState()
        )

        assertEquals(2, level)
    }

    // Note: resetStores and resetStoresForLanguage require android.content.Context.
    // These methods only delegate to store.clear() / clearLanguage() with context unused,
    // so they are better tested via Robolectric integration tests. The pure-JUnit tests
    // for those stores already cover the clear/clearLanguage logic.

    // ── Edge cases ────────────────────────────────────────────────────────

    @Test
    fun testRecordCardShowForMastery_cardInDifferentLesson_resolvesCorrectly() {
        val lesson1 = makeLesson("lesson-1", cardCount = 5)
        val lesson2 = makeLesson("lesson-2", cardCount = 5)

        // Show lesson-2's card, but lesson-1 is selected
        // resolveCardLessonId should find it in lesson-2
        tracker.recordCardShowForMastery(
            card = lesson2.cards[0],
            bossActive = false,
            isDrillMode = false,
            inputMode = InputMode.VOICE,
            selectedLanguageId = LanguageId("en"),
            lessons = listOf(lesson1, lesson2),
            selectedLessonId = LessonId("lesson-1")
        )

        assertEquals(1, fakeMasteryStore.recordCardShowCalls)
        // Should be recorded under lesson-2, not lesson-1
        assertNull(fakeMasteryStore.get("lesson-1", "en"))
        assertNotNull(fakeMasteryStore.get("lesson-2", "en"))
    }

    @Test
    fun testCalculateCompletedSubLessons_emptySubLessons_returnsZero() {
        val lesson = makeLesson("lesson-1", cardCount = 10)
        val mastery = LessonMasteryState(
            lessonId = LessonId("lesson-1"),
            languageId = LanguageId("en"),
            shownCardIds = lesson.cards.map { it.id }.toSet()
        )

        val result = tracker.calculateCompletedSubLessons(
            subLessons = emptyList(),
            mastery = mastery,
            lessonId = LessonId("lesson-1"),
            lessons = listOf(lesson)
        )

        assertEquals(0, result)
    }

    @Test
    fun testSaveProgress_preservesActivePackId() {
        val state = TrainingUiState(
            navigation = NavigationState(activePackId = PackId("my-pack"))
        )

        tracker.saveProgress(state, forceBackup = false, normalizedEliteSpeeds = emptyList())

        assertEquals(PackId("my-pack"), fakeProgressStore.savedProgress!!.activePackId)
    }

    @Test
    fun testAdvanceCursor_multipleLessons_progressesThroughAll() {
        fakeLessonStore.lessons.add(makeLesson("lesson-1", cardCount = 10))
        fakeLessonStore.lessons.add(makeLesson("lesson-2", cardCount = 10))
        fakeLessonStore.lessons.add(makeLesson("lesson-3", cardCount = 10))

        // Start at lesson 0, offset 0, add 10 → exactly fills lesson 1
        var cursor = tracker.advanceCursor(
            DailyCursorState(sentenceOffset = 0, currentLessonIndex = 0),
            sentenceCount = 10,
            selectedLanguageId = LanguageId("en")
        )
        assertEquals(0, cursor.sentenceOffset)
        assertEquals(1, cursor.currentLessonIndex)

        // Now at lesson 1, offset 0, add 10 → fills lesson 2
        cursor = tracker.advanceCursor(
            cursor,
            sentenceCount = 10,
            selectedLanguageId = LanguageId("en")
        )
        assertEquals(0, cursor.sentenceOffset)
        assertEquals(2, cursor.currentLessonIndex)

        // Now at lesson 2 (last), offset 0, add 10 → fills it, but clamps to last
        cursor = tracker.advanceCursor(
            cursor,
            sentenceCount = 10,
            selectedLanguageId = LanguageId("en")
        )
        assertEquals(0, cursor.sentenceOffset)
        assertEquals(2, cursor.currentLessonIndex)
    }
}

package com.alexpo.grammermate.data

import android.content.ContextWrapper
import com.alexpo.grammermate.feature.progress.BadSentenceHelper
import com.alexpo.grammermate.feature.progress.ProgressTracker
import com.alexpo.grammermate.feature.training.CardProvider
import com.alexpo.grammermate.testharness.FakeTrainingStateAccess
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ScopedCardIdentityRegressionTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun context() = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getFilesDir(): File = temporary.root
    }

    private fun state(pack: String, lesson: Lesson, card: SentenceCard = lesson.cards.first()) =
        TrainingUiState().let { base -> base.copy(
            navigation = base.navigation.copy(activePackId = PackId(pack), selectedLessonId = lesson.id, selectedLanguageId = lesson.languageId),
            cardSession = base.cardSession.copy(currentCard = card)
        ) }

    @Test fun hideInFirstRealEnglishPackDoesNotHideSameRowInSecondPack() {
        val app = context()
        val store = LessonStoreImpl(app)
        val a = store.importPackFromAssets("grammarmate/packs/EN_WORD_ORDER_A1.zip")
        val b = store.importPackFromAssets("grammarmate/packs/EN_WORD_ORDER_A1_DRILLS.zip")
        val lessonA = store.getLessons(a.packId.value, "en").single()
        val lessonB = store.getLessons(b.packId.value, "en").single()
        val cardA = lessonA.cards.first()
        val cardB = lessonB.cards.first()
        assertEquals("card_2", cardA.id)
        assertEquals(cardA.id, cardB.id)
        assertNotEquals(cardA.acceptedAnswers, cardB.acceptedAnswers)
        assertEquals(a.packId.value, cardA.sourcePackId)
        assertEquals(lessonA.id.value, cardA.sourceLessonId)
        val hidden = HiddenCardStoreImpl(app)
        val access = FakeTrainingStateAccess(state(a.packId.value, lessonA))
        BadSentenceHelper(access, mockk(relaxed = true), hidden).hideCurrentCard()
        assertEquals(setOf(HiddenCardKeys.forCard(cardA)), hidden.getHiddenCardIds())
        val provider = CardProvider()
        assertFalse(provider.buildReviewCards(listOf(lessonA), lessonA.id, hidden.getHiddenCardIds()).contains(cardA))
        assertTrue(provider.buildReviewCards(listOf(lessonB), lessonB.id, hidden.getHiddenCardIds()).contains(cardB))
    }

    @Test fun samePackDifferentLessonsKeepIndependentHiddenAndCompletionState() {
        val app = context()
        val store = LessonStoreImpl(app)
        val pack = store.importPackFromAssets("grammarmate/packs/EN_ADV_BASE_GRAMMAR.zip").packId.value
        val lessons = store.getLessons(pack, "en").take(2).map { it.copy(cards = it.cards.take(10)) }
        val a = lessons[0]
        val b = lessons[1]
        assertEquals("card_2", a.cards.first().id)
        assertEquals(a.cards.first().id, b.cards.first().id)
        val hidden = HiddenCardStoreImpl(app)
        val access = FakeTrainingStateAccess(state(pack, a))
        val helper = BadSentenceHelper(access, mockk(relaxed = true), hidden)
        helper.hideCurrentCard()
        access.setState(state(pack, a, a.cards[1]))
        helper.hideCurrentCard()
        assertTrue(HiddenCardKeys.contains(hidden.getHiddenCardIds(), a.cards.first()))
        assertFalse(HiddenCardKeys.contains(hidden.getHiddenCardIds(), b.cards.first()))
        assertTrue(CardProvider().buildReviewCards(listOf(b), b.id, hidden.getHiddenCardIds()).contains(b.cards.first()))

        val mastery = MasteryStoreImpl(app)
        lessons.forEach { lesson -> mastery.saveForPack(LessonMasteryState(lesson.id, lesson.languageId, uniqueCardShows = 8), pack) }
        val tracker = ProgressTracker(access, mastery, ProgressStoreImpl(app), store, PackDailyCursorStoreImpl(app), PackLessonProgressStoreImpl(app))
        tracker.recalculateCompletionsExcludingHidden(lessons, LanguageId("en"), hidden.getHiddenCardIds(), pack)
        assertNotNull(mastery.getForPack(pack, a.id.value)!!.completedAtMs)
        assertNull(mastery.getForPack(pack, b.id.value)!!.completedAtMs)
        val shownA = mastery.getForPack(pack, a.id.value)!!.copy(shownCardIds = setOf(a.cards[2].id))
        val shownB = mastery.getForPack(pack, b.id.value)!!.copy(shownCardIds = setOf(b.cards[2].id))
        assertEquals(1, tracker.calculateCompletedSubLessons(
            listOf(ScheduledSubLesson(SubLessonType.NEW_ONLY, listOf(a.cards.first()))), shownA, a.id, lessons, hidden.getHiddenCardIds()
        ))
        assertEquals(0, tracker.calculateCompletedSubLessons(
            listOf(ScheduledSubLesson(SubLessonType.NEW_ONLY, listOf(b.cards.first()))), shownB, b.id, lessons, hidden.getHiddenCardIds()
        ))
        assertEquals(1, tracker.calculateCompletedSubLessons(
            listOf(ScheduledSubLesson(SubLessonType.MIXED, listOf(a.cards[2], b.cards.first()))), shownA, a.id, lessons
        ))
        mastery.flush()
    }

    @Test fun mixedReviewCreditsItsSourceLessonAndWordBankCannotCreditAnotherLessonsSameRow() {
        val app = context()
        val store = LessonStoreImpl(app)
        val pack = store.importPackFromAssets("grammarmate/packs/EN_ADV_BASE_GRAMMAR.zip").packId.value
        val lessons = store.getLessons(pack, "en").take(2)
        val a = lessons[0]
        val b = lessons[1]
        val reviewCard = b.cards.first()
        assertEquals(a.cards.first().id, reviewCard.id)
        val access = FakeTrainingStateAccess(state(pack, a, reviewCard))
        val mastery = MasteryStoreImpl(app)
        val tracker = ProgressTracker(access, mastery, ProgressStoreImpl(app), store, PackDailyCursorStoreImpl(app), PackLessonProgressStoreImpl(app))

        tracker.recordCardShowForMastery(reviewCard, false, InputMode.KEYBOARD, LanguageId("en"), lessons, a.id)
        assertNull(mastery.getForPack(pack, a.id.value))
        assertEquals(setOf("card_2"), mastery.getForPack(pack, b.id.value)!!.shownCardIds)
        assertEquals(1, mastery.getForPack(pack, b.id.value)!!.totalCardShows)

        tracker.markSubLessonCardsShown(listOf(reviewCard), InputMode.WORD_BANK, a.id, LanguageId("en"), lessons, pack)
        assertNull(mastery.getForPack(pack, a.id.value))
        tracker.markSubLessonCardsShown(listOf(a.cards.first(), reviewCard), InputMode.WORD_BANK, a.id, LanguageId("en"), lessons, pack)
        assertEquals(setOf("card_2"), mastery.getForPack(pack, a.id.value)!!.shownCardIds)
        assertEquals(0, mastery.getForPack(pack, a.id.value)!!.uniqueCardShows)
        assertEquals(0, mastery.getForPack(pack, a.id.value)!!.totalCardShows)

        val staleCard = reviewCard.copy(sourcePackId = "foreign")
        assertNull(tracker.resolveCardLessonId(staleCard, a.id, lessons))
        tracker.recordCardShowForMastery(staleCard, false, InputMode.KEYBOARD, LanguageId("en"), lessons, a.id)
        tracker.markSubLessonCardsShown(listOf(staleCard), InputMode.WORD_BANK, a.id, LanguageId("en"), lessons, pack)
        assertEquals(1, mastery.getForPack(pack, b.id.value)!!.totalCardShows)
        mastery.flush()
        assertEquals(setOf("card_2"), MasteryStoreImpl(app).getForPack(pack, b.id.value)!!.shownCardIds)
    }

    @Test fun legacyHiddenRowsRemainUnmappedAndRoundTripAlongsideScopedKeys() {
        val app = context()
        val hidden = HiddenCardStoreImpl(app)
        val a = SentenceCard("card_2", "A", listOf("A"), sourcePackId = "A", sourceLessonId = "lesson")
        val b = a.copy(promptRu = "B", sourcePackId = "AB")
        hidden.hideCard("card_2")
        assertFalse(HiddenCardKeys.contains(hidden.getHiddenCardIds(), a))
        assertFalse(HiddenCardKeys.contains(hidden.getHiddenCardIds(), b))
        hidden.hideCard(HiddenCardKeys.forCard(a)!!)
        hidden.hideCard(HiddenCardKeys.forCard(b)!!)
        val before = hidden.getHiddenCardIds()
        val manager = BackupManagerImpl(app)
        assertTrue(manager.createBackup())
        hidden.clearAll()
        assertTrue(manager.restoreFromBackup(File(app.filesDir, "grammarmate/backups/backup_latest").path))
        val restored = HiddenCardStoreImpl(app)
        assertEquals(before, restored.getHiddenCardIds())
        restored.clearPack("A")
        assertEquals(setOf("card_2", HiddenCardKeys.forCard(b)), restored.getHiddenCardIds())
        assertFalse(HiddenCardKeys.contains(restored.getHiddenCardIds(), a))
        assertTrue(HiddenCardKeys.contains(restored.getHiddenCardIds(), b))
        assertNull(HiddenCardKeys.forCard(a, fallbackPackId = "AB", fallbackLessonId = "lesson"))
        assertNull(HiddenCardKeys.forCard(a.copy(sourcePackId = null, sourceLessonId = null)))
    }
}

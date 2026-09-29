package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.AppConfig
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.PackId
import com.alexpo.grammermate.data.PackLessonProgressState
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.StreakData
import com.alexpo.grammermate.data.TrainingProgress
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingStartupStateTest {
    @Test
    fun packScopedLessonProgressWinsOverLegacyGlobalFields() {
        val language = LanguageId("en")
        val lesson = Lesson(LessonId("L1"), language, "One", emptyList())
        val startup = TrainingStartupState(
            content = StartupContent(language, PackId("EN"), listOf("L1"), listOf(lesson), lesson.id),
            languages = emptyList(), packs = emptyList(),
            progress = TrainingProgress(currentIndex = 99, correctCount = 99, voiceWordCount = 4),
            config = AppConfig(testMode = true), profile = UserProfile(userName = "Ada"),
            streak = StreakData(languageId = language),
            lessonProgress = PackLessonProgressState.LessonProgress(
                currentIndex = 3, correctCount = 2, state = SessionState.ACTIVE
            ),
            normalizedEliteSpeeds = listOf(1.5), eliteUnlocked = true,
            eliteStepCount = 5, badSentenceCount = 7, hintSessionOffset = 12
        )

        val before = TrainingUiState(isLoading = true).let {
            it.copy(cardSession = it.cardSession.copy(returnTo = "lesson"))
        }
        val state = startup.apply(before)

        assertFalse(state.isLoading)
        assertEquals(PackId("EN"), state.navigation.activePackId)
        assertEquals(lesson.id, state.navigation.selectedLessonId)
        assertEquals(3, state.cardSession.currentIndex)
        assertEquals(2, state.cardSession.correctCount)
        assertEquals(SessionState.ACTIVE, state.cardSession.sessionState)
        assertEquals(4, state.cardSession.voiceWordCount)
        assertEquals(7, state.cardSession.badSentenceCount)
        assertEquals("lesson", state.cardSession.returnTo)
        assertEquals("Ada", state.navigation.userName)
    }

    @Test
    fun zeroStateKeepsLanguageAndPackUnselected() {
        val startup = TrainingStartupState(
            content = StartupContent(null, null, null, emptyList(), null),
            languages = emptyList(), packs = emptyList(),
            progress = TrainingProgress(), config = AppConfig(), profile = UserProfile(),
            streak = StreakData(languageId = LanguageId("")), lessonProgress = null,
            normalizedEliteSpeeds = emptyList(), eliteUnlocked = false,
            eliteStepCount = 5, badSentenceCount = 0, hintSessionOffset = 0
        )

        val state = startup.apply(TrainingUiState(isLoading = true))

        assertNull(state.navigation.selectedLanguageId)
        assertNull(state.navigation.activePackId)
        assertNull(state.navigation.selectedLessonId)
        assertEquals(SessionState.PAUSED, state.cardSession.sessionState)
    }
}

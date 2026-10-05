package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.AppConfig
import com.alexpo.grammermate.data.Language
import com.alexpo.grammermate.data.LessonPack
import com.alexpo.grammermate.data.PackLessonProgressState
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.StreakData
import com.alexpo.grammermate.data.TrainingProgress
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.UserProfile

/** Pure startup projection; feature initialization and effects stay in the ViewModel. */
internal data class TrainingStartupState(
    val content: StartupContent,
    val languages: List<Language>,
    val packs: List<LessonPack>,
    val progress: TrainingProgress,
    val config: AppConfig,
    val profile: UserProfile,
    val streak: StreakData,
    val lessonProgress: PackLessonProgressState.LessonProgress?,
    val normalizedEliteSpeeds: List<Double>,
    val eliteUnlocked: Boolean,
    val eliteStepCount: Int,
    val badSentenceCount: Int,
    val hintSessionOffset: Int
) {
    fun apply(current: TrainingUiState): TrainingUiState {
        return current.copy(
            isLoading = false,
            navigation = current.navigation.copy(
                languages = languages,
                installedPacks = packs,
                selectedLanguageId = content.languageId,
                activePackId = content.packId,
                activePackLessonIds = content.lessonIds,
                lessons = content.lessons,
                selectedLessonId = content.selectedLessonId,
                mode = progress.mode,
                userName = profile.userName,
                initialScreen = "HOME",
                welcomeDialogAttempts = profile.welcomeDialogAttempts,
                themeMode = config.themeMode,
                uiLanguage = config.uiLanguage,
                clickableWordHints = config.clickableWordHints
            ),
            cardSession = current.cardSession.copy(
                sessionState = lessonProgress?.state?.takeUnless { it == SessionState.HINT_SHOWN }
                    ?: SessionState.PAUSED,
                currentIndex = lessonProgress?.currentIndex ?: 0,
                activeSubLessonIndex = lessonProgress?.activeSubLessonIndex ?: 0,
                correctCount = lessonProgress?.correctCount ?: 0,
                incorrectCount = lessonProgress?.incorrectCount ?: 0,
                incorrectAttemptsForCard = 0,
                activeTimeMs = lessonProgress?.activeTimeMs ?: 0L,
                voiceActiveMs = progress.voiceActiveMs,
                voiceWordCount = progress.voiceWordCount,
                hintCount = progress.hintCount,
                testMode = config.testMode,
                vocabSprintLimit = config.vocabSprintLimit,
                currentStreak = streak.currentStreak,
                longestStreak = streak.longestStreak,
                todayFireCount = streak.todayFireCount,
                badSentenceCount = badSentenceCount,
                hintLevel = config.hintLevel,
                hintSessionOffset = hintSessionOffset
            ),
            elite = current.elite.copy(
                eliteStepIndex = progress.eliteStepIndex.coerceIn(0, eliteStepCount - 1),
                eliteBestSpeeds = normalizedEliteSpeeds,
                eliteUnlocked = eliteUnlocked,
                eliteSizeMultiplier = config.eliteSizeMultiplier
            )
        )
    }
}

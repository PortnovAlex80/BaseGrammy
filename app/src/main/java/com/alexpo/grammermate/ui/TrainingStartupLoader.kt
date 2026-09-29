package com.alexpo.grammermate.ui

import android.app.Application
import com.alexpo.grammermate.AppContainer
import com.alexpo.grammermate.data.AppConfig
import com.alexpo.grammermate.data.GrammarChipStore
import com.alexpo.grammermate.data.Language
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.LessonPack
import com.alexpo.grammermate.data.PackLessonProgressState
import com.alexpo.grammermate.data.StreakData
import com.alexpo.grammermate.data.TrainingProgress
import com.alexpo.grammermate.data.UserProfile
import com.alexpo.grammermate.feature.pomodoro.PomodoroRecords
import java.io.File

/** Performs startup disk reads before the ViewModel publishes or starts feature state. */
internal class TrainingStartupLoader(
    private val application: Application,
    private val container: AppContainer,
    private val contentResolver: StartupContentResolver,
    private val pomodoroRecords: PomodoroRecords
) {
    suspend fun load(): TrainingStartupData {
        val lessonStore = container.lessonStore
        lessonStore.ensureSeedData()
        lessonStore.updateDefaultPacksIfNeeded()
        GrammarChipStore.initialize(application, File(application.filesDir, "grammarmate/packs"))
        container.badSentenceStore.migrateIfNeeded(lessonStore)

        val progress = container.progressStore.load()
        val config = container.configStore.load()
        val profile = container.profileStore.load()
        container.masteryStore.loadAll()
        pomodoroRecords.loadDuration()

        val languages = lessonStore.getLanguagesWithPacks()
        val packs = lessonStore.getInstalledPacks()
        val content = contentResolver.resolve(
            preferredLanguageId = progress.languageId.takeIf { container.progressStore.exists() },
            preferredPackId = progress.activePackId,
            preferredLessonId = null,
            languages = languages,
            packs = packs
        )
        val packProgress = content.packId?.let { container.packLessonProgressStore.loadPackProgress(it.value) }
        val lessonProgress = content.selectedLessonId?.let { packProgress?.lessonProgress?.get(it.value) }
        val streak = content.languageId?.let { container.streakStore.getCurrentStreak(it.value) }
            ?: StreakData(languageId = LanguageId(""))
        val badSentenceCount = content.packId?.let {
            container.badSentenceStore.getBadSentenceCount(it.value)
        } ?: 0
        return TrainingStartupData(
            content, languages, packs, progress, config, profile, streak, lessonProgress, badSentenceCount
        )
    }
}

internal data class TrainingStartupData(
    val content: StartupContent,
    val languages: List<Language>,
    val packs: List<LessonPack>,
    val progress: TrainingProgress,
    val config: AppConfig,
    val profile: UserProfile,
    val streak: StreakData,
    val lessonProgress: PackLessonProgressState.LessonProgress?,
    val badSentenceCount: Int
)

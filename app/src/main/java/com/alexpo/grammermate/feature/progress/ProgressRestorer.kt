package com.alexpo.grammermate.feature.progress

import android.util.Log
import com.alexpo.grammermate.data.BackupManager
import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonPack
import com.alexpo.grammermate.data.Language
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.PackId
import com.alexpo.grammermate.data.ProfileStore
import com.alexpo.grammermate.data.ProgressStore
import com.alexpo.grammermate.data.StreakData
import com.alexpo.grammermate.data.StreakStore
import com.alexpo.grammermate.data.TrainingProgress
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.UserProfile
import com.alexpo.grammermate.feature.daily.TrainingStateAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper responsible for restoring progress from disk, backup, or initial load.
 *
 * Owns the restoration pipeline: load persisted data, resolve valid references,
 * apply to UI state, and trigger downstream rebuilds (schedules, cards, flowers).
 *
 * Query-style operations (normalizeEliteSpeeds, resolveEliteUnlocked, parseBossRewards)
 * are injected as constructor function parameters.
 * Command-style results are returned as [List]<[ProgressResult]> for the ViewModel to execute.
 */
class ProgressRestorer(
    private val stateAccess: TrainingStateAccess,
    private val progressStore: ProgressStore,
    private val profileStore: ProfileStore,
    private val streakStore: StreakStore,
    private val lessonStore: LessonStore,
    private val backupManager: BackupManager,
    private val eliteStepCount: Int,
    private val normalizeEliteSpeeds: (List<Double>) -> List<Double>,
    private val resolveEliteUnlocked: (List<Lesson>, Boolean) -> Boolean,
    private val parseBossRewards: (Map<String, String>) -> Map<String, BossReward>
) {
    companion object {
        private const val logTag = "GrammarMate"
    }

    /**
     * Apply progress restoration to UI state and rebuild derived data.
     * Shared by [restoreBackup], [reloadFromDisk], and init block.
     *
     * Returns a list of [ProgressResult] commands for the ViewModel to execute.
     */
    fun applyRestoredProgress(
        progress: TrainingProgress,
        languages: List<Language>,
        packs: List<LessonPack>,
        lessons: List<Lesson>,
        selectedLanguageId: LanguageId,
        selectedLessonId: LessonId?,
        streak: StreakData,
        profile: UserProfile,
        bossLessonRewards: Map<String, BossReward>,
        bossMegaRewards: Map<String, BossReward>,
        includeLanguageData: Boolean = false
    ): List<ProgressResult> {
        val normalizedEliteSpeeds = normalizeEliteSpeeds(progress.eliteBestSpeeds)
        stateAccess.updateState {
            val base = it.copy(
                navigation = it.navigation.copy(
                    selectedLanguageId = selectedLanguageId,
                    lessons = lessons,
                    selectedLessonId = selectedLessonId,
                    mode = progress.mode,
                    userName = profile.userName
                ),
                cardSession = it.cardSession.copy(
                    sessionState = progress.state,
                    currentIndex = progress.currentIndex,
                    correctCount = progress.correctCount,
                    incorrectCount = progress.incorrectCount,
                    incorrectAttemptsForCard = progress.incorrectAttemptsForCard,
                    activeTimeMs = progress.activeTimeMs,
                    voiceActiveMs = progress.voiceActiveMs,
                    voiceWordCount = progress.voiceWordCount,
                    hintCount = progress.hintCount,
                    currentStreak = streak.currentStreak,
                    longestStreak = streak.longestStreak
                ),
                boss = it.boss.copy(
                    bossLessonRewards = bossLessonRewards,
                    bossMegaRewards = bossMegaRewards
                ),
                elite = it.elite.copy(
                    eliteStepIndex = progress.eliteStepIndex.coerceIn(0, eliteStepCount - 1),
                    eliteBestSpeeds = normalizedEliteSpeeds
                )
            )
            if (includeLanguageData) {
                base.copy(
                    navigation = base.navigation.copy(
                        languages = languages,
                        installedPacks = packs
                    ),
                    elite = base.elite.copy(
                        eliteUnlocked = resolveEliteUnlocked(lessons, base.cardSession.testMode)
                    )
                )
            } else {
                base
            }
        }
        return listOf(
            ProgressResult.RebuildSchedules(lessons),
            ProgressResult.BuildSessionCards,
            ProgressResult.RefreshFlowerStates
        )
    }

    /**
     * Restore user progress from backup folder.
     * Returns a list of [ProgressResult] commands for the ViewModel to execute.
     */
    fun restoreBackup(backupUri: android.net.Uri): List<ProgressResult> {
        Log.d(logTag, "=== Starting Backup Restore ===")
        Log.d(logTag, "Backup URI: $backupUri")

        val success = backupManager.restoreFromBackupUri(backupUri)
        Log.d(logTag, "Backup restore result: $success")

        if (success) {
            val progress = progressStore.load()
            val profile = profileStore.load()
            val selectedLanguageId = if (progress.languageId.value.isNotEmpty()) progress.languageId else LanguageId("en")
            val lessons = lessonStore.getLessons(selectedLanguageId.value)
            val selectedLessonId = progress.lessonId?.let { id ->
                lessons.firstOrNull { it.id.value == id }?.id
            } ?: lessons.firstOrNull()?.id
            val streak = streakStore.getCurrentStreak(selectedLanguageId.value)
            val bossLessonRewards = parseBossRewards(progress.bossLessonRewards)
            val bossMegaRewards = parseBossRewards(progress.bossMegaRewards)

            val results = applyRestoredProgress(
                progress = progress,
                languages = emptyList(),
                packs = emptyList(),
                lessons = lessons,
                selectedLanguageId = selectedLanguageId,
                selectedLessonId = selectedLessonId,
                streak = streak,
                profile = profile,
                bossLessonRewards = bossLessonRewards,
                bossMegaRewards = bossMegaRewards,
                includeLanguageData = false
            )

            Log.d(logTag, "=== Backup Restore Complete ===")
            return results
        } else {
            Log.e(logTag, "Failed to restore backup - check restore_log.txt in backup folder")
            return emptyList()
        }
    }

    /**
     * Reload all progress from disk (async).
     * Used after external changes (e.g., pack import) to refresh state.
     *
     * Returns a list of [ProgressResult] commands for the ViewModel to execute.
     */
    suspend fun reloadFromDisk(): List<ProgressResult> {
        val progress = progressStore.load()
        val profile = profileStore.load()
        val languages = lessonStore.getLanguages()
        val packs = lessonStore.getInstalledPacks()
        val selectedLanguageId = languages.firstOrNull { it.id == progress.languageId }?.id
            ?: languages.firstOrNull()?.id
            ?: LanguageId("en")
        val lessons = lessonStore.getLessons(selectedLanguageId.value)
        val selectedLessonId = progress.lessonId?.let { id ->
            lessons.firstOrNull { it.id.value == id }?.id
        } ?: lessons.firstOrNull()?.id
        val streak = streakStore.getCurrentStreak(selectedLanguageId.value)
        val bossLessonRewards = parseBossRewards(progress.bossLessonRewards)
        val bossMegaRewards = parseBossRewards(progress.bossMegaRewards)

        return withContext(Dispatchers.Main) {
            applyRestoredProgress(
                progress = progress,
                languages = languages,
                packs = packs,
                lessons = lessons,
                selectedLanguageId = selectedLanguageId,
                selectedLessonId = selectedLessonId,
                streak = streak,
                profile = profile,
                bossLessonRewards = bossLessonRewards,
                bossMegaRewards = bossMegaRewards,
                includeLanguageData = true
            )
        }
    }
}

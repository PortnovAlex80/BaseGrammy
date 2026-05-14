package com.alexpo.grammermate.shared

import android.util.Log
import com.alexpo.grammermate.data.AppConfigStore
import com.alexpo.grammermate.data.BackupManager
import com.alexpo.grammermate.data.DailyCursorState
import com.alexpo.grammermate.data.DailySessionState
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.ProfileStore
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.UserProfile
import com.alexpo.grammermate.data.WordMasteryStore
import com.alexpo.grammermate.feature.daily.TrainingStateAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles settings-screen actions: config changes, profile updates,
 * progress reset, and backup creation.
 *
 * All state writes go through [TrainingStateAccess].
 * Query-style operations (resolveEliteUnlocked) are injected as constructor function parameters.
 * Command-style results are returned as [List]<[SettingsResult]> for the ViewModel to execute.
 */
class SettingsActionHandler(
    private val stateAccess: TrainingStateAccess,
    private val configStore: AppConfigStore,
    private val profileStore: ProfileStore,
    private val backupManager: BackupManager,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope,
    private val resolveEliteUnlocked: (List<Lesson>, Boolean) -> Boolean
) {
    companion object {
        private const val logTag = "GrammarMate"
    }

    // ── Config changes ──────────────────────────────────────────────────

    fun toggleTestMode(): List<SettingsResult> {
        val state = stateAccess.uiState.value
        val newTestMode = !state.cardSession.testMode
        stateAccess.updateState {
            it.copy(
                cardSession = it.cardSession.copy(testMode = newTestMode),
                elite = it.elite.copy(eliteUnlocked = resolveEliteUnlocked(state.navigation.lessons, newTestMode))
            )
        }
        configStore.save(configStore.load().copy(testMode = newTestMode))
        Log.d(logTag, "Test mode toggled: $newTestMode")
        return emptyList()
    }

    fun updateVocabSprintLimit(limit: Int) {
        val nextLimit = limit.coerceAtLeast(0)
        stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(vocabSprintLimit = nextLimit)) }
        configStore.save(configStore.load().copy(vocabSprintLimit = nextLimit))
    }

    fun setHintLevel(level: HintLevel) {
        stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(hintLevel = level)) }
        configStore.save(configStore.load().copy(hintLevel = level))
    }

    // ── Profile ─────────────────────────────────────────────────────────

    fun updateUserName(newName: String) {
        val trimmed = newName.trim().take(50)
        if (trimmed.isEmpty()) return
        val current = profileStore.load()
        profileStore.save(current.copy(userName = trimmed))
        stateAccess.updateState {
            it.copy(navigation = it.navigation.copy(userName = trimmed))
        }
    }

    fun incrementWelcomeDialogAttempts() {
        val current = profileStore.load()
        val newAttempts = current.welcomeDialogAttempts + 1
        profileStore.save(current.copy(welcomeDialogAttempts = newAttempts))
        stateAccess.updateState {
            it.copy(navigation = it.navigation.copy(welcomeDialogAttempts = newAttempts))
        }
    }

    // ── Progress management ─────────────────────────────────────────────

    fun saveProgressNow(): List<SettingsResult> {
        Log.d(logTag, "Manual progress save requested from settings")
        return listOf(SettingsResult.SetForceBackup, SettingsResult.SaveProgress)
    }

    fun createProgressBackup() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val success = backupManager.createBackup()
                Log.d(logTag, "Progress backup created: success=$success")
            } catch (e: Exception) {
                Log.e(logTag, "Failed to create progress backup", e)
            }
        }
    }

    fun resetAllProgress(app: android.app.Application): List<SettingsResult> {
        stateAccess.updateState {
            it.copy(
                daily = it.daily.copy(dailySession = DailySessionState(), dailyCursor = DailyCursorState()),
                cardSession = it.cardSession.copy(
                    currentIndex = 0,
                    correctCount = 0,
                    incorrectCount = 0,
                    sessionState = SessionState.PAUSED,
                    inputText = "",
                    lastResult = null,
                    answerText = null,
                    incorrectAttemptsForCard = 0
                )
            )
        }

        Log.d(logTag, "All progress reset: mastery, daily, verb drill, vocab mastery, training progress")
        return listOf(
            SettingsResult.ResetStores(app),
            SettingsResult.ResetDrillFiles(app),
            SettingsResult.ClearWordMastery,
            SettingsResult.ResetDailyState,
            SettingsResult.RefreshLessons(null)
        )
    }

    /**
     * Reset progress for the current language/pack only.
     * Clears mastery, drill progress, daily state, and training session.
     * Other language packs are NOT affected.
     */
    fun resetLanguageProgress(app: android.app.Application, languageId: String, packId: String?): List<SettingsResult> {
        stateAccess.updateState {
            it.copy(
                daily = it.daily.copy(dailySession = DailySessionState(), dailyCursor = DailyCursorState()),
                cardSession = it.cardSession.copy(
                    currentIndex = 0,
                    correctCount = 0,
                    incorrectCount = 0,
                    sessionState = SessionState.PAUSED,
                    inputText = "",
                    lastResult = null,
                    answerText = null,
                    incorrectAttemptsForCard = 0
                )
            )
        }

        val results = mutableListOf<SettingsResult>(
            SettingsResult.ResetStoresForLanguage(app, languageId),
            SettingsResult.ClearWordMastery,
            SettingsResult.ResetDailyState,
            SettingsResult.RefreshLessons(null)
        )
        if (packId != null) {
            results.add(1, SettingsResult.ResetDrillFilesForPack(app, packId))
        }

        Log.d(logTag, "Language progress reset for language=$languageId, pack=$packId: mastery, daily, verb drill, vocab mastery, training progress")
        return results
    }

    // ── Screen tracking ─────────────────────────────────────────────────

    fun onScreenChanged(screenName: String) {
        stateAccess.updateState { it.copy(navigation = it.navigation.copy(currentScreen = screenName)) }
    }
}

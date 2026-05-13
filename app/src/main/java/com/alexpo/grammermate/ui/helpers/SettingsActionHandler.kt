package com.alexpo.grammermate.ui.helpers

import android.util.Log
import com.alexpo.grammermate.data.AppConfigStore
import com.alexpo.grammermate.data.BackupManager
import com.alexpo.grammermate.data.DailyCursorState
import com.alexpo.grammermate.data.DailySessionState
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.ProfileStore
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.UserProfile
import com.alexpo.grammermate.data.WordMasteryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Callback interface for cross-module orchestration from [SettingsActionHandler].
 */
interface SettingsCallbacks {
    fun resolveEliteUnlocked(lessons: List<com.alexpo.grammermate.data.Lesson>, testMode: Boolean): Boolean
    fun refreshLessons(selectedLessonId: String?)
    fun resetStores(app: android.app.Application)
    fun resetStoresForLanguage(app: android.app.Application, languageId: String)
    fun resetDrillFiles(app: android.app.Application)
    fun resetDrillFilesForPack(app: android.app.Application, packId: String)
    fun clearWordMastery()
    fun resetDailyState()
    fun setForceBackup(value: Boolean)
    fun saveProgress()
}

/**
 * Handles settings-screen actions: config changes, profile updates,
 * progress reset, and backup creation.
 *
 * All state writes go through [TrainingStateAccess].
 * Cross-module coordination uses [SettingsCallbacks].
 */
class SettingsActionHandler(
    private val stateAccess: TrainingStateAccess,
    private val callbacks: SettingsCallbacks,
    private val configStore: AppConfigStore,
    private val profileStore: ProfileStore,
    private val backupManager: BackupManager,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    companion object {
        private const val logTag = "GrammarMate"
    }

    // ── Config changes ──────────────────────────────────────────────────

    fun toggleTestMode() {
        val state = stateAccess.uiState.value
        val newTestMode = !state.cardSession.testMode
        stateAccess.updateState {
            it.copy(
                cardSession = it.cardSession.copy(testMode = newTestMode),
                elite = it.elite.copy(eliteUnlocked = callbacks.resolveEliteUnlocked(state.navigation.lessons, newTestMode))
            )
        }
        configStore.save(configStore.load().copy(testMode = newTestMode))
        Log.d(logTag, "Test mode toggled: $newTestMode")
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
        val profile = UserProfile(userName = trimmed)
        profileStore.save(profile)
        stateAccess.updateState {
            it.copy(navigation = it.navigation.copy(userName = trimmed))
        }
    }

    // ── Progress management ─────────────────────────────────────────────

    fun saveProgressNow() {
        Log.d(logTag, "Manual progress save requested from settings")
        callbacks.setForceBackup(true)
        callbacks.saveProgress()
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

    fun resetAllProgress(app: android.app.Application) {
        callbacks.resetStores(app)
        callbacks.resetDrillFiles(app)
        callbacks.clearWordMastery()
        callbacks.resetDailyState()

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

        callbacks.refreshLessons(null)
        Log.d(logTag, "All progress reset: mastery, daily, verb drill, vocab mastery, training progress")
    }

    /**
     * Reset progress for the current language/pack only.
     * Clears mastery, drill progress, daily state, and training session.
     * Other language packs are NOT affected.
     */
    fun resetLanguageProgress(app: android.app.Application, languageId: String, packId: String?) {
        callbacks.resetStoresForLanguage(app, languageId)

        if (packId != null) {
            callbacks.resetDrillFilesForPack(app, packId)
        }

        callbacks.clearWordMastery()
        callbacks.resetDailyState()

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

        callbacks.refreshLessons(null)
        Log.d(logTag, "Language progress reset for language=$languageId, pack=$packId: mastery, daily, verb drill, vocab mastery, training progress")
    }

    // ── Screen tracking ─────────────────────────────────────────────────

    fun onScreenChanged(screenName: String) {
        stateAccess.updateState { it.copy(navigation = it.navigation.copy(currentScreen = screenName)) }
    }
}

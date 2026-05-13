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
 * Handles settings-screen actions: config changes, profile updates,
 * progress reset, and backup creation.
 *
 * All state writes go through [TrainingStateAccess].
 * Cross-module coordination (daily session reset, progress tracker reset)
 * uses callbacks so this helper never holds references to other helpers.
 */
class SettingsActionHandler(
    private val stateAccess: TrainingStateAccess,
    private val configStore: AppConfigStore,
    private val profileStore: ProfileStore,
    private val backupManager: BackupManager,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    companion object {
        private const val logTag = "GrammarMate"
    }

    // ── Callbacks for cross-module orchestration ────────────────────────

    /** Resolve elite unlocked status from lessons and test mode. */
    var onResolveEliteUnlocked: ((List<com.alexpo.grammermate.data.Lesson>, Boolean) -> Boolean)? = null

    /** Refresh lessons to reflect changes (e.g., after reset). */
    var onRefreshLessons: ((String?) -> Unit)? = null

    /** Reset progress stores (mastery, drill, etc.). */
    var onResetStores: ((android.app.Application) -> Unit)? = null

    /** Reset drill files for all installed packs. */
    var onResetDrillFiles: ((android.app.Application) -> Unit)? = null

    /** Clear legacy word mastery store. */
    var onClearWordMastery: (() -> Unit)? = null

    /** Reset daily practice coordinator state. */
    var onResetDailyState: (() -> Unit)? = null

    /** Force backup flag setter. */
    var onSetForceBackup: ((Boolean) -> Unit)? = null

    /** Save progress. */
    var onSaveProgress: (() -> Unit)? = null

    // ── Config changes ──────────────────────────────────────────────────

    fun toggleTestMode() {
        val state = stateAccess.uiState.value
        val newTestMode = !state.cardSession.testMode
        stateAccess.updateState {
            it.copy(
                cardSession = it.cardSession.copy(testMode = newTestMode),
                elite = it.elite.copy(eliteUnlocked = onResolveEliteUnlocked?.invoke(state.navigation.lessons, newTestMode) ?: false)
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
        onSetForceBackup?.invoke(true)
        onSaveProgress?.invoke()
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
        onResetStores?.invoke(app)
        onResetDrillFiles?.invoke(app)
        onClearWordMastery?.invoke()
        onResetDailyState?.invoke()

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

        onRefreshLessons?.invoke(null)
        Log.d(logTag, "All progress reset: mastery, daily, verb drill, vocab mastery, training progress")
    }

    // ── Screen tracking ─────────────────────────────────────────────────

    fun onScreenChanged(screenName: String) {
        stateAccess.updateState { it.copy(navigation = it.navigation.copy(currentScreen = screenName)) }
    }
}

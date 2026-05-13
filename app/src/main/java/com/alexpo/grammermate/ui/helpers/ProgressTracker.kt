package com.alexpo.grammermate.ui.helpers

import android.util.Log
import com.alexpo.grammermate.data.DailyCursorState
import com.alexpo.grammermate.data.FlowerCalculator
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.MasteryStore
import com.alexpo.grammermate.data.PackId
import com.alexpo.grammermate.data.ProgressStore
import com.alexpo.grammermate.data.ScheduledSubLesson
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.TrainingProgress
import com.alexpo.grammermate.data.TrainingUiState

/**
 * Stateful module that wraps MasteryStore + ProgressStore operations.
 *
 * Single source of truth for all mastery tracking, progress persistence,
 * and lesson completion detection. The ViewModel delegates to this module
 * and applies results back to state.
 *
 * This module is primarily a **reader** of state (takes values as parameters)
 * and a **writer** to data stores. It does NOT directly update TrainingUiState.
 */
class ProgressTracker(
    private val stateAccess: TrainingStateAccess,
    private val masteryStore: MasteryStore,
    private val progressStore: ProgressStore,
    private val lessonStore: LessonStore
) {

    private val logTag = "GrammarMate"

    // ── Card show tracking ───────────────────────────────────────────

    /**
     * Record a card show for mastery tracking.
     * Skips if bossActive, isDrillMode, or inputMode == WORD_BANK.
     * WORD_BANK mode never counts for mastery (flower growth).
     */
    fun recordCardShowForMastery(
        card: SentenceCard,
        bossActive: Boolean,
        isDrillMode: Boolean,
        inputMode: InputMode,
        selectedLanguageId: LanguageId,
        lessons: List<Lesson>,
        selectedLessonId: LessonId?
    ) {
        // Boss battles do not count toward mastery/flower/SRS progress
        if (bossActive) return
        // Drill mode: pure card training, no mastery/flower progress
        if (isDrillMode) return

        val lessonId = resolveCardLessonId(card, selectedLessonId, lessons)
        val languageId = selectedLanguageId

        // Word Bank mode: does NOT count for mastery (flower growth)
        // Only voice and keyboard input count for skill formation
        if (inputMode == InputMode.WORD_BANK) {
            Log.d(logTag, "Skipping card show record for Word Bank mode - does not count for mastery")
            return
        }

        Log.d(logTag, "Recording card show: lessonId=${lessonId.value}, cardId=${card.id}, mode=$inputMode")
        masteryStore.recordCardShow(lessonId.value, languageId.value, card.id)
        val mastery = masteryStore.get(lessonId.value, languageId.value)
        Log.d(logTag, "After record: uniqueCardShows=${mastery?.uniqueCardShows}, totalShows=${mastery?.totalCardShows}")
    }

    /**
     * Mark sub-lesson cards as shown for progress tracking.
     * Only operates when inputMode == WORD_BANK (batch marking for
     * word-bank sessions that don't count for mastery but need tracking).
     */
    fun markSubLessonCardsShown(
        cards: List<SentenceCard>,
        inputMode: InputMode,
        selectedLessonId: LessonId?,
        selectedLanguageId: LanguageId,
        lessons: List<Lesson>
    ) {
        if (inputMode != InputMode.WORD_BANK || cards.isEmpty()) return
        val lessonId = selectedLessonId ?: return
        val lessonCardIds = lessons
            .firstOrNull { it.id == lessonId }
            ?.cards
            ?.map { it.id }
            ?.toSet()
            ?: return
        val cardIds = cards.map { it.id }.filter { lessonCardIds.contains(it) }
        masteryStore.markCardsShownForProgress(lessonId.value, selectedLanguageId.value, cardIds)
    }

    // ── Lesson completion ────────────────────────────────────────────

    /**
     * Check and mark a lesson as completed when completedSubLessonCount >= 15.
     * The 15-sublesson threshold represents the first full cycle through the lesson.
     */
    fun checkAndMarkLessonCompleted(
        completedSubLessonCount: Int,
        selectedLessonId: LessonId?,
        selectedLanguageId: LanguageId
    ) {
        val completedFirstCycle = completedSubLessonCount >= TrainingConfig.BOSS_UNLOCK_SUB_LESSONS
        if (completedFirstCycle && selectedLessonId != null) {
            masteryStore.markLessonCompleted(selectedLessonId.value, selectedLanguageId.value)
        }
    }

    /**
     * Count how many sub-lessons are fully completed based on shown card IDs.
     * A sub-lesson is considered completed when every card from the main pool
     * in that sub-lesson has been shown at least once.
     *
     * Sequential: stops counting at the first incomplete sub-lesson.
     */
    fun calculateCompletedSubLessons(
        subLessons: List<ScheduledSubLesson>,
        mastery: LessonMasteryState?,
        lessonId: LessonId?,
        lessons: List<Lesson>
    ): Int {
        if (lessonId == null || mastery == null || mastery.shownCardIds.isEmpty()) return 0

        val lessonCardIds = lessons
            .firstOrNull { it.id == lessonId }
            ?.cards
            ?.map { it.id }
            ?.toSet()
            ?: return 0

        var completed = 0
        for (subLesson in subLessons) {
            val allCardsShown = subLesson.cards.all { card ->
                !lessonCardIds.contains(card.id) || mastery.shownCardIds.contains(card.id)
            }
            if (allCardsShown) {
                completed++
            } else {
                // Stop at first incomplete sub-lesson
                break
            }
        }
        return completed
    }

    /**
     * Resolve which lesson a card belongs to.
     * Prefers the currently selected lesson if it contains the card,
     * then searches all lessons. Falls back to selectedLessonId or "unknown".
     */
    fun resolveCardLessonId(
        card: SentenceCard,
        selectedLessonId: LessonId?,
        lessons: List<Lesson>
    ): LessonId {
        // Prefer the currently selected lesson if it contains this card
        if (selectedLessonId != null) {
            val selectedLesson = lessons.find { it.id == selectedLessonId }
            if (selectedLesson != null && selectedLesson.cards.any { it.id == card.id }) {
                return selectedLessonId
            }
        }
        return lessons
            .find { lesson -> lesson.cards.any { it.id == card.id } }
            ?.id
            ?: selectedLessonId
            ?: LessonId("unknown")
    }

    // ── Progress-based lesson info ───────────────────────────────────

    /**
     * Resolve the user's actual progress lesson based on mastery data.
     * Independent of selectedLessonId -- uses the flower/mastery state
     * to find the highest lesson with any progress, then returns the
     * NEXT lesson (the one the user should be practicing).
     *
     * Falls back to the first lesson if no progress exists.
     * Returns (lessonId, lessonLevel) where lessonLevel is 1-based.
     */
    fun resolveProgressLessonInfo(
        activePackId: PackId?,
        selectedLanguageId: LanguageId,
        activePackLessonIds: List<String>?,
        lessons: List<Lesson>,
        dailyCursor: DailyCursorState
    ): Pair<String, Int>? {
        activePackId ?: return null
        val langId = selectedLanguageId
        val packLessonIds = activePackLessonIds ?: return null
        if (packLessonIds.isEmpty()) return null

        val orderedLessons = packLessonIds.mapNotNull { id -> lessons.firstOrNull { it.id.value == id } }
        if (orderedLessons.isEmpty()) return null

        // Find the highest lesson with any progress (mastery > 0)
        var lastLessonWithProgress = -1
        for (i in orderedLessons.indices) {
            val mastery = masteryStore.get(orderedLessons[i].id.value, langId.value)
            val flower = FlowerCalculator.calculate(mastery, orderedLessons[i].cards.size)
            if (flower.masteryPercent > 0f) {
                lastLessonWithProgress = i
            }
        }

        // The "current" lesson for daily practice:
        // - If no progress: first lesson (index 0)
        // - If progress exists: next lesson after the last with progress
        //   BUT if the cursor has already advanced past it, use cursor position
        val cursorLessonIndex = dailyCursor.currentLessonIndex
        val progressIndex = if (lastLessonWithProgress < 0) {
            0
        } else {
            val nextUnlocked = (lastLessonWithProgress + 1).coerceAtMost(orderedLessons.size - 1)
            maxOf(nextUnlocked, cursorLessonIndex).coerceAtMost(orderedLessons.size - 1)
        }

        val lesson = orderedLessons.getOrNull(progressIndex) ?: return null
        val level = (progressIndex + 1).coerceIn(1, 12)
        return lesson.id.value to level
    }

    /**
     * Public helper to get the progress-based lesson level.
     * Delegates to resolveProgressLessonInfo, returns level or 1.
     */
    fun getProgressLessonLevel(
        activePackId: PackId?,
        selectedLanguageId: LanguageId,
        activePackLessonIds: List<String>?,
        lessons: List<Lesson>,
        dailyCursor: DailyCursorState
    ): Int {
        return resolveProgressLessonInfo(
            activePackId, selectedLanguageId, activePackLessonIds,
            lessons, dailyCursor
        )?.second ?: 1
    }

    // ── Persistence ──────────────────────────────────────────────────

    /**
     * Serialize state to ProgressStore.
     * Skips if bossActive (unless ELITE type).
     * Returns true if backup should be triggered (forceBackup = true),
     * so the ViewModel can call createProgressBackup().
     *
     * @param state current UI state snapshot
     * @param forceBackup if true, triggers a backup after saving
     * @param normalizedEliteSpeeds pre-normalized elite speed list
     * @return true if backup should be triggered by the ViewModel
     */
    fun saveProgress(
        state: TrainingUiState,
        forceBackup: Boolean,
        normalizedEliteSpeeds: List<Double>
    ): Boolean {
        if (state.boss.bossActive && state.boss.bossType != com.alexpo.grammermate.data.BossType.ELITE) {
            return false
        }
        progressStore.save(
            TrainingProgress(
                languageId = state.navigation.selectedLanguageId,
                mode = state.navigation.mode,
                lessonId = state.navigation.selectedLessonId?.value,
                currentIndex = state.cardSession.currentIndex,
                correctCount = state.cardSession.correctCount,
                incorrectCount = state.cardSession.incorrectCount,
                incorrectAttemptsForCard = state.cardSession.incorrectAttemptsForCard,
                activeTimeMs = state.cardSession.activeTimeMs,
                state = state.cardSession.sessionState,
                bossLessonRewards = state.boss.bossLessonRewards.mapValues { it.value.name },
                bossMegaReward = null,
                bossMegaRewards = state.boss.bossMegaRewards.mapValues { it.value.name },
                voiceActiveMs = state.cardSession.voiceActiveMs,
                voiceWordCount = state.cardSession.voiceWordCount,
                hintCount = state.cardSession.hintCount,
                eliteStepIndex = state.elite.eliteStepIndex,
                eliteBestSpeeds = normalizedEliteSpeeds,
                currentScreen = state.navigation.currentScreen,
                activePackId = state.navigation.activePackId,
                dailyLevel = state.daily.dailySession.level,
                dailyTaskIndex = state.daily.dailySession.taskIndex,
                dailyCursor = state.daily.dailyCursor
            )
        )

        return if (forceBackup) {
            // Signal to ViewModel that backup should be created
            true
        } else {
            false
        }
    }

    // ── Cursor ───────────────────────────────────────────────────────

    /**
     * Advance the daily cursor offsets after a session is built.
     * If the sentence offset exceeds the current lesson's card count,
     * advances currentLessonIndex and resets sentenceOffset.
     *
     * @return the updated cursor state (caller must apply to state)
     */
    fun advanceCursor(
        currentCursor: DailyCursorState,
        sentenceCount: Int,
        selectedLanguageId: LanguageId
    ): DailyCursorState {
        val newOffset = currentCursor.sentenceOffset + sentenceCount
        val lessons = lessonStore.getLessons(selectedLanguageId.value)
        val currentLesson = lessons.getOrNull(currentCursor.currentLessonIndex)
        val lessonSize = currentLesson?.cards?.size ?: 0

        return if (lessonSize > 0 && newOffset >= lessonSize) {
            // Current lesson exhausted -- advance to next lesson, reset offset
            val nextIndex = (currentCursor.currentLessonIndex + 1).coerceAtMost(lessons.size - 1)
            currentCursor.copy(
                currentLessonIndex = nextIndex,
                sentenceOffset = 0
            )
        } else {
            currentCursor.copy(sentenceOffset = newOffset)
        }
    }

    /**
     * Store the first session of the day's card IDs in the cursor state.
     * This allows Repeat to reconstruct the exact same cards even after restart.
     *
     * @return the updated cursor state (caller must apply to state)
     */
    fun storeFirstSessionCardIds(
        currentCursor: DailyCursorState,
        sentenceIds: List<String>,
        verbIds: List<String>
    ): DailyCursorState {
        val today = java.time.LocalDate.now().toString()
        return currentCursor.copy(
            firstSessionDate = today,
            firstSessionSentenceCardIds = sentenceIds,
            firstSessionVerbCardIds = verbIds
        )
    }

    // ── Reset ────────────────────────────────────────────────────────

    /**
     * Clear mastery and progress stores. Does NOT update UI state --
     * the ViewModel is responsible for resetting TrainingUiState fields.
     *
     * @param context application context for file operations (unused, kept for API consistency)
     */
    @Suppress("UNUSED_PARAMETER")
    fun resetStores(context: android.content.Context) {
        progressStore.clear()
        masteryStore.clear()
    }

    /**
     * Clear drill-related files for all installed packs.
     */
    fun resetDrillFiles(context: android.content.Context) {
        val packs = lessonStore.getInstalledPacks()
        val baseDir = java.io.File(context.filesDir, "grammarmate")
        for (pack in packs) {
            val verbDrillFile = java.io.File(baseDir, "drills/${pack.packId}/verb_drill_progress.yaml")
            if (verbDrillFile.exists()) verbDrillFile.delete()

            val wordMasteryFile = java.io.File(baseDir, "drills/${pack.packId}/word_mastery.yaml")
            if (wordMasteryFile.exists()) wordMasteryFile.delete()
        }
    }
}

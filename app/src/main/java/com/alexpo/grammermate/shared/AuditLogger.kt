package com.alexpo.grammermate.shared

import android.content.Context
import android.util.Log
import com.alexpo.grammermate.data.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * File-persistent audit logger for GrammarMate.
 *
 * Records every user action (card shown, answer submitted, navigation, settings change, etc.)
 * to a rotating file on device. Each line includes full context:
 *
 *     pack=ITALIAN_EXPRESS lesson=lesson_07_A07 sub=2/4 idx=5/10 card=card_10 ru="Ты врач" it="Sei dottore"
 *
 * Extract from device:
 *     adb pull /data/data/com.alexpo.grammermate/files/grammarmate/audit.log ./audit.log
 */
class AuditLogger private constructor(
    private val logFile: File,
    private val maxFileSizeBytes: Long = 1_048_576L // 1 MB
) {
    companion object {
        private const val TAG = "AuditLog"

        @Volatile
        private var instance: AuditLogger? = null

        fun initialize(context: Context) {
            val baseDir = File(context.filesDir, "grammarmate")
            if (!baseDir.exists()) baseDir.mkdirs()
            instance = AuditLogger(File(baseDir, "audit.log"))
        }

        fun getInstance(): AuditLogger =
            instance ?: throw IllegalStateException("AuditLogger not initialized. Call initialize(context) first.")

        /** Safe accessor that returns null if not initialized. */
        fun getInstanceOrNull(): AuditLogger? = instance
    }

    // ── Session ──────────────────────────────────────────────────────

    var sessionId: String = ""
        private set

    private val seq = AtomicInteger(0)
    private val startMs: Long = System.currentTimeMillis()

    // ── Context (updated from TrainingUiState) ───────────────────────

    data class AuditContext(
        val pack: String = "",
        val lesson: String = "",
        val subIdx: Int = 0,
        val subTotal: Int = 0,
        val cardIdx: Int = 0,
        val cardTotal: Int = 0
    )

    @Volatile
    private var currentContext = AuditContext()

    fun updateContext(state: TrainingUiState) {
        val nav = state.navigation
        val cs = state.cardSession
        currentContext = AuditContext(
            pack = nav.activePackId?.value ?: "",
            lesson = nav.selectedLessonId?.value ?: "",
            subIdx = cs.activeSubLessonIndex,
            subTotal = cs.subLessonCount,
            cardIdx = cs.currentIndex,
            cardTotal = cs.subLessonTotal
        )
    }

    fun updateContext(pack: String, lesson: String = "") {
        currentContext = currentContext.copy(pack = pack, lesson = lesson)
    }

    fun updateContext(pack: String, lesson: String, subIdx: Int, subTotal: Int, cardIdx: Int, cardTotal: Int) {
        currentContext = AuditContext(pack, lesson, subIdx, subTotal, cardIdx, cardTotal)
    }

    // ── Buffer and writer ────────────────────────────────────────────

    private val buffer = ConcurrentLinkedQueue<String>()
    private var writerJob: Job? = null
    private var scope: CoroutineScope? = null

    fun startSession(appVersion: String = "") {
        sessionId = UUID.randomUUID().toString().take(8)
        seq.set(0)
        emit("APP_START", "appVersion" to appVersion)
    }

    fun endSession() {
        val duration = System.currentTimeMillis() - startMs
        emit("APP_END", "durationMs" to duration)
        flush()
    }

    /** Start the async writer coroutine. Call from ViewModel init. */
    fun startWriter(coroutineScope: CoroutineScope) {
        scope = coroutineScope
        writerJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(500)
                flushBuffer()
            }
        }
    }

    /** Stop the writer. Call from ViewModel onCleared. */
    fun stopWriter() {
        flush()
        writerJob?.cancel()
        writerJob = null
        scope = null
    }

    /** Synchronous flush — drains buffer to file. */
    fun flush() {
        flushBuffer()
    }

    // ── Core emit ────────────────────────────────────────────────────

    private fun emit(event: String, vararg pairs: Pair<String, Any?>) {
        val n = seq.incrementAndGet()
        val ts = System.currentTimeMillis()
        val ctx = currentContext
        val line = buildString {
            append("ts=$ts sid=$sessionId seq=$n evt=$event")
            if (ctx.pack.isNotEmpty() || ctx.lesson.isNotEmpty()) {
                append(" pack=\"${ctx.pack}\" lesson=\"${ctx.lesson}\"")
                append(" sub=${ctx.subIdx}/${ctx.subTotal} idx=${ctx.cardIdx}/${ctx.cardTotal}")
            }
            for ((k, v) in pairs) {
                if (v != null) {
                    val escaped = v.toString().replace("\"", "\\\"")
                    append(" $k=\"$escaped\"")
                }
            }
        }
        buffer.add(line)
        // Also to Logcat for real-time debugging
        Log.d(TAG, line.take(500))
    }

    // ── File operations ──────────────────────────────────────────────

    private fun flushBuffer() {
        val lines = mutableListOf<String>()
        while (true) {
            val line = buffer.poll() ?: break
            lines.add(line)
        }
        if (lines.isEmpty()) return

        try {
            val append = logFile.exists() && logFile.length() < maxFileSizeBytes
            if (append) {
                logFile.appendText(lines.joinToString("\n", postfix = "\n"))
            } else {
                // Rotation: keep last half of existing + new lines
                val existing = if (logFile.exists()) logFile.readLines() else emptyList()
                val keepCount = existing.size / 2
                val kept = existing.takeLast(keepCount)
                val all = (kept + lines).joinToString("\n", postfix = "\n")
                logFile.writeText(all)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flush audit log", e)
        }
    }

    /** Clear the audit log file. */
    fun clear() {
        buffer.clear()
        if (logFile.exists()) logFile.writeText("")
    }

    // ════════════════════════════════════════════════════════════════════
    //  TYPED EVENT HELPERS
    // ════════════════════════════════════════════════════════════════════

    // ── Navigation ───────────────────────────────────────────────────

    fun screenOpen(from: String, to: String, trigger: String = "") {
        emit("SCREEN_OPEN", "from" to from, "to" to to, "trigger" to trigger)
    }

    fun screenClose(screen: String, reason: String = "") {
        emit("SCREEN_CLOSE", "screen" to screen, "reason" to reason)
    }

    fun backPress(screen: String, action: String = "") {
        emit("BACK_PRESS", "screen" to screen, "action" to action)
    }

    fun packSelect(packId: String, packName: String = "") {
        updateContext(pack = packId)
        emit("PACK_SELECT", "packId" to packId, "packName" to packName)
    }

    fun langSelect(langId: String, langName: String = "", zeroState: Boolean = false) {
        emit("LANG_SELECT", "langId" to langId, "langName" to langName, "zeroState" to zeroState)
    }

    fun lessonSelect(lessonId: String, chapterId: String = "") {
        updateContext(pack = currentContext.pack, lesson = lessonId)
        emit("LESSON_SELECT", "lessonId" to lessonId, "chapterId" to chapterId)
    }

    fun chapterSelect(chapterId: String) {
        emit("CHAPTER_SELECT", "chapterId" to chapterId)
    }

    // ── Session lifecycle ────────────────────────────────────────────

    fun sessionStart(lessonId: String, mode: String, subLessonCount: Int, totalCards: Int, screenMode: String) {
        emit("SESSION_START",
            "lessonId" to lessonId, "mode" to mode,
            "subLessonCount" to subLessonCount, "totalCards" to totalCards,
            "screenMode" to screenMode)
    }

    fun sessionEnd(rating: Double, correct: Int, incorrect: Int, durationMs: Long, hintCount: Int) {
        emit("SESSION_END",
            "rating" to rating, "correct" to correct, "incorrect" to incorrect,
            "durationMs" to durationMs, "hintCount" to hintCount)
    }

    fun sessionPause(activeTimeMs: Long) {
        emit("SESSION_PAUSE", "activeTimeMs" to activeTimeMs)
    }

    fun sessionResume(activeTimeMs: Long) {
        emit("SESSION_RESUME", "activeTimeMs" to activeTimeMs)
    }

    // ── Card events ──────────────────────────────────────────────────

    fun cardShown(cardId: String, ru: String, it: String, idx: Int, total: Int, subIdx: Int, subTotal: Int, mode: String) {
        currentContext = currentContext.copy(cardIdx = idx, cardTotal = total, subIdx = subIdx, subTotal = subTotal)
        emit("CARD_SHOWN", "card" to cardId, "ru" to ru, "it" to it, "mode" to mode)
    }

    fun cardNav(direction: String, fromIdx: Int, toIdx: Int, trigger: String = "") {
        currentContext = currentContext.copy(cardIdx = toIdx)
        emit("CARD_NAV", "dir" to direction, "fromIdx" to fromIdx, "toIdx" to toIdx, "trigger" to trigger)
    }

    fun answerCorrect(cardId: String, input: String, normalizedInput: String, ru: String, it: String, mode: String, attempts: Int) {
        emit("ANSWER_CORRECT",
            "card" to cardId, "input" to input, "normalized" to normalizedInput,
            "ru" to ru, "it" to it, "mode" to mode, "attempts" to attempts)
    }

    fun answerWrong(cardId: String, input: String, normalizedInput: String, ru: String, it: String, mode: String, attempts: Int) {
        emit("ANSWER_WRONG",
            "card" to cardId, "input" to input, "normalized" to normalizedInput,
            "ru" to ru, "it" to it, "mode" to mode, "attempts" to attempts)
    }

    fun answerDedup(cardId: String) {
        emit("ANSWER_DEDUP", "card" to cardId)
    }

    fun hintManual(cardId: String, ru: String, it: String, hintCount: Int) {
        emit("HINT_MANUAL", "card" to cardId, "ru" to ru, "it" to it, "hintCount" to hintCount)
    }

    fun hintAuto(cardId: String, ru: String, it: String, attempts: Int, hintCount: Int) {
        emit("HINT_AUTO", "card" to cardId, "ru" to ru, "it" to it, "attempts" to attempts, "hintCount" to hintCount)
    }

    fun inputModeChange(from: String, to: String, cardId: String) {
        emit("INPUT_MODE_CHANGE", "from" to from, "to" to to, "card" to cardId)
    }

    fun wordBankSelect(word: String, selectedCount: Int, cardId: String) {
        emit("WORD_BANK_SELECT", "word" to word, "count" to selectedCount, "card" to cardId)
    }

    fun wordBankRemove(remainingCount: Int, cardId: String) {
        emit("WORD_BANK_REMOVE", "remaining" to remainingCount, "card" to cardId)
    }

    fun subLessonComplete(subIdx: Int, completedCount: Int, totalSub: Int) {
        emit("SUB_LESSON_COMPLETE", "subIdx" to subIdx, "completedCount" to completedCount, "totalSub" to totalSub)
    }

    fun subLessonTransition(fromSub: Int, toSub: Int, totalSub: Int, fromIdx: Int, toIdx: Int) {
        currentContext = currentContext.copy(subIdx = toSub, cardIdx = toIdx)
        emit("SUB_LESSON_TRANSITION",
            "fromSub" to fromSub, "toSub" to toSub, "totalSub" to totalSub,
            "fromIdx" to fromIdx, "toIdx" to toIdx)
    }

    fun lessonComplete(lessonId: String, uniqueShows: Int, totalCards: Int) {
        emit("LESSON_COMPLETE", "lessonId" to lessonId, "uniqueShows" to uniqueShows, "totalCards" to totalCards)
    }

    fun cardsReplaced(newCount: Int, reason: String) {
        emit("CARDS_REPLACED", "newCount" to newCount, "reason" to reason)
    }

    fun reviewStart(lessonId: String, hintLevel: String) {
        emit("REVIEW_START", "lessonId" to lessonId, "hintLevel" to hintLevel)
    }

    // ── Boss / Elite ─────────────────────────────────────────────────

    fun bossStart(type: String, lessonId: String) {
        emit("BOSS_START", "type" to type, "lessonId" to lessonId)
    }

    fun bossFinish(type: String, result: String, lessonId: String) {
        emit("BOSS_FINISH", "type" to type, "result" to result, "lessonId" to lessonId)
    }

    fun eliteStepStart(stepIndex: Int, totalSteps: Int, cardCount: Int) {
        emit("ELITE_STEP_START", "step" to stepIndex, "totalSteps" to totalSteps, "cards" to cardCount)
    }

    fun eliteStepFinish(stepIndex: Int, speed: Double, bestSpeed: Double, cardsCount: Int) {
        emit("ELITE_STEP_FINISH", "step" to stepIndex, "speed" to speed, "bestSpeed" to bestSpeed, "cards" to cardsCount)
    }

    // ── Pomodoro ─────────────────────────────────────────────────────

    fun pomodoroStart(durationMin: Int, lessonId: String) {
        emit("POMODORO_START", "durationMin" to durationMin, "lessonId" to lessonId)
    }

    fun pomodoroPause(elapsedMs: Long) {
        emit("POMODORO_PAUSE", "elapsedMs" to elapsedMs)
    }

    fun pomodoroResume(elapsedMs: Long) {
        emit("POMODORO_RESUME", "elapsedMs" to elapsedMs)
    }

    fun pomodoroComplete(durationMin: Int, cardsShown: Int, correct: Int, incorrect: Int) {
        emit("POMODORO_COMPLETE", "durationMin" to durationMin, "shown" to cardsShown, "correct" to correct, "incorrect" to incorrect)
    }

    // ── Daily practice ───────────────────────────────────────────────

    fun dailyStart(lessonId: String, level: Int) {
        emit("DAILY_START", "lessonId" to lessonId, "level" to level)
    }

    fun dailyBlockStart(blockType: String) {
        emit("DAILY_BLOCK_START", "blockType" to blockType)
    }

    fun dailyBlockComplete(blockType: String, cardCount: Int) {
        emit("DAILY_BLOCK_COMPLETE", "blockType" to blockType, "cards" to cardCount)
    }

    fun dailyExit(sentenceCount: Int? = null) {
        emit("DAILY_EXIT", "sentenceCount" to sentenceCount)
    }

    // ── Drills ───────────────────────────────────────────────────────

    fun verbDrillStart(cardCount: Int, tense: String = "") {
        emit("VERB_DRILL_START", "cards" to cardCount, "tense" to tense)
    }

    fun verbDrillMore(newCardCount: Int) {
        emit("VERB_DRILL_MORE", "newCards" to newCardCount)
    }

    fun vocabDrillStart(direction: String, pos: String = "", rankRange: String = "") {
        emit("VOCAB_DRILL_START", "direction" to direction, "pos" to pos, "rankRange" to rankRange)
    }

    fun vocabCardFlip(cardId: String, direction: String) {
        emit("VOCAB_FLIP", "card" to cardId, "direction" to direction)
    }

    fun vocabCardRate(cardId: String, rating: Int) {
        emit("VOCAB_RATE", "card" to cardId, "rating" to rating)
    }

    // ── UI events ────────────────────────────────────────────────────

    fun settingsOpen(fromScreen: String) {
        emit("SETTINGS_OPEN", "from" to fromScreen)
    }

    fun settingsChange(key: String, value: String) {
        emit("SETTINGS_CHANGE", "key" to key, "value" to value)
    }

    fun storyRead(chapterId: String, chapterTitle: String) {
        emit("STORY_READ", "chapterId" to chapterId, "title" to chapterTitle)
    }

    fun storyControl(action: String, chapterId: String, positionMs: Long? = null) {
        emit("STORY_$action", "chapterId" to chapterId, "positionMs" to positionMs)
    }

    fun dialogOpen(dialogName: String, fromScreen: String = "") {
        emit("DIALOG_OPEN", "dialog" to dialogName, "from" to fromScreen)
    }

    fun dialogClose(dialogName: String, action: String) {
        emit("DIALOG_CLOSE", "dialog" to dialogName, "action" to action)
    }

    fun dialogAction(dialogName: String, action: String) {
        emit("DIALOG_ACTION", "dialog" to dialogName, "action" to action)
    }

    fun ttsSpeak(cardId: String, text: String, source: String) {
        emit("TTS_SPEAK", "card" to cardId, "text" to text.take(80), "source" to source)
    }

    fun voiceStart(language: String, autoStart: Boolean) {
        emit("VOICE_START", "language" to language, "autoStart" to autoStart)
    }

    fun voiceResult(text: String) {
        emit("VOICE_RESULT", "text" to text)
    }

    fun badSentenceFlag(cardId: String, ru: String, it: String) {
        emit("BAD_SENTENCE_FLAG", "card" to cardId, "ru" to ru, "it" to it)
    }

    fun cardHidden(cardId: String) {
        emit("CARD_HIDDEN", "card" to cardId)
    }

    fun grammarChipClick(chipKey: String, lessonId: String) {
        emit("GRAMMAR_CHIP_CLICK", "chipKey" to chipKey, "lessonId" to lessonId)
    }

    fun reportSheetAction(action: String, cardId: String = "") {
        emit("REPORT_SHEET", "action" to action, "card" to cardId)
    }

    fun pomodoroSelector(durationMin: Int, source: String) {
        emit("POMODORO_SELECTOR", "durationMin" to durationMin, "source" to source)
    }

    fun profileOpen() {
        emit("PROFILE_OPEN")
    }

    fun methodInfo() {
        emit("METHOD_INFO")
    }
}

package com.alexpo.grammermate.shared

import android.util.Log
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Structured navigation/session logger for GrammarMate.
 *
 * All log entries share tag [TAG] and include a short [sessionId] so you can
 * filter a single user session in Logcat:
 *
 *     adb logcat -s ScreenFlow | grep "abc123"
 *
 * Entry format:
 *     [sessionId] KIND: details | key=value ...
 *
 * Kinds:
 *   SESSION_START / SESSION_END   — app lifecycle
 *   SHOWN                         — screen / overlay became visible
 *   NAV                           — NavController route change
 *   TAP                           — user tap / action
 *   OVERLAY                       — dialog / bottom-sheet open/close
 *   STATE                         — notable state change (pack select, language, etc.)
 */
object ScreenLogger {

    private const val TAG = "ScreenFlow"

    /** Short session ID for log correlation (first 8 chars of UUID). */
    var sessionId: String = ""
        private set

    /** Monotonic event counter within a session. */
    private val eventCounter = AtomicInteger(0)

    /** Screen display history for the current session (for debugging). */
    private val screenHistory = mutableListOf<String>()

    /** Full event log (capped at [MAX_HISTORY] entries). */
    private const val MAX_HISTORY = 500
    private val eventLog = mutableListOf<String>()

    // ── Session lifecycle ──────────────────────────────────────────────

    /** Start a new session. Call once on app launch. */
    fun startSession(appVersion: String = "") {
        sessionId = UUID.randomUUID().toString().take(8)
        eventCounter.set(0)
        screenHistory.clear()
        eventLog.clear()
        emit("SESSION_START", "appVersion=$appVersion")
    }

    /** End the current session. Call on Activity.onDestroy or process exit. */
    fun endSession() {
        emit("SESSION_END", "screens=${screenHistory.size}, events=${eventCounter.get()}")
    }

    // ── Screen shown ───────────────────────────────────────────────────

    /** Log that a screen became visible. */
    fun screenShown(screen: String, details: String = "") {
        screenHistory.add(screen)
        val detailPart = if (details.isNotBlank()) " | $details" else ""
        emit("SHOWN", "$screen detail=history[${screenHistory.size}]$detailPart")
    }

    // ── Navigation ─────────────────────────────────────────────────────

    /** Log a route navigation event. */
    fun nav(from: String, to: String, trigger: String = "", details: String = "") {
        val parts = mutableListOf<String>()
        parts.add("$from → $to")
        if (trigger.isNotBlank()) parts.add("trigger=$trigger")
        if (details.isNotBlank()) parts.add(details)
        emit("NAV", parts.joinToString(" | "))
    }

    // ── User taps / actions ────────────────────────────────────────────

    /** Log a user tap or action. */
    fun tap(action: String, details: String = "") {
        val detailPart = if (details.isNotBlank()) " | $details" else ""
        emit("TAP", "$action$detailPart")
    }

    // ── Overlays (dialogs, bottom sheets) ──────────────────────────────

    /** Log an overlay (dialog/sheet) being shown or hidden. */
    fun overlay(name: String, shown: Boolean, details: String = "") {
        val detailPart = if (details.isNotBlank()) " | $details" else ""
        emit("OVERLAY", "$name shown=$shown$detailPart")
    }

    // ── State changes ──────────────────────────────────────────────────

    /** Log a notable state change. */
    fun state(change: String, details: String = "") {
        val detailPart = if (details.isNotBlank()) " | $details" else ""
        emit("STATE", "$change$detailPart")
    }

    // ── Query ──────────────────────────────────────────────────────────

    /** Get the current screen history for debugging. */
    fun getScreenHistory(): List<String> = screenHistory.toList()

    /** Get the full event log for debugging. */
    fun getEventLog(): List<String> = eventLog.toList()

    // ── Internal ───────────────────────────────────────────────────────

    private fun emit(kind: String, message: String) {
        val seq = eventCounter.incrementAndGet()
        val line = "[$sessionId] #$seq $kind: $message"
        Log.d(TAG, line)

        synchronized(eventLog) {
            if (eventLog.size >= MAX_HISTORY) {
                eventLog.removeAt(0)
            }
            eventLog.add(line)
        }
    }
}

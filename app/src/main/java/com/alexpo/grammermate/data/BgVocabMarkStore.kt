package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Per-word mark for the background-vocab deck.
 *
 * - [GREEN]: the listener knows this word — exclude it from background playback
 *   (the DeckPlayer skips GREEN words).
 * - [RED]: the word is hard / worth revisiting — added to the "hard words" list
 *   the user can open from the background-vocab screen.
 * - [NONE]: default, no action.
 *
 * Persisted as JSON (`bg_vocab_marks.json` in filesDir), keyed by the Italian
 * word. Writes use the temp → rename pattern so a crash mid-write can't corrupt
 * the store. Only non-NONE marks are stored.
 */
enum class BgVocabMark { NONE, GREEN, RED }

class BgVocabMarkStore(private val context: Context) {

    private val file: File get() = File(context.filesDir, "bg_vocab_marks.json")
    private val marks = mutableMapOf<String, BgVocabMark>()
    private val lock = Any()

    init { load() }

    fun getMark(word: String): BgVocabMark = synchronized(lock) {
        marks[word] ?: BgVocabMark.NONE
    }

    fun setMark(word: String, mark: BgVocabMark) = synchronized(lock) {
        if (mark == BgVocabMark.NONE) marks.remove(word.lowercase()) else marks[word.lowercase()] = mark
        save()
    }

    /** All non-NONE marks (word -> mark). */
    fun allMarked(): Map<String, BgVocabMark> = synchronized(lock) { marks.toMap() }

    /** Words marked RED (the "hard words" list). */
    fun redWords(): List<String> = synchronized(lock) {
        marks.filter { it.value == BgVocabMark.RED }.keys.toList()
    }

    /** Count of GREEN-marked (excluded) words. */
    fun greenCount(): Int = synchronized(lock) { marks.count { it.value == BgVocabMark.GREEN } }

    /**
     * Remove all GREEN marks (the "easy/known" words) so they are included in
     * background playback again. Returns how many were cleared. Safe to call from
     * the settings UI; the DeckPlayer reads marks live, so the change takes effect
     * on the next word without a restart.
     */
    fun clearGreen(): Int = synchronized(lock) {
        val before = marks.size
        val it = marks.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value == BgVocabMark.GREEN) it.remove()
        }
        val removed = before - marks.size
        if (removed > 0) save()
        Log.d(TAG, "clearGreen: removed $removed GREEN marks, ${marks.size} remain")
        removed
    }

    private fun load() = synchronized(lock) {
        try {
            val f = file
            if (!f.exists() || f.length() == 0L) return@synchronized
            val obj = JSONObject(f.readText())
            val out = mutableMapOf<String, BgVocabMark>()
            for (key in obj.keys()) {
                val m = when (obj.optString(key)) {
                    "GREEN" -> BgVocabMark.GREEN
                    "RED" -> BgVocabMark.RED
                    else -> null
                }
                if (m != null) out[key.lowercase()] = m
            }
            marks.clear()
            marks.putAll(out)
            Log.d(TAG, "Loaded ${marks.size} bg-vocab marks")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load bg-vocab marks: ${e.message}")
        }
    }

    private fun save() {
        // Caller holds `lock`.
        try {
            val obj = JSONObject()
            for ((w, m) in marks) obj.put(w, m.name)
            val tmp = File(file.parentFile, "bg_vocab_marks.json.tmp")
            tmp.writeText(obj.toString())
            // fsync-ish: rename onto the target (atomic on the same filesystem).
            if (!tmp.renameTo(file)) {
                // Fallback: copy bytes if rename failed (cross-device).
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save bg-vocab marks: ${e.message}")
        }
    }

    companion object { private const val TAG = "BgVocabMarkStore" }
}

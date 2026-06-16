package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Persists the last-played background-vocab word so the deck resumes where the
 * user left off after the app is closed/reopened (instead of restarting at 0).
 *
 * Keyed by the Italian word (`wordIt`) — NOT an index — so it stays valid when
 * the deck is re-sorted or grows (new words inserted by the content workflow).
 * On load, the DeckPlayer finds the saved word's current index and jumps there;
 * if the word is no longer present, it falls back to index 0.
 *
 * Persisted as `bg_vocab_position.json` in filesDir; writes use temp → rename.
 * Writes are cheap (one tiny file per word-advance) and happen on the playback
 * coroutine, never blocking the UI.
 */
class BgVocabPositionStore(private val context: Context) {

    private val file: File get() = File(context.filesDir, "bg_vocab_position.json")
    private val lock = Any()

    fun getLastWord(): String? = synchronized(lock) {
        try {
            val f = file
            if (!f.exists() || f.length() == 0L) return@synchronized null
            val obj = JSONObject(f.readText())
            obj.optString("word").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "load failed: ${e.message}")
            null
        }
    }

    fun setLastWord(word: String?) {
        synchronized(lock) {
            try {
                val obj = JSONObject()
                if (!word.isNullOrBlank()) obj.put("word", word)
                val tmp = File(file.parentFile, "bg_vocab_position.json.tmp")
                tmp.writeText(obj.toString())
                if (!tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
                Unit
            } catch (e: Exception) {
                Log.w(TAG, "save failed: ${e.message}")
            }
        }
    }

    companion object { private const val TAG = "BgVocabPositionStore" }
}

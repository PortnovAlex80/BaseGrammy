package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Store for pack-scoped daily cursor state.
 * Each pack maintains its own cursor position to prevent cross-pack contamination.
 *
 * Used by TASK-080: State isolation bug fix.
 * Follows VerbDrillStore pattern for consistency.
 */
interface PackDailyCursorStore {

    /** Load cursor state for a specific pack. Returns null if not found. */
    fun loadPackCursor(packId: String): PackDailyCursorState?

    /** Save cursor state for a specific pack. */
    fun savePackCursor(cursor: PackDailyCursorState)

    /** Delete cursor state for a specific pack. */
    fun deletePackCursor(packId: String)

    /** Load all pack cursor states. Returns map of packId -> cursor state. */
    fun loadAllPackCursors(): Map<String, PackDailyCursorState>

    /** Flush any pending writes to disk immediately. */
    fun flush()
}

class PackDailyCursorStoreImpl(context: Context) : PackDailyCursorStore {

    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val schemaVersion = 1
    private val mutex = ReentrantLock()

    // In-memory cache for cursor data — invalidated on save
    private var cursorCache: MutableMap<String, PackDailyCursorState>? = null

    private fun getFileForPack(packId: String): File {
        return File(baseDir, "daily_cursor_$packId.yaml")
    }

    override fun loadPackCursor(packId: String): PackDailyCursorState? {
        return mutex.withLock {
            // Check cache first
            cursorCache?.get(packId)?.let { return@withLock it }

            val file = getFileForPack(packId)
            if (!file.exists()) {
                Log.d("PackDailyCursorStore", "No cursor file found for pack: $packId")
                return@withLock null
            }

            try {
                val data = file.readText()
                val map = yaml.load(data) as? Map<String, Any>
                if (map != null) {
                    val cursor = parseCursorState(map, packId)
                    // Update cache
                    cursorCache?.put(packId, cursor) ?: run {
                        cursorCache = mutableMapOf(packId to cursor)
                    }
                    Log.d("PackDailyCursorStore", "Loaded cursor for pack: $packId, offset: ${cursor.sentenceOffset}")
                    return@withLock cursor
                }
            } catch (e: Exception) {
                Log.e("PackDailyCursorStore", "Failed to load cursor for pack: $packId", e)
            }
            return@withLock null
        }
    }

    override fun savePackCursor(cursor: PackDailyCursorState) {
        mutex.withLock {
            val file = getFileForPack(cursor.packId)
            try {
                val map = mapOf(
                    "schemaVersion" to schemaVersion,
                    "packId" to cursor.packId,
                    "sentenceOffset" to cursor.sentenceOffset,
                    "currentLessonIndex" to cursor.currentLessonIndex,
                    "lastSessionHash" to cursor.lastSessionHash,
                    "firstSessionDate" to cursor.firstSessionDate,
                    "firstSessionSentenceCardIds" to cursor.firstSessionSentenceCardIds,
                    "firstSessionVerbCardIds" to cursor.firstSessionVerbCardIds,
                    "verbOffset" to cursor.verbOffset
                )
                val yaml = Yaml()
                val data = yaml.dump(map)
                AtomicFileWriter.writeText(file, data)

                // Update cache
                cursorCache?.put(cursor.packId, cursor) ?: run {
                    cursorCache = mutableMapOf(cursor.packId to cursor)
                }

                Log.d("PackDailyCursorStore", "Saved cursor for pack: ${cursor.packId}, offset: ${cursor.sentenceOffset}")
            } catch (e: Exception) {
                Log.e("PackDailyCursorStore", "Failed to save cursor for pack: ${cursor.packId}", e)
                throw IOException("Failed to save pack cursor state", e)
            }
        }
    }

    override fun deletePackCursor(packId: String) {
        mutex.withLock {
            val file = getFileForPack(packId)
            if (file.exists()) {
                file.delete()
                // Remove from cache
                cursorCache?.remove(packId)
                Log.d("PackDailyCursorStore", "Deleted cursor for pack: $packId")
            }
        }
    }

    override fun loadAllPackCursors(): Map<String, PackDailyCursorState> {
        return mutex.withLock {
            // Return all cached cursors if available
            cursorCache?.let { return@withLock it.toMap() }

            // Otherwise, scan directory for all cursor files
            val cursors = mutableMapOf<String, PackDailyCursorState>()
            baseDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("daily_cursor_") && file.name.endsWith(".yaml")) {
                    // Extract packId from filename: "daily_cursor_ru-en-v1.yaml" → "ru-en-v1"
                    val packId = file.name.removePrefix("daily_cursor_").removeSuffix(".yaml")
                    try {
                        val data = file.readText()
                        val map = yaml.load(data) as? Map<String, Any>
                        if (map != null) {
                            val cursor = parseCursorState(map, packId)
                            cursors[packId] = cursor
                        }
                    } catch (e: Exception) {
                        Log.e("PackDailyCursorStore", "Failed to load cursor from file: ${file.name}", e)
                    }
                }
            }

            // Update cache
            cursorCache = cursors
            Log.d("PackDailyCursorStore", "Loaded ${cursors.size} pack cursors")
            return@withLock cursors
        }
    }

    override fun flush() {
        // All writes are immediate (AtomicFileWriter), so this is a no-op
        // Kept for interface consistency
    }

    private fun parseCursorState(map: Map<String, Any>, packId: String): PackDailyCursorState {
        return PackDailyCursorState(
            packId = packId,
            sentenceOffset = (map["sentenceOffset"] as? Int) ?: 0,
            currentLessonIndex = (map["currentLessonIndex"] as? Int) ?: 0,
            lastSessionHash = (map["lastSessionHash"] as? Int) ?: 0,
            firstSessionDate = (map["firstSessionDate"] as? String) ?: "",
            firstSessionSentenceCardIds = (map["firstSessionSentenceCardIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            firstSessionVerbCardIds = (map["firstSessionVerbCardIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            verbOffset = (map["verbOffset"] as? Int) ?: 0
        )
    }
}
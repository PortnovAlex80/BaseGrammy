package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Store for loading and caching grammar chip content from pack directories.
 *
 * Grammar chips are loaded from installed pack directories at:
 * `filesDir/grammarmate/packs/{packId}/grammar_chips/grammar_chip_*.json`
 *
 * Each lesson can reference a grammar chip file via the manifest's `grammarChip` field.
 */
object GrammarChipStore {
    private val cache = mutableMapOf<String, GrammarChip>()
    private val lessonToChipFile = mutableMapOf<String, Pair<String, String>>() // lessonId -> (packId, chipFile)
    private var initialized = false
    private var initializing = false

    private const val TAG = "GrammarChipStore"

    /**
     * Check if the store has been initialized.
     */
    fun isInitialized(): Boolean = initialized

    /**
     * Check if the store is currently initializing.
     */
    fun isInitializing(): Boolean = initializing

    /**
     * Initialize grammar chip store by scanning all installed packs.
     * This should be called after packs are imported/installed.
     */
    suspend fun initialize(@Suppress("UNUSED_PARAMETER") context: Context, packsDir: File) = withContext(Dispatchers.IO) {
        if (initialized) {
            Log.d(TAG, "Already initialized, skipping")
            return@withContext
        }

        if (initializing) {
            Log.d(TAG, "Already initializing, waiting...")
            while (initializing) {
                kotlinx.coroutines.delay(100)
            }
            return@withContext
        }

        initializing = true
        Log.d(TAG, "Starting initialization...")

        try {
            cache.clear()
            lessonToChipFile.clear()

            // Scan all installed pack directories
            val packDirectories = packsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()

            Log.d(TAG, "Scanning ${packDirectories.size} pack directories for grammar chips")

            packDirectories.forEach { packDir ->
                val manifestFile = File(packDir, "manifest.json")
                if (!manifestFile.exists()) {
                    Log.d(TAG, "No manifest.json found in ${packDir.name}, skipping")
                    return@forEach
                }

                try {
                    @Suppress("UNUSED_VARIABLE") val manifest = LessonPackManifest.fromJson(manifestFile.readText())
                    val packId = manifest.packId

                    // Build lesson -> chip file mapping from manifest
                    manifest.lessons.forEach { lesson ->
                        val chipFile = lesson.grammarChip
                        if (chipFile != null) {
                            lessonToChipFile[lesson.lessonId] = Pair(packId, chipFile)
                            Log.d(TAG, "Mapped ${lesson.lessonId} -> $packId/$chipFile")
                        }
                    }

                    // Check if grammar_chips directory exists
                    val gcDir = File(packDir, "grammar_chips")
                    if (!gcDir.exists()) {
                        Log.d(TAG, "No grammar_chips directory in $packId")
                        return@forEach
                    }

                    // Load and cache grammar chip JSON files
                    val chipFiles = gcDir.listFiles()?.filter {
                        it.isFile && it.name.endsWith(".json")
                    } ?: emptyList()

                    Log.d(TAG, "Found ${chipFiles.size} grammar chip files in $packId")

                    chipFiles.forEach { chipFile ->
                        try {
                            val chip = parseGrammarChipFromJson(chipFile)
                            val chipKey = chip.key.uppercase() // Normalize to uppercase
                            cache[chipKey] = chip
                            Log.d(TAG, "Loaded grammar chip: $chipKey (from ${chipFile.name})")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse grammar chip file: ${chipFile.name}", e)
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process pack ${packDir.name}", e)
                }
            }

            initialized = true
            initializing = false
            Log.d(TAG, "Initialized with ${cache.size} grammar chips and ${lessonToChipFile.size} lesson mappings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GrammarChipStore", e)
            initializing = false
        }
    }

    fun getChipByKey(key: String): GrammarChip? = cache[key.uppercase()]

    /**
     * Get grammar chip for a lesson ID.
     * This looks up the lesson in the manifest mappings and returns the appropriate chip.
     */
    fun getChipForLesson(lessonId: String): GrammarChip? {
        if (!initialized) {
            Log.w(TAG, "GrammarChipStore not initialized yet! Cannot get chip for lesson: $lessonId")
            Log.d(TAG, "Cache size: ${cache.size}, Mappings size: ${lessonToChipFile.size}")
            return null
        }

        val mapping = lessonToChipFile[lessonId]
        if (mapping == null) {
            Log.d(TAG, "No grammar chip mapping for lesson: $lessonId")
            Log.d(TAG, "Available mappings: ${lessonToChipFile.keys.take(10)}...")
            return null
        }

        val (packId, chipFile) = mapping
        Log.d(TAG, "Found mapping for $lessonId -> $packId/$chipFile")

        // Extract chip number from filename and get key
        val chipNumber = chipFile.removePrefix("grammar_chip_").removeSuffix(".json")
        val chipKey = getChipKeyFromNumber(chipNumber)

        Log.d(TAG, "Looking for chip with key: $chipKey (from file: $chipFile, lesson: $lessonId)")

        // Try both uppercase and lowercase keys
        val chip = cache[chipKey] ?: cache[chipKey.uppercase()] ?: cache[chipKey.lowercase()]

        if (chip == null) {
            Log.w(TAG, "Chip not found in cache for key: $chipKey")
            Log.d(TAG, "Available keys in cache: ${cache.keys.take(10)}...")
            Log.d(TAG, "Total cache size: ${cache.size}")
        } else {
            Log.d(TAG, "Successfully loaded chip: ${chip.key} (for lesson: $lessonId)")
        }

        return chip
    }

    fun hasChipForLesson(lessonId: String): Boolean = getChipForLesson(lessonId) != null

    fun getAllChipKeys(): Set<String> = cache.keys

    /**
     * Parse grammar chip from JSON file.
     * JSON format matches the GrammarChip data class structure.
     */
    private fun parseGrammarChipFromJson(jsonFile: File): GrammarChip {
        val jsonText = jsonFile.readText()
        val json = JSONObject(jsonText)

        val key = json.getString("key")
        val title = json.getString("title")
        val essence = json.getString("essence")
        val formula = json.optString("formula").ifBlank { null }
        val base = json.optString("base").ifBlank { null }
        val dontConfuse = json.optString("dontConfuse").ifBlank { null }

        // Parse examples array
        val examplesArray = json.optJSONArray("examples")
        val examples = if (examplesArray != null) {
            (0 until examplesArray.length()).map { i ->
                val exampleObj = examplesArray.getJSONObject(i)
                GrammarExample(
                    it = exampleObj.getString("it"),
                    ru = exampleObj.optString("ru", ""),
                    note = exampleObj.optString("note", "")
                )
            }
        } else {
            emptyList()
        }

        // Parse notes if present
        val notes = mutableMapOf<String, String>()
        val keys = json.keys()
        keys?.forEach { noteKey ->
            if (noteKey !in listOf("key", "title", "essence", "formula", "base", "examples", "dontConfuse")) {
                notes[noteKey] = json.getString(noteKey)
            }
        }

        return GrammarChip(
            key = key,
            title = title,
            essence = essence,
            formula = formula,
            base = base,
            examples = examples,
            dontConfuse = dontConfuse,
            notes = notes
        )
    }

    /**
     * Convert chip number to chip key (e.g., "01" -> "A01", "18" -> "B02").
     * This is a simplified mapping - in production this should come from the chip data itself.
     */
    private fun getChipKeyFromNumber(number: String): String {
        val num = number.toIntOrNull() ?: return number.uppercase()

        // Map lesson numbers to grammar chip keys based on the curriculum structure
        // A01-A16: lessons 1-16
        // B01-B27: lessons 17-43
        // C01-C20: lessons 44-63
        return when {
            num <= 16 -> "A$num.toString().padStart(2, '0')"
            num <= 43 -> "B${(num - 16).toString().padStart(2, '0')}"
            num <= 63 -> "C${(num - 43).toString().padStart(2, '0')}"
            else -> number.uppercase()
        }
    }

    fun clearCache() {
        cache.clear()
        lessonToChipFile.clear()
        initialized = false
        initializing = false
    }
}

package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import com.alexpo.grammermate.BuildConfig
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
    private val cache = mutableMapOf<String, GrammarChip>()            // "packId::chipKey" -> GrammarChip
    private val lessonToChipFile = mutableMapOf<String, Pair<String, String>>() // "packId::lessonId" -> (packId, chipFile)
    private var initialized = false
    private var initializing = false

    private const val TAG = "GrammarChipStore"

    private fun packChipKey(packId: String, chipKey: String) = "${packId}::${chipKey}"
    private fun packLessonKey(packId: String, lessonId: String) = "${packId}::${lessonId}"

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

                    // Build lesson -> chip file mapping from manifest (pack-scoped)
                    manifest.lessons.forEach { lesson ->
                        val chipFile = lesson.grammarChip
                        if (chipFile != null) {
                            val pKey = packLessonKey(packId, lesson.lessonId)
                            lessonToChipFile[pKey] = Pair(packId, chipFile)
                            Log.d(TAG, "Mapped ${lesson.lessonId} -> $packId/$chipFile")
                        }
                    }

                    // Also build mappings for chapter-level lessons (schema v2, pack-scoped)
                    manifest.chapters.forEach { chapter ->
                        chapter.lessons.forEach { lessonId ->
                            // Extract level from lesson ID (e.g., "A01" from "lesson_01_A01")
                            val levelMatch = Regex("_(A\\d\\d|B\\d\\d|C\\d\\d)").find(lessonId)
                            if (levelMatch != null) {
                                val level = levelMatch.value.removePrefix("_")
                                // Map level to chip number (A01 -> chip_01, B01 -> chip_17, C01 -> chip_44)
                                val chipNumber = when {
                                    level.startsWith("A") -> level.removePrefix("A").toIntOrNull() ?: 1
                                    level.startsWith("B") -> 16 + (level.removePrefix("B").toIntOrNull() ?: 1)
                                    level.startsWith("C") -> 43 + (level.removePrefix("C").toIntOrNull() ?: 1)
                                    else -> 1
                                }
                                val chipFileName = "grammar_chip_${chipNumber.toString().padStart(2, '0')}.json"
                                val pKey = packLessonKey(packId, lessonId)
                                lessonToChipFile[pKey] = Pair(packId, chipFileName)
                                Log.d(TAG, "Mapped chapter lesson $lessonId -> $packId/$chipFileName (level: $level)")
                            }
                        }
                    }

                    // Check for grammar chips in grammar_chips/ subdir OR pack root
                    val gcDir = File(packDir, "grammar_chips")
                    val gcDirFiles = if (gcDir.exists()) {
                        gcDir.listFiles()?.filter {
                            it.isFile && it.name.endsWith(".json")
                        } ?: emptyList()
                    } else emptyList()

                    // Also check pack root for grammar_chip_*.json files
                    val rootChipFiles = packDir.listFiles()?.filter {
                        it.isFile && it.name.startsWith("grammar_chip_") && it.name.endsWith(".json")
                    } ?: emptyList()

                    val chipFiles = gcDirFiles + rootChipFiles

                    Log.d(TAG, "Found ${chipFiles.size} grammar chip files in $packId")

                    chipFiles.forEach { chipFile ->
                        try {
                            val chip = parseGrammarChipFromJson(chipFile)
                            val chipKey = chip.key.uppercase() // Normalize to uppercase
                            cache[packChipKey(packId, chipKey)] = chip
                            Log.d(TAG, "Loaded grammar chip: $chipKey (from $packId/${chipFile.name})")
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

    fun getChipByKey(key: String): GrammarChip? {
        // Search all packs for backwards compatibility
        val upperKey = key.uppercase()
        return cache.entries.firstOrNull { it.key.endsWith("::$upperKey") }?.value
    }

    /**
     * Get grammar chip by key for a specific pack.
     */
    fun getChipByKey(key: String, packId: String): GrammarChip? = cache[packChipKey(packId, key.uppercase())]

    /**
     * Get grammar chip for a lesson ID in a specific pack.
     * This looks up the lesson in the manifest mappings and returns the appropriate chip.
     */
    fun getChipForLesson(lessonId: String, packId: String? = null): GrammarChip? {
        if (!initialized) {
            Log.w(TAG, "GrammarChipStore not initialized yet! Cannot get chip for lesson: $lessonId")
            return null
        }

        val mapping = if (packId != null) {
            lessonToChipFile[packLessonKey(packId, lessonId)]
        } else {
            // Fallback: search all packs (returns first match)
            lessonToChipFile.entries.firstOrNull { it.key.endsWith("::$lessonId") }?.value
        }

        if (mapping == null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "No grammar chip mapping for lesson: $lessonId (packId=$packId)")
            return null
        }

        val (resolvedPackId, chipFile) = mapping

        // Extract chip number from filename and get key
        val chipNumber = chipFile.removePrefix("grammar_chip_").removeSuffix(".json")
        val chipKey = getChipKeyFromNumber(chipNumber)

        // Pack-scoped lookup with case fallbacks
        val chip = cache[packChipKey(resolvedPackId, chipKey)]
            ?: cache[packChipKey(resolvedPackId, chipKey.uppercase())]
            ?: cache[packChipKey(resolvedPackId, chipKey.lowercase())]

        if (chip == null) {
            Log.w(TAG, "Chip not found in cache for key: $chipKey (pack: $resolvedPackId)")
        }

        return chip
    }

    fun hasChipForLesson(lessonId: String, packId: String? = null): Boolean = getChipForLesson(lessonId, packId) != null

    fun getAllChipKeys(): Set<String> = cache.keys.map { it.substringAfter("::") }.toSet()

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

        // Parse examples — supports two formats:
        // 1. Array of objects (Italian): [{"it": "Parlo.", "ru": "", "note": ""}, ...]
        // 2. Object with language keys (German/Chinese): {"de": ["Der Tisch."], "ru": ["Стол."]}
        val examplesArray = json.optJSONArray("examples")
        val examplesObj = json.optJSONObject("examples")
        val examples = if (examplesArray != null) {
            // Format 1: array of example objects
            (0 until examplesArray.length()).map { i ->
                val exampleObj = examplesArray.getJSONObject(i)
                val targetText = exampleObj.optString("target", "").ifBlank {
                    val langKeys = listOf("it", "de", "zh", "el", "en", "ru_target")
                    langKeys.firstNotNullOfOrNull { key ->
                        exampleObj.optString(key, "").ifBlank { null }
                    } ?: ""
                }
                GrammarExample(
                    target = targetText,
                    ru = exampleObj.optString("ru", ""),
                    note = exampleObj.optString("note", "")
                )
            }
        } else if (examplesObj != null) {
            // Format 2: object with language-keyed arrays
            val targetLangKeys = listOf("it", "de", "zh", "el", "en", "ru_target")
            val targetKey = targetLangKeys.firstOrNull { examplesObj.has(it) }
            val ruKey = "ru"
            if (targetKey != null) {
                val targetArr = examplesObj.optJSONArray(targetKey)
                val ruArr = examplesObj.optJSONArray(ruKey)
                if (targetArr != null) {
                    (0 until targetArr.length()).map { i ->
                        GrammarExample(
                            target = targetArr.getString(i),
                            ru = ruArr?.optString(i, "") ?: "",
                            note = ""
                        )
                    }
                } else emptyList()
            } else emptyList()
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
            num <= 16 -> "A${num.toString().padStart(2, '0')}"
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

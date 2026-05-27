package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Store for loading and caching grammar chip content from markdown files.
 *
 * Grammar chips are loaded from `docs/lesson-methodology/grammar_chips/GRAMMAR_CHIP_*.md`.
 * Each file contains a single grammar explanation with sections: Суть, Формула, База, Примеры, etc.
 *
 * Lesson IDs are mapped to chip keys (e.g., "lesson_01_A01" → "A01").
 */
@Singleton
class GrammarChipStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cache = mutableMapOf<String, GrammarChip>()
    private val lessonToChipKey = mutableMapOf<String, String>()
    private var initialized = false

    companion object {
        private const val TAG = "GrammarChipStore"
        private const val GRAMMAR_CHIPS_DIR = "docs/lesson-methodology/grammar_chips"
    }

    /**
     * Initialize the store by scanning grammar chip files and building the lesson→chip mapping.
     * Must be called before accessing grammar chips.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext

        try {
            val chipsDir = File(context.filesDir, GRAMMAR_CHIPS_DIR)
            if (!chipsDir.exists()) {
                Log.w(TAG, "Grammar chips directory not found: ${chipsDir.absolutePath}")
                initialized = true
                return@withContext
            }

            val chipFiles = chipsDir.listFiles { file ->
                file.isFile && file.name.startsWith("GRAMMAR_CHIP_") && file.name.endsWith(".md")
            } ?: emptyArray()

            Log.d(TAG, "Found ${chipFiles.size} grammar chip files")

            chipFiles.forEach { file ->
                try {
                    val chip = parseGrammarChipFile(file)
                    cache[chip.key] = chip

                    // Map lesson IDs to chip key
                    // Pattern: lesson_XX_YY where YY is the chip key
                    val lessonPattern = chip.key.lowercase()
                    lessonToChipKey[lessonPattern] = chip.key
                    lessonToChipKey["lesson_01_$lessonPattern"] = chip.key
                    lessonToChipKey["lesson_02_$lessonPattern"] = chip.key
                    lessonToChipKey["lesson_03_$lessonPattern"] = chip.key
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse grammar chip file: ${file.name}", e)
                }
            }

            initialized = true
            Log.d(TAG, "Initialized with ${cache.size} grammar chips")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GrammarChipStore", e)
            initialized = true // Don't retry on next call
        }
    }

    /**
     * Get a grammar chip by its key (e.g., "A01", "A02").
     * Returns null if the chip is not found.
     */
    fun getChipByKey(key: String): GrammarChip? = cache[key.uppercase()]

    /**
     * Get a grammar chip for a specific lesson ID.
     * Maps lesson IDs like "lesson_01_A01" to chip key "A01".
     * Returns null if no chip is mapped to this lesson.
     */
    fun getChipForLesson(lessonId: String): GrammarChip? {
        val normalizedId = lessonId.lowercase()

        // Try exact match first
        lessonToChipKey[normalizedId]?.let { key -> return cache[key] }

        // Try extracting chip key from lesson ID (pattern: lesson_XX_YY or XX_YY or just YY)
        val chipKey = when {
            normalizedId.contains("_") -> {
                // Extract last part after last underscore
                normalizedId.substringAfterLast("_")
            }
            else -> normalizedId
        }

        return cache[chipKey.uppercase()]
    }

    /**
     * Check if a lesson has an associated grammar chip.
     */
    fun hasChipForLesson(lessonId: String): Boolean = getChipForLesson(lessonId) != null

    /**
     * Get all cached chip keys.
     */
    fun getAllChipKeys(): Set<String> = cache.keys

    /**
     * Parse a grammar chip markdown file.
     * Expected format:
     * ```
     * # A01 - Presente Indicativo
     * ## Суть
     * Explanation text...
     * ## Формула
     * Formula content...
     * ## Примеры
     * - `Italian text.` - Russian translation.
     * ```
     */
    private fun parseGrammarChipFile(file: File): GrammarChip {
        val content = file.readText()
        val lines = content.lines()

        // Extract title from first heading
        val title = lines.firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()
            ?: throw IllegalArgumentException("No title found in ${file.name}")

        // Extract chip key from title (e.g., "A01 - Presente Indicativo" -> "A01")
        val key = title.substringBefore("-").trim().uppercase()

        var currentSection: String? = null
        var essence: String? = null
        var formula: StringBuilder? = null
        var base: StringBuilder? = null
        val examples = mutableListOf<GrammarExample>()
        var dontConfuse: StringBuilder? = null
        val notes = mutableMapOf<String, StringBuilder>()
        val otherSections = mutableMapOf<String, StringBuilder>()

        lines.forEach { line ->
            when {
                line.startsWith("## ") -> {
                    val sectionName = line.removePrefix("## ").trim()
                    currentSection = when (sectionName) {
                        "Суть" -> "essence"
                        "Формула" -> "formula"
                        "База" -> "base"
                        "Примеры" -> "examples"
                        "Не путать" -> "dontConfuse"
                        else -> "other:$sectionName"
                    }

                    if (currentSection?.startsWith("other:") == true) {
                        val sectionTitle = currentSection!!.substringAfter(":")
                        otherSections.getOrPut(sectionTitle) { StringBuilder() }
                    }
                }
                line.isNotBlank() && currentSection != null -> {
                    when (currentSection) {
                        "essence" -> {
                            essence = if (essence == null) line else "$essence\n$line"
                        }
                        "formula" -> {
                            formula = formula ?: StringBuilder()
                            formula!!.appendLine(line)
                        }
                        "base" -> {
                            base = base ?: StringBuilder()
                            base!!.appendLine(line)
                        }
                        "examples" -> {
                            // Parse example line: `- `Italian text.` - Russian translation.`
                            val exampleMatch = Regex("""^- `([^`]+)`\s*-\s*(.+?)\s*$""").find(line.trim())
                            if (exampleMatch != null) {
                                val (itText, ruText) = exampleMatch.destructured
                                examples.add(GrammarExample(itText, ruText.trim()))
                            }
                        }
                        "dontConfuse" -> {
                            dontConfuse = dontConfuse ?: StringBuilder()
                            dontConfuse!!.appendLine(line)
                        }
                        else -> {
                            val sectionTitle = currentSection!!.substringAfter(":")
                            otherSections[sectionTitle]!!.appendLine(line)
                        }
                    }
                }
            }
        }

        return GrammarChip(
            key = key,
            title = title,
            essence = essence ?: throw IllegalArgumentException("No Суть section found in ${file.name}"),
            formula = formula?.toString()?.trim(),
            base = base?.toString()?.trim(),
            examples = examples,
            dontConfuse = dontConfuse?.toString()?.trim(),
            notes = otherSections.mapValues { it.value.toString().trim() }
        )
    }

    /**
     * Clear the cache (mainly for testing).
     */
    fun clearCache() {
        cache.clear()
        lessonToChipKey.clear()
        initialized = false
    }
}
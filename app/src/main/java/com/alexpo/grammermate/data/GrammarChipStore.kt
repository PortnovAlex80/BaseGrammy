package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Store for loading and caching grammar chip content from markdown files.
 *
 * Grammar chips are loaded from `docs/lesson-methodology/grammar_chips/GRAMMAR_CHIP_*.md`.
 * The parser supports the short English chip format and the legacy Russian section names.
 */
object GrammarChipStore {
    private val cache = mutableMapOf<String, GrammarChip>()
    private val lessonToChipKey = mutableMapOf<String, String>()
    private var initialized = false

    private const val TAG = "GrammarChipStore"
    private const val GRAMMAR_CHIPS_DIR = "docs/lesson-methodology/grammar_chips"

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
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
            initialized = true
        }
    }

    fun getChipByKey(key: String): GrammarChip? = cache[key.uppercase()]

    fun getChipForLesson(lessonId: String): GrammarChip? {
        val normalizedId = lessonId.lowercase()

        lessonToChipKey[normalizedId]?.let { key -> return cache[key] }

        val chipKey = when {
            normalizedId.contains("_") -> normalizedId.substringAfterLast("_")
            else -> normalizedId
        }

        return cache[chipKey.uppercase()]
    }

    fun hasChipForLesson(lessonId: String): Boolean = getChipForLesson(lessonId) != null

    fun getAllChipKeys(): Set<String> = cache.keys

    private fun parseGrammarChipFile(file: File): GrammarChip {
        val lines = file.readText().lines()

        val title = lines.firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()
            ?: throw IllegalArgumentException("No title found in ${file.name}")
        val key = title.substringBefore("-").trim().uppercase()

        var currentSection: String? = null
        var essence: String? = null
        var formula: StringBuilder? = null
        var base: StringBuilder? = null
        val examples = mutableListOf<GrammarExample>()
        var dontConfuse: StringBuilder? = null
        val otherSections = mutableMapOf<String, StringBuilder>()

        lines.forEach { line ->
            when {
                line.startsWith("## ") -> {
                    val sectionName = line.removePrefix("## ").trim()
                    currentSection = when (sectionName) {
                        "Core Idea", "Суть", "РЎСѓС‚СЊ" -> "essence"
                        "Form", "Formula", "Формула", "Р¤РѕСЂРјСѓР»Р°" -> "formula"
                        "Base", "База", "Р‘Р°Р·Р°" -> "base"
                        "Examples", "Примеры", "РџСЂРёРјРµСЂС‹" -> "examples"
                        "Watch Out", "Don't Confuse", "Не путать", "РќРµ РїСѓС‚Р°С‚СЊ" -> "dontConfuse"
                        else -> "other:$sectionName"
                    }

                    if (currentSection?.startsWith("other:") == true) {
                        otherSections.getOrPut(currentSection!!.substringAfter(":")) { StringBuilder() }
                    }
                }
                line.isNotBlank() && currentSection != null -> {
                    when (currentSection) {
                        "essence" -> essence = if (essence == null) line else "$essence\n$line"
                        "formula" -> formula = appendLine(formula, line)
                        "base" -> base = appendLine(base, line)
                        "examples" -> {
                            val trimmedLine = line.trim()
                            val translatedExample =
                                Regex("""^- `([^`]+)`\s*-\s*(.+?)\s*$""").find(trimmedLine)
                            val italianOnlyExample = Regex("""^- `([^`]+)`\s*$""").find(trimmedLine)

                            when {
                                translatedExample != null -> {
                                    val (itText, ruText) = translatedExample.destructured
                                    examples.add(GrammarExample(itText, ruText.trim()))
                                }
                                italianOnlyExample != null -> {
                                    examples.add(
                                        GrammarExample(
                                            it = italianOnlyExample.groupValues[1],
                                            ru = ""
                                        )
                                    )
                                }
                            }
                        }
                        "dontConfuse" -> dontConfuse = appendLine(dontConfuse, line)
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
            essence = essence ?: throw IllegalArgumentException("No Core Idea section found in ${file.name}"),
            formula = formula?.toString()?.trim(),
            base = base?.toString()?.trim(),
            examples = examples,
            dontConfuse = dontConfuse?.toString()?.trim(),
            notes = otherSections.mapValues { it.value.toString().trim() }
        )
    }

    private fun appendLine(builder: StringBuilder?, line: String): StringBuilder {
        val target = builder ?: StringBuilder()
        target.appendLine(line)
        return target
    }

    fun clearCache() {
        cache.clear()
        lessonToChipKey.clear()
        initialized = false
    }
}

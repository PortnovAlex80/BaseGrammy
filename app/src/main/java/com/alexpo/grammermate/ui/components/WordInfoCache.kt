package com.alexpo.grammermate.ui.components

import android.util.Log
import com.alexpo.grammermate.data.ItalianDrillVocabParser
import java.io.File
import java.io.FileInputStream

/**
 * Simple cache for Italian drill word information.
 * Loads drill data from pack directories and provides fast lookup.
 *
 * This is a lightweight alternative to Hilt-based injection - uses simple singleton pattern.
 */
class WordInfoCache private constructor(
    private val baseDir: File
) {
    companion object {
        private const val TAG = "WordInfoCache"

        @Volatile
        private var instance: WordInfoCache? = null

        fun getInstance(baseDir: File): WordInfoCache {
            return instance ?: synchronized(this) {
                instance ?: WordInfoCache(baseDir).also { instance = it }
            }
        }

        // Part of speech detection from file names
        private fun getPosFromFileName(fileName: String): String {
            return when {
                fileName.contains("verbs") -> "verbs"
                fileName.contains("nouns") -> "nouns"
                fileName.contains("adjectives") -> "adjectives"
                fileName.contains("adverbs") -> "adverbs"
                fileName.contains("numbers") -> "numbers"
                fileName.contains("pronouns") -> "pronouns"
                else -> "unknown"
            }
        }
    }

    private val cache = mutableMapOf<String, WordHint>()
    private var loaded = false
    private val lock = Any()

    /**
     * Load drill data from pack directories.
     * Searches for drill files in grammarmate/drills/{packId}/vocab_drill/
     */
    fun loadDrillData() {
        if (loaded) return

        synchronized(lock) {
            if (loaded) return

            val drillsDir = File(baseDir, "grammarmate/drills")
            if (!drillsDir.exists()) {
                Log.w(TAG, "Drills directory not found: ${drillsDir.absolutePath}")
                loaded = true
                return
            }

            var totalLoaded = 0
            drillsDir.listFiles()?.forEach { packDir ->
                val vocabDrillDir = File(packDir, "vocab_drill")
                if (!vocabDrillDir.exists()) return@forEach

                vocabDrillDir.listFiles { file ->
                    file.extension.equals("csv", ignoreCase = true)
                }?.forEach { file ->
                    try {
                        val pos = getPosFromFileName(file.name)
                        val rows = ItalianDrillVocabParser.parse(FileInputStream(file), file.name)

                        for (row in rows) {
                            val hint = WordHint(
                                translation = row.meaningRu ?: "",
                                rank = row.rank,
                                partOfSpeech = pos,
                                collocations = row.collocations
                            )
                            cache[row.word.lowercase()] = hint
                            totalLoaded++
                        }

                        Log.d(TAG, "Loaded ${rows.size} words from ${file.name}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load drill file: ${file.name}", e)
                    }
                }
            }

            loaded = true
            Log.i(TAG, "Drill data cache loaded: $totalLoaded words total")
        }
    }

    /**
     * Get word information for all words in the given text.
     * Returns a map of lowercase word -> WordHint (only for words found in drill data).
     */
    fun getWordsInfo(text: String): Map<String, WordHint> {
        if (!loaded) {
            loadDrillData()
        }

        val result = mutableMapOf<String, WordHint>()
        val words = text.split(Regex("\\s+"))

        for (word in words) {
            val cleanWord = word.replace(Regex("[.,!?;:»«\"'\\[\\](){}]"), "").lowercase()
            if (cleanWord.isBlank()) continue

            cache[cleanWord]?.let { hint ->
                result[cleanWord] = hint
            }
        }

        return result
    }

    /**
     * Get word information for a single word.
     */
    fun getWordInfo(word: String): WordHint? {
        if (!loaded) {
            loadDrillData()
        }

        val cleanWord = word.replace(Regex("[.,!?;:»«\"'\\[\\](){}]"), "").lowercase()
        return cache[cleanWord]
    }

    /**
     * Clear the cache (useful for testing or pack reload).
     */
    fun clearCache() {
        synchronized(lock) {
            cache.clear()
            loaded = false
        }
    }

    /**
     * Check if cache is loaded.
     */
    fun isLoaded(): Boolean = loaded
}

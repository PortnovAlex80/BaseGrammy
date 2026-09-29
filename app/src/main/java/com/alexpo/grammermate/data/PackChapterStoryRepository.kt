package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import com.alexpo.grammermate.BuildConfig
import java.io.File

/** Reads pack-scoped chapter metadata and story content without owning the manifest cache. */
internal class PackChapterStoryRepository(
    private val context: Context,
    private val packsDir: File,
    private val readManifest: (String) -> LessonPackManifest?
) {
    fun getChapters(packId: String): List<Chapter> {
        val manifest = readManifest(packId) ?: return emptyList()
        val chapters = manifest.chapters.sortedBy { it.order }
        if (BuildConfig.DEBUG) {
            Log.d("LessonStore", "getChapters($packId): returning ${chapters.size} chapters")
            chapters.forEach { Log.d("LessonStore", "  - ${it.chapterId}: ${it.title}") }
        }
        return chapters
    }

    fun getChapterStory(packId: String, storyFile: String): String? {
        if (storyFile.isBlank()) return null

        val packDir = File(packsDir, packId)
        val storiesDir = File(packDir, "stories")
        val storyFileObj = File(storiesDir, storyFile)

        Log.d("LessonStore", "Loading story: packId=$packId, storyFile=$storyFile")
        Log.d("LessonStore", "Pack dir exists: ${packDir.exists()}, Stories dir exists: ${storiesDir.exists()}")

        return try {
            // First, try to read from internal storage
            if (storyFileObj.exists()) {
                Log.d("LessonStore", "Story found in internal storage: ${storyFileObj.absolutePath}")
                return storyFileObj.readText()
            }

            Log.d("LessonStore", "Story NOT found in internal storage, trying assets...")
            Log.d("LessonStore", "Story file path: ${storyFileObj.absolutePath}")

            // If not in internal storage, try to read from assets and copy it
            val assetPath = "grammarmate/packs/$packId/stories/$storyFile"
            try {
                context.assets.open(assetPath).use { input ->
                    // Ensure the stories directory exists
                    if (!storiesDir.exists()) {
                        storiesDir.mkdirs()
                        Log.d("LessonStore", "Created stories directory: ${storiesDir.absolutePath}")
                    }

                    // Read from assets and write to internal storage
                    val content = input.bufferedReader().readText()
                    AtomicFileWriter.writeText(storyFileObj, content)

                    Log.d("LessonStore", "Copied story from assets: $assetPath -> $storyFileObj")
                    return content
                }
            } catch (e: Exception) {
                Log.e("LessonStore", "Story file not found in assets: $assetPath", e)
                Log.d("LessonStore", "Available stories in pack:")
                storiesDir.listFiles()?.forEach { file ->
                    Log.d("LessonStore", "  - ${file.name}")
                }
                null
            }
        } catch (e: Exception) {
            Log.e("LessonStore", "Failed to load story content: $storyFile", e)
            null
        }
    }

    /**
     * Get chapter story with automatic language detection based on UI language preference.
     * This is the preferred method for loading stories in the Grammar Story Roadmap.
     *
     * @param packId Pack identifier
     * @param chapterId Chapter identifier (e.g., "chapter_01")
     * @param uiLanguage UI language setting ("ru", "en", "system")
     * @return Story content if found, null otherwise
     */
    fun getChapterStoryWithLanguageDetection(packId: String, chapterId: String, uiLanguage: String): String? {
        val storyFile = detectStoryLanguage(packId, chapterId, uiLanguage) ?: return null
        return getChapterStory(packId, storyFile)
    }

    fun hasChapters(packId: String): Boolean {
        val manifest = readManifest(packId) ?: return false
        return manifest.chapters.isNotEmpty()
    }

    /**
     * Detect which story file to use based on UI language preference.
     *
     * Logic:
     * - If uiLanguage is "ru", try Russian story file first (chapter_XX_original.md)
     * - If uiLanguage is "en" or "system", try English story file first (chapter_XX.md)
     * - Fall back to available file if preferred language doesn't exist
     *
     * @param packId Pack identifier
     * @param chapterId Chapter identifier (e.g., "chapter_01")
     * @param uiLanguage UI language setting ("ru", "en", "system")
     * @return Story filename if found, null otherwise
     */
    fun detectStoryLanguage(packId: String, chapterId: String, uiLanguage: String): String? {
        val packDir = File(packsDir, packId)
        val storiesDir = File(packDir, "stories")
        if (!storiesDir.exists()) {
            android.util.Log.w("LessonStore", "Stories directory does not exist for pack: $packId")
            return null
        }

        // Normalize chapter ID for filename
        val chapterBase = chapterId.removePrefix("chapter_").trim()
        if (chapterBase.isEmpty()) {
            android.util.Log.w("LessonStore", "Invalid chapter ID: $chapterId")
            return null
        }

        // Determine preferred language
        val preferRussian = when (uiLanguage.lowercase()) {
            "ru" -> true
            "system" -> {
                // Check system locale
                val systemLang = java.util.Locale.getDefault().language
                systemLang == "ru"
            }
            else -> false
        }

        android.util.Log.d("LessonStore", "Detecting story language for chapterId=$chapterId, preferRussian=$preferRussian")

        // List all available story files for this chapter
        val availableFiles = storiesDir.listFiles()?.filter { file ->
            file.name.startsWith("chapter_$chapterBase") && file.name.endsWith(".md")
        } ?: emptyList()

        android.util.Log.d("LessonStore", "Available story files for chapter_$chapterBase: ${availableFiles.map { it.name }}")

        if (availableFiles.isEmpty()) {
            android.util.Log.w("LessonStore", "No story files found for chapter_$chapterBase")
            return null
        }

        // Prioritize based on language preference
        // _original.md files are Russian, others are English translations
        val prioritizedFiles = if (preferRussian) {
            // Prefer _original.md (Russian), then any other variant
            availableFiles.sortedByDescending { it.name.endsWith("_original.md") }
        } else {
            // Prefer non-_original.md files (English variants), then _original.md
            availableFiles.sortedBy { it.name.endsWith("_original.md") }
        }

        android.util.Log.d("LessonStore", "Prioritized story files: ${prioritizedFiles.map { it.name }}")

        for (file in prioritizedFiles) {
            android.util.Log.d("LessonStore", "Trying story file: ${file.name}")
            if (file.exists()) {
                android.util.Log.d("LessonStore", "Found story file for $chapterId: ${file.name} (uiLanguage=$uiLanguage, preferRussian=$preferRussian)")
                return file.name
            }
        }

        android.util.Log.w("LessonStore", "No suitable story file found for chapter: $chapterId")
        return null
    }


}

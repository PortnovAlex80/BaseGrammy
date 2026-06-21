package com.alexpo.grammermate.feature.backgroundvocab

import java.io.File

/**
 * Resolves a pre-rendered narration clip (Opus) for a pack chapter story.
 *
 * Convention (matches what [com.alexpo.grammermate.data.PackImporter] unpacks and what
 * the pack builder writes under `stories/audio/`):
 * ```
 * packs/{packId}/stories/audio/book3_ch{NN}.opus
 * ```
 * where `NN` is the two-digit chapter number extracted from the chapter's [storyFile]
 * (e.g. `chapter_03.md` → `03` → `book3_ch03.opus`).
 *
 * Returns the [File] only if it exists on disk; `null` otherwise. Callers fall back to
 * TTS synthesis (via [com.alexpo.grammermate.shared.audio.SegmentPlayer]) when this
 * returns null. Pure file logic — no Android dependencies, fully unit-testable on JVM.
 *
 * @param baseDir the grammarmate root directory (`File(context.filesDir, "grammarmate")`),
 *  matching the convention used by `DrillFileManager` and [BgVocabAudioResolver].
 */
class StoryAudioResolver(private val baseDir: File) {

    /**
     * Extract the two-digit chapter number from a [storyFile] name.
     *
     * Accepts `chapter_00.md`, `chapter_03_original.md`, `chapter_3.md` → `chapter_NN`
     * where NN is the first numeric run after `chapter_`, zero-padded to 2 digits.
     * Returns `null` when no chapter number can be parsed (blank/garbage input).
     *
     * Visible for testing.
     */
    fun chapterNumberFromStoryFile(storyFile: String?): String? {
        if (storyFile.isNullOrBlank()) return null
        val base = storyFile.substringAfterLast('/').substringBeforeLast('.')
        // Drop the leading "chapter_" prefix (case-insensitive) and take the first
        // numeric run. This tolerates suffixes like "_original".
        val afterPrefix = base.removePrefix("chapter_")
            .removePrefix("Chapter_")
        val num = afterPrefix.takeWhile { it.isDigit() }
        return num.ifBlank { null }?.padStart(2, '0')
    }

    /**
     * @return the expected `.opus` narration [File] for chapter whose story lives at
     *  [storyFile] in pack [packId], if it exists on disk; `null` otherwise (caller
     *  falls back to TTS).
     */
    fun resolveChapterAudio(packId: String, storyFile: String?): File? {
        val num = chapterNumberFromStoryFile(storyFile) ?: return null
        val file = File(baseDir, "packs/$packId/stories/audio/book3_ch$num.opus")
        return file.takeIf { it.exists() && it.canRead() }
    }
}

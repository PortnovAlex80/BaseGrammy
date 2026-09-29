package com.alexpo.grammermate.feature.story

import android.util.Log
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.shared.audio.AudioCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Owns story-reader content, displayed story state and narration commands. */
internal class StoryReaderController(
    private val lessonStore: LessonStore,
    private val audio: AudioCoordinator,
    private val coreState: MutableStateFlow<TrainingUiState>,
    private val logTag: String
) {
    fun speakText(text: String, languageId: String = "en") {
        audio.onTtsSpeak(text, languageId = languageId)
    }

    fun speakMultilingual(
        content: String,
        defaultLanguageId: String = "en",
        storyFile: String? = null,
        packId: String? = null
    ) {
        audio.playMultilingualStory(content, defaultLanguageId, storyFile, packId)
    }

    fun stop() = audio.stopTts()
    fun pause() = audio.pauseStoryPlayback()
    fun resume() = audio.resumeStoryPlayback()

    fun open(chapterTitle: String, content: String, storyFile: String? = null) {
        coreState.update {
            it.copy(
                storyReaderChapterTitle = chapterTitle,
                storyReaderStoryFile = storyFile,
                storyReaderContent = content
            )
        }
    }

    fun clear() {
        coreState.update {
            it.copy(storyReaderChapterTitle = null, storyReaderStoryFile = null, storyReaderContent = "")
        }
    }

    /** Resolve the chapter's preferred language, falling back through LessonStore. */
    fun loadContent(storyFile: String?): String? {
        if (storyFile == null) return null
        return try {
            val nav = coreState.value.navigation
            val packId = nav.activePackId?.value ?: return null
            val uiLanguage = nav.uiLanguage
            val chapterBase = storyFile.removePrefix("chapter_")
                .removeSuffix(".md")
                .split("_")[0]
            val chapterId = "chapter_$chapterBase"

            Log.d(logTag, "Loading story: storyFile=$storyFile, chapterId=$chapterId, uiLanguage=$uiLanguage")
            val detectedFile = lessonStore.detectStoryLanguage(packId, chapterId, uiLanguage)
            if (detectedFile != null) {
                Log.d(logTag, "Detected story file: $detectedFile (original request: $storyFile)")
                val content = lessonStore.getChapterStory(packId, detectedFile)
                if (content != null) Log.d(logTag, "Successfully loaded story: $detectedFile")
                else Log.w(logTag, "Story file not found: $detectedFile")
                content
            } else {
                Log.w(logTag, "No story file detected for chapter: $chapterId")
                null
            }
        } catch (e: Exception) {
            Log.e(logTag, "Failed to load story content: $storyFile", e)
            null
        }
    }
}

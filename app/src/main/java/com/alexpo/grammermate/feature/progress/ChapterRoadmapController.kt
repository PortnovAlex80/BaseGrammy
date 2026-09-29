package com.alexpo.grammermate.feature.progress

import android.util.Log
import com.alexpo.grammermate.data.Chapter
import com.alexpo.grammermate.data.ChapterCardUi
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.TrainingUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Publishes the live chapter roadmap into the ViewModel's single core state. */
internal class ChapterRoadmapController(
    private val lessonStore: LessonStore,
    private val repository: ChapterRepository,
    private val coreState: MutableStateFlow<TrainingUiState>,
    private val logTag: String
) {
    private val _chapterCards = MutableStateFlow<List<ChapterCardUi>>(emptyList())
    val chapterCards: StateFlow<List<ChapterCardUi>> = _chapterCards.asStateFlow()

    fun getChapterCards(): List<ChapterCardUi> {
        val nav = coreState.value.navigation
        val packId = nav.activePackId?.value ?: return emptyList()
        val languageId = nav.selectedLanguageId?.value ?: return emptyList()
        if (!lessonStore.hasChapters(packId)) return emptyList()
        return repository.getChapterCards(lessonStore.getChapters(packId), packId, languageId)
    }

    private fun refreshCards() {
        val cards = getChapterCards()
        if (_chapterCards.value != cards) _chapterCards.value = cards
    }

    fun getFirstIncompleteLesson(chapter: Chapter): String? {
        val nav = coreState.value.navigation
        val languageId = nav.selectedLanguageId?.value ?: return null
        return repository.getFirstIncompleteLesson(chapter, nav.activePackId?.value, languageId)
    }

    fun getCompletedLessonIds(chapter: Chapter): Set<String> {
        val nav = coreState.value.navigation
        if (nav.selectedLanguageId == null) return emptySet()
        return repository.getCompletedLessonIds(chapter, nav.activePackId?.value)
    }

    fun loadChapters() {
        val packId = coreState.value.navigation.activePackId?.value
        if (packId == null || !lessonStore.hasChapters(packId)) {
            coreState.update {
                it.copy(chapters = emptyList(), chapterProgresses = emptyMap(), activeChapterId = null)
            }
            _chapterCards.value = emptyList()
            if (packId == null) Log.d(logTag, "loadChapters: no active pack, clearing chapters")
            else Log.d(logTag, "loadChapters: pack $packId has no chapters, clearing")
            return
        }

        Log.d(logTag, "loadChapters: loading chapters for pack $packId")
        val chapters = lessonStore.getChapters(packId)
        // Compute from current mastery, never the stale on-disk chapter snapshot.
        val languageId = coreState.value.navigation.selectedLanguageId?.value ?: ""
        val progresses = repository.computeAllChapterProgress(chapters, packId, languageId)
        val activeChapterId = repository.determineActiveChapter(chapters, progresses)
        Log.d(logTag, "loadChapters: updating state with ${chapters.size} chapters, activeChapterId=$activeChapterId")
        coreState.update {
            it.copy(chapters = chapters, chapterProgresses = progresses, activeChapterId = activeChapterId)
        }
        refreshCards()
    }

    fun updateChapterProgress(lessonId: String) {
        val nav = coreState.value.navigation
        val packId = nav.activePackId?.value ?: return
        val languageId = nav.selectedLanguageId?.value ?: return
        if (!lessonStore.hasChapters(packId)) return

        val chapters = lessonStore.getChapters(packId)
        val affected = repository.getAffectedChapterProgresses(chapters, lessonId, packId, languageId)
        if (affected.isEmpty()) return
        for (progress in affected) {
            coreState.update {
                it.copy(chapterProgresses = it.chapterProgresses.toMutableMap().apply {
                    put(progress.chapterId, progress)
                })
            }
        }
        refreshCards()
    }
}

package com.alexpo.grammermate.v2.feature.packcontent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.domain.model.Chapter
import com.alexpo.grammermate.domain.model.Lesson
import com.alexpo.grammermate.domain.model.Pack
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel экрана содержимого пака — главы с уроками (Фаза 1 плана:
 * путь Pack → Chapter → Lesson вместо `lessonId = packId`).
 *
 * Грузит пак + главы одним входом, уроки пака одним запросом и группирует их
 * по главам в порядке `order` (без N+1). Уроки вне глав (manifest v1)
 * попадают в секцию «Уроки» без главы.
 */
@HiltViewModel
class PackContentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contentRepository: ContentRepository,
) : ViewModel() {

    /** Секция списка: глава (null = уроки вне глав) + её уроки по order. */
    data class Section(val chapter: Chapter?, val lessons: List<Lesson>)

    data class ViewState(
        val isLoading: Boolean = true,
        val pack: Pack? = null,
        val sections: List<Section> = emptyList(),
        val error: String? = null,
    )

    private val packId: PackId = PackId(savedStateHandle.get<String>("packId").orEmpty())

    private val _state = MutableStateFlow(ViewState())
    val state: StateFlow<ViewState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching {
                val pack = contentRepository.getPack(packId)
                    ?: error("Пак не найден: ${packId.value}")
                val chapters = contentRepository.getChapters(packId)
                val lessons = contentRepository.getLessons(packId)
                Triple(pack, chapters, lessons)
            }.onSuccess { (pack, chapters, lessons) ->
                _state.value = ViewState(
                    isLoading = false,
                    pack = pack,
                    sections = buildSections(chapters, lessons),
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Не удалось загрузить пак",
                )
            }
        }
    }

    /** Главы по order; уроки каждой главы по order; уроки без главы — хвостовой секцией. */
    private fun buildSections(chapters: List<Chapter>, lessons: List<Lesson>): List<Section> {
        val byChapter = lessons.filter { it.chapterId != null }
            .groupBy { it.chapterId!! }
            .mapValues { (_, list) -> list.sortedBy(Lesson::order) }
        val knownChapterIds = chapters.map(Chapter::id).toSet()
        val sections = chapters
            .sortedBy(Chapter::order)
            .map { chapter ->
                Section(chapter = chapter, lessons = byChapter[chapter.id].orEmpty())
            }
            .toMutableList()
        // Уроки вне известных глав (manifest v1 / упавший CSV) не теряются.
        val orphans = lessons
            .filter { it.chapterId == null || it.chapterId !in knownChapterIds }
            .sortedBy(Lesson::order)
        if (orphans.isNotEmpty()) {
            sections.add(Section(chapter = null, lessons = orphans))
        }
        return sections
    }
}

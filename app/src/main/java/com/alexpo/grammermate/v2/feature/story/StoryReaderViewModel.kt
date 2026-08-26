package com.alexpo.grammermate.v2.feature.story

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.story.MultilingualStoryParser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * FSM story reader (срез 6 Фазы 4): Loading → Content(paragraphs) | Error.
 */
sealed interface StoryReaderViewState {
    data object Loading : StoryReaderViewState
    data class Content(val title: String, val paragraphs: List<String>) : StoryReaderViewState
    data class Error(val message: String) : StoryReaderViewState
}

/**
 * ViewModel story reader (срез 6 Фазы 4, шаг 3b).
 *
 * Резолвит `chapters[].storyFile` по chapterId, читает текст через
 * [ContentRepository.getStoryText] (файлы кладёт importer — шаг 3a) и
 * подготавливает к отображению: языковые маркеры/{pause} снимаются доменным
 * [MultilingualStoryParser.stripMarkers], текст режется на абзацы.
 */
@HiltViewModel
class StoryReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contentRepository: ContentRepository,
) : ViewModel() {

    private val packId: PackId = PackId(savedStateHandle.get<String>("packId").orEmpty())
    private val chapterId: String = savedStateHandle.get<String>("chapterId").orEmpty()

    private val _state = MutableStateFlow<StoryReaderViewState>(StoryReaderViewState.Loading)
    val state: StateFlow<StoryReaderViewState> = _state.asStateFlow()

    init {
        if (packId.value.isBlank() || chapterId.isBlank()) {
            _state.value = StoryReaderViewState.Error("Некорректный маршрут")
        } else {
            load()
        }
    }

    fun load() {
        if (packId.value.isBlank() || chapterId.isBlank()) return
        _state.value = StoryReaderViewState.Loading
        viewModelScope.launch {
            runCatching {
                val chapter = contentRepository.getChapters(packId)
                    .firstOrNull { it.id.value == chapterId }
                    ?: return@runCatching null
                val storyFile = chapter.storyFile
                    ?: return@runCatching chapter.title to null // глава без стори
                chapter.title to contentRepository.getStoryText(packId, storyFile)
            }.onSuccess { result ->
                _state.value = when {
                    result == null ->
                        StoryReaderViewState.Error("Глава не найдена")
                    result.second == null ->
                        StoryReaderViewState.Error("У этой главы нет истории (или файл не импортирован)")
                    else -> StoryReaderViewState.Content(
                        title = result.first,
                        paragraphs = MultilingualStoryParser.stripMarkers(result.second!!)
                            .split("\n\n")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            // Markdown-заголовки читаются как текст без '#'
                            // (полная cleanMarkdown склеивает абзацы — не для ридера).
                            .map { it.replace(Regex("^#{1,6}\\s+"), "") }
                            .filter { it.isNotEmpty() },
                    )
                }
            }.onFailure { e ->
                _state.value = StoryReaderViewState.Error(e.message ?: "Не удалось загрузить историю")
            }
        }
    }
}

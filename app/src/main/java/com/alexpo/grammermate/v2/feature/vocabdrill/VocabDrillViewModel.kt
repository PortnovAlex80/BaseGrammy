package com.alexpo.grammermate.v2.feature.vocabdrill

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.VocabWord
import com.alexpo.grammermate.domain.repository.VocabDrillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * FSM vocab drill (Фаза 4 срез 3): Anki-style карточки слов.
 *
 * Question (слово, перевод скрыт) → Revealed (перевод + знаю/не знаю) →
 * следующий Question → … → Done. Без SessionEngine-сессий по дизайну строки
 * mode-matrix: durable-состояние режима — сам word-SRS ([ADR-003] pack-
 * scoped `recordWordReview` фиксирует каждый ответ немедленно).
 */
sealed interface VocabDrillViewState {
    data object Loading : VocabDrillViewState
    data class Empty(val message: String) : VocabDrillViewState
    data class Question(val word: VocabWord, val index: Int, val total: Int) : VocabDrillViewState
    data class Revealed(val word: VocabWord, val index: Int, val total: Int) : VocabDrillViewState
    data class Done(val reviewed: Int, val correct: Int) : VocabDrillViewState
    data class Error(val message: String) : VocabDrillViewState
}

/**
 * ViewModel vocab drill (Фаза 4 срез 3).
 *
 * Батч ([BATCH_SIZE]) детерминирован: сначала due-слова пака (самые
 * просроченные), затем — новые слова по рангу частотности (без mastery-строки).
 * Правило §3.1.5: результат ответа публикуется только после успешного commit
 * `recordWordReview`; ошибка → Error без продвижения.
 */
@HiltViewModel
class VocabDrillViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vocabDrillRepository: VocabDrillRepository,
) : ViewModel() {

    private val packId: PackId = PackId(savedStateHandle.get<String>("packId").orEmpty())

    private val _state = MutableStateFlow<VocabDrillViewState>(VocabDrillViewState.Loading)
    val state: StateFlow<VocabDrillViewState> = _state.asStateFlow()

    private val commands = Mutex()

    /** Батч карточек (wordId → слово); порядок фиксируется при загрузке. */
    private var batch: List<VocabWord> = emptyList()
    private var position = 0
    private var correct = 0

    init {
        if (packId.value.isBlank()) {
            _state.value = VocabDrillViewState.Error("Некорректный маршрут")
        } else {
            loadBatch()
        }
    }

    /** Собрать батч: due-слова пака + добор новыми по рангу. */
    fun loadBatch() {
        if (packId.value.isBlank()) return
        _state.value = VocabDrillViewState.Loading
        viewModelScope.launch {
            commands.withLock {
                runCatching {
                    val due = vocabDrillRepository.observeDueWords(packId, BATCH_SIZE)
                        .first()
                        .map { it.first }
                    val reviewedIds = vocabDrillRepository.getAllWordMastery(packId).keys
                    val fresh = vocabDrillRepository.getVocabWords(packId)
                        .filter { it.id !in reviewedIds && it.id !in due }
                    (due + fresh.map { it.id })
                        .take(BATCH_SIZE) to vocabDrillRepository.getVocabWords(packId)
                }.onSuccess { (ids, allWords) ->
                    val byId = allWords.associateBy { it.id }
                    batch = ids.mapNotNull(byId::get)
                    position = 0
                    correct = 0
                    publishCurrentOrDone()
                }.onFailure { e ->
                    _state.value = VocabDrillViewState.Error(e.message ?: "Не удалось загрузить слова")
                }
            }
        }
    }

    /** Показать перевод текущего слова (без записи SRS). */
    fun reveal() {
        val q = _state.value as? VocabDrillViewState.Question ?: return
        _state.value = VocabDrillViewState.Revealed(q.word, q.index, q.total)
    }

    /** Зафиксировать ответ и перейти к следующему слову батча. */
    fun answer(isCorrect: Boolean) {
        val r = _state.value as? VocabDrillViewState.Revealed ?: return
        viewModelScope.launch {
            commands.withLock {
                if (_state.value !is VocabDrillViewState.Revealed) return@withLock
                runCatching {
                    vocabDrillRepository.recordWordReview(
                        packId = packId,
                        wordId = r.word.id,
                        isCorrect = isCorrect,
                        nowMs = System.currentTimeMillis(),
                    )
                }.onSuccess {
                    if (isCorrect) correct++
                    position++
                    publishCurrentOrDone()
                }.onFailure { e ->
                    _state.value = VocabDrillViewState.Error(e.message ?: "Не удалось сохранить ответ")
                }
            }
        }
    }

    private fun publishCurrentOrDone() {
        if (batch.isEmpty()) {
            _state.value = VocabDrillViewState.Empty("В этом паке пока нет слов для изучения")
            return
        }
        if (position >= batch.size) {
            _state.value = VocabDrillViewState.Done(reviewed = batch.size, correct = correct)
            return
        }
        _state.value = VocabDrillViewState.Question(
            word = batch[position],
            index = position + 1,
            total = batch.size,
        )
    }

    private companion object {
        const val BATCH_SIZE = 10
    }
}

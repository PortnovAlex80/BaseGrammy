package com.alexpo.grammermate.v2.feature.daily

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.domain.daily.DailyContent
import com.alexpo.grammermate.domain.daily.DailySettings
import com.alexpo.grammermate.domain.daily.DailyTaskComposer
import com.alexpo.grammermate.domain.model.CardType
import com.alexpo.grammermate.domain.model.DailyBlockType
import com.alexpo.grammermate.domain.model.DailyCursor
import com.alexpo.grammermate.domain.model.DailyTask
import com.alexpo.grammermate.domain.model.InputMode
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.VocabDrillDirection
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.repository.ProgressRepository
import com.alexpo.grammermate.domain.repository.VocabDrillRepository
import com.alexpo.grammermate.domain.validation.AnswerValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * FSM дневной нормы (срез 4 Фазы 4): Question → Answered → … → Done.
 * Режим безсессионный (как vocab): durable-состояние — курсор дня +
 * word-SRS для флешкарт; ответы translate/verbs эфемерны (follow-up строки
 * матрицы — проводка через lesson mastery).
 */
sealed interface DailyViewState {
    data object Loading : DailyViewState
    data class Empty(val message: String) : DailyViewState
    data class Question(val task: DailyTask, val index: Int, val total: Int) : DailyViewState
    data class Answered(
        val task: DailyTask,
        val index: Int,
        val total: Int,
        val correct: Boolean,
        val correctAnswer: String?,
    ) : DailyViewState

    data class Done(val reviewed: Int, val correct: Int) : DailyViewState
    data class Error(val message: String) : DailyViewState
}

/**
 * ViewModel дневной нормы (срез 4 Фазы 4): {5 TRANSLATE, 3 VOCAB, 2 VERBS}
 * из [DailyTaskComposer] по курсору пака.
 *
 * По завершении дня курсор двигается на число ПОТРЕБЛЁННЫХ задач каждого
 * блока (`sentenceOffset/verbOffset/vocabOffset`) — следующий день получает
 * новый срез контента. «Знаю» на флешкарте фиксирует word-SRS (ADR-003
 * pack-scoped) немедленно.
 */
@HiltViewModel
class DailyPracticeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val progressRepository: ProgressRepository,
    private val contentRepository: ContentRepository,
    private val vocabDrillRepository: VocabDrillRepository,
    private val answerValidator: AnswerValidator,
) : ViewModel() {

    private val packId: PackId = PackId(savedStateHandle.get<String>("packId").orEmpty())

    private val _state = MutableStateFlow<DailyViewState>(DailyViewState.Loading)
    val state: StateFlow<DailyViewState> = _state.asStateFlow()

    private val commands = Mutex()

    private var tasks: List<DailyTask> = emptyList()
    private var position = 0
    private var correct = 0
    private var consumed = mutableMapOf<DailyBlockType, Int>()
    private var baseCursor: DailyCursor? = null

    init {
        if (packId.value.isBlank()) {
            _state.value = DailyViewState.Error("Некорректный маршрут")
        } else {
            load()
        }
    }

    /** Собрать дневную норму по курсору пака (Retry = тот же путь). */
    fun load() {
        if (packId.value.isBlank()) return
        _state.value = DailyViewState.Loading
        viewModelScope.launch {
            commands.withLock {
                runCatching {
                    val cursor = progressRepository.getDailyCursor(packId)
                        ?: DailyCursor(
                            packId = packId,
                            sentenceOffset = 0,
                            currentLessonIndex = 0,
                            verbOffset = 0,
                            vocabOffset = 0,
                            firstSessionDate = null,
                            firstSessionSentenceCardIds = emptyList(),
                            firstSessionVerbCardIds = emptyList(),
                            firstSessionLessonId = null,
                        )
                    val sentences = contentRepository.getLessons(packId)
                        .flatMap { lesson -> contentRepository.getCards(packId, lesson.id) }
                        .filter { it.type == CardType.SENTENCE }
                    val content = DailyContent(
                        sentenceCards = sentences,
                        verbCards = vocabDrillRepository.getVerbDrillCards(packId, null),
                        vocabWords = vocabDrillRepository.getVocabWords(packId),
                    )
                    cursor to DailyTaskComposer.compose(cursor, content, DEFAULT_SETTINGS)
                }.onSuccess { (cursor, composed) ->
                    baseCursor = cursor
                    tasks = composed
                    position = 0
                    correct = 0
                    consumed = mutableMapOf()
                    publishCurrentOrEmpty()
                }.onFailure { e ->
                    _state.value = DailyViewState.Error(e.message ?: "Не удалось собрать дневную норму")
                }
            }
        }
    }

    /** Показать перевод флешкарты (без записи SRS). */
    fun revealVocab() {
        val q = _state.value as? DailyViewState.Question ?: return
        val task = q.task as? DailyTask.VocabFlashcard ?: return
        _state.value = DailyViewState.Answered(
            task = task,
            index = q.index,
            total = q.total,
            correct = true,
            correctAnswer = task.word.meaningRu,
        )
    }

    /**
     * Ответ на задачу: translate/verbs — валидация ввода; vocab — «Знаю» с
     * немедленной записью word-SRS. Публикация — только после commit.
     */
    fun submitAnswer(answer: String) {
        val q = _state.value as? DailyViewState.Question ?: return
        viewModelScope.launch {
            commands.withLock {
                if (_state.value !is DailyViewState.Question) return@withLock
                when (val task = q.task) {
                    is DailyTask.TranslateSentence -> runCatching {
                        answerValidator.validate(
                            input = answer,
                            acceptedAnswers = task.card.acceptedAnswers,
                            inputMode = task.inputMode,
                        ).isCorrect
                    }.onSuccess { isCorrect ->
                        publishAnswered(task, isCorrect, if (isCorrect) null else task.card.acceptedAnswers.firstOrNull())
                    }.onFailure { e ->
                        _state.value = DailyViewState.Error(e.message ?: "Не удалось проверить ответ")
                    }

                    is DailyTask.ConjugateVerb -> runCatching {
                        answerValidator.validate(
                            input = answer,
                            acceptedAnswers = listOf(task.card.answer),
                            inputMode = task.inputMode,
                        ).isCorrect
                    }.onSuccess { isCorrect ->
                        publishAnswered(task, isCorrect, if (isCorrect) null else task.card.answer)
                    }.onFailure { e ->
                        _state.value = DailyViewState.Error(e.message ?: "Не удалось проверить ответ")
                    }

                    is DailyTask.VocabFlashcard -> runCatching {
                        vocabDrillRepository.recordWordReview(
                            packId = packId,
                            wordId = task.word.id,
                            isCorrect = true,
                            nowMs = System.currentTimeMillis(),
                        )
                    }.onSuccess {
                        publishAnswered(task, correct = true, correctAnswer = null)
                    }.onFailure { e ->
                        _state.value = DailyViewState.Error(e.message ?: "Не удалось сохранить ответ")
                    }
                }
            }
        }
    }

    /** «Не знаю» на флешкарте — запись word-SRS и показ перевода. */
    fun vocabDontKnow() {
        val q = _state.value as? DailyViewState.Question ?: return
        val task = q.task as? DailyTask.VocabFlashcard ?: return
        viewModelScope.launch {
            commands.withLock {
                if (_state.value !is DailyViewState.Question) return@withLock
                runCatching {
                    vocabDrillRepository.recordWordReview(
                        packId = packId,
                        wordId = task.word.id,
                        isCorrect = false,
                        nowMs = System.currentTimeMillis(),
                    )
                }.onSuccess {
                    publishAnswered(task, correct = false, correctAnswer = task.word.meaningRu)
                }.onFailure { e ->
                    _state.value = DailyViewState.Error(e.message ?: "Не удалось сохранить ответ")
                }
            }
        }
    }

    /** Следующая задача; на последней — зафиксировать курсор и Done. */
    fun next() {
        val a = _state.value as? DailyViewState.Answered ?: return
        consumed[a.task.blockType] = (consumed[a.task.blockType] ?: 0) + 1
        position++
        viewModelScope.launch {
            commands.withLock {
                if (_state.value !is DailyViewState.Answered) return@withLock
                if (position >= tasks.size) {
                    finishDay()
                } else {
                    publishCurrentOrEmpty()
                }
            }
        }
    }

    private suspend fun finishDay() {
        val base = baseCursor ?: return
        runCatching {
            progressRepository.saveDailyCursor(
                base.copy(
                    sentenceOffset = base.sentenceOffset + (consumed[DailyBlockType.TRANSLATE] ?: 0),
                    verbOffset = base.verbOffset + (consumed[DailyBlockType.VERBS] ?: 0),
                    vocabOffset = base.vocabOffset + (consumed[DailyBlockType.VOCAB] ?: 0),
                ),
            )
        }.onSuccess {
            _state.value = DailyViewState.Done(reviewed = position, correct = correct)
        }.onFailure { e ->
            _state.value = DailyViewState.Error(e.message ?: "Не удалось сохранить прогресс дня")
        }
    }

    private fun publishAnswered(task: DailyTask, correct: Boolean, correctAnswer: String?) {
        if (correct) this.correct++
        _state.value = DailyViewState.Answered(
            task = task,
            index = position + 1,
            total = tasks.size,
            correct = correct,
            correctAnswer = correctAnswer,
        )
    }

    private fun publishCurrentOrEmpty() {
        if (tasks.isEmpty()) {
            _state.value = DailyViewState.Empty("Нет контента для дневной нормы — импортируйте пак")
            return
        }
        _state.value = DailyViewState.Question(task = tasks[position], index = position + 1, total = tasks.size)
    }

    private companion object {
        /** AC-16: 5 переводов + 3 слова + 2 спряжения. */
        val DEFAULT_SETTINGS = DailySettings(
            blockConfig = linkedMapOf(
                DailyBlockType.TRANSLATE to 5,
                DailyBlockType.VOCAB to 3,
                DailyBlockType.VERBS to 2,
            ),
            inputMode = InputMode.KEYBOARD,
            vocabDirection = VocabDrillDirection.IT_TO_RU,
        )
    }
}

package com.alexpo.grammermate.v2.feature.verbdrill

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.domain.TrainingConfig
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.InputMode
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionId
import com.alexpo.grammermate.domain.model.SessionSnapshot
import com.alexpo.grammermate.domain.model.SessionStatus
import com.alexpo.grammermate.domain.model.VerbDrillCard
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.session.SessionEngine
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
 * FSM verb drill (Фаза 4 срез 2): те же правила, что Training — одна фаза в
 * момент времени, success публикуется только после commit.
 */
sealed interface VerbDrillViewState {
    data object Loading : VerbDrillViewState
    data class Empty(val message: String) : VerbDrillViewState
    data class Active(
        val card: String,
        val promptRu: String,
        val answeredCards: Int,
        val totalCards: Int,
    ) : VerbDrillViewState

    data class Checking(val card: String) : VerbDrillViewState
    data class Feedback(
        val card: String,
        val correct: Boolean,
        val correctAnswer: String?,
        val answeredCards: Int,
        val totalCards: Int,
    ) : VerbDrillViewState

    data class Completed(val correctCount: Int, val incorrectCount: Int, val totalCards: Int) : VerbDrillViewState
    data class Error(val message: String) : VerbDrillViewState
}

/**
 * ViewModel verb drill (Фаза 4 срез 2): спряжение по combo-фильтрам.
 *
 * Модель та же, что Training (ADR-001): единственный mutating-путь —
 * [SessionEngine]; success-фазы публикуются только после commit; команды
 * сериализованы. Пул строит [SessionEngine.startVerbDrillSession]
 * (частотность + combo-фильтры персистятся в снимке).
 */
@HiltViewModel
class VerbDrillViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionEngine: SessionEngine,
    private val contentRepository: ContentRepository,
    private val answerValidator: AnswerValidator,
) : ViewModel() {

    private val packId: PackId = PackId(savedStateHandle.get<String>("packId").orEmpty())
    private val sessionId: SessionId = SessionId.forVerbDrill(packId)

    private val _state = MutableStateFlow<VerbDrillViewState>(VerbDrillViewState.Loading)
    val state: StateFlow<VerbDrillViewState> = _state.asStateFlow()

    private val commands = Mutex()

    /** Карточки combo-выборки по id — грузятся один раз на вход. */
    private var cardsById: Map<String, VerbDrillCard> = emptyMap()

    init {
        if (packId.value.isBlank()) {
            _state.value = VerbDrillViewState.Error("Некорректный маршрут")
        } else {
            reload()
        }
    }

    /** Создать/возобновить drill-сессию; завершённая перезапускается свежей. */
    fun reload() {
        if (packId.value.isBlank()) return
        _state.value = VerbDrillViewState.Loading
        viewModelScope.launch {
            commands.withLock {
                runCatching {
                    val resumed = sessionEngine.resumeSession(sessionId)
                    if (resumed != null && resumed.status != SessionStatus.COMPLETED) resumed
                    else sessionEngine.startVerbDrillSession(
                        packId = packId,
                        sessionSize = TrainingConfig.SUB_LESSON_SIZE_DEFAULT,
                    )
                }.onSuccess { snapshot ->
                    cardsById = runCatching {
                        contentRepository.getVerbDrillCards(
                            packId = packId,
                            tense = snapshot.selectedTense,
                            group = snapshot.selectedGroup,
                            person = snapshot.selectedPerson,
                        )
                    }.getOrElse { emptyList() }.associateBy { it.id }
                    applySnapshot(snapshot)
                }.onFailure { e ->
                    _state.value = VerbDrillViewState.Error(e.message ?: "Не удалось загрузить спряжение")
                }
            }
        }
    }

    /** Проверить ответ (форма глагола) и зафиксировать в сессии. */
    fun submitAnswer(answer: String) {
        val active = _state.value as? VerbDrillViewState.Active ?: return
        if (answer.isBlank()) return
        _state.value = VerbDrillViewState.Checking(active.card)
        viewModelScope.launch {
            commands.withLock {
                if (_state.value !is VerbDrillViewState.Checking) return@withLock
                val card = cardsById[active.card]
                    ?: run {
                        _state.value = VerbDrillViewState.Error("Карточка вне выборки")
                        return@withLock
                    }
                runCatching {
                    val validation = answerValidator.validate(
                        input = answer,
                        acceptedAnswers = listOf(card.answer),
                        inputMode = InputMode.KEYBOARD,
                    )
                    sessionEngine.submitAnswer(
                        sessionId = sessionId,
                        cardId = CardId(active.card),
                        isCorrect = validation.isCorrect,
                        inputMode = InputMode.KEYBOARD,
                    ) to validation.isCorrect
                }.onSuccess { (snapshot, isCorrect) ->
                    _state.value = VerbDrillViewState.Feedback(
                        card = active.card,
                        correct = isCorrect,
                        correctAnswer = if (isCorrect) null else card.answer,
                        answeredCards = snapshot.correctCount + snapshot.incorrectCount,
                        totalCards = snapshot.poolCardIds.size,
                    )
                }.onFailure { e ->
                    _state.value = VerbDrillViewState.Error(e.message ?: "Не удалось сохранить ответ")
                }
            }
        }
    }

    /** Следующая карточка / завершение на последней. */
    fun next() {
        if (_state.value !is VerbDrillViewState.Feedback) return
        viewModelScope.launch {
            commands.withLock {
                if (_state.value !is VerbDrillViewState.Feedback) return@withLock
                runCatching { sessionEngine.nextCardOrComplete(sessionId) }
                    .onSuccess { snapshot ->
                        if (snapshot.status == SessionStatus.COMPLETED) {
                            _state.value = VerbDrillViewState.Completed(
                                correctCount = snapshot.correctCount,
                                incorrectCount = snapshot.incorrectCount,
                                totalCards = snapshot.poolCardIds.size,
                            )
                        } else {
                            applySnapshot(snapshot)
                        }
                    }
                    .onFailure { e ->
                        _state.value = VerbDrillViewState.Error(e.message ?: "Не удалось сохранить прогресс")
                    }
            }
        }
    }

    private fun applySnapshot(snapshot: SessionSnapshot) {
        if (snapshot.poolCardIds.isEmpty()) {
            _state.value = VerbDrillViewState.Empty("Нет карточек спряжения для этого набора фильтров")
            return
        }
        val current = snapshot.currentCardId?.value
        if (current == null || !cardsById.containsKey(current)) {
            _state.value = VerbDrillViewState.Error("Сессия ссылается на карточку вне выборки")
            return
        }
        _state.value = VerbDrillViewState.Active(
            card = current,
            promptRu = cardsById.getValue(current).promptRu,
            answeredCards = snapshot.correctCount + snapshot.incorrectCount,
            totalCards = snapshot.poolCardIds.size,
        )
    }
}

package com.alexpo.grammermate.v2.feature.training

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.v2.core.domain.model.Card
import com.alexpo.grammermate.v2.core.domain.model.CardId
import com.alexpo.grammermate.v2.core.domain.model.LessonId
import com.alexpo.grammermate.v2.core.domain.model.PackId
import com.alexpo.grammermate.v2.core.domain.model.SessionId
import com.alexpo.grammermate.v2.core.domain.model.TrainingMode
import com.alexpo.grammermate.v2.core.domain.repository.ContentRepository
import com.alexpo.grammermate.v2.core.domain.repository.MasteryRepository
import com.alexpo.grammermate.v2.core.domain.repository.SessionRepository
import com.alexpo.grammermate.v2.core.ui.MviReducer
import com.alexpo.grammermate.v2.core.ui.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * ViewModel экрана тренировки — координация тренировочной сессии.
 *
 * Сидит на базовом [MviViewModel]: чистый [trainingReducer] формирует UI-проекцию
 * intents, а здесь — side-effects (загрузка сессии, проверка ответов, advance по
 * пулу, persist прогресса, навигация).
 *
 * Поток:
 *  1. **init** — читает контекст (packId/lessonId) из [SavedStateHandle]
 *     (nav-args из `Destination.Training`), запускает [loadSession].
 *  2. **loadSession** — создаёт/возобновляет сессию через [SessionRepository],
 *     тянет карточки урока из [ContentRepository], выставляет `currentCard` и
 *     `totalCards` (размер пула).
 *  3. **onSubmitAnswer** — проверяет ответ (нормализация + сравнение с
 *     [Card.acceptedAnswers]), фиксирует результат в сессии, выставляет
 *     мгновенную UI-обратную связь (`lastResult`).
 *  4. **onNextCard** — двигает курсор сессии, обновляет `currentCard`.
 *
 * Полная SRS-логика (FSRS-пересчёт, persist mastery, TTS) — TODO Фаза 6; сейчас
 * реализован минимально-рабочий цикл «карточка → ответ → следующая», достаточный
 * для отладки presentation-слоя.
 *
 * @property savedStateHandle   nav-args (packId, lessonId) из [Destination.Training].
 * @property sessionRepository  управление тренировочными сессиями (фикс card_15).
 * @property contentRepository  read-only контент (карточки урока).
 * @property masteryRepository  освоение/SRS-пересчёт карточек.
 */
@HiltViewModel
class TrainingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val contentRepository: ContentRepository,
    private val masteryRepository: MasteryRepository,
) : MviViewModel<TrainingViewState, TrainingIntent, TrainingEffect>(
    initialState = TrainingViewState(isLoading = true),
    reducer = MviReducer { state, intent -> trainingReducer(state, intent) },
) {

    /** Контекст тренировки, распарсенный из nav-args. */
    private val packId: PackId = PackId(
        savedStateHandle.get<String>("packId").orEmpty(),
    )
    private val lessonId: LessonId = LessonId(
        savedStateHandle.get<String>("lessonId").orEmpty(),
    )

    /** Стабильный ID сессии урока (resume-точка). */
    private val sessionId: SessionId = SessionId.forLesson(packId, lessonId)

    init {
        loadSession()
    }

    /**
     * Загрузить/возобновить сессию при старте экрана.
     *
     * Берёт сессию (create-or-resume) по [sessionId], тянет карточки урока и
     * выставляет текущую карточку (по `currentCardId` из снимка) и размер пула.
     * На ошибку — кладёт сообщение в [TrainingViewState.error].
     */
    private fun loadSession() {
        viewModelScope.launch {
            runCatching {
                val snapshot = sessionRepository.getOrCreateSession(
                    sessionId = sessionId,
                    packId = packId,
                    lessonId = lessonId,
                    mode = TrainingMode.LESSON,
                )
                val cards = contentRepository.getCards(lessonId)
                val byId = cards.associateBy { it.id }
                val current = snapshot.currentCardId?.let(byId::get)
                Triple(snapshot, cards, current)
            }.onSuccess { (snapshot, cards, current) ->
                updateState {
                    it.copy(
                        isLoading = false,
                        currentCard = current ?: cards.firstOrNull(),
                        totalCards = snapshot.poolCardIds.size.coerceAtLeast(cards.size),
                        answeredCards = snapshot.shownCardIds.size,
                        error = null,
                    )
                }
            }.onFailure { e ->
                updateState {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Не удалось загрузить тренировку",
                    )
                }
            }
        }
    }

    /**
     * Проверить ответ пользователя и зафиксировать результат.
     *
     * Нормализует ввод (trim + lower-case) и сравнивает с любым из
     * [Card.acceptedAnswers] (также нормализованных). Результат уходит в сессию
     * (через [SessionRepository.updateProgress]) и в state (`lastResult`).
     *
     * `updateProgress` ставит абсолютные счётчики correct/incorrect, поэтому
     * сначала читаем снимок сессии, чтобы инкрементировать от актуальных значений.
     *
     * @param answer введённый пользователем текст.
     */
    fun onSubmitAnswer(answer: String) {
        val card = currentState.currentCard ?: return
        viewModelScope.launch {
            val normalized = answer.trim().lowercase()
            val isCorrect = card.acceptedAnswers.any { it.trim().lowercase() == normalized }
            runCatching {
                val snapshot = sessionRepository.loadSession(sessionId)
                val newCorrect = (snapshot?.correctCount ?: 0) + (if (isCorrect) 1 else 0)
                val newIncorrect = (snapshot?.incorrectCount ?: 0) + (if (!isCorrect) 1 else 0)
                val newHints = (snapshot?.hintCount ?: 0) + (if (currentState.showHint) 1 else 0)
                sessionRepository.updateProgress(sessionId, newCorrect, newIncorrect, newHints)
                sessionRepository.markCardShown(sessionId, card.id)
            }
            updateState {
                it.copy(
                    lastResult = AnswerResult(isCorrect),
                    answeredCards = it.answeredCards + 1,
                )
            }
        }
    }

    /**
     * Перейти к следующей карточке пула.
     *
     * Пересчитывает `currentCardId` на следующую карту пула (с зацикливанием),
     * persist через [SessionRepository.setCurrentCard] и обновляет state.
     */
    fun onNextCard() {
        val card = currentState.currentCard ?: return
        viewModelScope.launch {
            runCatching {
                val snapshot = sessionRepository.loadSession(sessionId)
                val pool = snapshot?.poolCardIds ?: return@launch
                val nextId = nextInPool(pool, card.id)
                nextId?.let { sessionRepository.setCurrentCard(sessionId, it) }
                nextId
            }.onSuccess { nextId ->
                if (nextId != null) {
                    val cards = contentRepository.getCards(lessonId)
                    val nextCard = cards.firstOrNull { it.id == nextId }
                    updateState {
                        it.copy(
                            currentCard = nextCard,
                            showHint = false,
                            lastResult = null,
                        )
                    }
                }
            }
        }
    }

    /** Запросить подсказку (делегирует в reducer через intent). */
    fun requestHint() = onIntent(TrainingIntent.RequestHint)

    /** Пометить карточку флажком («плохое» предложение). TODO Фаза 6: persist. */
    fun flagCard() = onIntent(TrainingIntent.FlagCard)

    /** Выйти с экрана тренировки (навигация через эффект). */
    fun navigateBack() = emitEffect(TrainingEffect.NavigateBack)

    // ── Внутренние хелперы ───────────────────────────────────────────────────

    /** Следующая карточка пула после [currentId] (с зацикливанием). */
    private fun nextInPool(pool: List<CardId>, currentId: CardId): CardId? {
        if (pool.isEmpty()) return null
        val idx = pool.indexOf(currentId)
        val nextIdx = if (idx < 0) 0 else (idx + 1) % pool.size
        return pool[nextIdx]
    }
}

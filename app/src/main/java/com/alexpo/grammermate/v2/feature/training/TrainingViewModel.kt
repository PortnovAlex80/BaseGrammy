package com.alexpo.grammermate.v2.feature.training

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.domain.TrainingConfig
import com.alexpo.grammermate.domain.model.Card
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.InputMode
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionId
import com.alexpo.grammermate.domain.model.SessionSnapshot
import com.alexpo.grammermate.domain.model.SessionStatus
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.session.SessionEngine
import com.alexpo.grammermate.domain.validation.AnswerValidator
import com.alexpo.grammermate.v2.core.ui.MviReducer
import com.alexpo.grammermate.v2.core.ui.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ViewModel экрана тренировки — сериализация и оркестрация, БЕЗ бизнес-правил
 * (Фаза 1 плана стабилизации 2026-08-26, ADR-001).
 *
 * Единственный mutating-путь сессии — доменный [SessionEngine] (план §3.1.2);
 * ViewModel НЕ импортирует mutating API `SessionRepository`. Все команды одной
 * сессии сериализованы через [commands]-mutex; повторный Submit/Next в фазе
 * commit игнорируется по фазе FSM (reducer no-op + проверка фазы под локом).
 *
 * Поток команды (план §3.2):
 *
 * ```
 * Compose action → TrainingIntent → reducer (фаза UI)
 *     → ViewModel (orchestration, mutex)
 *     → SessionEngine (правила: пул/advance/completion/счётчики)
 *     → Room commit (saveSession, одна транзакция)
 *     → публикация фазы FSM (Feedback/Active/Completed) ТОЛЬКО после commit
 * ```
 *
 * Правило §3.1.5: упавший persist НЕ публикует success —
 * [TrainingViewState.Error] вместо [TrainingViewState.Feedback]
 * (`TrainingViewModelRegressionTest.submitAnswer_persistenceFailure_…`).
 *
 * Draft ответа живёт в [SavedStateHandle] (ключ [DRAFT_CARD_KEY] +
 * [DRAFT_TEXT_KEY]): rotation/process death восстанавливают ввод для той же
 * карточки, смена карточки сбрасывает его.
 *
 * Back-семантика (MODE_MATRIX.md → Navigation): сессия durable после каждого
 * commit, поэтому Back — выход без потерь (авто-pause: снимок остаётся ACTIVE,
 * повторный вход resume'ит тот же PK с той же карточкой).
 *
 * @property savedStateHandle  nav-args (packId, lessonId) + draft ответа.
 * @property sessionEngine     единственный mutating-путь сессии.
 * @property contentRepository read-only контент (карточки урока).
 * @property answerValidator   доменная проверка ответа (Normalizer, `+`-альтернативы).
 */
@HiltViewModel
class TrainingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionEngine: SessionEngine,
    private val contentRepository: ContentRepository,
    private val answerValidator: AnswerValidator,
) : MviViewModel<TrainingViewState, TrainingIntent, TrainingEffect>(
    initialState = TrainingViewState.Loading,
    reducer = MviReducer { state, intent -> trainingReducer(state, intent) },
) {

    private val packId: PackId = PackId(
        savedStateHandle.get<String>("packId").orEmpty(),
    )
    private val lessonId: LessonId = LessonId(
        savedStateHandle.get<String>("lessonId").orEmpty(),
    )

    /** Стабильный ID сессии урока (resume-точка). */
    private val sessionId: SessionId = SessionId.forLesson(packId, lessonId)

    /** Сериализация команд сессии (план §3.1.6): Submit/Next/Skip выполняются строго по одному. */
    private val commands = Mutex()

    /** Карточки урока по PK — грузятся один раз на вход, advance не перечитывает контент. */
    private var cardsById: Map<CardId, Card> = emptyMap()

    init {
        reload()
    }

    // ── Загрузка / resume ─────────────────────────────────────────────────────

    /**
     * (Пере)загрузить сессию: resume существующей по PK либо старт новой с
     * реальным пулом ([SessionEngine.startLessonSession] строит пул: карты урока
     * минус скрытые, нарезка под-уроков). Завершённая (COMPLETED) сессия
     * перезапускается свежим проходом с тем же PK.
     */
    fun reload() {
        onIntent(TrainingIntent.StartSession)
        viewModelScope.launch {
            commands.withLock {
                runCatching {
                    val resumed = sessionEngine.resumeSession(sessionId)
                    if (resumed != null && resumed.status != SessionStatus.COMPLETED) {
                        resumed
                    } else {
                        sessionEngine.startLessonSession(
                            packId = packId,
                            lessonId = lessonId,
                            sessionSize = TrainingConfig.SUB_LESSON_SIZE_DEFAULT,
                        )
                    }
                }.onSuccess { snapshot ->
                    // Контент читаем в той же command-критической секции —
                    // projection согласована со снимком.
                    cardsById = runCatching {
                        contentRepository.getCards(lessonId).associateBy { it.id }
                    }.getOrElse { emptyMap() }
                    applySession(snapshot)
                }.onFailure { e ->
                    updateState { TrainingViewState.Error(e.message ?: "Не удалось загрузить тренировку") }
                }
            }
        }
    }

    // ── Команды пользователя ──────────────────────────────────────────────────

    /**
     * Проверить ответ и зафиксировать его в сессии.
     *
     * Валидация — доменный [AnswerValidator] (нормализация, `+`-альтернативы),
     * фиксация — [SessionEngine.submitAnswer] (счётчики + shown одной
     * транзакцией; mastery-gate по InputMode). Feedback публикуется только
     * после успешного commit.
     */
    fun submitAnswer() {
        val active = currentState as? TrainingViewState.Active ?: return
        val answer = active.draft
        if (answer.isBlank()) return
        onIntent(TrainingIntent.SubmitAnswer)
        viewModelScope.launch {
            commands.withLock {
                val checking = currentState as? TrainingViewState.Checking ?: return@withLock
                val card = checking.card
                runCatching {
                    val validation = answerValidator.validate(
                        input = answer,
                        acceptedAnswers = card.acceptedAnswers,
                        inputMode = InputMode.KEYBOARD,
                    )
                    val updated = sessionEngine.submitAnswer(
                        sessionId = sessionId,
                        cardId = card.id,
                        isCorrect = validation.isCorrect,
                        inputMode = InputMode.KEYBOARD,
                    )
                    updated to validation.isCorrect
                }.onSuccess { (snapshot, isCorrect) ->
                    clearDraft()
                    updateState {
                        TrainingViewState.Feedback(
                            card = card,
                            result = AnswerResult(isCorrect),
                            correctAnswer = if (isCorrect) null else card.acceptedAnswers.firstOrNull(),
                            answeredCards = snapshot.correctCount + snapshot.incorrectCount,
                            totalCards = snapshot.poolCardIds.size,
                            isLastCard = snapshot.poolCardIds.lastOrNull() == card.id,
                        )
                    }
                }.onFailure { e ->
                    // Persist упал → карточка НЕ зачтена: никакого success-UI.
                    updateState { TrainingViewState.Error(e.message ?: "Не удалось сохранить ответ") }
                }
            }
        }
    }

    /**
     * Следующая карточка (из Feedback) / завершение урока на последней карте.
     * Терминальное условие — в [SessionEngine.nextCardOrComplete].
     */
    fun next() {
        if (currentState !is TrainingViewState.Feedback) return
        viewModelScope.launch {
            commands.withLock {
                if (currentState !is TrainingViewState.Feedback) return@withLock
                advanceAfterCommit()
            }
        }
    }

    /** Пропустить карточку без ответа (из Active): advance без shown-метки и счётчиков. */
    fun skip() {
        if (currentState !is TrainingViewState.Active) return
        viewModelScope.launch {
            commands.withLock {
                if (currentState !is TrainingViewState.Active) return@withLock
                advanceAfterCommit()
            }
        }
    }

    /** Изменение черновика ответа (переживает rotation/process death). */
    fun onDraftChange(text: String) {
        onIntent(TrainingIntent.DraftChanged(text))
        (currentState as? TrainingViewState.Active)?.let { active ->
            savedStateHandle[DRAFT_CARD_KEY] = active.card.id.value
            savedStateHandle[DRAFT_TEXT_KEY] = text
        }
    }

    /** Запросить подсказку (эфемерный UI; persist hintCount — Фаза 2). */
    fun requestHint() = onIntent(TrainingIntent.RequestHint)

    /** Пометить карточку флажком («плохое» предложение). Persist — Фаза 3. */
    fun flagCard() = onIntent(TrainingIntent.FlagCard)

    /**
     * Выйти с экрана: сессия durable (каждый commit — saveSession), снимок
     * остаётся ACTIVE; повторный вход resume'ит тот же PK (MODE_MATRIX.md).
     */
    fun navigateBack() = emitEffect(TrainingEffect.NavigateBack)

    // ── Внутренние хелперы ────────────────────────────────────────────────────

    /** Advance после команды (Next/Skip): публикует Active/Completed после commit. */
    private suspend fun advanceAfterCommit() {
        runCatching { sessionEngine.nextCardOrComplete(sessionId) }
            .onSuccess { snapshot ->
                clearDraft()
                if (snapshot.status == SessionStatus.COMPLETED) {
                    updateState {
                        TrainingViewState.Completed(
                            correctCount = snapshot.correctCount,
                            incorrectCount = snapshot.incorrectCount,
                            totalCards = snapshot.poolCardIds.size,
                        )
                    }
                } else {
                    applySession(snapshot)
                }
            }
            .onFailure { e ->
                updateState { TrainingViewState.Error(e.message ?: "Не удалось сохранить прогресс") }
            }
    }

    /**
     * Спроецировать снимок в фазу UI. Без fallback-карточки (план §3.1.4):
     * пустой пул → [TrainingViewState.Empty]; карточка вне контента →
     * [TrainingViewState.Error] (данные рассогласованы — это не молчаливая подмена).
     */
    private fun applySession(snapshot: SessionSnapshot) {
        if (snapshot.poolCardIds.isEmpty()) {
            updateState { TrainingViewState.Empty("В уроке нет доступных карточек") }
            return
        }
        val card = snapshot.currentCardId?.let(cardsById::get)
        if (card == null) {
            updateState {
                TrainingViewState.Error("Сессия ссылается на карточку вне контента урока")
            }
            return
        }
        updateState {
            TrainingViewState.Active(
                card = card,
                draft = restoreDraftFor(card.id),
                showHint = false,
                answeredCards = snapshot.correctCount + snapshot.incorrectCount,
                totalCards = snapshot.poolCardIds.size,
            )
        }
    }

    /** Draft из SavedStateHandle — только если он принадлежит текущей карточке. */
    private fun restoreDraftFor(cardId: CardId): String {
        val savedCard = savedStateHandle.get<String>(DRAFT_CARD_KEY)
        return if (savedCard == cardId.value) {
            savedStateHandle.get<String>(DRAFT_TEXT_KEY).orEmpty()
        } else {
            ""
        }
    }

    private fun clearDraft() {
        savedStateHandle[DRAFT_TEXT_KEY] = ""
    }

    private companion object {
        const val DRAFT_CARD_KEY = "training_draft_card_id"
        const val DRAFT_TEXT_KEY = "training_draft_text"
    }
}

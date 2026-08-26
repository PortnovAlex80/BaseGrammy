package com.alexpo.grammermate.v2.feature.training

/**
 * Чистый редюсер экрана тренировки: `(state, intent) → state` над FSM
 * [TrainingViewState] (Фаза 1 плана стабилизации 2026-08-26, §3.3).
 *
 * **БЕЗ side-effects:** не трогает репозитории/БД/TTS/навигацию. Только
 * детерминированные синхронные переходы фаз. Side-effects (commit через
 * SessionEngine, навигация) живут в [TrainingViewModel] и публикуют новую фазу
 * через `updateState` только после успешного commit (правило плана §3.1.5).
 *
 * Переходы вне допустимой фазы — no-op (повторный Submit в `Checking`,
 * Next в `Active` и т.п.): команда игнорируется, состояние не портится.
 *
 * exhaustive `when` → компилятор не даст забыть новый intent.
 */
fun trainingReducer(state: TrainingViewState, intent: TrainingIntent): TrainingViewState =
    when (intent) {
        // (Пере)загрузка сессии: в Loading; результат опубликует ViewModel.
        TrainingIntent.StartSession -> TrainingViewState.Loading

        // Черновик живёт только в Active.
        is TrainingIntent.DraftChanged -> (state as? TrainingViewState.Active)
            ?.copy(draft = intent.text)
            ?: state

        // Подсказка — чистый UI-toggle в Active.
        TrainingIntent.RequestHint -> (state as? TrainingViewState.Active)
            ?.copy(showHint = true)
            ?: state

        // Submit: Active → Checking (ввод заблокирован до публикации Feedback/Error).
        TrainingIntent.SubmitAnswer -> when (state) {
            is TrainingViewState.Active -> TrainingViewState.Checking(
                card = state.card,
                answeredCards = state.answeredCards,
                totalCards = state.totalCards,
            )
            else -> state
        }

        // Next/Skip: реальный advance — side-effect в ViewModel; новая фаза
        // (Active/Completed/Error) публикуется после commit.
        TrainingIntent.NextCard,
        TrainingIntent.SkipCard -> state

        // Флаг карточки — пока только UI-noop (persist Report/Flag — Фаза 3).
        TrainingIntent.FlagCard -> state

        // Выход с экрана — state не меняется; навигация идёт через эффект.
        TrainingIntent.NavigateBack -> state
    }

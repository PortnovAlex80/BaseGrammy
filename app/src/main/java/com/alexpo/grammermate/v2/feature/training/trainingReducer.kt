package com.alexpo.grammermate.v2.feature.training

/**
 * Чистый редюсер экрана тренировки: `(state, intent) → state`.
 *
 * **БЕЗ side-effects:** не трогает репозитории/БД/TTS/навигацию. Только
 * детерминированная трансформация [TrainingViewState] по [TrainingIntent].
 * Тестируется на чистом JVM (например, Truth-ассертами, см. test/.../training).
 *
 * Side-effects (persist сессии, навигация, TTS) живут в [TrainingViewModel] и
 * запускаются по контексту intent'а, а НЕ здесь. Здесь лишь «UI-проекция» события.
 *
 * exhaustive `when` → компилятор не даст забыть новый intent.
 *
 * @param state  текущий state экрана.
 * @param intent намерение для обработки.
 * @return новый state.
 */
fun trainingReducer(state: TrainingViewState, intent: TrainingIntent): TrainingViewState =
    when (intent) {
        // Старт/возобновление сессии — переводим в loading; реальную загрузку и
        // заполнение currentCard выполняет ViewModel через updateState (Фаза 6).
        is TrainingIntent.StartSession,
        is TrainingIntent.Resume -> state.copy(isLoading = true, error = null)

        // Подсказка — чистый UI-toggle, без I/O.
        TrainingIntent.RequestHint -> state.copy(showHint = true)

        // Флаг карточки — пока только UI-маркер (TODO Фаза 6: persist флага).
        TrainingIntent.FlagCard -> state

        // Ответ пользователя — мгновенная UI-обратная связь (например, очистка
        // подсказки); проверка ответа и persist — в ViewModel (Фаза 6).
        is TrainingIntent.SubmitAnswer -> state.copy(showHint = false)

        // SRS-рейтинг — запоминаем для мгновенной отрисовки; persist карточки
        // и пересчёт расписания — в ViewModel через masteryRepository (Фаза 6).
        is TrainingIntent.RateCard -> state.copy(lastRating = intent.rating)

        // Выход с экрана — state не меняется; навигация идёт через эффект.
        TrainingIntent.NavigateBack -> state
    }

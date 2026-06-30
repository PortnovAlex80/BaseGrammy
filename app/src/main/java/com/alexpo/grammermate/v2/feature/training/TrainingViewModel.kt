package com.alexpo.grammermate.v2.feature.training

import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.v2.core.domain.repository.MasteryRepository
import com.alexpo.grammermate.v2.core.domain.repository.SessionRepository
import com.alexpo.grammermate.v2.core.ui.MviReducer
import com.alexpo.grammermate.v2.core.ui.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * ViewModel экрана тренировки — пример применения MVI-core на конкретном экране.
 *
 * Скелет (НЕ полная реализация): показывает паттерн, как экран садится на базовый
 * класс [MviViewModel]. Реальная бизнес-логика (загрузка сессии, проверка ответов,
 * persist прогресса, SRS-пересчёт, TTS) будет добавлена в Фазе 6.
 *
 * Разделение ответственности:
 *  * **reducer** (pure) — детерминированная UI-проекция intent'ов ([trainingReducer]).
 *  * **ViewModel** — координация side-effects: repository-calls, навигация, TTS.
 *  * **state** ([TrainingViewState]) — единственный источник истины для View.
 *
 * Аннотирован [@HiltViewModel][HiltViewModel] → инжектируется в Compose через
 * `hiltViewModel()` из `androidx.hilt.navigation.compose`.
 *
 * @property sessionRepository управление тренировочными сессиями (см. card_15 fix).
 * @property masteryRepository  освоение/SRS-пересчёт карточек.
 * @constructor Hilt-инжекция репозиториев.
 */
@HiltViewModel
class TrainingViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val masteryRepository: MasteryRepository,
) : MviViewModel<TrainingViewState, TrainingIntent, TrainingEffect>(
    initialState = TrainingViewState(),
    reducer = MviReducer { state, intent -> trainingReducer(state, intent) },
) {

    init {
        loadInitialSession()
    }

    /**
     * Загрузить/возобновить сессию при старте экрана.
     *
     * TODO Фаза 6: получить контекст (packId/lessonId) из SavedStateHandle/nav-args,
     * вызвать `sessionRepository.getOrCreateSession(...)`, наполнить
     * [TrainingViewState.currentCard] через `updateState { }` и при необходимости
     * кинуть [TrainingEffect.ShowToast]/[PlayTts].
     */
    private fun loadInitialSession() {
        viewModelScope.launch {
            // TODO Фаза 6:
            //   val snapshot = sessionRepository.getOrCreateSession(...)
            //   updateState { it.copy(isLoading = false, currentCard = /* resolved Card */, error = null) }
        }
    }
}

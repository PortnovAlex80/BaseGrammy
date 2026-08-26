package com.alexpo.grammermate.v2.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.domain.model.PackLessonProgress
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.repository.MasteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel экрана Home — реактивный список паков с прогрессом для сетки.
 *
 * Home — экран навигации верхнего уровня (точка входа в обучение). Здесь нет
 * сложного MVI (как в тренировке): пользователь только выбирает пак, поэтому
 * используется простой state-holder на [MutableStateFlow].
 *
 * Фаза 1 плана стабилизации 2026-08-26: подписка на [ContentRepository.observePacks]
 * вместо одноразового `getPacks` — fresh install видит bundled-пак, как только
 * завершится первый seed-import.
 *
 * Фаза 3 (slice 2) + ADR-002 слой 1: [combine] списка паков с агрегатом
 * [MasteryRepository.observePackProgress] — завершение урока в тренировке
 * мгновенно отражается на карточке пака (фиктивный 0% удалён ещё в Фазе 1).
 *
 * @property contentRepository read-only доступ к контенту паков.
 * @property masteryRepository  read-only агрегаты прогресса (completedAtMs).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    masteryRepository: MasteryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeViewState(isLoading = true))
    val state: StateFlow<HomeViewState> = _state.asStateFlow()

    init {
        observePacks(masteryRepository)
    }

    /**
     * Реактивная подписка «паки + прогресс»; ошибка любого канала — в
     * [HomeViewState.error]. Прогресс приходить может позже паков (Room
     * инвалидация mastery_states) — тогда обновится через copy без мигания
     * списка.
     */
    private fun observePacks(masteryRepository: MasteryRepository) {
        combine(
            contentRepository.observePacks(),
            masteryRepository.observePackProgress(),
        ) { packs, progress ->
            packs to progress.associateBy { it.packId.value }
        }
            .onEach { (packs, progress) ->
                _state.value = HomeViewState(
                    packs = packs,
                    packProgress = progress,
                    isLoading = false,
                    error = null,
                )
            }
            .catch { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Не удалось загрузить паки",
                )
            }
            .launchIn(viewModelScope)
    }

    /** Повторить подписку после ошибки (колбэк для retry-кнопки в UI). */
    fun retry() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            // Одноразовый re-query для мгновенного фидбека; реактивная подписка
            // из init продолжает работать и обновит state при изменениях.
            runCatching { contentRepository.getPacks() }
                .onSuccess { packs ->
                    _state.value = _state.value.copy(packs = packs, isLoading = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Не удалось загрузить паки",
                    )
                }
        }
    }
}

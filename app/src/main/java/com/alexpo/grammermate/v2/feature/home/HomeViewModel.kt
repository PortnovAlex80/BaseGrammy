package com.alexpo.grammermate.v2.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.domain.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel экрана Home — реактивный список паков для сетки.
 *
 * Home — экран навигации верхнего уровня (точка входа в обучение). Здесь нет
 * сложного MVI (как в тренировке): пользователь только выбирает пак, поэтому
 * используется простой state-holder на [MutableStateFlow].
 *
 * Фаза 1 плана стабилизации 2026-08-26: подписка на [ContentRepository.observePacks]
 * вместо одноразового `getPacks` — fresh install видит bundled-пак, как только
 * завершится первый seed-import (P2 «Home не реактивен» закрыт для списка паков;
 * реактивный progress-процент — Фаза 3).
 *
 * @property contentRepository read-only доступ к контенту паков.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeViewState(isLoading = true))
    val state: StateFlow<HomeViewState> = _state.asStateFlow()

    init {
        observePacks()
    }

    /** Реактивная подписка на список паков; ошибка канала — в [HomeViewState.error]. */
    private fun observePacks() {
        contentRepository.observePacks()
            .onEach { packs ->
                _state.value = HomeViewState(packs = packs, isLoading = false, error = null)
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
                    _state.value = HomeViewState(packs = packs, isLoading = false)
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

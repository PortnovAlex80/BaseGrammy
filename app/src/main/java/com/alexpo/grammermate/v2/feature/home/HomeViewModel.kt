package com.alexpo.grammermate.v2.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.v2.core.domain.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel экрана Home — загрузка списка паков для сетки.
 *
 * Home — экран навигации верхнего уровня (точка входа в обучение). Здесь нет
 * сложного MVI (как в тренировке): пользователь не «взаимодействует» со state,
 * а только выбирает пак. Поэтому используется простой state-holder на
 * [MutableStateFlow] вместо полного MviViewModel — меньше шаблонного кода без
 * потери single-source-of-truth.
 *
 * Загрузка паков идёт через [ContentRepository.getPacks] (одноразовое
 * suspend-чтение). Ошибки перехватываются и кладутся в [HomeViewState.error],
 * чтобы UI показал retry-вместо краша.
 *
 * @property contentRepository read-only доступ к контенту паков.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeViewState(isLoading = true))
    /** State экрана — единственный источник истины для View. */
    val state: StateFlow<HomeViewState> = _state.asStateFlow()

    init {
        loadPacks()
    }

    /**
     * Загрузить паки из [ContentRepository] и наполнить state.
     *
     * Тащит `ContentRepository.getPacks()` (all packs across languages). На
     * ошибку — `error` сообщение в state; UI может предложить retry через [retry].
     */
    private fun loadPacks() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
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

    /** Повторить загрузку паков (колбэк для retry-кнопки в UI). */
    fun retry() = loadPacks()
}

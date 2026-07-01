package com.alexpo.grammermate.v2.feature.home

import com.alexpo.grammermate.v2.core.domain.model.Pack

/**
 * State экрана Home (сетка паков) — single source of truth для UI.
 *
 * Формируется [HomeViewModel] из [com.alexpo.grammermate.v2.core.domain.repository.ContentRepository].
 * View читает ровно один [HomeViewState] и перерисовывается детерминированно.
 *
 * Поля намеренно плоские (а не `UiState<List<Pack>>`), чтобы UI разветвлялся по
 * простым флагам `isLoading`/`error` — так проще и прозрачнее на старте.
 *
 * @property packs     список доступных паков обучения (пусто — нет контента).
 * @property isLoading идёт ли фоновая загрузка (показ skeleton/spinner).
 * @property error     человекочитаемое сообщение об ошибке (null — ошибок нет).
 */
data class HomeViewState(
    val packs: List<Pack> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

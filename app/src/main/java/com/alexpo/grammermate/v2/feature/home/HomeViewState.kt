package com.alexpo.grammermate.v2.feature.home

import com.alexpo.grammermate.domain.model.Pack
import com.alexpo.grammermate.domain.model.PackLessonProgress

/**
 * State экрана Home (сетка паков) — single source of truth для UI.
 *
 * Формируется [HomeViewModel] из [com.alexpo.grammermate.domain.repository.ContentRepository]
 * (список паков) и [com.alexpo.grammermate.domain.repository.MasteryRepository]
 * (агрегат прогресса — ADR-002 слой 1). View читает ровно один
 * [HomeViewState] и перерисовывается детерминированно.
 *
 * Поля намеренно плоские (а не `UiState<List<Pack>>`), чтобы UI разветвлялся по
 * простым флагам `isLoading`/`error` — так проще и прозрачнее на старте.
 *
 * @property packs        список доступных паков обучения (пусто — нет контента).
 * @property packProgress прогресс по packId (ключ — `Pack.id.value`); пак без
 *                        записи = 0 завершённых уроков.
 * @property isLoading    идёт ли фоновая загрузка (показ skeleton/spinner).
 * @property error        человекочитаемое сообщение об ошибке (null — ошибок нет).
 */
data class HomeViewState(
    val packs: List<Pack> = emptyList(),
    val packProgress: Map<String, PackLessonProgress> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

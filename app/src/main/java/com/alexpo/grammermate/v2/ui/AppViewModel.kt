package com.alexpo.grammermate.v2.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.v2.core.domain.model.ThemeMode
import com.alexpo.grammermate.v2.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * App-scoped ViewModel — глобальное UI-состояние всего приложения.
 *
 * В отличие от экранов (которые сидят на [com.alexpo.grammermate.v2.core.ui.MviViewModel]),
 * этот ViewModel не управляет конкретным экраном — он держит cross-cutting state:
 * тему приложения (LIGHT/DARK/SYSTEM), взятую реактивно из [SettingsRepository].
 *
 * Подписка на `observeAppConfig()` стартует лениво (`WhileSubscribed(5000)`) —
 * корутина крутится, только пока [GrammarMateApp] на экране, и гаснет через 5 с
 * после ухода, экономя батарею. Это стандартный приём для app-scoped UI-flow.
 *
 * @property settingsRepository key-value настройки приложения.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /**
     * Глобальное UI-состояние: тема приложения, пересчитанная из [SettingsRepository].
     *
     * `map { it.themeMode }` → [AppState]; стартует при появлении подписчика.
     */
    val state: StateFlow<AppState> = settingsRepository.observeAppConfig()
        .map { AppState(themeMode = it.themeMode) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AppState(), // SYSTEM до первой эмиссии из DataStore
        )
}

/**
 * Состояние приложения на app-уровне (тема и глобальные флаги).
 *
 * Простой holder, который [AppViewModel] наполняет из [SettingsRepository].
 * Сейчас тут только тема; по мере роста (например, глобальный loading) —
 * расширяется без переделки интерфейса.
 *
 * @property themeMode пользовательский режим темы (LIGHT/DARK/SYSTEM).
 */
data class AppState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

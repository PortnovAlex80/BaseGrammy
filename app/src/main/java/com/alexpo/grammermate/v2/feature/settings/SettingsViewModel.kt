package com.alexpo.grammermate.v2.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.domain.model.AppConfig
import com.alexpo.grammermate.domain.model.ThemeMode
import com.alexpo.grammermate.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** FSM настроек (Фаза 7: последний Placeholder → реальный экран). */
sealed interface SettingsViewState {
    data object Loading : SettingsViewState
    data class Content(
        val themeMode: ThemeMode,
        val sessionSize: Int,
    ) : SettingsViewState
    data class Error(val message: String) : SettingsViewState
}

/**
 * ViewModel настроек: режим темы (LIGHT/DARK/SYSTEM) через
 * [SettingsRepository.updateAppConfig] — применяется реактивно
 * (AppViewModel подписан на AppConfig).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SettingsViewState>(SettingsViewState.Loading)
    val state: StateFlow<SettingsViewState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeAppConfig()
                .collect { config ->
                    _state.value = SettingsViewState.Content(
                        themeMode = config.themeMode,
                        sessionSize = config.sessionSize,
                    )
                }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            runCatching {
                settingsRepository.updateAppConfig { it.copy(themeMode = mode) }
            }.onFailure { e ->
                _state.value = SettingsViewState.Error(e.message ?: "Не удалось сохранить настройку")
            }
        }
    }

    fun setSessionSize(size: Int) {
        viewModelScope.launch {
            runCatching {
                settingsRepository.updateAppConfig { it.copy(sessionSize = size) }
            }.onFailure { e ->
                _state.value = SettingsViewState.Error(e.message ?: "Не удалось сохранить настройку")
            }
        }
    }
}

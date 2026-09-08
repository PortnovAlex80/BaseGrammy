package com.alexpo.grammermate.testharness

import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.feature.daily.TrainingStateAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory fake implementation of TrainingStateAccess for testing.
 * No file I/O, no Android dependencies.
 */
class FakeTrainingStateAccess(
    initialState: TrainingUiState = TrainingUiState()
) : TrainingStateAccess {

    private val _uiState = MutableStateFlow(initialState)
    override val uiState: StateFlow<TrainingUiState> = _uiState

    override fun updateState(transform: (TrainingUiState) -> TrainingUiState) {
        _uiState.value = transform(_uiState.value)
    }

    override fun saveProgress() {
        // No-op for tests
    }

    fun setState(state: TrainingUiState) {
        _uiState.value = state
    }
}

package com.alexpo.grammermate.shared

import com.alexpo.grammermate.data.TrainingUiState
import kotlinx.coroutines.flow.StateFlow

/** Narrow read/write bridge for the shared training state owned by the ViewModel. */
interface TrainingStateAccess {
    val uiState: StateFlow<TrainingUiState>
    fun updateState(transform: (TrainingUiState) -> TrainingUiState)
    fun saveProgress()
}

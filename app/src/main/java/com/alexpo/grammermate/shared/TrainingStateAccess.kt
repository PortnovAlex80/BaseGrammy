package com.alexpo.grammermate.shared

import com.alexpo.grammermate.data.TrainingUiState

/** Synchronous command-side snapshot and writer for ViewModel-owned training state. */
interface TrainingStateAccess {
    val currentState: TrainingUiState
    fun updateState(transform: (TrainingUiState) -> TrainingUiState)
    fun saveProgress()
}

package com.alexpo.grammermate.v2.core.seed

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface BundledSeedState {
    data object Idle : BundledSeedState
    data object Running : BundledSeedState
    data object Ready : BundledSeedState
    data class Failed(val message: String) : BundledSeedState
}

@Singleton
class BundledSeedStatus @Inject constructor() {
    private val mutableState = MutableStateFlow<BundledSeedState>(BundledSeedState.Idle)
    val state: StateFlow<BundledSeedState> = mutableState.asStateFlow()
    private var retryAction: (suspend () -> Unit)? = null

    fun publish(state: BundledSeedState) {
        mutableState.value = state
    }

    fun registerRetry(action: suspend () -> Unit) {
        retryAction = action
    }

    suspend fun retry() {
        retryAction?.invoke()
    }
}

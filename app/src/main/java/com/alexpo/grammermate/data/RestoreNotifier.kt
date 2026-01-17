package com.alexpo.grammermate.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object RestoreNotifier {
    private val _restoreState = MutableStateFlow(RestoreState())
    val restoreState: StateFlow<RestoreState> = _restoreState

    fun start() {
        _restoreState.value = _restoreState.value.copy(
            status = RestoreStatus.IN_PROGRESS,
            token = _restoreState.value.token + 1
        )
    }

    fun requireUser() {
        _restoreState.value = _restoreState.value.copy(
            status = RestoreStatus.NEEDS_USER,
            token = _restoreState.value.token + 1
        )
    }

    fun markComplete(restored: Boolean) {
        _restoreState.value = _restoreState.value.copy(
            status = RestoreStatus.DONE,
            token = _restoreState.value.token + 1,
            restored = restored
        )
    }
}

data class RestoreState(
    val token: Int = 0,
    val status: RestoreStatus = RestoreStatus.IDLE,
    val restored: Boolean = false
)

enum class RestoreStatus {
    IDLE,
    IN_PROGRESS,
    NEEDS_USER,
    DONE
}

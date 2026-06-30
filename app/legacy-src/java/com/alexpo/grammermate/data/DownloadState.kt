package com.alexpo.grammermate.data

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val percent: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Extracting(val percent: Int) : DownloadState()
    data class Initializing(val phase: InitPhase, val percent: Int) : DownloadState()
    object Done : DownloadState()
    data class Error(val message: String) : DownloadState()
}

enum class InitPhase {
    CHECKING_FILES,
    LOADING_MODEL,
    PREPARING_ENGINE
}

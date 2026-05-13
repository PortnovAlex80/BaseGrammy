package com.alexpo.grammermate.shared

/**
 * Result types for [SettingsActionHandler] methods.
 * Replaces [SettingsCallbacks] — each method returns a list of results
 * instead of calling callbacks.
 */
sealed class SettingsResult {
    object None : SettingsResult()
    data class RefreshLessons(val selectedLessonId: String?) : SettingsResult()
    data class ResetStores(val app: android.app.Application) : SettingsResult()
    data class ResetStoresForLanguage(val app: android.app.Application, val languageId: String) : SettingsResult()
    data class ResetDrillFiles(val app: android.app.Application) : SettingsResult()
    data class ResetDrillFilesForPack(val app: android.app.Application, val packId: String) : SettingsResult()
    object ClearWordMastery : SettingsResult()
    object ResetDailyState : SettingsResult()
    object SetForceBackup : SettingsResult()
    object SaveProgress : SettingsResult()
}

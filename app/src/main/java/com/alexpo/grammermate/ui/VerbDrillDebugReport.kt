package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.VerbDrillComboProgress
import com.alexpo.grammermate.data.VerbDrillStore
import com.alexpo.grammermate.data.VerbDrillStoreImpl
import java.io.File

/** Builds the Verb Drill support report from a captured pack and progress snapshot. */
internal object VerbDrillDebugReport {
    fun build(
        packId: String?,
        progressMap: Map<String, VerbDrillComboProgress>,
        store: VerbDrillStore
    ): String = buildString {
        appendLine("=== Verb Drill Debug Info ===")
        appendLine()
        appendLine("Pack ID: ${packId ?: "null (legacy mode)"}")
        appendLine()

        appendLine("Progress Map Size: ${progressMap.size} entries")
        if (progressMap.isNotEmpty()) {
            appendLine("Progress Keys:")
            progressMap.keys.forEach { key ->
                val progress = progressMap[key]
                appendLine("  - $key: everShown=${progress?.everShownCardIds?.size ?: 0}, todayShown=${progress?.todayShownCardIds?.size ?: 0}")
            }
        }
        appendLine()

        val lastSession = store.loadLastSession()
        if (lastSession != null) {
            appendLine("Last Session State:")
            appendLine("  - Tense: ${lastSession.selectedTense ?: "null"}")
            appendLine("  - Group: ${lastSession.selectedGroup ?: "null"}")
            appendLine("  - SortByFrequency: ${lastSession.sortByFrequency}")
            appendLine("  - TodayShownCardIds: ${lastSession.todayShownCardIds.size}")
            appendLine("  - SessionCardIds: ${lastSession.sessionCardIds.size}")
            appendLine("  - CurrentIndex: ${lastSession.currentIndex}")
        } else {
            appendLine("Last Session State: null")
        }
        appendLine()

        if (store is VerbDrillStoreImpl) {
            try {
                val fileField = store.javaClass.getDeclaredField("file")
                fileField.isAccessible = true
                val file = fileField.get(store) as? File
                appendLine("Progress File Path: ${file?.absolutePath ?: "unknown"}")
                appendLine("Progress File Exists: ${file?.exists() ?: false}")
                appendLine("Progress File Size: ${if (file?.exists() == true) "${file.length()} bytes" else "N/A"}")

                val lastSessionFileField = store.javaClass.getDeclaredField("lastSessionFile")
                lastSessionFileField.isAccessible = true
                val lastSessionFile = lastSessionFileField.get(store) as? File
                appendLine("Last Session File Path: ${lastSessionFile?.absolutePath ?: "unknown"}")
                appendLine("Last Session File Exists: ${lastSessionFile?.exists() ?: false}")
                appendLine("Last Session File Size: ${if (lastSessionFile?.exists() == true) "${lastSessionFile.length()} bytes" else "N/A"}")
            } catch (e: Exception) {
                appendLine("Error accessing file info: ${e.message}")
            }
        }
    }
}

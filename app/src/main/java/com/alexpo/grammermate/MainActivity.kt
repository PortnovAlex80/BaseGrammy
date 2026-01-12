package com.alexpo.grammermate

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.alexpo.grammermate.data.BackupManager
import com.alexpo.grammermate.ui.GrammarMateApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check and restore from backup on app first launch
        initializeProgressData()

        setContent {
            GrammarMateApp()
        }
    }

    private fun initializeProgressData() {
        val backupManager = BackupManager(this)

        // Check if app data exists
        val masteryFile = java.io.File(filesDir, "grammarmate/mastery.yaml")
        val hasExistingData = masteryFile.exists()

        if (!hasExistingData && backupManager.hasBackup()) {
            // First launch after reinstall - show notification about available backups
            val backups = backupManager.getAvailableBackups()
            if (backups.isNotEmpty()) {
                val latestBackup = backups.first()
                val restored = backupManager.restoreFromBackup(latestBackup.path)
                if (restored) {
                    Toast.makeText(
                        this,
                        "Progress restored from backup (${latestBackup.name})",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "Failed to restore backup",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}

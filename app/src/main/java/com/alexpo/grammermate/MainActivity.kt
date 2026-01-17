package com.alexpo.grammermate

import android.os.Bundle
import android.os.Environment
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.alexpo.grammermate.data.BackupManager
import com.alexpo.grammermate.ui.GrammarMateApp
import java.io.File

class MainActivity : ComponentActivity() {
    private val storagePermissionsRequestCode = 101
    private val backupTreeUriKey = "backup_tree_uri"

    private val openBackupTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
            getSharedPreferences("backup_prefs", MODE_PRIVATE)
                .edit()
                .putString(backupTreeUriKey, uri.toString())
                .apply()
            initializeProgressData()
        } else {
            Toast.makeText(
                this,
                "Backup folder not selected",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("backup_prefs", MODE_PRIVATE)
        val storedTreeUri = prefs.getString(backupTreeUriKey, null)

        if (storedTreeUri != null) {
            initializeProgressData()
        } else if (android.os.Build.VERSION.SDK_INT >= 29) {
            Toast.makeText(
                this,
                "Select BaseGrammy backup folder in Downloads",
                Toast.LENGTH_LONG
            ).show()
            openBackupTreeLauncher.launch(null)
        } else if (hasStoragePermissions()) {
            // Check and restore from backup on app first launch
            initializeProgressData()
        } else {
            requestStoragePermissions()
        }

        setContent {
            GrammarMateApp()
        }
    }

    private fun initializeProgressData() {
        val backupManager = BackupManager(this)
        val backupRoot = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "BaseGrammy"
        )
        val prefs = getSharedPreferences("backup_prefs", MODE_PRIVATE)
        val storedTreeUri = prefs.getString(backupTreeUriKey, null)?.let { Uri.parse(it) }

        // Check if app data exists
        val masteryFile = java.io.File(filesDir, "grammarmate/mastery.yaml")
        val hasExistingData = masteryFile.exists()

        if (storedTreeUri != null) {
            val backups = backupManager.getAvailableBackups(storedTreeUri)
            if (!hasExistingData && backups.isNotEmpty()) {
                val latestBackup = backups.first()
                val restored = backupManager.restoreFromBackupUri(Uri.parse(latestBackup.path))
                if (restored) {
                    Toast.makeText(
                        this,
                        "Backup restored from ${latestBackup.path}",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "Backup restore failed from ${latestBackup.path}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                val latestPath = backups.firstOrNull()?.path ?: "none"
                Toast.makeText(
                    this,
                    "Backup check (SAF): existing=$hasExistingData, latest=$latestPath",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            Toast.makeText(
                this,
                "Backup check: SAF not selected",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!hasExistingData && backupManager.hasBackup()) {
            // First launch after reinstall - show notification about available backups
            val backups = backupManager.getAvailableBackups()
            if (backups.isNotEmpty()) {
                val latestBackup = backups.first()
                val restored = backupManager.restoreFromBackup(latestBackup.path)
                if (restored) {
                    Toast.makeText(
                        this,
                        "Backup restored from ${latestBackup.path}",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "Backup restore failed from ${latestBackup.path}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    this,
                    "Backup check: no backups in ${backupRoot.absolutePath}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else if (!hasExistingData) {
            Toast.makeText(
                this,
                "Backup check: no backups in ${backupRoot.absolutePath}",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            val backupPath = backupManager.getAvailableBackups().firstOrNull()?.path ?: "none"
            Toast.makeText(
                this,
                "Backup check: existing data, latest=$backupPath",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != storagePermissionsRequestCode) return
        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (granted) {
            initializeProgressData()
        } else {
            Toast.makeText(
                this,
                "Storage permission denied; backups in Downloads won't be read",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun hasStoragePermissions(): Boolean {
        return requiredStoragePermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermissions() {
        ActivityCompat.requestPermissions(
            this,
            requiredStoragePermissions(),
            storagePermissionsRequestCode
        )
    }

    private fun requiredStoragePermissions(): Array<String> {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }
}

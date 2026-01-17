package com.alexpo.grammermate

import android.os.Bundle
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.alexpo.grammermate.data.BackupManager
import com.alexpo.grammermate.data.ProfileStore
import com.alexpo.grammermate.data.RestoreNotifier
import com.alexpo.grammermate.ui.AppRoot
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val storagePermissionsRequestCode = 101
    private val backupTreeUriKey = "backup_tree_uri"
    private val backupManager by lazy { BackupManager(this) }

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
            startRestoreFromUri(uri)
        } else {
            Toast.makeText(
                this,
                "Backup folder not selected",
                Toast.LENGTH_SHORT
            ).show()
            RestoreNotifier.markComplete(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("backup_prefs", MODE_PRIVATE)
        val storedTreeUri = prefs.getString(backupTreeUriKey, null)

        // Check if app data already exists
        val baseDir = File(filesDir, "grammarmate")
        val masteryFile = File(baseDir, "mastery.yaml")
        val progressFile = File(baseDir, "progress.yaml")
        val profileFile = File(baseDir, "profile.yaml")
        val hasFullData = masteryFile.exists() && progressFile.exists() && profileFile.exists()
        val profile = ProfileStore(this).load()
        val shouldRestore = !hasFullData || profile.userName == "GrammarMateUser"

        if (storedTreeUri != null) {
            // We have a stored URI - use it for restore if needed
            if (shouldRestore) {
                startRestoreFromUri(Uri.parse(storedTreeUri))
            } else {
                RestoreNotifier.markComplete(false)
            }
        } else if (android.os.Build.VERSION.SDK_INT >= 29) {
            // Android 10+: request SAF access for restore when needed
            if (shouldRestore) {
                Toast.makeText(
                    this,
                    "Select BaseGrammy or backup_latest folder to restore",
                    Toast.LENGTH_LONG
                ).show()
                RestoreNotifier.requireUser()
                openBackupTreeLauncher.launch(null)
            } else {
                // No backup needed or data already exists - just start the app
                // Don't bother user with folder selection
                RestoreNotifier.markComplete(false)
            }
        } else if (hasStoragePermissions()) {
            // Check and restore from backup on app first launch
            startLegacyRestore(shouldRestore)
        } else {
            RestoreNotifier.requireUser()
            requestStoragePermissions()
        }

        setContent {
            AppRoot()
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
            val baseDir = File(filesDir, "grammarmate")
            val masteryFile = File(baseDir, "mastery.yaml")
            val progressFile = File(baseDir, "progress.yaml")
            val profileFile = File(baseDir, "profile.yaml")
            val hasFullData = masteryFile.exists() && progressFile.exists() && profileFile.exists()
            val profile = ProfileStore(this).load()
            val shouldRestore = !hasFullData || profile.userName == "GrammarMateUser"
            startLegacyRestore(shouldRestore)
        } else {
            Toast.makeText(
                this,
                "Storage permission denied; backups in Downloads won't be read",
                Toast.LENGTH_LONG
            ).show()
            RestoreNotifier.markComplete(false)
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

    private fun startRestoreFromUri(uri: Uri) {
        RestoreNotifier.start()
        lifecycleScope.launch {
            val restored = withContext(Dispatchers.IO) {
                backupManager.restoreFromBackupUri(uri)
            }
            RestoreNotifier.markComplete(restored)
        }
    }

    private fun startLegacyRestore(shouldRestore: Boolean) {
        if (!shouldRestore) {
            RestoreNotifier.markComplete(false)
            return
        }
        RestoreNotifier.start()
        lifecycleScope.launch {
            val restored = withContext(Dispatchers.IO) {
                if (!backupManager.hasBackup()) {
                    false
                } else {
                    val backups = backupManager.getAvailableBackups()
                    val latest = backups.firstOrNull() ?: return@withContext false
                    backupManager.restoreFromBackup(latest.path)
                }
            }
            RestoreNotifier.markComplete(restored)
        }
    }
}

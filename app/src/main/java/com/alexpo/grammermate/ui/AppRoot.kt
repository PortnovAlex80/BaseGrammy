package com.alexpo.grammermate.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.AppConfigStore
import com.alexpo.grammermate.data.AppConfigStoreImpl
import com.alexpo.grammermate.data.AppVersions
import com.alexpo.grammermate.data.PackDailyCursorStoreImpl
import com.alexpo.grammermate.data.ProgressStore
import com.alexpo.grammermate.data.ProgressStoreImpl
import com.alexpo.grammermate.data.RestoreNotifier
import com.alexpo.grammermate.data.RestoreStatus

private fun checkAndMigrate(context: Context) {
    val configStore = AppConfigStoreImpl(context)
    val progressStore = ProgressStoreImpl(context)
    val packCursorStore = PackDailyCursorStoreImpl(context)
    val lastVersion = configStore.getLastVersion()

    // Migrate daily cursor from global to pack-scoped (TASK-080)
    if (lastVersion < AppVersions.VERSION_080_STATE_ISOLATION) {
        val progress = progressStore.load()
        val activePackId = progress.activePackId?.value

        try {
            val migrated = progressStore.migrateGlobalDailyCursorToPackScoped(
                activePackId = activePackId,
                packCursorStore = packCursorStore
            )
            if (migrated) {
                Log.i("AppRoot", "Successfully migrated daily cursor to pack-scoped for pack: $activePackId")
            } else {
                Log.d("AppRoot", "No daily cursor data to migrate")
            }
        } catch (e: Exception) {
            Log.e("AppRoot", "Failed to migrate daily cursor", e)
            // Continue anyway - don't block app launch
        }

        configStore.setLastVersion(AppVersions.VERSION_080_STATE_ISOLATION)
    }

    // Future migrations: TASK-081 (lesson progress), etc.
    // if (lastVersion < AppVersions.VERSION_081_LESSON_PROGRESS_ISOLATION) { ... }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current

    // Trigger migration on app launch
    LaunchedEffect(Unit) {
        checkAndMigrate(context)
    }

    val vm: TrainingViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    GrammarMateTheme(themeMode = state.navigation.themeMode) {
        val restoreState by RestoreNotifier.restoreState.collectAsStateWithLifecycle()
        if (restoreState.status == RestoreStatus.DONE) {
            GrammarMateApp(vm = vm)
        } else {
            StartupScreen(status = restoreState.status)
        }
    }
}

@Composable
private fun StartupScreen(status: RestoreStatus) {
    val message = when (status) {
        RestoreStatus.IN_PROGRESS -> stringResource(R.string.app_restoring_backup)
        RestoreStatus.NEEDS_USER -> stringResource(R.string.app_waiting_backup)
        else -> stringResource(R.string.app_preparing)
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            CircularProgressIndicator()
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

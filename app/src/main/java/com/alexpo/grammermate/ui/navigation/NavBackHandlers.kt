package com.alexpo.grammermate.ui.navigation

import androidx.activity.compose.BackHandler
import com.alexpo.grammermate.shared.AuditLogger
import com.alexpo.grammermate.shared.ScreenLogger
import android.util.Log
import com.alexpo.grammermate.ui.TrainingViewModel
import com.alexpo.grammermate.ui.NavigationEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.AppScreen
import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.CompletionNextAction
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.DownloadState
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.GrammarChipStore
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun NavBackHandlers(
    currentRoute: String?,
    showSettings: Boolean,
    state: TrainingUiState,
    vm: TrainingViewModel,
    navController: androidx.navigation.NavHostController,
    onShowExitDialog: () -> Unit
) {
    // NOTE: CHAPTER_LESSONS and LESSON back are handled by INNER BackHandlers
    // inside their composable() blocks — they have higher priority than NavController's
    // internal handler and thus work correctly with Android 14+ predictive back.

    // ── HOME with chapters pack back → clear pack, stay on HOME ──
    // When HOME renders GrammarStoryRoadmapScreen (active chapters pack),
    // back should clear the pack and show pack selection (also on HOME).
    BackHandler(enabled = currentRoute == Routes.HOME && state.navigation.activePackId != null && state.navigation.activePackHasChapters && !showSettings) {
        Log.d("NavDebug", "BACK: HOME+chapters → clearActivePack")
        ScreenLogger.nav(currentRoute ?: "?", "BACK", trigger = "back_press")
        AuditLogger.getInstanceOrNull()?.backPress("home", "clear_active_pack")
        vm.clearActivePack()
        // No navigation needed — HOME recomposes with pack selection when activePackId becomes null
    }

    // ── TRAINING back ──
    // All TRAINING back cases are handled by INNER BackHandlers inside the
    // TRAINING destination (drill return / daily cancel / lesson exit dialog)
    // so they outrank NavController's own pop. Nothing to do here.

    // ── DAILY_PRACTICE back → exit dialog ──
    BackHandler(enabled = currentRoute == Routes.DAILY_PRACTICE && !showSettings) {
        ScreenLogger.nav(currentRoute ?: "?", "BACK", trigger = "back_press")
        AuditLogger.getInstanceOrNull()?.backPress("daily_practice", "show_exit_dialog")
        onShowExitDialog()
    }

    // ── STORY back → LESSON ──
    BackHandler(enabled = currentRoute == Routes.STORY && !showSettings) {
        ScreenLogger.nav(currentRoute ?: "?", "BACK", trigger = "back_press")
        AuditLogger.getInstanceOrNull()?.backPress("story", "back_to_lesson")
        navController.navigate(Routes.LESSON) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }

    // ── LADDER back → the screen that opened it ──
    BackHandler(enabled = currentRoute == Routes.LADDER && !showSettings) {
        ScreenLogger.nav(currentRoute ?: "?", "BACK", trigger = "back_press")
        AuditLogger.getInstanceOrNull()?.backPress("ladder", "pop_back")
        val opener = navController.previousBackStackEntry?.destination?.route
        if (opener == Routes.TRAINING && state.cardSession.currentCard != null) {
            vm.resumeFromSettings()
        }
        navController.popBackStack()
    }

    // (TRAINING with daily-practice return is handled by an inner BackHandler
    // inside the TRAINING destination — see the comment above.)

    // ── STORY_READER back → whatever launched it ──
    // STORY_READER is entered from the HOME roadmap; pop back to it.
    BackHandler(enabled = currentRoute == Routes.STORY_READER && !showSettings) {
        ScreenLogger.nav(currentRoute ?: "?", "BACK", trigger = "back_press")
        AuditLogger.getInstanceOrNull()?.backPress("story_reader", "pop_back")
        // Pop back one step — returns to whatever launched STORY_READER
        // (HOME showing roadmap). Also stop TTS if playing.
        vm.stopStoryNarration()
        vm.clearStoryReader()
        navController.popBackStack()
    }
}

// ── Shared TrainingScreen helper ─────────────────────────────────────────────
// This helper avoids duplicating callback parameters.


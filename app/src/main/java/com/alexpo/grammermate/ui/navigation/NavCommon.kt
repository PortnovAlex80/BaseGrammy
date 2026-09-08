package com.alexpo.grammermate.ui.navigation

import androidx.activity.compose.BackHandler
import com.alexpo.grammermate.ui.TrainingViewModel
import com.alexpo.grammermate.data.AppScreen
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun isVerbDrillLikeReturn(returnTo: String?): Boolean =
    returnTo == Routes.VERB_DRILL || returnTo == Routes.AUX_DRILL

// ── Route-to-AppScreen mapping ───────────────────────────────────────────────

internal fun routeToScreen(route: String?): AppScreen = when (route) {
    Routes.HOME -> AppScreen.HOME
    Routes.LESSON -> AppScreen.LESSON
    Routes.ELITE -> AppScreen.ELITE
    Routes.VOCAB -> AppScreen.VOCAB
    Routes.DAILY_PRACTICE -> AppScreen.DAILY_PRACTICE
    Routes.STORY -> AppScreen.STORY
    Routes.TRAINING -> AppScreen.TRAINING
    Routes.LADDER -> AppScreen.LADDER
    Routes.VERB_DRILL -> AppScreen.VERB_DRILL
    Routes.VOCAB_DRILL -> AppScreen.VOCAB_DRILL
    Routes.CHAPTER_LESSONS -> AppScreen.CHAPTER_LESSONS
    Routes.AUX_DRILL -> AppScreen.AUX_DRILL
    Routes.STORY_READER -> AppScreen.STORY // Treat as story for tracking
    Routes.BACKGROUND_VOCAB -> AppScreen.HOME
    else -> AppScreen.HOME
}

// ── Back handlers ────────────────────────────────────────────────────────────
//
// Architecture note:
//   GrammarStoryRoadmapScreen is rendered INSIDE the HOME composable when
//   `hasChapters == true` — the "grammar roadmap" that appears after
//   selecting a chapters pack IS the HOME screen. There is no separate
//   roadmap route.
//
//   BackHandler priority in Compose: the LAST registered (innermost) handler
//   with enabled=true intercepts the back press first.  Inner handlers
//   (inside NavHost composables) take priority over outer handlers (here).


package com.alexpo.grammermate.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.alexpo.grammermate.data.SpeakSlot
import com.alexpo.grammermate.feature.backgroundvocab.DeckPlayer
import com.alexpo.grammermate.feature.backgroundvocab.VocabPlaybackService
import com.alexpo.grammermate.shared.AuditLogger
import com.alexpo.grammermate.shared.ScreenLogger

/**
 * Wave-4 UI for the Background Vocab Listener.
 *
 * Binds to [VocabPlaybackService] while composed, observes [DeckPlayer.state], and
 * drives transport directly on the bound [DeckPlayer]. Start of playback requires a
 * foreground service promotion, which only [android.content.Context.startForegroundService]
 * with [VocabPlaybackService.ACTION_PLAY] can trigger — so the first "Play" taps go through
 * `startForegroundService`, while subsequent transport (pause/resume/next/prev) calls the
 * bound player directly (cheap, no intent round-trip).
 *
 * POST_NOTIFICATIONS (API 33+) is requested before the first foreground start. On denial
 * the service still starts and audio plays; only the lock-screen notification is suppressed.
 *
 * NOTE: branches below are plain `if/else` (no early `return` out of the Scaffold/Column
 * content lambdas) so Compose's positional memoization stays balanced across recompositions
 * when the bound player / deck state flips. An early `return` here previously caused a
 * composer `Stack.pop IndexOutOfBoundsException` crash.
 *
 * @param onBack Called when the user taps Stop (or the back button) — typically navigates
 *               back to the home/roadmap screen. Stop also halts playback and unbinds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundVocabScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // ── Bind state ──────────────────────────────────────────────────────────
    var deckPlayer by remember { mutableStateOf<DeckPlayer?>(null) }
    var serviceBound by remember { mutableStateOf(false) }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val local = binder as? VocabPlaybackService.LocalBinder ?: return
                deckPlayer = local.deckPlayer()
                serviceBound = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                deckPlayer = null
                serviceBound = false
            }
        }
    }

    DisposableEffect(context) {
        val intent = Intent(context, VocabPlaybackService::class.java)
        val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        ScreenLogger.screenShown("BACKGROUND_VOCAB_BIND=$bound")
        onDispose {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                // Ignore — already unbound or service died.
            }
            deckPlayer = null
            serviceBound = false
        }
    }

    // ── POST_NOTIFICATIONS (API 33+) ────────────────────────────────────────
    var notificationsDenied by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsDenied = !granted
        startForegroundPlayback(context)
    }

    val requestStartPlayback = {
        ScreenLogger.tap("bg_vocab_play")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                startForegroundPlayback(context)
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startForegroundPlayback(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Фоновое слушание") },
                navigationIcon = {
                    IconButton(onClick = {
                        AuditLogger.getInstanceOrNull()?.backPress("background_vocab")
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val player = deckPlayer
        if (player == null) {
            // Still binding (or service not yet created). Spinner while onCreate primes deck.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            DeckControls(
                player = player,
                padding = padding,
                notificationsDenied = notificationsDenied,
                requestStartPlayback = requestStartPlayback,
                onBack = onBack
            )
        }
    }
}

/**
 * The bound-deck controls, extracted as a separate composable so the player-null branch
 * and the player-bound branch are cleanly separated composables (deterministic structure,
 * no in-lambda `return`). Handles the deck-loading (totalWords==0) vs ready split with a
 * plain `if/else`.
 */
@Composable
private fun DeckControls(
    player: DeckPlayer,
    padding: androidx.compose.foundation.layout.PaddingValues,
    notificationsDenied: Boolean,
    requestStartPlayback: () -> Unit,
    onBack: () -> Unit
) {
    val state by player.state.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.totalWords == 0) {
            // Deck still loading or empty. (No fillMaxSize inside verticalScroll.)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Загрузка слов…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            DeckReadyContent(
                state = state,
                player = player,
                notificationsDenied = notificationsDenied,
                requestStartPlayback = requestStartPlayback,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun DeckReadyContent(
    state: com.alexpo.grammermate.feature.backgroundvocab.DeckState,
    player: DeckPlayer,
    notificationsDenied: Boolean,
    requestStartPlayback: () -> Unit,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Position indicator
    Text(
        text = "Слово ${state.currentIndex + 1} / ${state.totalWords}",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )

    // Current word card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Difficulty badge (top-end): 😊 easy / 😐 medium / 😅 hard, by frequency rank.
            Text(
                text = difficultyEmoji(state.currentWord?.rank ?: 0),
                fontSize = 24.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            Text(
                text = state.currentWord?.wordIt ?: "",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = state.currentWord?.wordRu ?: "",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
            val colloIt = state.currentWord?.colloIt
            if (!colloIt.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = colloIt,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                state.currentWord?.colloRu?.let { colloRu ->
                    Text(
                        text = colloRu,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
        }
    }

    // All example sentences; the one currently being spoken is highlighted.
    val currentSlot = state.currentSlot
    state.currentWord?.sentences?.forEachIndexed { i, sentence ->
        val isActive = (currentSlot is SpeakSlot.SentenceIt && currentSlot.index == i) ||
                       (currentSlot is SpeakSlot.SentenceRu && currentSlot.index == i)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isActive) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    else Modifier
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                                 else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = sentence.it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = sentence.ru,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // POST_NOTIFICATIONS denial note (API 33+ only).
    if (notificationsDenied && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Text(
            text = "Уведомление отключено: управление с экрана блокировки недоступно. Воспроизведение работает.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Transport controls.
    val isForegroundActive = state.isPlaying || state.isPaused

    // Primary Play/Pause toggle.
    Button(
        onClick = {
            if (state.isPlaying) {
                player.pause()
            } else if (state.isPaused) {
                player.resume()
            } else {
                requestStartPlayback()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Icon(
            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = when {
                state.isPlaying -> "Пауза"
                state.isPaused -> "Продолжить"
                else -> "Слушать"
            },
            fontSize = 18.sp
        )
    }

    // Prev / Next row.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = {
                ScreenLogger.tap("bg_vocab_prev")
                if (isForegroundActive) player.prevWord() else requestStartPlayback()
            },
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Icon(Icons.Filled.FastRewind, contentDescription = "Previous word")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Назад")
        }
        OutlinedButton(
            onClick = {
                ScreenLogger.tap("bg_vocab_next")
                if (isForegroundActive) player.nextWord() else requestStartPlayback()
            },
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Text("Дальше")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Filled.FastForward, contentDescription = "Next word")
        }
    }

    // Pack navigation (±50 words) — simulate 50-word packs.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = {
                ScreenLogger.tap("bg_vocab_prev_pack")
                if (isForegroundActive) player.prevPack() else requestStartPlayback()
            },
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Text("−50", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Пред. пакет", style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
            onClick = {
                ScreenLogger.tap("bg_vocab_next_pack")
                if (isForegroundActive) player.nextPack() else requestStartPlayback()
            },
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Text("След. пакет", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Text("+50", fontWeight = FontWeight.Bold)
        }
    }

    // Stop — halts playback, stops the service, and navigates back.
    OutlinedButton(
        onClick = {
            ScreenLogger.tap("bg_vocab_stop")
            stopForegroundPlayback(context)
            onBack()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Icon(Icons.Default.Stop, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Стоп")
    }
}

// ── Service start/stop helpers ────────────────────────────────────────────────

/** Difficulty emoji by frequency rank: easy 😊 (≤1000) / medium 😐 (≤2500) / hard 😅 (>2500). */
private fun difficultyEmoji(rank: Int): String = when {
    rank <= 1000 -> "😊"
    rank <= 2500 -> "😐"
    else -> "😅"
}

private fun startForegroundPlayback(context: Context) {
    val intent = VocabPlaybackService.startIntent(context, VocabPlaybackService.ACTION_PLAY)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopForegroundPlayback(context: Context) {
    val intent = VocabPlaybackService.startIntent(context, VocabPlaybackService.ACTION_STOP)
    context.startService(intent)
}

package com.alexpo.grammermate.feature.backgroundvocab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.os.Binder
import com.alexpo.grammermate.GrammarMateApplication
import com.alexpo.grammermate.MainActivity
import com.alexpo.grammermate.data.BgVocabLoader
import com.alexpo.grammermate.data.TtsEngine
import com.alexpo.grammermate.shared.audio.SegmentPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the background-vocab deck and plays it with the
 * screen off / app backgrounded.
 *
 * Wave 3b of the Background Vocab Listener feature (design S5). It consumes the
 * Wave 1/2/3a APIs without modifying them:
 *
 *  - [TtsEngine] singleton, obtained from [GrammarMateApplication.container].
 *    `ttsEngine` (`AppContainer.ttsEngine`) — mirrors how ViewModels obtain it.
 *  - [SegmentPlayer] / [DeckPlayer] (feature.backgroundvocab) drive the actual audio.
 *  - [BgVocabLoader] loads the 12000-word CSV from `grammarmate/packs/bg_vocab_12000.csv`.
 *
 * The service exposes transport controls two ways:
 *
 *  1. A framework [MediaSession] (no androidx.media dependency — works on API 21+,
 *     minSdk 24) whose callbacks route Play/Pause/Next/Prev/Stop to the [DeckPlayer].
 *     This is what lock-screen + Bluetooth headset buttons drive.
 *  2. A MediaStyle notification (tied to the session token) with Play/Pause, Next
 *     and Prev actions. The actions are delivered back to the service via
 *     [onStartCommand] intents ([ACTION_PLAY] etc.), so both the notification
 *     buttons and a future UI screen drive the same code path through
 *     `startForegroundService(Intent(...))`.
 *
 * A single long-lived [scope] (SupervisorJob + Dispatchers.Default) survives
 * configuration changes and the screen turning off. [DeckPlayer] launches its play
 * coroutine on this scope; the service collects [DeckPlayer.state] and reflects
 * every change into the MediaSession playback state/metadata and a refreshed
 * notification.
 *
 * NOTE on POST_NOTIFICATIONS: the permission is declared in the manifest; the
 * runtime request on API 33+ is a UI concern and is deferred to Wave 4. If the
 * permission is not granted the notification simply will not be shown, but the
 * foreground service still runs and audio still plays.
 */
class VocabPlaybackService : Service() {

    companion object {
        private const val TAG = "VocabPlaybackService"

        const val ACTION_PLAY = "com.alexpo.grammermate.action.PLAY"
        const val ACTION_PAUSE = "com.alexpo.grammermate.action.PAUSE"
        const val ACTION_NEXT = "com.alexpo.grammermate.action.NEXT"
        const val ACTION_PREV = "com.alexpo.grammermate.action.PREV"
        const val ACTION_STOP = "com.alexpo.grammermate.action.STOP"

        private const val CHANNEL_ID = "bg_vocab_playback"
        private const val NOTIFICATION_ID = 0xB9A1 // stable id for the ongoing notif
        private const val SESSION_TAG = "GrammarMate.BgVocab"

        /**
         * Convenience for starting the service in foreground mode with a transport
         * action. UI (Wave 4) calls `startForegroundService(startIntent(context))`
         * to begin playback, or `startForegroundService(Intent(context, cls).putExtra action)`
         * to drive a transport button.
         */
        fun startIntent(context: Context, action: String? = null): Intent =
            Intent(context, VocabPlaybackService::class.java).apply {
                action?.let { this.action = it }
            }
    }

    // Long-lived scope. Default dispatcher: TTS work already lives on Default, and the
    // service has no UI thread affinity. Survives the screen turning off.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var ttsEngine: TtsEngine
    private lateinit var segmentPlayer: SegmentPlayer
    private lateinit var deckPlayer: DeckPlayer
    private lateinit var mediaSession: MediaSession

    private var stateCollectorJob: Job? = null
    private var isForeground = false

    // ── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        val app = applicationContext as GrammarMateApplication
        ttsEngine = app.container.ttsEngine
        segmentPlayer = SegmentPlayer(ttsEngine)

        // Resolve the active pack id from the persistent ProgressStore — NOT an Intent
        // extra. The service can be killed & recreated as START_STICKY and would lose
        // extras; ProgressStore is the canonical persisted source
        // (TrainingProgress.activePackId, see Models.kt). Null when no pack is active.
        val packId: String? = try {
            app.container.progressStore.load().activePackId?.value
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to read activePackId from ProgressStore", e)
            null
        }

        deckPlayer = DeckPlayer(
            ttsEngine,
            segmentPlayer,
            scope,
            speedProvider = { app.container.configStore.load().ttsSpeed },
            audioResolver = app.container.bgVocabAudioResolver,
            packId = packId,
            markStore = app.container.bgVocabMarkStore,
            positionStore = app.container.bgVocabPositionStore
        )

        createNotificationChannel()
        setupMediaSession()

        // Load the deck on the scope, then prime the DeckPlayer. Pack-scoped first
        // (drills/{packId}/bg_vocab/...); fall back to the bundled 12000-word asset when
        // the pack declares no background-vocab deck or no pack is active. Does NOT
        // auto-play — playback starts only when an explicit Play arrives (from
        // MediaSession callback, notification action, or a
        // startForegroundService(ACTION_PLAY) intent).
        scope.launch {
            val words = packId
                ?.let { pid -> BgVocabLoader.loadPackScoped(this@VocabPlaybackService, app.container.baseDir, pid) }
                ?.takeIf { it.isNotEmpty() }
                ?: BgVocabLoader.load(this@VocabPlaybackService)
            deckPlayer.setWords(words)
            Log.d(TAG, "Deck primed with ${words.size} words (packId=$packId)")
        }

        // Reflect every DeckPlayer state change into the MediaSession + notification.
        stateCollectorJob = scope.launch {
            deckPlayer.state.collectLatest { state ->
                updateMediaSession(state)
                refreshNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")

        // Ensure we are in the foreground before doing any transport work — a
        // startForegroundService() call REQUIRES startForeground() within ~5s.
        ensureForeground()

        when (intent?.action) {
            ACTION_PLAY -> {
                val s = deckPlayer.state.value
                if (s.isPaused) deckPlayer.resume() else deckPlayer.play()
            }
            ACTION_PAUSE -> deckPlayer.pause()
            ACTION_NEXT -> deckPlayer.nextWord()
            ACTION_PREV -> deckPlayer.prevWord()
            ACTION_STOP -> {
                deckPlayer.stop()
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            else -> {
                // Boot intent (no action / MAIN): just enter foreground and wait for
                // a Play. If the deck is already loaded and was previously playing we
                // do not auto-resume — the user must press Play.
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App was swiped away. Background-vocab is meant to keep running, but if the
        // user has swiped the task we treat that as "stop" to avoid a zombie service
        // the user cannot easily dismiss. (Matches typical media-app behavior.)
        Log.d(TAG, "onTaskRemoved — stopping service")
        stopForegroundAndSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        stateCollectorJob?.cancel()
        stateCollectorJob = null
        try {
            deckPlayer.stop()
        } catch (e: Throwable) {
            Log.w(TAG, "deckPlayer.stop() failed in onDestroy", e)
        }
        try {
            mediaSession.isActive = false
            mediaSession.release()
        } catch (e: Throwable) {
            Log.w(TAG, "mediaSession.release() failed", e)
        }
        scope.cancel()
        if (isForeground) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "stopForeground() failed", e)
            }
            isForeground = false
        }
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // No-op — the service has no UI resources to reload.
    }

    /**
     * In-process binder used by the Wave-4 UI ([com.alexpo.grammermate.ui.screens.BackgroundVocabScreen])
     * to observe [DeckPlayer.state] and drive transport directly while the screen is on top.
     *
     * The service supports BOTH a started lifecycle (foreground playback, driven by
     * [onStartCommand] / [startForegroundService] intents — the MediaSession + notification
     * path) AND a bound lifecycle (the in-process UI). The two are independent:
     *
     *   - Binding alone does NOT start foreground playback. Foreground promotion only
     *     happens inside [onStartCommand] via [ensureForeground], which is only reached
     *     through `startForegroundService(VocabPlaybackService.startIntent(ctx, ACTION_PLAY))`.
     *     This is by design: an app must not enter a `mediaPlayback` foreground service
     *     without an explicit user action, and binding is not such an action.
     *
     *   - The canonical UI flow (see BackgroundVocabScreen) is:
     *       1. UI requests POST_NOTIFICATIONS on API 33+ (runtime permission).
     *       2. UI calls `startForegroundService(startIntent(ctx, ACTION_PLAY))` — this
     *          promotes the service to foreground, posts the notification, and starts
     *          playback via [onStartCommand].
     *       3. UI binds (bindService) to observe `deckPlayer.state` and call
     *          `play()/pause()/resume()/nextWord()/prevWord()` directly.
     *       4. UI unbinds on dispose; the service keeps running (it's started, not just bound).
     *
     * If the UI binds before the service has been started (e.g. configuration change
     * re-compose before the user pressed Play), [deckPlayer] is still initialized in
     * [onCreate] and the UI will simply observe the (idle) state; calling transport on a
     * deck that isn't playing is a no-op / safe resume.
     */
    private val localBinder = LocalBinder()

    /** Binder that hands back this service (and thus the [DeckPlayer]). */
    inner class LocalBinder : Binder() {
        /** The owning service. */
        fun service(): VocabPlaybackService = this@VocabPlaybackService

        /** Direct access to the deck player for state observation + transport. */
        fun deckPlayer(): DeckPlayer = this@VocabPlaybackService.deckPlayer
    }

    override fun onBind(intent: Intent?): IBinder = localBinder

    // ── Foreground / notification ───────────────────────────────────────────

    /**
     * Promote the service to the foreground with a MEDIA_PLAYBACK type and the
     * current notification. Idempotent — safe to call from every onStartCommand.
     */
    private fun ensureForeground() {
        if (isForeground) {
            // Still (re)post the notification so it reflects the latest state.
            val s = if (::deckPlayer.isInitialized) deckPlayer.state.value else DeckState()
            val nm = getSystemService(NotificationManager::class.java)
            try {
                nm.notify(NOTIFICATION_ID, buildNotification(s, mediaSession.sessionToken))
            } catch (e: SecurityException) {
                // POST_NOTIFICATIONS not granted on API 33+ — service still runs.
                Log.w(TAG, "Notification not posted (permission denied)", e)
            }
            return
        }
        val initialState = if (::deckPlayer.isInitialized) deckPlayer.state.value else DeckState()
        val notification = buildNotification(initialState, mediaSession.sessionToken)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+: must specify foregroundServiceType at startForeground time.
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForeground = true
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background Vocab Playback",
            NotificationManager.IMPORTANCE_LOW // low: no sound, non-intrusive, lock-screen visible
        ).apply {
            description = "Plays the background vocabulary deck while the screen is off."
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    /**
     * Re-emit the ongoing notification so it reflects [state]. Called from the
     * state collector on every DeckPlayer state change.
     */
    private fun refreshNotification(state: DeckState) {
        if (!isForeground) return
        val nm = getSystemService(NotificationManager::class.java)
        try {
            nm.notify(NOTIFICATION_ID, buildNotification(state, mediaSession.sessionToken))
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification refresh skipped (permission denied)", e)
        }
    }

    /**
     * Build the MediaStyle notification. Tied to the MediaSession token so lock-screen
     * and system media controls are associated with this session.
     *
     * Actions: Prev, Play/Pause (toggle by isPlaying), Next. Each is a PendingIntent
     * that re-enters [onStartCommand] with the corresponding action.
     */
    private fun buildNotification(state: DeckState, sessionToken: MediaSession.Token): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = state.currentWord?.wordIt?.ifBlank { "Background Vocab" } ?: "Background Vocab"
        val text = state.currentWord?.wordRu?.ifBlank { "" } ?: ""

        // Framework Notification.Builder + Notification.MediaStyle. Using the framework
        // MediaStyle (NOT androidx.media) so we add zero new dependencies; it binds
        // directly to the framework MediaSession.Token used by setupMediaSession().
        // Channel-id constructor is API 26+; fall back to the deprecated no-arg ctor on 24-25.
        @Suppress("DEPRECATION")
        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                Notification.Builder(this)
            }
        builder
            .setSmallIcon(android.R.drawable.ic_media_play) // framework icon; no res dependency
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    actionPendingIntent(ACTION_PREV)
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (state.isPlaying) "Pause" else "Play",
                    actionPendingIntent(if (state.isPlaying) ACTION_PAUSE else ACTION_PLAY)
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "Next",
                    actionPendingIntent(ACTION_NEXT)
                ).build()
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        return builder.build()
    }

    /**
     * Build a PendingIntent that delivers [action] to this service via
     * startForegroundService (so the service is promoted to foreground before the
     * action is processed, which is required when the app is backgrounded).
     */
    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, VocabPlaybackService::class.java).apply { this.action = action }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            PendingIntent.getService(
                this,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }

    // ── MediaSession ─────────────────────────────────────────────────────────

    /**
     * Framework [MediaSession] (no androidx.media dependency). Works on API 21+
     * (minSdk 24). Callbacks route transport button events (lock-screen / Bluetooth /
     * headset) to the [DeckPlayer].
     */
    private fun setupMediaSession() {
        mediaSession = MediaSession(this, SESSION_TAG).apply {
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            // Initial state: stopped, no actions until the deck is primed.
            setPlaybackState(buildPlaybackState(DeckState()))
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    Log.d(TAG, "MediaSession.onPlay")
                    ensureForeground()
                    val s = deckPlayer.state.value
                    if (s.isPaused) deckPlayer.resume() else deckPlayer.play()
                }

                override fun onPause() {
                    Log.d(TAG, "MediaSession.onPause")
                    deckPlayer.pause()
                }

                override fun onSkipToNext() {
                    Log.d(TAG, "MediaSession.onSkipToNext")
                    deckPlayer.nextWord()
                }

                override fun onSkipToPrevious() {
                    Log.d(TAG, "MediaSession.onSkipToPrevious")
                    deckPlayer.prevWord()
                }

                override fun onStop() {
                    Log.d(TAG, "MediaSession.onStop")
                    deckPlayer.stop()
                    stopForegroundAndSelf()
                }
            })
            isActive = true
        }
    }

    /**
     * Translate a [DeckState] into a framework [PlaybackState] and push it into the
     * session, then update the session metadata so lock-screen shows the current word.
     */
    private fun updateMediaSession(state: DeckState) {
        if (!::mediaSession.isInitialized) return
        mediaSession.setPlaybackState(buildPlaybackState(state))
        val metadata = MediaMetadata.Builder().apply {
            putString(
                MediaMetadata.METADATA_KEY_TITLE,
                state.currentWord?.wordIt ?: "Background Vocab"
            )
            putString(
                MediaMetadata.METADATA_KEY_ARTIST,
                state.currentWord?.wordRu ?: ""
            )
            putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, state.totalWords.toLong())
            putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, (state.currentIndex + 1).toLong())
        }.build()
        mediaSession.setMetadata(metadata)
    }

    /**
     * Build the framework PlaybackState: state + the set of supported transport
     * actions + the active position indicator (word index).
     */
    private fun buildPlaybackState(state: DeckState): PlaybackState {
        val actions = (
            PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_STOP
        )

        val stateCode = when {
            state.isPlaying -> PlaybackState.STATE_PLAYING
            state.isPaused -> PlaybackState.STATE_PAUSED
            state.totalWords == 0 -> PlaybackState.STATE_BUFFERING // deck still loading
            else -> PlaybackState.STATE_STOPPED
        }

        return PlaybackState.Builder()
            .setActions(actions)
            .setState(stateCode, state.currentIndex.toLong(), 1.0f)
            .build()
    }

    // ── Teardown helpers ────────────────────────────────────────────────────

    private fun stopForegroundAndSelf() {
        if (isForeground) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "stopForeground() failed", e)
            }
            isForeground = false
        }
        stopSelf()
    }
}

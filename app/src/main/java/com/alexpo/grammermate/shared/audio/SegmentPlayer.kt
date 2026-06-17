package com.alexpo.grammermate.shared.audio

import android.media.MediaPlayer
import android.util.Log
import com.alexpo.grammermate.data.MultilingualStoryParser
import com.alexpo.grammermate.data.TtsEngine
import com.alexpo.grammermate.data.TtsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.coroutines.resume

/**
 * Reusable sequential playback engine for [MultilingualStoryParser.Segment] streams.
 *
 * Extracted verbatim from `AudioCoordinator.playSegments` (Wave 3a of the Background
 * Vocab Listener) so that both story playback (per-ViewModel AudioCoordinator) and
 * background-vocab playback (the long-lived DeckPlayer owned by a ForegroundService)
 * share one implementation without one depending on the other's state container.
 *
 * The player owns no playback-state flag of its own; pause/resume knowledge flows in
 * through the [playSegments] `isPaused` callback. It is otherwise stateful only in
 * that it serializes access to the shared [ttsEngine] via an internal [Mutex].
 *
 * @param ttsEngine the singleton Sherpa-ONNX engine (resident LRU cache, Wave 1).
 */
class SegmentPlayer(private val ttsEngine: TtsEngine) {

    companion object {
        private const val TAG = "SegmentPlayer"
    }

    /**
     * Serializes concurrent callers. The player owns TTS access for its consumers,
     * so it self-protects: even if two coroutines call [playSegments] simultaneously
     * they are queued rather than racing on native model state.
     */
    private val mutex = Mutex()

    /**
     * Iterate [segments] sequentially, speaking each [MultilingualStoryParser.Segment.Text]
     * via the TTS engine and waiting [MultilingualStoryParser.Segment.Pause.ms] for each pause.
     *
     * Per-segment contract (identical to the original AudioCoordinator loop):
     *
     * - [MultilingualStoryParser.Segment.Pause]: cooperatively `delay(ms)` (cancellable).
     *   If [isPaused] becomes true after the pause elapses, wait for it to clear, then
     *   continue to the next segment (a pause is never re-emitted).
     * - [MultilingualStoryParser.Segment.Text]: ensure the TTS engine is [TtsState.Ready]
     *   for the segment's language (initialize is a cheap fast-path for resident languages),
     *   speak the markdown-cleaned text at [speed], and poll until playback leaves the
     *   [TtsState.Speaking] state. If [isPaused] becomes true after the segment finishes,
     *   wait for it to clear and then re-speak the *same* segment (do NOT advance the
     *   index) — this preserves the exact pause/resume UX of story playback.
     * - [MultilingualStoryParser.Segment.Audio]: play the referenced `.wav` file via
     *   [MediaPlayer]. If the file is missing, log a warning and advance (no TTS
     *   fallback here — an Audio segment has no text). Applies the same re-play-on-resume
     *   UX as the Text branch. Emitted only by background-vocab playback; the story
     *   path never produces Audio segments.
     *
     * [onSegmentStart] is invoked for every segment (including pauses) just before it is
     * processed; the index passed is the position within [segments]. It is optional and
     * defaults to no-op.
     *
     * Cancellation-safe: all waiting uses [delay], which is cooperative with structured
     * concurrency. Concurrent calls are serialized by an internal mutex.
     *
     * @param segments       ordered segment stream (text + pauses interleaved).
     * @param speed          TTS playback speed multiplier, forwarded to [TtsEngine.speak].
     * @param isPaused       returns true when the caller wants playback to suspend.
     * @param onSegmentStart optional per-segment callback (index, segment) for UI hooks.
     */
    suspend fun playSegments(
        segments: List<MultilingualStoryParser.Segment>,
        speed: Float,
        isPaused: () -> Boolean,
        onSegmentStart: (Int, MultilingualStoryParser.Segment) -> Unit = { _, _ -> }
    ) = mutex.withLock {
        var currentSegmentIdx = 0
        while (currentSegmentIdx < segments.size) {
            val segment = segments[currentSegmentIdx]
            onSegmentStart(currentSegmentIdx, segment)

            when (segment) {
                is MultilingualStoryParser.Segment.Pause -> {
                    Log.d(TAG, "Segment $currentSegmentIdx/${segments.size} | Pause ${segment.ms}ms")
                    delay(segment.ms)

                    // After a pause, if playback was paused, wait for resume and
                    // continue. Pauses are not re-spoken (no audio to re-emit).
                    if (isPaused()) {
                        Log.d(TAG, "⏸ Paused after pause-segment $currentSegmentIdx — waiting for resume")
                        while (isPaused()) {
                            delay(100)
                        }
                        Log.d(TAG, "▶ Resuming after pause-segment $currentSegmentIdx")
                    }
                    currentSegmentIdx++
                }

                is MultilingualStoryParser.Segment.Text -> {
                    val cleanText = MultilingualStoryParser.cleanMarkdown(segment.text)
                    val previewText = cleanText.take(50).replace("\n", "\\n")

                    Log.d(TAG, "════════════════════════════════════════")
                    Log.d(TAG, "Segment $currentSegmentIdx/${segments.size} | Language: ${segment.languageId.uppercase()}")
                    Log.d(TAG, "Text preview: \"$previewText...\"")

                    // Initialize TTS for this segment's language if needed.
                    // With the resident LRU cache (Wave 1a), initialize() fast-paths
                    // an already-resident language, so this is cheap on repeats.
                    if (ttsEngine.state.value != TtsState.Ready
                        || ttsEngine.activeLanguageId != segment.languageId
                    ) {
                        Log.d(TAG, "→ Initializing TTS for: ${segment.languageId}")
                        ttsEngine.initialize(segment.languageId)
                    }

                    // Wait for TTS to be ready (increased timeout for VITS_PIPER)
                    var retries = 0
                    while (ttsEngine.state.value != TtsState.Ready && retries < 50) {
                        delay(100)
                        retries++
                        if (retries % 10 == 0) {
                            Log.d(TAG, "→ Still waiting for TTS initialization... (${retries * 100}ms)")
                        }
                    }

                    if (ttsEngine.state.value == TtsState.Ready) {
                        Log.d(TAG, "→ Speaking segment $currentSegmentIdx...")
                        Log.d(TAG, "→ Text length: ${cleanText.length}, full text: \"$cleanText\"")
                        ttsEngine.speak(cleanText, languageId = segment.languageId, speed = speed)

                        // Wait for this segment to finish playing
                        var waitRetries = 0
                        while (ttsEngine.state.value == TtsState.Speaking && waitRetries < 3000) {
                            delay(100)
                            waitRetries++
                        }

                        Log.d(TAG, "✓ Segment $currentSegmentIdx finished (${segment.languageId.uppercase()})")
                    } else {
                        Log.w(TAG, "⚠ TTS not ready for segment $currentSegmentIdx, skipping")
                    }

                    // After segment finishes, check if we were paused during playback.
                    // If so, wait for resume and then re-speak the SAME segment
                    // (don't advance index) — preserves the existing pause/resume UX.
                    if (isPaused()) {
                        Log.d(TAG, "⏸ Paused after segment $currentSegmentIdx — waiting for resume to re-speak this segment")
                        while (isPaused()) {
                            delay(100)
                        }
                        Log.d(TAG, "▶ Resuming — re-speaking segment $currentSegmentIdx")
                        continue
                    }

                    currentSegmentIdx++
                }

                is MultilingualStoryParser.Segment.Audio -> {
                    // Pre-rendered clip path (Wave 2). If the file is present, play it
                    // via MediaPlayer and mirror the Text branch's pause/resume contract:
                    // after the clip finishes, if isPaused() became true, wait for it to
                    // clear and then RE-PLAY the same segment (continue — do not advance).
                    // If the file is missing (deleted at runtime), log and advance without
                    // crashing or falling back to TTS — an Audio segment carries no text,
                    // so TTS fallback happens upstream at segment-construction time.
                    val file = segment.file
                    if (!file.exists() || !file.canRead()) {
                        Log.w(TAG, "Audio file missing, skipping: $file")
                        currentSegmentIdx++
                    } else {
                        Log.d(TAG, "Segment $currentSegmentIdx/${segments.size} | Audio: ${file.name} (${segment.languageId.uppercase()})")
                        playAudioFile(file)

                        // Same pause/resume UX as the Text branch: if playback was
                        // paused while/after the clip played, wait for resume then
                        // replay THIS segment (don't advance index).
                        if (isPaused()) {
                            Log.d(TAG, "⏸ Paused after audio-segment $currentSegmentIdx — waiting for resume to replay this clip")
                            while (isPaused()) {
                                delay(100)
                            }
                            Log.d(TAG, "▶ Resuming — replaying audio-segment $currentSegmentIdx")
                            continue
                        }

                        currentSegmentIdx++
                    }
                }
            }
        }
    }

    /**
     * Play [file] via [MediaPlayer], suspending until the clip completes (or errors).
     *
     * The player is created, prepared, started, and released entirely within this
     * call — no MediaPlayer instance escapes the function, so there is no leak.
     * On coroutine cancellation the in-flight [MediaPlayer] is [MediaPlayer.release]d
     * (best-effort; `release()` is idempotent and never throws on a released player).
     *
     * Resumes with `Unit` on `onCompletion` or `onError` (errors are logged and the
     * caller advances past the clip rather than crashing); `setDataSource`/`prepare`
     * failures throw synchronously and are caught here, also resuming with `Unit`.
     */
    private suspend fun playAudioFile(file: File) = suspendCancellableCoroutine { cont ->
        val player = MediaPlayer()
        // Safety net: if the coroutine is cancelled mid-clip, tear down native state.
        cont.invokeOnCancellation { runCatching { player.release() } }

        try {
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                player.release()
                if (cont.isActive) cont.resume(Unit)
            }
            player.setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra on $file")
                runCatching { mp.release() }
                if (cont.isActive) cont.resume(Unit)
                true
            }
            player.prepare()
            player.start()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to play audio clip $file", t)
            runCatching { player.release() }
            if (cont.isActive) cont.resume(Unit)
        }
    }
}

package com.alexpo.grammermate.shared.audio

import android.util.Log
import com.alexpo.grammermate.data.MultilingualStoryParser
import com.alexpo.grammermate.data.TtsEngine
import com.alexpo.grammermate.data.TtsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
            }
        }
    }
}

package com.alexpo.grammermate.feature.backgroundvocab

import android.util.Log
import com.alexpo.grammermate.data.BgVocabMark
import com.alexpo.grammermate.data.BgVocabMarkStore
import com.alexpo.grammermate.data.MultilingualStoryParser
import com.alexpo.grammermate.data.SpeakSlot
import com.alexpo.grammermate.data.TtsEngine
import com.alexpo.grammermate.data.WordScript
import com.alexpo.grammermate.shared.audio.SegmentPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Observable snapshot of a [DeckPlayer]'s position and transport state.
 *
 * Designed to drive a lock-screen MediaSession and a bound UI: every meaningful
 * change (word advance, play/pause/stop) re-emits a new immutable value.
 *
 * @property totalWords    size of the deck set via [DeckPlayer.setWords] (0 until set).
 * @property currentIndex  position within the deck, 0-based. Clamped to [0, totalWords-1].
 * @property currentWord   the [WordScript] at [currentIndex], or null when the deck is empty.
 * @property isPlaying     true when the player is actively cycling the deck (a coroutine is running).
 *                         False while paused *or* stopped.
 * @property isPaused      true when the user has paused playback. Paused implies the play
 *                         coroutine is still alive but the SegmentPlayer loop is parked,
 *                         waiting for the flag to clear (resume re-speaks the current segment).
 */
data class DeckState(
    val totalWords: Int = 0,
    val currentIndex: Int = 0,
    val currentWord: WordScript? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    /** Which part of the current word is being spoken right now (null = none/between). */
    val currentSlot: SpeakSlot? = null
)

/**
 * Endless background-vocab deck player.
 *
 * Owns a word cursor over a [WordScript] list and a single play [Job]. On [play] it
 * launches a coroutine on [scope] that, for each word, invokes [playWord] (by default:
 * parse the word's markup → [SegmentPlayer.playSegments]) and then waits
 * [betweenWordsPauseMs] before advancing. Playback wraps at the end of the deck so the
 * listener gets an endless loop — the intended UX for passive background learning.
 *
 * Transport:
 * - [play] starts or resumes from [DeckState.currentIndex]; cancels any prior job first.
 * - [pause]/[resume] flip [DeckState.isPaused]; the SegmentPlayer loop honors it
 *   (re-speaks the current segment on resume).
 * - [nextWord]/[prevWord]/[seekTo] move the cursor and, if playing, relaunch from the
 *   new word (current speak is interrupted via [TtsEngine.stop] on cancellation).
 * - [stop] cancels the job and clears [DeckState.isPlaying]; keeps [DeckState.currentIndex].
 *
 * ### Testability
 *
 * The segment-playback path is injected as [playWord] — a suspend lambda taking the
 * current [WordScript]. The default implementation builds a real [SegmentPlayer] call,
 * but a unit test can pass a recording fake (e.g. one that appends to a list and delays
 * a few ms) and drive the full play/next/prev/stop loop on a pure JVM [TestScope] with
 * no native TTS. [ttsEngine] is still required because [stop] and word-navigation cut
 * audio by calling [TtsEngine.stop]; tests pass a no-op fake TtsEngine.
 *
 * ### Cancellation safety
 *
 * A single `playJob` is the source of truth. Every transport mutation that changes the
 * cursor or stops playback cancels the prior job before relaunching. The play loop wraps
 * its body in a try/catch for [CancellationException] so that on cancel it calls
 * [TtsEngine.stop] to cut any in-flight audio (structured-concurrency cancellation is
 * cooperative; without the explicit stop, the native synth could keep streaming).
 *
 * @param ttsEngine            singleton TTS engine (for [stop] on cancel).
 * @param segmentPlayer        the shared playback engine; consumed by the default [playWord].
 * @param scope                long-lived coroutine scope, owned by the service (NOT viewModelScope).
 * @param betweenWordsPauseMs  gap between words. Default 1500ms (slightly longer than the
 *                             in-word pauses to give the listener a beat to register the word).
 * @param playWord             injected suspend hook that speaks one [WordScript]. Defaults to
 *                             parsing [WordScript.toMarkup] and feeding it to [SegmentPlayer].
 *                             Override in tests to avoid real TTS.
 * @param speed                TTS speed multiplier passed into the default [playWord].
 */
class DeckPlayer(
    private val ttsEngine: TtsEngine,
    private val segmentPlayer: SegmentPlayer,
    private val scope: CoroutineScope,
    private val betweenWordsPauseMs: Long = 1500L,
    private val speedProvider: () -> Float = { 1f },
    playWord: (suspend (WordScript) -> Unit)? = null,
    private val markStore: BgVocabMarkStore? = null
) {
    companion object {
        private const val TAG = "DeckPlayer"
    }

    private val _state = MutableStateFlow(DeckState())
    /** Observable transport/position state for UI + MediaSession. */
    val state: StateFlow<DeckState> = _state.asStateFlow()

    /** Default per-word playback: build segments from the word's speak plan, feed to
     *  [SegmentPlayer], and track the currently-spoken [SpeakSlot] (for in-sync UI). */
    private suspend fun defaultPlayWord(word: WordScript) {
        val plan = word.speakPlan()
        val segments = ArrayList<MultilingualStoryParser.Segment>(plan.size * 2)
        val slots = ArrayList<SpeakSlot?>(plan.size * 2)
        for (item in plan) {
            segments.add(MultilingualStoryParser.Segment.Text(item.text, item.lang))
            slots.add(item.slot)
            segments.add(MultilingualStoryParser.Segment.Pause(item.pauseAfterMs))
            slots.add(null)
        }
        segmentPlayer.playSegments(
            segments = segments,
            speed = speedProvider(),
            isPaused = { _state.value.isPaused },
            onSegmentStart = { idx, _ ->
                slots.getOrNull(idx)?.let { slot ->
                    _state.value = _state.value.copy(currentSlot = slot)
                }
            }
        )
    }

    /** Effective per-word hook: the injected [playWord] override, or [defaultPlayWord]. */
    private val playWordFn: suspend (WordScript) -> Unit = playWord ?: { defaultPlayWord(it) }

    private var words: List<WordScript> = emptyList()
    private var playJob: Job? = null

    /**
     * Load a fresh deck. Resets [DeckState.currentIndex] to 0, clears any running playback
     * job (does not cut audio unless a job was actually running — see [stop]), and emits a
     * state with [DeckState.isPlaying] = false. Safe to call with an empty list.
     */
    fun setWords(words: List<WordScript>) {
        cancelPlayJob(cutAudio = true)
        this.words = words
        val first = words.firstOrNull()
        _state.value = DeckState(
            totalWords = words.size,
            currentIndex = 0,
            currentWord = first,
            isPlaying = false,
            isPaused = false
        )
        Log.d(TAG, "setWords: ${words.size} words loaded")
    }

    /**
     * Start playback, or resume from the current cursor if already playing but paused.
     *
     * Cancels any prior play job, marks [DeckState.isPlaying] = true and
     * [DeckState.isPaused] = false, and launches the endless play loop on [scope].
     * No-op if the deck is empty.
     */
    fun play() {
        if (words.isEmpty()) {
            Log.w(TAG, "play() ignored — deck is empty")
            return
        }
        cancelPlayJob(cutAudio = true)
        _state.value = _state.value.copy(isPlaying = true, isPaused = false)
        launchPlayLoop()
    }

    /**
     * Pause playback. Sets [DeckState.isPaused] = true (and [DeckState.isPlaying] = false
     * to reflect that no audio is being produced). The running SegmentPlayer loop observes
     * the flag after the current segment and parks until [resume].
     *
     * Also immediately cuts any in-flight audio via [TtsEngine.stop] so the pause takes
     * effect right now rather than at the next segment boundary.
     */
    fun pause() {
        if (!_state.value.isPaused) {
            _state.value = _state.value.copy(isPaused = true, isPlaying = false)
            ttsEngine.stop()
            Log.d(TAG, "pause() — playback parked at index ${_state.value.currentIndex}")
        }
    }

    /**
     * Resume after [pause]. Clears [DeckState.isPaused] and restores [DeckState.isPlaying]
     * = true. The SegmentPlayer loop, which was parked on the flag, re-speaks the current
     * segment and continues. The play job is still alive (pause does not cancel it), so no
     * relaunch is needed.
     */
    fun resume() {
        if (_state.value.isPaused) {
            _state.value = _state.value.copy(isPaused = false, isPlaying = true)
            Log.d(TAG, "resume() — playback unparked at index ${_state.value.currentIndex}")
        }
    }

    /**
     * Skip to the next word (wrapping to 0 at the end). If playback was active, the current
     * speak is cancelled and the loop relaunches from the new word.
     */
    fun nextWord() {
        if (words.isEmpty()) return
        val newIndex = if (_state.value.currentIndex + 1 >= words.size) 0 else _state.value.currentIndex + 1
        moveTo(newIndex)
    }

    /**
     * Skip to the previous word (wrapping to the last index from 0). If playback was active,
     * the current speak is cancelled and the loop relaunches from the new word.
     */
    fun prevWord() {
        if (words.isEmpty()) return
        val newIndex = if (_state.value.currentIndex - 1 < 0) words.size - 1 else _state.value.currentIndex - 1
        moveTo(newIndex)
    }

    /**
     * Jump forward by [packSize] words (wraps around the deck). Simulates
     * "next 50-word pack" navigation so the listener can skip ahead in batches
     * instead of one word at a time.
     */
    fun nextPack(packSize: Int = 50) {
        if (words.isEmpty()) return
        val size = words.size
        val newIndex = (_state.value.currentIndex + packSize) % size
        moveTo(newIndex)
    }

    /** Jump backward by [packSize] words (wraps around the deck). */
    fun prevPack(packSize: Int = 50) {
        if (words.isEmpty()) return
        val size = words.size
        val newIndex = ((_state.value.currentIndex - packSize) % size + size) % size
        moveTo(newIndex)
    }

    /**
     * Jump to [index] (clamped into [0, totalWords-1]). If playback was active, relaunches
     * from the new word. Out-of-range indices are clamped, not rejected.
     */
    fun seekTo(index: Int) {
        if (words.isEmpty()) return
        val clamped = index.coerceIn(0, words.size - 1)
        moveTo(clamped)
    }

    /**
     * Stop playback. Cancels the play job, cuts any in-flight audio, and clears
     * [DeckState.isPlaying]. [DeckState.currentIndex] is preserved so a subsequent [play]
     * resumes from the same word.
     */
    fun stop() {
        cancelPlayJob(cutAudio = true)
        _state.value = _state.value.copy(isPlaying = false, isPaused = false)
        Log.d(TAG, "stop() — playback halted at index ${_state.value.currentIndex}")
    }

    // ── Internal ──────────────────────────────────────────────────────────

    /**
     * Move the cursor to [newIndex]; if the player was actively playing, cancel and
     * relaunch the loop from the new word. Otherwise just update state.
     */
    private fun moveTo(newIndex: Int) {
        val wasPlaying = _state.value.isPlaying || _state.value.isPaused
        cancelPlayJob(cutAudio = true)
        _state.value = _state.value.copy(
            currentIndex = newIndex,
            currentWord = words.getOrNull(newIndex),
            isPaused = false
        )
        Log.d(TAG, "moveTo($newIndex) — wasPlaying=$wasPlaying")
        if (wasPlaying) {
            _state.value = _state.value.copy(isPlaying = true)
            launchPlayLoop()
        }
    }

    /**
     * Launch the endless play loop on [scope]. Stores the resulting [Job] in [playJob].
     *
     * The loop:
     * 1. Snapshot the current word (re-read each iteration so cursor moves are visible).
     * 2. Invoke [playWord] (TTS via SegmentPlayer, or the injected fake in tests).
     * 3. Delay [betweenWordsPauseMs].
     * 4. Advance the cursor (wrapping to 0) and emit state.
     *
     * Cancellation handling: on [CancellationException] (from next/prev/seek/stop), call
     * [TtsEngine.stop] to cut any audio the native synth is still producing.
     */
    private fun launchPlayLoop() {
        playJob = scope.launch {
            try {
                while (true) {
                    // Skip GREEN-marked words before playing. A GREEN word is excluded
                    // from background playback (the listener already knows it). Walk
                    // forward (wrapping) until we find a non-GREEN word, but cap the
                    // number of attempts at words.size so we can never spin forever if
                    // every remaining word is marked GREEN — in that case break out of
                    // the loop (the deck is effectively exhausted for this listener).
                    var idx = _state.value.currentIndex
                    var attempts = 0
                    while (attempts < words.size) {
                        val candidate = words.getOrNull(idx) ?: break
                        if (markStore?.getMark(candidate.wordIt) != BgVocabMark.GREEN) break
                        idx = if (idx + 1 >= words.size) 0 else idx + 1
                        attempts++
                    }
                    if (attempts >= words.size) {
                        // Every word is GREEN — nothing to play. Park the loop.
                        Log.d(TAG, "play loop: all words are GREEN-marked, parking")
                        _state.value = _state.value.copy(isPlaying = false)
                        break
                    }
                    // Reflect the (possibly skipped-to) index in state before playing.
                    if (idx != _state.value.currentIndex) {
                        _state.value = _state.value.copy(
                            currentIndex = idx,
                            currentWord = words.getOrNull(idx)
                        )
                    }
                    val word = words.getOrNull(idx) ?: break
                    _state.value = _state.value.copy(currentSlot = null)
                    Log.d(TAG, "▶ Playing word index=$idx rank=${word.rank} '${word.wordIt}'")
                    playWordFn(word)

                    delay(betweenWordsPauseMs)

                    val nextIdx = if (idx + 1 >= words.size) 0 else idx + 1
                    _state.value = _state.value.copy(
                        currentIndex = nextIdx,
                        currentWord = words.getOrNull(nextIdx)
                    )
                }
            } catch (e: CancellationException) {
                // Transport mutation (next/prev/seek/stop) cancelled us. Cut any audio
                // the synth is still emitting; rethrow to honor structured concurrency.
                Log.d(TAG, "play loop cancelled at index ${_state.value.currentIndex}")
                ttsEngine.stop()
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "play loop crashed", e)
                _state.value = _state.value.copy(isPlaying = false)
            }
        }
    }

    /**
     * Cancel the current play job if any. When [cutAudio] is true (default), also call
     * [TtsEngine.stop] to halt any audio the native synth is still producing — this is the
     * mechanism by which next/prev/seek/stop interrupt a word mid-speak.
     */
    private fun cancelPlayJob(cutAudio: Boolean) {
        playJob?.cancel()
        playJob = null
        if (cutAudio) {
            ttsEngine.stop()
        }
    }
}

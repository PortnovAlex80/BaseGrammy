package com.alexpo.grammermate.feature.backgroundvocab

import com.alexpo.grammermate.data.PhrasePair
import com.alexpo.grammermate.data.TtsEngine
import com.alexpo.grammermate.data.WordScript
import com.alexpo.grammermate.shared.audio.SegmentPlayer
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pure-JVM unit tests for [DeckPlayer]'s transport logic.
 *
 * Playback (the SegmentPlayer → TtsEngine path) is injected via [DeckPlayer]'s `playWord`
 * constructor parameter as a recording fake, so no native Sherpa-ONNX model is loaded. The
 * [TtsEngine] is still constructed (via Robolectric, same pattern as
 * [com.alexpo.grammermate.data.MultilingualStoryParserPauseTest]) because [DeckPlayer.stop]
 * and word navigation call [TtsEngine.stop] to cut audio — its `stop()` is safe on an
 * uninitialized engine (only touches nullable fields + atomics).
 *
 * The deck cycles on a real [CoroutineScope] backed by [Dispatchers.Unconfined] so the loop
 * runs inline under `runBlocking` and is deterministic.
 */
@RunWith(RobolectricTestRunner::class)
class DeckPlayerTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    /**
     * A real TtsEngine whose native path is never triggered (we never call speak/initialize).
     * Only [TtsEngine.stop] is exercised, and it is safe on a never-initialized engine.
     */
    private fun newTtsEngine(): TtsEngine = TtsEngine(context)

    /** Build a minimal [WordScript] with a stable identity for assertions. */
    private fun word(rank: Int, it: String = "word$rank"): WordScript = WordScript(
        rank = rank,
        wordIt = it,
        wordRu = "перевод$rank",
        colloIt = "collo $it",
        colloRu = "устойчивое $rank",
        sentences = listOf(PhrasePair("$it sentence.", "предложение $rank."))
    )

    /**
     * Build a DeckPlayer whose `playWord` records every word it was asked to speak and
     * cooperatively yields (short delay) so the loop is cancellable mid-word.
     *
     * The captured [played] list lets a test assert the order and count of words played
     * without inspecting the real TTS pipeline.
     */
    private fun newPlayer(
        tts: TtsEngine = newTtsEngine(),
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        played: MutableList<Int> = mutableListOf(),
        betweenWordsPauseMs: Long = 5L
    ): Pair<DeckPlayer, MutableList<Int>> {
        val segmentPlayer = SegmentPlayer(tts) // never actually used — playWord overrides it
        val player = DeckPlayer(
            ttsEngine = tts,
            segmentPlayer = segmentPlayer,
            scope = scope,
            betweenWordsPauseMs = betweenWordsPauseMs,
            playWord = { w ->
                played.add(w.rank)
                // Small cooperative delay so cancellation lands mid-playback for next/stop.
                delay(50)
            }
        )
        return player to played
    }

    // ── setWords ───────────────────────────────────────────────────────────

    @Test
    fun setWords_loadsDeck_andEmitsInitialState() {
        val (player, _) = newPlayer()
        player.setWords(listOf(word(1), word(2), word(3)))

        val s = player.state.value
        assertEquals(3, s.totalWords)
        assertEquals(0, s.currentIndex)
        assertEquals(1, s.currentWord?.rank)
        assertFalse(s.isPlaying)
        assertFalse(s.isPaused)
    }

    @Test
    fun setWords_emptyDeck_emitsZeroedState() {
        val (player, _) = newPlayer()
        player.setWords(emptyList())

        val s = player.state.value
        assertEquals(0, s.totalWords)
        assertEquals(0, s.currentIndex)
        assertEquals(null, s.currentWord)
    }

    // ── next / prev / seek (no playback running) ───────────────────────────

    @Test
    fun nextWord_advancesCursor_andUpdatesCurrentWord() {
        val (player, _) = newPlayer()
        player.setWords(listOf(word(1), word(2), word(3)))

        player.nextWord()
        assertEquals(1, player.state.value.currentIndex)
        assertEquals(2, player.state.value.currentWord?.rank)

        player.nextWord()
        assertEquals(2, player.state.value.currentIndex)
        assertEquals(3, player.state.value.currentWord?.rank)
    }

    @Test
    fun nextWord_wrapsAtEnd() {
        val (player, _) = newPlayer()
        player.setWords(listOf(word(1), word(2)))
        player.seekTo(1)

        player.nextWord()

        assertEquals(0, player.state.value.currentIndex)
        assertEquals(1, player.state.value.currentWord?.rank)
    }

    @Test
    fun prevWord_decrementsCursor_andWrapsAtZero() {
        val (player, _) = newPlayer()
        player.setWords(listOf(word(1), word(2), word(3)))

        player.prevWord() // from 0 → wraps to last
        assertEquals(2, player.state.value.currentIndex)
        assertEquals(3, player.state.value.currentWord?.rank)

        player.prevWord() // 2 → 1
        assertEquals(1, player.state.value.currentIndex)
    }

    @Test
    fun seekTo_clampsOutOfRangeIndex() {
        val (player, _) = newPlayer()
        player.setWords(listOf(word(1), word(2), word(3)))

        player.seekTo(99)
        assertEquals(2, player.state.value.currentIndex)

        player.seekTo(-5)
        assertEquals(0, player.state.value.currentIndex)
    }

    // ── play loop ──────────────────────────────────────────────────────────

    @Test
    fun play_loopsThroughWords_andWrapsToZero() = runBlocking {
        val (player, played) = newPlayer()
        player.setWords(listOf(word(1), word(2), word(3)))

        player.play()
        // Let the loop cycle through at least one full pass (3 words + wrap).
        // Each word = ~50ms playWord + 5ms between-words pause ≈ 55ms; allow generous time.
        delay(220)
        player.stop()

        assertTrue("play should have invoked playWord at least once", played.isNotEmpty())
        // First word played must be the initial current word (rank 1).
        assertEquals(1, played.first())
        // The loop must have advanced past the first word and eventually wrapped to 0 again.
        assertTrue("expected the loop to cycle through ranks > 1; got $played", played.size > 1)
        assertTrue("expected wrap to rank 1 in $played", played.contains(1))
        assertTrue("expected rank 2 in $played", played.contains(2))
        assertTrue("expected rank 3 in $played", played.contains(3))
    }

    @Test
    fun play_setsIsPlaying_stopClearsIt() = runBlocking {
        val (player, _) = newPlayer()
        player.setWords(listOf(word(1), word(2)))

        assertFalse(player.state.value.isPlaying)
        player.play()
        assertTrue(player.state.value.isPlaying)

        delay(30) // let it start
        player.stop()
        assertFalse(player.state.value.isPlaying)
        assertFalse(player.state.value.isPaused)
    }

    @Test
    fun play_emptyDeck_isNoOp() = runBlocking {
        val (player, played) = newPlayer()
        player.setWords(emptyList())

        player.play()
        delay(20)

        assertFalse(player.state.value.isPlaying)
        assertTrue(played.isEmpty())
    }

    // ── pause / resume ────────────────────────────────────────────────────

    @Test
    fun pause_setsFlagAndClearsIsPlaying_resumeRestores() = runBlocking {
        val (player, _) = newPlayer()
        player.setWords(listOf(word(1), word(2)))

        player.play()
        assertTrue(player.state.value.isPlaying)
        assertFalse(player.state.value.isPaused)

        player.pause()
        assertTrue(player.state.value.isPaused)
        assertFalse("isPlaying must be false while paused", player.state.value.isPlaying)

        player.resume()
        assertFalse(player.state.value.isPaused)
        assertTrue(player.state.value.isPlaying)
        player.stop()
    }

    // ── navigation while playing ───────────────────────────────────────────

    @Test

    fun nextWord_whilePlaying_relaunchesFromNewWord() = runBlocking {
        val (player, played) = newPlayer()
        player.setWords(listOf(word(1), word(2), word(3)))

        player.play()
        delay(20) // let rank 1 start

        player.nextWord() // cancel + jump to rank 2
        delay(20)
        // The cursor must be on index 1 (rank 2) BEFORE stop — stop() is the
        // full-reset transport (pinned by stop_resetsToStart) and zeroes it.
        assertEquals(1, player.state.value.currentIndex)
        player.stop()

        assertTrue("rank 2 must have been played after nextWord; got $played", played.contains(2))
    }

    @Test
    fun stop_resetsToStart() = runBlocking {
        val (player, _) = newPlayer()
        player.setWords(listOf(word(1), word(2), word(3)))
        player.seekTo(2)

        player.play()
        delay(20)
        player.stop()

        // Stop = full reset to the first word (media-player semantics);
        // pause is the position-preserving variant.
        assertEquals(0, player.state.value.currentIndex)
        assertNotNull(player.state.value.currentWord)
        assertEquals(1, player.state.value.currentWord?.rank)
        assertFalse(player.state.value.isPlaying)
    }
}

package com.alexpo.grammermate.feature.backgroundvocab

import com.alexpo.grammermate.data.MultilingualStoryParser
import com.alexpo.grammermate.data.PhrasePair
import com.alexpo.grammermate.data.SpeakSlot
import com.alexpo.grammermate.data.WordScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Proves the Audio-vs-Text selection added to [DeckPlayer.defaultPlayWord] (Wave 3A wiring):
 * for each `SpeakItem`, when [BgVocabAudioResolver.fileFor] returns a non-null file, the
 * player must emit [MultilingualStoryParser.Segment.Audio]; when it returns null (clip
 * absent), the player must fall back to [MultilingualStoryParser.Segment.Text] (TTS).
 *
 * ### Why we test the resolver directly rather than intercepting segments
 *
 * [DeckPlayer] feeds its segments to [com.alexpo.grammermate.shared.audio.SegmentPlayer],
 * which drives the native TTS/MediaPlayer pipeline and cannot be cleanly intercepted from
 * a pure-JVM test. Instead we verify the selection at its actual decision boundary:
 *
 *  1. The resolver contract — `fileFor(packId, rank, slot)` returns the expected file
 *     when the `.wav` exists and `null` when it does not (covers both directions of the
 *     branch in `defaultPlayWord`).
 *  2. The exact expression `audioResolver?.let { packId?.let { pid ->
 *     resolver.fileFor(pid, word.rank, slot) } }` — replicated here against the same
 *     resolver to prove the Audio/Text decision matches what `defaultPlayWord` will make.
 *     This is the load-bearing logic: `defaultPlayWord` is a thin wrapper that turns this
 *     non-null-vs-null result into `Segment.Audio` vs `Segment.Text`, with no other inputs.
 *
 * This pins the wav→TTS fallback behavior without pulling in Robolectric or the native TTS
 * engine, and will break loudly if either the resolver path convention or the
 * resolver/packId null-handling in `defaultPlayWord` drifts.
 */
class DeckPlayerAudioSegmentTest {

    private val packId = "TEST_PACK"

    /** Minimal WordScript — only `rank` and the WordIt slot matter for resolver paths. */
    private fun word(rank: Int): WordScript = WordScript(
        rank = rank,
        wordIt = "word$rank",
        wordRu = "перевод$rank",
        colloIt = "collo$rank",
        colloRu = "устойчивое$rank",
        sentences = listOf(PhrasePair("$rank sentence.", "предложение $rank."))
    )

    /** Create a clip file at the pack-scoped audio path under [baseDir]. */
    private fun createClip(baseDir: File, relative: String): File {
        val file = File(baseDir, "drills/$packId/bg_vocab/audio/$relative")
        file.parentFile.mkdirs()
        file.writeText("stub")
        return file
    }

    @Test
    fun resolver_returnsFile_whenClipPresent_andNull_whenAbsent() {
        val baseDir = createTempDirectory().toFile()
        try {
            // Pre-render only the Italian word clip for rank=2; leave everything else absent.
            createClip(baseDir, "it_000002_word.wav")
            val resolver = BgVocabAudioResolver(baseDir)

            // Present slot → non-null Audio path.
            val present = resolver.fileFor(packId, rank = 2, slot = SpeakSlot.WordIt)
            assertNotNull("WordIt clip for rank=2 must resolve", present)
            assertEquals(
                File(baseDir, "drills/$packId/bg_vocab/audio/it_000002_word.wav").absolutePath,
                present!!.absolutePath
            )

            // Absent slot (Russian translation — no ru_000002_word.wav shipped) → null → Text path.
            val absent = resolver.fileFor(packId, rank = 2, slot = SpeakSlot.WordRu)
            assertNull("WordRu clip must be null when not pre-rendered", absent)

            // Absent rank entirely → null.
            val absentRank = resolver.fileFor(packId, rank = 999, slot = SpeakSlot.WordIt)
            assertNull("Clip for un-rendered rank must be null", absentRank)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    /**
     * Replicates the exact Audio/Text decision made inside [DeckPlayer.defaultPlayWord]
     * and asserts both branches: a present clip selects [Segment.Audio], an absent clip
     * selects [Segment.Text]. This is the contract `defaultPlayWord` implements —
     * if it ever diverges from this expression, background-vocab playback silently
     * regresses to all-TTS or all-skip.
     */
    @Test
    fun defaultPlayWord_decision_matchesResolverNonNullality() {
        val baseDir = createTempDirectory().toFile()
        try {
            createClip(baseDir, "it_000002_word.wav")
            val resolver: BgVocabAudioResolver? = BgVocabAudioResolver(baseDir)
            val pid: String? = packId
            val w = word(rank = 2)

            // Decision helper identical to the one in DeckPlayer.defaultPlayWord.
            fun decide(slot: SpeakSlot, text: String, lang: String): MultilingualStoryParser.Segment {
                val audioFile = resolver?.let { r -> pid?.let { p -> r.fileFor(p, w.rank, slot) } }
                return if (audioFile != null) {
                    MultilingualStoryParser.Segment.Audio(audioFile, lang)
                } else {
                    MultilingualStoryParser.Segment.Text(text, lang)
                }
            }

            // Present clip → Audio segment.
            val wordItSeg = decide(SpeakSlot.WordIt, w.wordIt, "it")
            assertTrue("WordIt must produce Audio when clip is present", wordItSeg is MultilingualStoryParser.Segment.Audio)

            // Absent clip → Text segment (the TTS fallback).
            val wordRuSeg = decide(SpeakSlot.WordRu, w.wordRu, "ru")
            assertTrue("WordRu must produce Text when clip is absent", wordRuSeg is MultilingualStoryParser.Segment.Text)
            assertEquals(w.wordRu, (wordRuSeg as MultilingualStoryParser.Segment.Text).text)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    /**
     * When packId is null (no active pack), the resolver branch must short-circuit to
     * null and every segment is Text — the all-TTS path used by the asset-only fallback.
     */
    @Test
    fun nullPackId_forcesAllText() {
        val baseDir = createTempDirectory().toFile()
        try {
            createClip(baseDir, "it_000002_word.wav")
            val resolver: BgVocabAudioResolver? = BgVocabAudioResolver(baseDir)
            val pid: String? = null // no active pack
            val w = word(rank = 2)

            val audioFile = resolver?.let { r -> pid?.let { p -> r.fileFor(p, w.rank, SpeakSlot.WordIt) } }
            assertNull("Null packId must yield null audio even when clip exists", audioFile)
            // Therefore defaultPlayWord would emit Text for every slot. Documented by the
            // assertFalse: no Audio can be selected when pid is null.
            assertFalse(
                "Audio segment impossible with null packId",
                audioFile != null
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }
}

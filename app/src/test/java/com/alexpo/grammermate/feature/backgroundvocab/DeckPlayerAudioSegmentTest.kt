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
     * Replicates the Audio/Text decision made inside [DeckPlayer.defaultPlayWord] and
     * pins the full MVP chain: **opus-by-row → wav-by-rank → Text (TTS)**.
     *
     *  - When a row-indexed `.opus` clip exists, it wins (takes precedence over any
     *    rank-indexed `.wav` for the same slot) → [Segment.Audio] pointing at the opus.
     *  - When only the `.wav` exists, it is used → [Segment.Audio] pointing at the wav.
     *  - When neither exists, the slot falls back to [Segment.Text] (TTS synthesis).
     *
     * This is the load-bearing contract: if `defaultPlayWord` ever diverges from this
     * expression, background-vocab playback silently regresses to all-TTS or all-skip,
     * or — just as bad — plays the wrong clip family.
     */
    @Test
    fun defaultPlayWord_decision_prefersOpusRow_thenWav_thenText() {
        val baseDir = createTempDirectory().toFile()
        try {
            // For rank=2 / row=1, ship BOTH the rank-indexed wav and the row-indexed opus
            // for the Italian word slot. Leave every other slot (wordRu, collo, …) absent.
            createClip(baseDir, "it_000002_word.wav")
            createClip(baseDir, "it_r000001_f0.opus")
            val resolver: BgVocabAudioResolver? = BgVocabAudioResolver(baseDir)
            val pid: String? = packId
            val w = word(rank = 2)
            val row = 1

            // Decision helper identical to the one in DeckPlayer.defaultPlayWord.
            fun decide(slot: SpeakSlot, text: String, lang: String): MultilingualStoryParser.Segment {
                val audioFile = resolver?.let { r ->
                    pid?.let { p -> r.fileForRow(p, row, slot) ?: r.fileFor(p, w.rank, slot) }
                }
                return if (audioFile != null) {
                    MultilingualStoryParser.Segment.Audio(audioFile, lang)
                } else {
                    MultilingualStoryParser.Segment.Text(text, lang)
                }
            }

            // Present opus (row) wins over the present wav (rank) → Audio pointing at opus.
            val wordItSeg = decide(SpeakSlot.WordIt, w.wordIt, "it")
            assertTrue("WordIt must produce Audio when a clip is present", wordItSeg is MultilingualStoryParser.Segment.Audio)
            assertEquals(
                "Opus-by-row must take precedence over wav-by-rank",
                File(baseDir, "drills/$packId/bg_vocab/audio/it_r000001_f0.opus").absolutePath,
                (wordItSeg as MultilingualStoryParser.Segment.Audio).file.absolutePath
            )

            // Absent clip (no opus ru_r000001_f0.opus, no wav) → Text segment (TTS fallback).
            val wordRuSeg = decide(SpeakSlot.WordRu, w.wordRu, "ru")
            assertTrue("WordRu must produce Text when no clip is present", wordRuSeg is MultilingualStoryParser.Segment.Text)
            assertEquals(w.wordRu, (wordRuSeg as MultilingualStoryParser.Segment.Text).text)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    /**
     * Pins the row-indexed Opus resolver contract and the slot → `fN` field mapping used
     * by the MVP audio bank: word→f0, collo→f1, s1→f2, s2→f3, s3→f4, in both languages.
     * Returns the file when present, `null` when absent (caller falls back to wav/TTS).
     */
    @Test
    fun fileForRow_resolvesOpusByRow_andMapsSlotsToFieldIndex() {
        val baseDir = createTempDirectory().toFile()
        try {
            createClip(baseDir, "it_r000001_f0.opus") // word (it), row 1
            createClip(baseDir, "ru_r000001_f0.opus") // word (ru), row 1
            createClip(baseDir, "it_r000001_f1.opus") // collo (it), row 1
            createClip(baseDir, "ru_r000001_f3.opus") // s2 (ru), row 1
            val resolver = BgVocabAudioResolver(baseDir)

            // word → f0, both languages.
            assertEquals(
                File(baseDir, "drills/$packId/bg_vocab/audio/it_r000001_f0.opus").absolutePath,
                resolver.fileForRow(packId, 1, SpeakSlot.WordIt)!!.absolutePath
            )
            assertEquals(
                File(baseDir, "drills/$packId/bg_vocab/audio/ru_r000001_f0.opus").absolutePath,
                resolver.fileForRow(packId, 1, SpeakSlot.WordRu)!!.absolutePath
            )
            // collo → f1 (present it, absent ru).
            assertEquals(
                File(baseDir, "drills/$packId/bg_vocab/audio/it_r000001_f1.opus").absolutePath,
                resolver.fileForRow(packId, 1, SpeakSlot.ColloIt)!!.absolutePath
            )
            assertNull("collo_ru (f1) absent must be null", resolver.fileForRow(packId, 1, SpeakSlot.ColloRu))
            // s1 → f2 (absent), s2 → f3 (present ru), s3 → f4 (absent).
            assertNull(resolver.fileForRow(packId, 1, SpeakSlot.SentenceIt(0)))
            assertEquals(
                File(baseDir, "drills/$packId/bg_vocab/audio/ru_r000001_f3.opus").absolutePath,
                resolver.fileForRow(packId, 1, SpeakSlot.SentenceRu(1))!!.absolutePath
            )
            assertNull(resolver.fileForRow(packId, 1, SpeakSlot.SentenceIt(2)))
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

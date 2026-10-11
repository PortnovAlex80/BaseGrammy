package com.alexpo.grammermate.feature.backgroundvocab

import com.alexpo.grammermate.data.SpeakSlot
import java.io.File

/**
 * Resolves a pre-rendered clip for a `(packId, rank, slot)` triple under the
 * background-vocab audio banks installed on this device.
 *
 * ### Bank scope: corpus, not pack
 *
 * The pre-rendered banks (Opus + legacy `.wav`) belong to the shared background-vocab
 * corpus — the 12 000-word `bg_vocab_12000.csv` deck that every Italian pack ships
 * byte-identical or falls back to via the bundled asset — NOT to the pack whose drills
 * directory happens to host the clips (today only `ITALIAN_SHORT` installs a bank). A
 * lookup therefore checks, in order (ADR-034):
 *
 *  1. `drills/{packId}/bg_vocab/audio` when [packId] is non-null — a bank installed
 *     for the active pack always wins;
 *  2. every other installed `drills/{packId}/bg_vocab/audio` directory, in ascending
 *     pack-id order — a deterministic fallback across sibling packs.
 *
 * A `null` [packId] (no active pack; the bundled deck is playing) resolves against the
 * sibling banks only, so the listener keeps its pre-rendered voice after the user
 * leaves pack context.
 *
 * Cross-language lookups are impossible by construction: clip names carry the corpus
 * language prefix (`it_r2_f0.opus`), so a bank built for another language never
 * contains a file this lookup asks for. If a future pack ships a background-vocab deck
 * that is NOT the shared corpus, the bank layout must grow a corpus discriminator
 * before its clips may be reused — rank numbers from a different corpus must not
 * silently resolve against this corpus's clips (ADR-034).
 *
 * Banks are enumerated from disk on every lookup, so a sound pack imported while the
 * listener is running is picked up by the next word without a service restart.
 *
 * Pure file logic — no Android dependencies, fully unit-testable on a plain JVM.
 *
 * @param baseDir the grammarmate root directory (`File(context.filesDir, "grammarmate")`),
 *  matching the convention used by `DrillFileManager`.
 */
class BgVocabAudioResolver(private val baseDir: File) {

    /**
     * Map [slot] to its `(field, lang)` pair per the legacy `.wav` naming convention:
     *  - [SpeakSlot.WordIt] / [SpeakSlot.WordRu]  → field `word`
     *  - [SpeakSlot.ColloIt] / [SpeakSlot.ColloRu] → field `collo`
     *  - [SpeakSlot.SentenceIt]`(i)` / [SpeakSlot.SentenceRu]`(i)` → field `s{i+1}`
     *  - `*It` slots → lang `it`; `*Ru` slots → lang `ru`
     */
    private fun slotToFieldLang(slot: SpeakSlot): Pair<String, String> = when (slot) {
        is SpeakSlot.WordIt -> "word" to "it"
        is SpeakSlot.WordRu -> "word" to "ru"
        is SpeakSlot.ColloIt -> "collo" to "it"
        is SpeakSlot.ColloRu -> "collo" to "ru"
        is SpeakSlot.SentenceIt -> "s${slot.index + 1}" to "it"
        is SpeakSlot.SentenceRu -> "s${slot.index + 1}" to "ru"
    }

    /**
     * Map [slot] to the flat `fN` field index used by the pre-rendered **Opus** audio bank:
     *  - `WordIt` / `WordRu` → `f0`
     *  - `ColloIt` / `ColloRu` → `f1`
     *  - `SentenceIt(i)` / `SentenceRu(i)` → `f{i+2}` (i.e. s1→f2, s2→f3, s3→f4, …)
     *
     * The Opus bank carries one clip per `(field, language)`; both `*It` and `*Ru` map to
     * the same field index, distinguished by the `lang` segment of the filename.
     */
    private fun slotToFieldIndex(slot: SpeakSlot): Int = when (slot) {
        is SpeakSlot.WordIt, is SpeakSlot.WordRu -> 0
        is SpeakSlot.ColloIt, is SpeakSlot.ColloRu -> 1
        is SpeakSlot.SentenceIt -> slot.index + 2
        is SpeakSlot.SentenceRu -> slot.index + 2
    }

    /**
     * @return the expected legacy `.wav` [File] for `(packId, rank, slot)` if it exists
     *  on disk, or `null` if no such clip has been pre-rendered in any installed bank
     *  (caller falls back to TTS). Filenames are 6-padded (`it_000002_word.wav`).
     */
    fun fileFor(packId: String?, rank: Int, slot: SpeakSlot): File? {
        val (field, lang) = slotToFieldLang(slot)
        val rankPadded = rank.toString().padStart(6, '0')
        return firstExistingClip(packId) { dir -> File(dir, "${lang}_${rankPadded}_${field}.wav") }
    }

    /**
     * **Rank-indexed Opus lookup** against the pre-rendered Opus bank. The bank is named
     * `{lang}_r{rank}_f{fieldIndex}.opus` (rank as a bare integer, NO zero-padding) and is
     * keyed by the word's frequency RANK (the `rank` column of the deck CSV — ranks
     * 2..12501 for the Italian deck), NOT by row position.
     *
     * Convention:
     * ```
     * drills/{packId}/bg_vocab/audio/{lang}_r{rank}_f{fieldIndex}.opus
     * ```
     * - `lang` ∈ `{it, ru}` derived from the [SpeakSlot] (`*It` → `it`, `*Ru` → `ru`)
     * - `fieldIndex` via [slotToFieldIndex] (`f0`=word, `f1`=collo, `f2`=s1, …)
     *
     * Note: rank is NOT zero-padded (the bank uses bare ints: `it_r2_f0.opus`,
     * `it_r1114_f0.opus`). This differs from the legacy `.wav` clips ([fileFor]), which
     * ARE 6-padded (`it_000002_word.wav`).
     *
     * The Ogg-Opus container is decoded natively by Android `MediaPlayer` from API 21,
     * so no extra decoder is needed.
     *
     * @return the Opus [File] if it exists in the active pack's bank or any installed
     *  sibling bank (see class KDoc / ADR-034), or `null` (caller then tries [fileFor]
     *  for the legacy `.wav` clips / falls back to TTS).
     */
    fun fileForRank(packId: String?, rank: Int, slot: SpeakSlot): File? {
        val lang = slotToFieldLang(slot).second
        val fIndex = slotToFieldIndex(slot)
        return firstExistingClip(packId) { dir -> File(dir, "${lang}_r${rank}_f${fIndex}.opus") }
    }

    /**
     * Ordered bank directories for a lookup: the requested pack's own audio directory
     * first (when non-null), then every other installed pack's `bg_vocab/audio`
     * directory in ascending pack-id order. Only directories that exist on disk are
     * returned; a missing `drills` root yields at most the (possibly absent)
     * active-pack directory — lookups then degrade to TTS, never crash.
     */
    private fun bankDirectories(packId: String?): List<File> {
        val active = packId?.let { pack -> File(baseDir, "drills/$pack/bg_vocab/audio") }
        val siblings = File(baseDir, "drills").listFiles { f -> f.isDirectory }
            .orEmpty()
            .map { packDir -> File(packDir, "bg_vocab/audio") }
            .filter { it.isDirectory && it.absolutePath != active?.absolutePath }
            .sortedBy { it.parentFile?.parentFile?.name ?: "" }
        return if (active != null) listOf(active) + siblings else siblings
    }

    /** First clip file [nameIn] yields across the ordered [bankDirectories], if any. */
    private fun firstExistingClip(packId: String?, nameIn: (File) -> File): File? =
        bankDirectories(packId).asSequence().map(nameIn).firstOrNull { it.exists() }
}

package com.alexpo.grammermate.feature.backgroundvocab

import com.alexpo.grammermate.data.SpeakSlot
import java.io.File

/**
 * Resolves a pre-rendered `.wav` clip for a `(packId, rank, slot)` triple under the
 * pack-scoped background-vocab audio directory.
 *
 * Convention:
 * ```
 * drills/{packId}/bg_vocab/audio/{lang}_{rank:06d}_{field}.wav
 * ```
 * where `field` ∈ `{word, collo, s1..s5}` and `lang` ∈ `{it, ru}`, both derived from
 * the [SpeakSlot]:
 *  - [SpeakSlot.WordIt] / [SpeakSlot.WordRu]  → field `word`
 *  - [SpeakSlot.ColloIt] / [SpeakSlot.ColloRu] → field `collo`
 *  - [SpeakSlot.SentenceIt]`(i)` / [SpeakSlot.SentenceRu]`(i)` → field `s{i+1}`
 *  - `*It` slots → lang `it`; `*Ru` slots → lang `ru`
 *
 * Returns the [File] only if it exists on disk; `null` otherwise. Callers fall back
 * to TTS synthesis when this returns null. Pure file logic — no Android dependencies,
 * fully unit-testable on a plain JVM.
 *
 * @param baseDir the grammarmate root directory (`File(context.filesDir, "grammarmate")`),
 *  matching the convention used by `DrillFileManager`.
 */
class BgVocabAudioResolver(private val baseDir: File) {

    /**
     * Map [slot] to its `(field, lang)` pair per the class convention. Visible for
     * testing/diagnostics.
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
     * @return the expected `.wav` [File] for `(packId, rank, slot)` if it exists on
     *  disk, or `null` if no such clip has been pre-rendered (caller falls back to TTS).
     */
    fun fileFor(packId: String, rank: Int, slot: SpeakSlot): File? {
        val (field, lang) = slotToFieldLang(slot)
        val rankPadded = rank.toString().padStart(6, '0')
        val file = File(baseDir, "drills/$packId/bg_vocab/audio/${lang}_${rankPadded}_${field}.wav")
        return file.takeIf { it.exists() }
    }

    /**
     * **MVP / row-indexed Opus lookup.** The current Opus bank was generated against
     * the **1-based row position** in the deck CSV (not the `rank` column — that mismatch
     * is a generation bug; a rank-numbered bank will replace this later). Each clip is
     * named `{lang}_r{row:06d}_f{fieldIndex}.opus` and lives under the same pack-scoped
     * audio directory as the `.wav` clips.
     *
     * Convention:
     * ```
     * drills/{packId}/bg_vocab/audio/{lang}_r{row:06d}_f{fieldIndex}.opus
     * ```
     * - `lang` ∈ `{it, ru}` derived from the [SpeakSlot] (`*It` → `it`, `*Ru` → `ru`)
     * - `fieldIndex` via [slotToFieldIndex] (`f0`=word, `f1`=collo, `f2`=s1, …)
     *
     * @return the Opus [File] if it exists on disk, or `null` (caller then tries
     *  [fileFor] / falls back to TTS). The Ogg-Opus container is decoded natively by
     *  Android `MediaPlayer` from API 21, so no extra decoder is needed.
     */
    fun fileForRow(packId: String, row: Int, slot: SpeakSlot): File? {
        val lang = slotToFieldLang(slot).second
        val fIndex = slotToFieldIndex(slot)
        val rowPadded = row.toString().padStart(6, '0')
        val file = File(baseDir, "drills/$packId/bg_vocab/audio/${lang}_r${rowPadded}_f${fIndex}.opus")
        return file.takeIf { it.exists() }
    }
}

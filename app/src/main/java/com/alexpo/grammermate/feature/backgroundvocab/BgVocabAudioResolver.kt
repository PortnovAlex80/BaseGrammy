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
     * @return the expected `.wav` [File] for `(packId, rank, slot)` if it exists on
     *  disk, or `null` if no such clip has been pre-rendered (caller falls back to TTS).
     */
    fun fileFor(packId: String, rank: Int, slot: SpeakSlot): File? {
        val (field, lang) = slotToFieldLang(slot)
        val rankPadded = rank.toString().padStart(6, '0')
        val file = File(baseDir, "drills/$packId/bg_vocab/audio/${lang}_${rankPadded}_${field}.wav")
        return file.takeIf { it.exists() }
    }
}

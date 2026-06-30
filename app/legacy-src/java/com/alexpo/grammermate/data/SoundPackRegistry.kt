package com.alexpo.grammermate.data

/**
 * Specification for a downloadable / importable pre-rendered **sound pack**: a single ZIP
 * archive of Ogg-Opus clips for the background-vocab audio bank of a given pack.
 *
 * The ZIP is laid out with entries of the form `bg_vocab/audio/{lang}_r{rank}_f{field}.opus`
 * and is stream-extracted into [SoundPackManager.audioDirFor] (the exact directory
 * [com.alexpo.grammermate.feature.backgroundvocab.BgVocabAudioResolver] reads via
 * `fileForRank`).
 *
 * Mirrors the shape of [TtsModelSpec] / [TtsModelRegistry]: an immutable spec data class
 * plus a small registry object exposing [specFor].
 *
 * @property packId              the pack this sound pack belongs to (matches `Pack.id`).
 * @property downloadUrl         HTTPS URL of the GitHub Release asset. **PLACEHOLDER** —
 *                               replaced with the real asset URL once the ZIP is uploaded
 *                               to the `soundpack-v1` release.
 * @property fallbackSizeBytes   best-known payload size (uncompressed bytes of the Opus
 *                               bank), used for progress reporting when the server does
 *                               not advertise a `Content-Length` and for the storage
 *                               pre-check. 615 MB for the full Italian bank.
 * @property authToken           optional bearer token injected as
 *                               `Authorization: Bearer <token>` on the download request,
 *                               to support private GitHub release assets. `null` for the
 *                               public release.
 */
data class SoundPackSpec(
    val packId: String,
    val downloadUrl: String,
    val fallbackSizeBytes: Long,
    val authToken: String? = null
)

/**
 * Registry of known sound-pack specs.
 *
 * NOTE: the [SoundPackSpec.downloadUrl] for `ITALIAN_SHORT` is a PLACEHOLDER pointing at the
 * `soundpack-v1` GitHub Release and MUST be corrected to the real asset URL after the ZIP
 * is uploaded. The [SoundPackSpec.fallbackSizeBytes] reflects the 615 MB actual payload.
 */
object SoundPackRegistry {

    val packs: Map<String, SoundPackSpec> = mapOf(
        "ITALIAN_SHORT" to SoundPackSpec(
            packId = "ITALIAN_SHORT",
            // PLACEHOLDER — update to the real asset URL after uploading the ZIP to the
            // `soundpack-v1` GitHub Release. Public release (authToken = null).
            downloadUrl = "https://github.com/PortnovAlex80/BaseGrammy/releases/download/soundpack-v1/ITALIAN_SHORT_soundpack_full.zip",
            fallbackSizeBytes = 644_847_464L,
            authToken = null
        )
    )

    /**
     * @return the [SoundPackSpec] registered for [packId], or `null` if the pack has no
     *  sound pack (caller then treats the pack as TTS-only).
     */
    fun specFor(packId: String): SoundPackSpec? = packs[packId]
}

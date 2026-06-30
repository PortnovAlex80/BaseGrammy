package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Loads background-vocab [WordScript] decks from app assets or pack-scoped
 * drill directories.
 *
 * Two entry points:
 *  - [load] reads the bundled asset deck (fallback / pre-pack default).
 *  - [loadPackScoped] reads a pack-declared deck from
 *    `drills/{packId}/bg_vocab/{file}` under [baseDir], resolving the file
 *    name from the pack's `manifest.json` `backgroundVocab.file` field.
 *
 * Mirrors the asset-loading pattern used by [ItalianDrillVocabParser] and
 * [LessonStore]: open the asset via [Context.assets], decode as UTF-8, delegate
 * parsing to [BgVocabCsvParser]. Synchronous — callers (the
 * VocabPlaybackService / DeckPlayer) are expected to invoke this on a background
 * dispatcher.
 *
 * On any I/O or parse error the loader returns an empty list and logs; it never
 * throws, so a missing/corrupt asset degrades to "no words" rather than
 * crashing the foreground service.
 */
object BgVocabLoader {

    private const val TAG = "BgVocabLoader"

    /** Default deck location under `assets/grammarmate/packs/` (12000 words). */
    const val DEFAULT_ASSET_PATH = "grammarmate/packs/bg_vocab_12000.csv"

    /**
     * Load the word deck at [assetPath]. Returns an empty list if the asset is
     * missing or fails to parse.
     */
    fun load(context: Context, assetPath: String = DEFAULT_ASSET_PATH): List<WordScript> {
        return try {
            context.assets.open(assetPath).use { stream ->
                val words = BgVocabCsvParser.parse(stream)
                Log.d(TAG, "Loaded ${words.size} background-vocab words from $assetPath")
                words
            }
        } catch (e: IOException) {
            Log.w(TAG, "Background-vocab asset not found: $assetPath", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Background-vocab CSV at $assetPath has an invalid header", e)
            emptyList()
        }
    }

    /**
     * Load a pack-scoped background-vocab deck declared in the pack manifest.
     *
     * Resolution order:
     *  1. Read `packs/{packId}/manifest.json` (under [baseDir]) and look up the
     *     `backgroundVocab.file` field. If the manifest, the section, or the
     *     file field is absent, return an empty list (the pack simply does not
     *     ship a background-vocab deck).
     *  2. Resolve the CSV at `drills/{packId}/bg_vocab/{file}` under [baseDir].
     *     This is the location [PackImporter.importBackgroundVocab] copies the
     *     file to during pack import.
     *  3. Parse via [BgVocabCsvParser].
     *
     * [baseDir] follows the project convention `File(context.filesDir,
     * "grammarmate")` (the same root [DrillFileManager] and the pack importer
     * operate on).
     *
     * Never throws — returns an empty list on any I/O, JSON, or parse error,
     * matching the resilience contract of [load].
     */
    fun loadPackScoped(context: Context, baseDir: File, packId: String): List<WordScript> {
        val manifestFile = File(baseDir, "packs/$packId/manifest.json")
        val fileName = try {
            if (!manifestFile.exists()) {
                Log.d(TAG, "loadPackScoped: manifest not found at ${manifestFile.absolutePath}")
                return emptyList()
            }
            val manifestJson = JSONObject(manifestFile.readText())
            val section = manifestJson.optJSONObject("backgroundVocab")
            section?.optString("file")?.trim()?.ifBlank { null }
        } catch (e: Exception) {
            Log.w(TAG, "loadPackScoped: failed to read backgroundVocab.file from $packId manifest", e)
            return emptyList()
        }
        if (fileName.isNullOrEmpty()) {
            Log.d(TAG, "loadPackScoped: pack $packId has no backgroundVocab.file")
            return emptyList()
        }

        val csvFile = File(baseDir, "drills/$packId/bg_vocab/$fileName")
        return try {
            if (!csvFile.exists()) {
                Log.w(TAG, "loadPackScoped: background-vocab CSV not found at ${csvFile.absolutePath}")
                return emptyList()
            }
            val words = csvFile.inputStream().use { BgVocabCsvParser.parse(it) }
            Log.d(TAG, "Loaded ${words.size} background-vocab words for pack=$packId from ${csvFile.absolutePath}")
            words
        } catch (e: IOException) {
            Log.w(TAG, "loadPackScoped: I/O error reading ${csvFile.absolutePath}", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "loadPackScoped: invalid header in ${csvFile.absolutePath}", e)
            emptyList()
        }
    }
}

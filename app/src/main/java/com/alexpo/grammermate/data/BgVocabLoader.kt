package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import java.io.IOException

/**
 * Loads background-vocab [WordScript] decks from app assets.
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

    /** Default MVP deck location under `assets/grammarmate/packs/`. */
    const val DEFAULT_ASSET_PATH = "grammarmate/packs/bg_vocab_50.csv"

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
}

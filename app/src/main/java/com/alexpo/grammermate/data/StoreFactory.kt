package com.alexpo.grammermate.data

import android.app.Application

/**
 * Thread-safe singleton that caches [WordMasteryStore] and [VerbDrillStore]
 * instances per packId. Prevents multiple independent store instances for the
 * same packId from causing data loss (load-modify-save races, stale caches).
 *
 * All consumers must go through this factory instead of creating stores directly.
 */
class StoreFactory private constructor(private val appContext: Application) {

    private val wordMasteryCache = mutableMapOf<String?, WordMasteryStoreImpl>()
    private val verbDrillCache = mutableMapOf<String?, VerbDrillStoreImpl>()
    private val badSentenceCache: BadSentenceStoreImpl by lazy { BadSentenceStoreImpl(appContext) }

    @Synchronized
    fun getWordMasteryStore(packId: String?): WordMasteryStoreImpl {
        return wordMasteryCache.getOrPut(packId) {
            WordMasteryStoreImpl(appContext, packId = packId)
        }
    }

    @Synchronized
    fun getVerbDrillStore(packId: String?): VerbDrillStoreImpl {
        return verbDrillCache.getOrPut(packId) {
            VerbDrillStoreImpl(appContext, packId = packId)
        }
    }

    /**
     * Returns the singleton [BadSentenceStoreImpl] instance.
     * All consumers share the same in-memory cache so that a bad sentence
     * flagged from any screen (training, verb drill, vocab drill) is
     * immediately visible to all others.
     */
    fun getBadSentenceStore(): BadSentenceStoreImpl = badSentenceCache

    /**
     * Remove cached entries for the given packId, forcing fresh instances
     * on next access. Useful after progress resets.
     */
    @Synchronized
    fun evict(packId: String?) {
        wordMasteryCache.remove(packId)
        verbDrillCache.remove(packId)
    }

    /**
     * Clear all cached instances. Called during full progress reset.
     */
    @Synchronized
    fun clearCache() {
        wordMasteryCache.clear()
        verbDrillCache.clear()
    }

    companion object {
        @Volatile
        private var instance: StoreFactory? = null

        fun getInstance(application: Application): StoreFactory {
            return instance ?: synchronized(this) {
                instance ?: StoreFactory(application).also { instance = it }
            }
        }
    }
}

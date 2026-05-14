package com.alexpo.grammermate.data

import android.app.Application

/**
 * Thread-safe singleton that caches store instances.
 * Prevents multiple independent store instances from causing data loss
 * (load-modify-save races, stale caches).
 *
 * All consumers must go through this factory instead of creating stores directly.
 */
class StoreFactory private constructor(private val appContext: Application) {

    // ── Pack-scoped stores (keyed by packId) ─────────────────────────────
    private val wordMasteryCache = mutableMapOf<String?, WordMasteryStoreImpl>()
    private val verbDrillCache = mutableMapOf<String?, VerbDrillStoreImpl>()

    // ── Singleton stores (lazy, thread-safe) ─────────────────────────────
    private val badSentenceCache: BadSentenceStoreImpl by lazy { BadSentenceStoreImpl(appContext) }
    private val masteryCache: MasteryStoreImpl by lazy { MasteryStoreImpl(appContext) }
    private val progressCache: ProgressStoreImpl by lazy { ProgressStoreImpl(appContext) }
    private val streakCache: StreakStoreImpl by lazy { StreakStoreImpl(appContext) }
    private val lessonCache: LessonStoreImpl by lazy { LessonStoreImpl(appContext) }
    private val appConfigCache: AppConfigStoreImpl by lazy { AppConfigStoreImpl(appContext) }
    private val hiddenCardCache: HiddenCardStore by lazy { HiddenCardStore(appContext) }
    private val vocabProgressCache: VocabProgressStore by lazy { VocabProgressStore(appContext) }
    private val profileCache: ProfileStore by lazy { ProfileStore(appContext) }
    private val drillProgressCache: DrillProgressStore by lazy { DrillProgressStore(appContext) }

    // ── Pack-scoped accessors ────────────────────────────────────────────

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

    // ── Singleton accessors ──────────────────────────────────────────────

    /** All consumers share the same in-memory cache so that a bad sentence
     *  flagged from any screen (training, verb drill, vocab drill) is
     *  immediately visible to all others. */
    fun getBadSentenceStore(): BadSentenceStoreImpl = badSentenceCache

    fun getMasteryStore(): MasteryStoreImpl = masteryCache

    fun getProgressStore(): ProgressStoreImpl = progressCache

    fun getStreakStore(): StreakStoreImpl = streakCache

    fun getLessonStore(): LessonStoreImpl = lessonCache

    fun getAppConfigStore(): AppConfigStoreImpl = appConfigCache

    fun getHiddenCardStore(): HiddenCardStore = hiddenCardCache

    fun getVocabProgressStore(): VocabProgressStore = vocabProgressCache

    fun getProfileStore(): ProfileStore = profileCache

    fun getDrillProgressStore(): DrillProgressStore = drillProgressCache

    // ── Cache management ─────────────────────────────────────────────────

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

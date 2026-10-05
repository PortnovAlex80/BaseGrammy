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
    private val auxDrillCache = mutableMapOf<String?, AuxDrillStoreImpl>()

    // ── Singleton stores (lazy, thread-safe) ─────────────────────────────
    private val badSentenceCache: BadSentenceStoreImpl by lazy { BadSentenceStoreImpl(appContext) }
    private val masteryCache: MasteryStoreImpl by lazy { MasteryStoreImpl(appContext) }
    private val progressCache: ProgressStoreImpl by lazy { ProgressStoreImpl(appContext) }
    private val streakCache: StreakStoreImpl by lazy { StreakStoreImpl(appContext) }
    private val lessonCache: LessonStoreImpl by lazy { LessonStoreImpl(appContext) }
    private val appConfigCache: AppConfigStoreImpl by lazy { AppConfigStoreImpl(appContext) }
    private val hiddenCardCache: HiddenCardStoreImpl by lazy { HiddenCardStoreImpl(appContext) }
    private val vocabProgressCache = mutableMapOf<String?, VocabProgressStoreImpl>()
    private val profileCache: ProfileStoreImpl by lazy { ProfileStoreImpl(appContext) }

    // ── Pack-scoped accessors ────────────────────────────────────────────

    @Synchronized
    fun getWordMasteryStore(packId: String?): WordMasteryStore {
        return wordMasteryCache.getOrPut(packId) {
            WordMasteryStoreImpl(appContext, packId = packId)
        }
    }

    @Synchronized
    fun getVerbDrillStore(packId: String?): VerbDrillStore {
        return verbDrillCache.getOrPut(packId) {
            VerbDrillStoreImpl(appContext, packId = packId)
        }
    }

    @Synchronized
    fun getAuxDrillStore(packId: String?): AuxDrillStore {
        return auxDrillCache.getOrPut(packId) {
            AuxDrillStoreImpl(appContext, packId = packId)
        }
    }

    // ── Singleton accessors ──────────────────────────────────────────────

    /** All consumers share the same in-memory cache so that a bad sentence
     *  flagged from any screen (training, verb drill, vocab drill) is
     *  immediately visible to all others. */
    fun getBadSentenceStore(): BadSentenceStore = badSentenceCache

    fun getMasteryStore(): MasteryStore = masteryCache

    fun getProgressStore(): ProgressStore = progressCache

    fun getStreakStore(): StreakStore = streakCache

    fun getLessonStore(): LessonStore = lessonCache

    fun getAppConfigStore(): AppConfigStore = appConfigCache

    fun getHiddenCardStore(): HiddenCardStore = hiddenCardCache

    @Synchronized
    fun getVocabProgressStore(packId: String?): VocabProgressStore {
        return vocabProgressCache.getOrPut(packId) {
            VocabProgressStoreImpl(appContext, packId = packId)
        }
    }

    fun getProfileStore(): ProfileStore = profileCache


    // ── Cache management ─────────────────────────────────────────────────

    /**
     * Remove cached entries for the given packId, forcing fresh instances
     * on next access. Useful after progress resets.
     */
    @Synchronized
    fun evict(packId: String?) {
        auxDrillCache.remove(packId)?.invalidateCache()
        wordMasteryCache.remove(packId)?.invalidateCache()
        verbDrillCache.remove(packId)?.invalidateCache()
        vocabProgressCache.remove(packId)?.invalidateCache()
    }

    /**
     * Clear all cached instances. Called during full progress reset.
     */
    @Synchronized
    fun clearCache() {
        auxDrillCache.values.forEach(AuxDrillStoreImpl::invalidateCache)
        auxDrillCache.clear()
        wordMasteryCache.values.forEach(WordMasteryStoreImpl::invalidateCache)
        wordMasteryCache.clear()
        verbDrillCache.values.forEach(VerbDrillStoreImpl::invalidateCache)
        verbDrillCache.clear()
        vocabProgressCache.values.forEach(VocabProgressStoreImpl::invalidateCache)
        vocabProgressCache.clear()
    }

    /** Refresh live references after a backup replaces their underlying files. */
    @Synchronized
    internal fun invalidateAfterRestore() {
        hiddenCardCache.invalidateCache()
        badSentenceCache.invalidateCache()
        wordMasteryCache.values.forEach(WordMasteryStoreImpl::invalidateCache)
        verbDrillCache.values.forEach(VerbDrillStoreImpl::invalidateCache)
        auxDrillCache.values.forEach(AuxDrillStoreImpl::invalidateCache)
        vocabProgressCache.values.forEach(VocabProgressStoreImpl::invalidateCache)
    }

    companion object {
        @Volatile
        private var instance: StoreFactory? = null

        fun getInstance(application: Application): StoreFactory {
            return instance?.takeIf { it.appContext === application } ?: synchronized(this) {
                instance?.takeIf { it.appContext === application }
                    ?: StoreFactory(application).also { instance = it }
            }
        }
    }
}

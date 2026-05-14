package com.alexpo.grammermate

import android.app.Application
import com.alexpo.grammermate.data.*

/**
 * Centralized dependency container that exposes all store interfaces.
 * Replaces direct StoreFactory usage in ViewModels and other consumers.
 *
 * Thread-safe: all stores are lazily initialized via StoreFactory's internal caching.
 */
class AppContainer(private val application: Application) {
    private val storeFactory: StoreFactory = StoreFactory.getInstance(application)

    val lessonStore: LessonStore by lazy { storeFactory.getLessonStore() }
    val progressStore: ProgressStore by lazy { storeFactory.getProgressStore() }
    val configStore: AppConfigStore by lazy { storeFactory.getAppConfigStore() }
    val masteryStore: MasteryStore by lazy { storeFactory.getMasteryStore() }
    val streakStore: StreakStore by lazy { storeFactory.getStreakStore() }
    val badSentenceStore: BadSentenceStore by lazy { storeFactory.getBadSentenceStore() }
    val hiddenCardStore: HiddenCardStore by lazy { storeFactory.getHiddenCardStore() }
    val vocabProgressStore: VocabProgressStore by lazy { storeFactory.getVocabProgressStore() }
    val profileStore: ProfileStore by lazy { storeFactory.getProfileStore() }
    val drillProgressStore: DrillProgressStore by lazy { storeFactory.getDrillProgressStore() }
    val backupManager: BackupManager by lazy { BackupManagerImpl(application) }
    val ttsEngine: TtsEngine by lazy { TtsProvider.getInstance(application).ttsEngine }

    // Pack-scoped stores
    fun wordMasteryStore(packId: String?): WordMasteryStore = storeFactory.getWordMasteryStore(packId)
    fun verbDrillStore(packId: String?): VerbDrillStore = storeFactory.getVerbDrillStore(packId)

    // Cache management
    fun evict(packId: String?) = storeFactory.evict(packId)
    fun clearCache() = storeFactory.clearCache()
}

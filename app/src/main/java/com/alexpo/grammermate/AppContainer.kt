package com.alexpo.grammermate

import android.app.Application
import com.alexpo.grammermate.data.*
import com.alexpo.grammermate.data.PackLessonProgressStoreImpl
import com.alexpo.grammermate.feature.backgroundvocab.BgVocabAudioResolver
import com.alexpo.grammermate.feature.backgroundvocab.StoryAudioResolver

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
    fun vocabProgressStore(packId: String?): VocabProgressStore = storeFactory.getVocabProgressStore(packId)
    val profileStore: ProfileStore by lazy { storeFactory.getProfileStore() }
    val backupManager: BackupManager by lazy { BackupManagerImpl(application) }
    val ttsEngine: TtsEngine by lazy { TtsProvider.getInstance(application).ttsEngine }
    val bgVocabMarkStore: BgVocabMarkStore by lazy { BgVocabMarkStore(application) }
    val bgVocabPositionStore: BgVocabPositionStore by lazy { BgVocabPositionStore(application) }

    /**
     * Root directory for pack-scoped background-vocab data: `filesDir/grammarmate`.
     * Matches the convention used by [com.alexpo.grammermate.data.DrillFileManager] and
     * the pack importer — the same root [BgVocabAudioResolver] and
     * [com.alexpo.grammermate.data.BgVocabLoader.loadPackScoped] operate on.
     */
    val baseDir: java.io.File get() = java.io.File(application.filesDir, "grammarmate")

    /**
     * Resolves pre-rendered `.wav` clips for pack-scoped background-vocab playback.
     * Used by [com.alexpo.grammermate.feature.backgroundvocab.DeckPlayer] to emit
     * [com.alexpo.grammermate.data.MultilingualStoryParser.Segment.Audio] in place of
     * TTS when a clip exists on disk. See [BgVocabAudioResolver] for the path convention.
     */
    val bgVocabAudioResolver: BgVocabAudioResolver by lazy { BgVocabAudioResolver(baseDir) }

    /**
     * Resolves pre-rendered chapter narration clips (Opus) for pack-scoped story playback.
     * Used by [com.alexpo.grammermate.shared.audio.AudioCoordinator.playMultilingualStory]
     * to play real-voice narration when a clip exists on disk, falling back to TTS
     * synthesis otherwise. See [StoryAudioResolver] for the path convention.
     */
    val storyAudioResolver: StoryAudioResolver by lazy { StoryAudioResolver(baseDir) }

    // Pack-scoped stores
    fun wordMasteryStore(packId: String?): WordMasteryStore = storeFactory.getWordMasteryStore(packId)
    fun verbDrillStore(packId: String?): VerbDrillStore = storeFactory.getVerbDrillStore(packId)
    fun auxDrillStore(packId: String?): AuxDrillStore = storeFactory.getAuxDrillStore(packId)
    fun packDailyCursorStore(): PackDailyCursorStore = PackDailyCursorStoreImpl(application)
    val packLessonProgressStore: PackLessonProgressStore by lazy { PackLessonProgressStoreImpl(application) }

    // Cache management
    fun evict(packId: String?) = storeFactory.evict(packId)
    fun clearCache() = storeFactory.clearCache()
}

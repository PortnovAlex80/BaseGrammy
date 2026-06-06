package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log

import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import com.alexpo.grammermate.data.validation.DataValidator

interface MasteryStore {

    fun loadAll(): Map<String, Map<String, LessonMasteryState>>

    @Deprecated("Use pack-scoped version", level = DeprecationLevel.WARNING)
    fun get(lessonId: String, languageId: String): LessonMasteryState?

    /** Pack-scoped lookup. Keyed by (packId, lessonId) instead of (languageId, lessonId). */
    fun getForPack(packId: String, lessonId: String): LessonMasteryState?

    @Deprecated("Use pack-scoped version", level = DeprecationLevel.WARNING)
    fun save(state: LessonMasteryState)

    /** Save mastery state with pack-scoped key. */
    fun saveForPack(state: LessonMasteryState, packId: String)

    @Deprecated("Use pack-scoped version", level = DeprecationLevel.WARNING)
    fun recordCardShow(lessonId: String, languageId: String, cardId: String)

    /** Record card show with pack-scoped key. */
    fun recordCardShowForPack(packId: String, lessonId: String, cardId: String)

    @Deprecated("Use pack-scoped version", level = DeprecationLevel.WARNING)
    fun markCardsShownForProgress(lessonId: String, languageId: String, cardIds: Collection<String>)

    /** Pack-scoped card progress marking. */
    fun markCardsShownForProgressForPack(packId: String, lessonId: String, cardIds: Collection<String>)

    @Deprecated("Use pack-scoped version", level = DeprecationLevel.WARNING)
    fun markLessonCompleted(lessonId: String, languageId: String)

    /** Pack-scoped lesson completion marking. */
    fun markLessonCompletedForPack(packId: String, lessonId: String)

    @Deprecated("Use pack-scoped version", level = DeprecationLevel.WARNING)
    fun getOrCreate(lessonId: String, languageId: String): LessonMasteryState

    /** Pack-scoped get-or-create. */
    fun getOrCreateForPack(packId: String, lessonId: String): LessonMasteryState

    fun clear()

    @Deprecated("Use pack-scoped version", level = DeprecationLevel.WARNING)
    fun clearLanguage(languageId: String)

    /** Pack-scoped clear: removes all mastery data for a given pack. */
    fun clearPack(packId: String)

    @Deprecated("Use pack-scoped version", level = DeprecationLevel.WARNING)
    fun recordCardEncounter(lessonId: String, languageId: String, cardId: String): Int

    /** Pack-scoped card encounter tracking. Returns new encounter count. */
    fun recordCardEncounterForPack(packId: String, lessonId: String, cardId: String): Int

    @Deprecated("Use pack-scoped version", level = DeprecationLevel.WARNING)
    fun getCardEncounterCount(lessonId: String, languageId: String, cardId: String): Int

    /** Pack-scoped encounter count retrieval. */
    fun getCardEncounterCountForPack(packId: String, lessonId: String, cardId: String): Int

    /** Flush any pending writes to disk immediately. Call at session end, app background, etc. */
    fun flush()
}

/**
 * Хранилище состояний освоения уроков (mastery).
 * Сохраняет данные о показах карточек для каждого урока.
 */
class MasteryStoreImpl(private val context: Context) : MasteryStore {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file = File(baseDir, "mastery.yaml")
    private var schemaVersion = 1
    private val mutex = ReentrantLock()

    // Кеш для быстрого доступа
    private var cache: MutableMap<String, MutableMap<String, LessonMasteryState>> = mutableMapOf()
    private var cacheLoaded = false

    /**
     * Загрузить все состояния освоения.
     * @return Map<languageId, Map<lessonId, LessonMasteryState>>
     */
    override fun loadAll(): Map<String, Map<String, LessonMasteryState>> = mutex.withLock {
        loadAllInternal()
    }

    private fun loadAllInternal(): Map<String, Map<String, LessonMasteryState>> {
        if (cacheLoaded) return cache

        if (!file.exists()) {
            cacheLoaded = true
            return cache
        }

        val previousCache = cache

        try {
            val raw = yaml.load<Any>(file.readText()) ?: return cache
            val data = (raw as? Map<*, *>) ?: return cache
            val payload = (data["data"] as? Map<*, *>) ?: data

            for ((langKey, langValue) in payload) {
                val languageId = langKey as? String ?: continue
                val lessonMap = langValue as? Map<*, *> ?: continue

                cache[languageId] = mutableMapOf()

                for ((lessonKey, lessonValue) in lessonMap) {
                    val lessonId = lessonKey as? String ?: continue
                    val lessonData = lessonValue as? Map<*, *> ?: continue

                    // Validate mastery data before using it
                    @Suppress("UNCHECKED_CAST")
                    val validationResult = DataValidator.validateMasteryState(
                        lessonId = lessonId,
                        languageId = languageId,
                        data = lessonData as? Map<String, Any>
                    )

                    when (validationResult) {
                        is com.alexpo.grammermate.data.validation.ValidationResult.Valid -> {
                            cache[languageId]!![lessonId] = validationResult.data
                        }
                        is com.alexpo.grammermate.data.validation.ValidationResult.Invalid -> {
                            Log.w("MasteryStore", "Using safe default for corrupted mastery data: lesson=$lessonId")
                            cache[languageId]!![lessonId] = validationResult.safeDefault
                        }
                        is com.alexpo.grammermate.data.validation.ValidationResult.Warning -> {
                            cache[languageId]!![lessonId] = validationResult.data
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MasteryStore", "Failed to parse ${file.name}", e)
            cache = previousCache
        }

        cacheLoaded = true
        return cache
    }

    /**
     * Получить состояние освоения для конкретного урока.
     */
    @Suppress("DEPRECATION")
    override fun get(lessonId: String, languageId: String): LessonMasteryState? {
        loadAll()
        return cache[languageId]?.get(lessonId)
    }

    /**
     * Pack-scoped mastery lookup.
     * Uses "pack:{packId}" as the top-level cache key to isolate mastery per pack.
     */
    override fun getForPack(packId: String, lessonId: String): LessonMasteryState? {
        loadAll()
        val packKey = "pack:$packId"
        return cache[packKey]?.get(lessonId)
    }

    /**
     * Сохранить состояние освоения урока.
     */
    @Suppress("DEPRECATION")
    override fun save(state: LessonMasteryState) = mutex.withLock {
        loadAllInternal()

        if (!cache.containsKey(state.languageId.value)) {
            cache[state.languageId.value] = mutableMapOf()
        }
        cache[state.languageId.value]!![state.lessonId.value] = state

        persistToFile()
    }

    /**
     * Pack-scoped save. Stores mastery under "pack:{packId}" key.
     */
    override fun saveForPack(state: LessonMasteryState, packId: String) = mutex.withLock {
        loadAllInternal()

        val packKey = "pack:$packId"
        if (!cache.containsKey(packKey)) {
            cache[packKey] = mutableMapOf()
        }
        cache[packKey]!![state.lessonId.value] = state

        persistToFile()
    }

    /**
     * Записать показ карточки для урока.
     *
     * @param lessonId ID урока
     * @param languageId ID языка
     * @param cardId ID показанной карточки
     */
    @Suppress("DEPRECATION")
    override fun recordCardShow(lessonId: String, languageId: String, cardId: String) = mutex.withLock {
        loadAllInternal()

        val existing = cache[languageId]?.get(lessonId)
        val now = System.currentTimeMillis()

        val isNewCard = existing?.shownCardIds?.contains(cardId) != true
        val newShownCardIds = (existing?.shownCardIds ?: emptySet()) + cardId

        // Рассчитываем новый шаг интервала
        val daysSinceLastShow = if (existing?.lastShowDateMs != null && existing.lastShowDateMs > 0) {
            ((now - existing.lastShowDateMs) / (24 * 60 * 60 * 1000)).toInt()
        } else {
            0
        }

        val currentStep = existing?.intervalStepIndex ?: 0
        val wasOnTime = SpacedRepetitionConfig.wasRepetitionOnTime(daysSinceLastShow, currentStep)
        val newStep = if (existing != null && daysSinceLastShow > 0) {
            SpacedRepetitionConfig.nextIntervalStep(currentStep, wasOnTime)
        } else {
            currentStep
        }

        val updated = LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId(languageId),
            uniqueCardShows = if (isNewCard) {
                (existing?.uniqueCardShows ?: 0) + 1
            } else {
                existing?.uniqueCardShows ?: 0
            },
            totalCardShows = (existing?.totalCardShows ?: 0) + 1,
            lastShowDateMs = now,
            intervalStepIndex = newStep,
            completedAtMs = existing?.completedAtMs,
            shownCardIds = newShownCardIds
        )

        if (!cache.containsKey(updated.languageId.value)) {
            cache[updated.languageId.value] = mutableMapOf()
        }
        cache[updated.languageId.value]!![updated.lessonId.value] = updated
        persistToFile()
    }

    /**
     * Pack-scoped card show recording. Stores under "pack:{packId}" key.
     */
    override fun recordCardShowForPack(packId: String, lessonId: String, cardId: String) = mutex.withLock {
        loadAllInternal()

        val packKey = "pack:$packId"
        val existing = cache[packKey]?.get(lessonId)
        val now = System.currentTimeMillis()

        val isNewCard = existing?.shownCardIds?.contains(cardId) != true
        val newShownCardIds = (existing?.shownCardIds ?: emptySet()) + cardId

        val daysSinceLastShow = if (existing?.lastShowDateMs != null && existing.lastShowDateMs > 0) {
            ((now - existing.lastShowDateMs) / (24 * 60 * 60 * 1000)).toInt()
        } else {
            0
        }

        val currentStep = existing?.intervalStepIndex ?: 0
        val wasOnTime = SpacedRepetitionConfig.wasRepetitionOnTime(daysSinceLastShow, currentStep)
        val newStep = if (existing != null && daysSinceLastShow > 0) {
            SpacedRepetitionConfig.nextIntervalStep(currentStep, wasOnTime)
        } else {
            currentStep
        }

        // Use packId-derived language from existing state, or empty placeholder
        val languageId = existing?.languageId?.value ?: ""
        val updated = LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId(languageId),
            uniqueCardShows = if (isNewCard) {
                (existing?.uniqueCardShows ?: 0) + 1
            } else {
                existing?.uniqueCardShows ?: 0
            },
            totalCardShows = (existing?.totalCardShows ?: 0) + 1,
            lastShowDateMs = now,
            intervalStepIndex = newStep,
            completedAtMs = existing?.completedAtMs,
            shownCardIds = newShownCardIds
        )

        if (!cache.containsKey(packKey)) {
            cache[packKey] = mutableMapOf()
        }
        cache[packKey]!![updated.lessonId.value] = updated
        persistToFile()
    }

    /**
     * Mark cards as shown for progress tracking without affecting mastery metrics.
     */
    @Suppress("DEPRECATION")
    override fun markCardsShownForProgress(lessonId: String, languageId: String, cardIds: Collection<String>) = mutex.withLock {
        loadAllInternal()
        if (cardIds.isEmpty()) return

        val existing = cache[languageId]?.get(lessonId) ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId(languageId)
        )
        val updated = existing.copy(shownCardIds = existing.shownCardIds + cardIds)

        if (!cache.containsKey(updated.languageId.value)) {
            cache[updated.languageId.value] = mutableMapOf()
        }
        cache[updated.languageId.value]!![updated.lessonId.value] = updated
        persistToFile()
    }

    /**
     * Pack-scoped card progress marking. Stores under "pack:{packId}" key.
     */
    override fun markCardsShownForProgressForPack(packId: String, lessonId: String, cardIds: Collection<String>) = mutex.withLock {
        loadAllInternal()
        if (cardIds.isEmpty()) return

        val packKey = "pack:$packId"
        val existing = cache[packKey]?.get(lessonId) ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId("")
        )
        val updated = existing.copy(shownCardIds = existing.shownCardIds + cardIds)

        if (!cache.containsKey(packKey)) {
            cache[packKey] = mutableMapOf()
        }
        cache[packKey]!![updated.lessonId.value] = updated
        persistToFile()
    }

    /**
     * Отметить урок как завершённый (все карточки урока пройдены хотя бы раз).
     */
    @Suppress("DEPRECATION")
    override fun markLessonCompleted(lessonId: String, languageId: String) = mutex.withLock {
        loadAllInternal()
        val existing = cache[languageId]?.get(lessonId) ?: return

        if (existing.completedAtMs != null) return // Уже завершён

        val updated = existing.copy(completedAtMs = System.currentTimeMillis())

        if (!cache.containsKey(updated.languageId.value)) {
            cache[updated.languageId.value] = mutableMapOf()
        }
        cache[updated.languageId.value]!![updated.lessonId.value] = updated
        persistToFile()
    }

    /**
     * Pack-scoped lesson completion marking. Stores under "pack:{packId}" key.
     */
    override fun markLessonCompletedForPack(packId: String, lessonId: String) = mutex.withLock {
        loadAllInternal()
        val packKey = "pack:$packId"
        val existing = cache[packKey]?.get(lessonId) ?: return

        if (existing.completedAtMs != null) return

        val updated = existing.copy(completedAtMs = System.currentTimeMillis())

        if (!cache.containsKey(packKey)) {
            cache[packKey] = mutableMapOf()
        }
        cache[packKey]!![updated.lessonId.value] = updated
        persistToFile()
    }

    /**
     * Получить или создать состояние для урока.
     */
    @Suppress("DEPRECATION")
    override fun getOrCreate(lessonId: String, languageId: String): LessonMasteryState {
        return get(lessonId, languageId) ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId(languageId)
        )
    }

    /**
     * Pack-scoped get-or-create. Returns existing state or creates a new default.
     */
    override fun getOrCreateForPack(packId: String, lessonId: String): LessonMasteryState {
        return getForPack(packId, lessonId) ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId("")
        )
    }

    /**
     * Record an encounter for a specific card and return the new count.
     */
    @Suppress("DEPRECATION")
    override fun recordCardEncounter(lessonId: String, languageId: String, cardId: String): Int = mutex.withLock {
        loadAllInternal()
        val existing = cache[languageId]?.get(lessonId) ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId(languageId)
        )
        val currentCount = existing.cardEncounterCounts[cardId] ?: 0
        val newCount = currentCount + 1
        val updated = existing.copy(
            cardEncounterCounts = existing.cardEncounterCounts + (cardId to newCount)
        )
        if (!cache.containsKey(updated.languageId.value)) {
            cache[updated.languageId.value] = mutableMapOf()
        }
        cache[updated.languageId.value]!![updated.lessonId.value] = updated
        persistToFile()
        newCount
    }

    /**
     * Pack-scoped card encounter tracking. Returns new encounter count.
     * Stores under "pack:{packId}" key.
     */
    override fun recordCardEncounterForPack(packId: String, lessonId: String, cardId: String): Int = mutex.withLock {
        loadAllInternal()
        val packKey = "pack:$packId"
        val existing = cache[packKey]?.get(lessonId) ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId("")
        )
        val currentCount = existing.cardEncounterCounts[cardId] ?: 0
        val newCount = currentCount + 1
        val updated = existing.copy(
            cardEncounterCounts = existing.cardEncounterCounts + (cardId to newCount)
        )
        if (!cache.containsKey(packKey)) {
            cache[packKey] = mutableMapOf()
        }
        cache[packKey]!![updated.lessonId.value] = updated
        persistToFile()
        newCount
    }

    /**
     * Get the encounter count for a specific card.
     */
    @Suppress("DEPRECATION")
    override fun getCardEncounterCount(lessonId: String, languageId: String, cardId: String): Int {
        loadAll()
        return cache[languageId]?.get(lessonId)?.cardEncounterCounts?.get(cardId) ?: 0
    }

    /**
     * Pack-scoped encounter count retrieval. Looks up under "pack:{packId}" key.
     */
    override fun getCardEncounterCountForPack(packId: String, lessonId: String, cardId: String): Int {
        loadAll()
        val packKey = "pack:$packId"
        return cache[packKey]?.get(lessonId)?.cardEncounterCounts?.get(cardId) ?: 0
    }

    /**
     * Очистить все данные.
     */
    override fun clear() = mutex.withLock {
        cache.clear()
        cacheLoaded = true
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * Очистить данные для конкретного языка.
     */
    @Suppress("DEPRECATION")
    override fun clearLanguage(languageId: String) = mutex.withLock {
        loadAllInternal()
        cache.remove(languageId)
        persistToFile()
    }

    /**
     * Pack-scoped clear: removes all mastery data for a given pack.
     */
    override fun clearPack(packId: String) = mutex.withLock {
        loadAllInternal()
        cache.remove("pack:$packId")
        persistToFile()
    }

    /**
     * Flush any pending dirty data to disk immediately.
     * No-op: writes are now immediate (no write-behind batching).
     * Kept for API compatibility.
     */
    override fun flush() {
        // No-op: writes are immediate now
    }

    // --- v1 → v2 Migration ---

    /**
     * Migrate v1 language-scoped mastery data to v2 pack-scoped data.
     *
     * In v1, mastery was keyed by languageId (e.g. "en", "it").
     * In v2, mastery is keyed by "pack:{packId}" to isolate data per pack.
     *
     * @param getInstalledPacks returns all installed LessonPacks
     * @param getPackIdForLesson resolves which pack owns a given lesson (languageId, lessonId)
     * @return true if migration happened, false if already at v2
     */
    fun runV1ToV2Migration(
        getInstalledPacks: () -> List<LessonPack>,
        getPackIdForLesson: (languageId: String, lessonId: String) -> String?
    ): Boolean = mutex.withLock {
        // Read schema version from file directly (before cache is loaded)
        val currentVersion = readSchemaVersion()
        if (currentVersion >= 2) {
            Log.i("MasteryStore", "Schema already at v$currentVersion, skipping migration")
            return false
        }

        Log.i("MasteryStore", "Starting v1→v2 mastery migration")
        loadAllInternal()

        val installedPacks = getInstalledPacks()
        val packsByLanguage = installedPacks.groupBy { it.languageId.value }

        var migrated = false

        // Collect top-level keys that are language-scoped (don't start with "pack:")
        val languageKeys = cache.keys.filter { !it.startsWith("pack:") }.toList()

        for (languageId in languageKeys) {
            val lessonMap = cache[languageId] ?: continue
            val packsForLanguage = packsByLanguage[languageId] ?: emptyList()

            if (packsForLanguage.isEmpty()) {
                Log.w("MasteryStore", "Orphaned language data for '$languageId' (no installed packs), leaving as-is")
                continue
            }

            if (packsForLanguage.size == 1) {
                // Exactly one pack for this language: move all entries to that pack
                val pack = packsForLanguage.first()
                val packKey = "pack:${pack.packId.value}"
                if (!cache.containsKey(packKey)) {
                    cache[packKey] = mutableMapOf()
                }
                for ((lessonId, state) in lessonMap) {
                    cache[packKey]!![lessonId] = state
                }
                cache.remove(languageId)
                migrated = true
                Log.i("MasteryStore", "Migrated ${lessonMap.size} lessons from language '$languageId' to pack '${pack.packId.value}'")
            } else {
                // Multiple packs: resolve each lesson to its owning pack
                for ((lessonId, state) in lessonMap) {
                    val resolvedPackId = getPackIdForLesson(languageId, lessonId)
                    if (resolvedPackId != null) {
                        val packKey = "pack:$resolvedPackId"
                        if (!cache.containsKey(packKey)) {
                            cache[packKey] = mutableMapOf()
                        }
                        cache[packKey]!![lessonId] = state
                    } else {
                        Log.w("MasteryStore", "Could not resolve pack for lesson '$lessonId' in language '$languageId', skipping")
                    }
                }
                cache.remove(languageId)
                migrated = true
                Log.i("MasteryStore", "Migrated lessons from language '$languageId' across ${packsForLanguage.size} packs")
            }
        }

        if (migrated) {
            schemaVersion = 2
            persistToFile()
            Log.i("MasteryStore", "v1→v2 migration complete, schemaVersion updated to 2")
        }

        migrated
    }

    /**
     * Read the schema version from the YAML file on disk.
     * Returns 2 if file doesn't exist (no migration needed) or 1 if no version field.
     */
    private fun readSchemaVersion(): Int {
        if (!file.exists()) return 2
        return try {
            val content = file.readText()
            val data = yaml.load<Map<String, Any>>(content) as? Map<String, Any> ?: return 2
            (data["schemaVersion"] as? Int) ?: 1
        } catch (e: Exception) {
            Log.w("MasteryStore", "Failed to read schema version, assuming v1", e)
            1
        }
    }

    private fun persistToFile() {
        val payload = linkedMapOf<String, Any>()

        for ((languageId, lessonMap) in cache) {
            val lessonsPayload = linkedMapOf<String, Any>()

            for ((lessonId, mastery) in lessonMap) {
                lessonsPayload[lessonId] = linkedMapOf(
                    "uniqueCardShows" to mastery.uniqueCardShows,
                    "totalCardShows" to mastery.totalCardShows,
                    "lastShowDateMs" to mastery.lastShowDateMs,
                    "intervalStepIndex" to mastery.intervalStepIndex,
                    "completedAtMs" to mastery.completedAtMs,
                    "shownCardIds" to mastery.shownCardIds.toList(),
                    "cardEncounterCounts" to mastery.cardEncounterCounts
                )
            }

            payload[languageId] = lessonsPayload
        }

        val data = linkedMapOf(
            "schemaVersion" to schemaVersion,
            "data" to payload
        )

        try {
            AtomicFileWriter.writeText(file, yaml.dump(data))
            Log.i("MasteryStore", "Successfully persisted mastery data: ${file.name} (${file.length()} bytes)")
        } catch (e: IOException) {
            Log.e("MasteryStore", "Failed to persist mastery data: ${file.name}", e)
            throw e
        } catch (e: Exception) {
            Log.e("MasteryStore", "Unexpected error persisting mastery data: ${file.name}", e)
            throw IOException("Failed to persist mastery data", e)
        }
    }
}

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

    /** Pack-scoped lookup. Keyed by (packId, lessonId) instead of (languageId, lessonId). */
    fun getForPack(packId: String, lessonId: String): LessonMasteryState?

    /** Save mastery state with pack-scoped key. */
    fun saveForPack(state: LessonMasteryState, packId: String)

    /** Record card show with pack-scoped key. */
    fun recordCardShowForPack(packId: String, lessonId: String, cardId: String)

    /** Pack-scoped card progress marking. */
    fun markCardsShownForProgressForPack(packId: String, lessonId: String, cardIds: Collection<String>)

    /** Pack-scoped lesson completion marking. */
    fun markLessonCompletedForPack(packId: String, lessonId: String)

    /** Pack-scoped get-or-create. */
    fun getOrCreateForPack(packId: String, lessonId: String): LessonMasteryState

    fun clear()

    /** Pack-scoped clear: removes all mastery data for a given pack. */
    fun clearPack(packId: String)

    /** Pack-scoped card encounter tracking. Returns new encounter count. */
    fun recordCardEncounterForPack(packId: String, lessonId: String, cardId: String): Int

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
     * Pack-scoped mastery lookup.
     * Uses "pack:{packId}" as the top-level cache key to isolate mastery per pack.
     */
    override fun getForPack(packId: String, lessonId: String): LessonMasteryState? {
        loadAll()
        val packKey = "pack:$packId"
        return cache[packKey]?.get(lessonId)
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
     * Pack-scoped get-or-create. Returns existing state or creates a new default.
     */
    override fun getOrCreateForPack(packId: String, lessonId: String): LessonMasteryState {
        return getForPack(packId, lessonId) ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId("")
        )
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
            "schemaVersion" to 2,
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

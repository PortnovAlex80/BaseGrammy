package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface MasteryStore {

    fun loadAll(): Map<String, Map<String, LessonMasteryState>>

    fun get(lessonId: String, languageId: String): LessonMasteryState?

    fun save(state: LessonMasteryState)

    fun recordCardShow(lessonId: String, languageId: String, cardId: String)

    fun markCardsShownForProgress(lessonId: String, languageId: String, cardIds: Collection<String>)

    fun markLessonCompleted(lessonId: String, languageId: String)

    fun getOrCreate(lessonId: String, languageId: String): LessonMasteryState

    fun clear()

    fun clearLanguage(languageId: String)

    fun recordCardEncounter(lessonId: String, languageId: String, cardId: String): Int

    fun getCardEncounterCount(lessonId: String, languageId: String, cardId: String): Int

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
    private val schemaVersion = 1
    private val mutex = ReentrantLock()

    // Кеш для быстрого доступа
    private var cache: MutableMap<String, MutableMap<String, LessonMasteryState>> = mutableMapOf()
    private var cacheLoaded = false

    // Write-behind batching: defer disk writes by up to 3 seconds
    private val debounceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var dirty = false
    private var persistJob: Job? = null

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

                    val shownCardIds = (lessonData["shownCardIds"] as? List<*>)
                        ?.mapNotNull { it as? String }
                        ?.toSet()
                        ?: emptySet()

                    val cardEncounterCounts = (lessonData["cardEncounterCounts"] as? Map<*, *>)
                        ?.mapNotNull { (k, v) ->
                            val key = k as? String ?: return@mapNotNull null
                            val value = (v as? Number)?.toInt() ?: return@mapNotNull null
                            key to value
                        }
                        ?.toMap()
                        ?: emptyMap()

                    val mastery = LessonMasteryState(
                        lessonId = LessonId(lessonId),
                        languageId = LanguageId(languageId),
                        uniqueCardShows = (lessonData["uniqueCardShows"] as? Number)?.toInt() ?: 0,
                        totalCardShows = (lessonData["totalCardShows"] as? Number)?.toInt() ?: 0,
                        lastShowDateMs = (lessonData["lastShowDateMs"] as? Number)?.toLong() ?: 0L,
                        intervalStepIndex = (lessonData["intervalStepIndex"] as? Number)?.toInt() ?: 0,
                        completedAtMs = (lessonData["completedAtMs"] as? Number)?.toLong(),
                        shownCardIds = shownCardIds,
                        cardEncounterCounts = cardEncounterCounts
                    )

                    cache[languageId]!![lessonId] = mastery
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
    override fun get(lessonId: String, languageId: String): LessonMasteryState? {
        loadAll()
        return cache[languageId]?.get(lessonId)
    }

    /**
     * Сохранить состояние освоения урока.
     */
    override fun save(state: LessonMasteryState) = mutex.withLock {
        loadAllInternal()

        if (!cache.containsKey(state.languageId.value)) {
            cache[state.languageId.value] = mutableMapOf()
        }
        cache[state.languageId.value]!![state.lessonId.value] = state

        schedulePersist()
    }

    /**
     * Записать показ карточки для урока.
     *
     * @param lessonId ID урока
     * @param languageId ID языка
     * @param cardId ID показанной карточки
     */
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
        schedulePersist()
    }

    /**
     * Mark cards as shown for progress tracking without affecting mastery metrics.
     */
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
        schedulePersist()
    }

    /**
     * Отметить урок как завершённый (все карточки урока пройдены хотя бы раз).
     */
    override fun markLessonCompleted(lessonId: String, languageId: String) = mutex.withLock {
        loadAllInternal()
        val existing = cache[languageId]?.get(lessonId) ?: return

        if (existing.completedAtMs != null) return // Уже завершён

        val updated = existing.copy(completedAtMs = System.currentTimeMillis())

        if (!cache.containsKey(updated.languageId.value)) {
            cache[updated.languageId.value] = mutableMapOf()
        }
        cache[updated.languageId.value]!![updated.lessonId.value] = updated
        schedulePersist()
    }

    /**
     * Получить или создать состояние для урока.
     */
    override fun getOrCreate(lessonId: String, languageId: String): LessonMasteryState {
        return get(lessonId, languageId) ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId(languageId)
        )
    }

    /**
     * Record an encounter for a specific card and return the new count.
     */
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
        schedulePersist()
        newCount
    }

    /**
     * Get the encounter count for a specific card.
     */
    override fun getCardEncounterCount(lessonId: String, languageId: String, cardId: String): Int {
        loadAll()
        return cache[languageId]?.get(lessonId)?.cardEncounterCounts?.get(cardId) ?: 0
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
    override fun clearLanguage(languageId: String) = mutex.withLock {
        loadAllInternal()
        cache.remove(languageId)
        schedulePersist()
    }

    /**
     * Flush any pending dirty data to disk immediately.
     * Call at session end, app background, screen transitions.
     */
    override fun flush() = mutex.withLock {
        persistJob?.cancel()
        persistJob = null
        if (dirty) {
            persistToFile()
            dirty = false
        }
    }

    /**
     * Schedule a deferred persist to disk. Cancels any previous pending write.
     * The in-memory cache is already up-to-date; reads will see fresh data.
     */
    private fun schedulePersist() {
        dirty = true
        persistJob?.cancel()
        persistJob = debounceScope.launch {
            delay(3000L)
            mutex.withLock {
                if (dirty) {
                    persistToFile()
                    dirty = false
                }
            }
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
                    "completedAtMs" to (mastery.completedAtMs ?: 0L),
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

        AtomicFileWriter.writeText(file, yaml.dump(data))
    }
}

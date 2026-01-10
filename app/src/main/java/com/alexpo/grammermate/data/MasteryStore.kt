package com.alexpo.grammermate.data

import android.content.Context
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Хранилище состояний освоения уроков (mastery).
 * Сохраняет данные о показах карточек для каждого урока.
 */
class MasteryStore(private val context: Context) {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file = File(baseDir, "mastery.yaml")
    private val schemaVersion = 1

    // Кеш для быстрого доступа
    private var cache: MutableMap<String, MutableMap<String, LessonMasteryState>> = mutableMapOf()
    private var cacheLoaded = false

    /**
     * Загрузить все состояния освоения.
     * @return Map<languageId, Map<lessonId, LessonMasteryState>>
     */
    fun loadAll(): Map<String, Map<String, LessonMasteryState>> {
        if (cacheLoaded) return cache

        if (!file.exists()) {
            cacheLoaded = true
            return cache
        }

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

                    val mastery = LessonMasteryState(
                        lessonId = lessonId,
                        languageId = languageId,
                        uniqueCardShows = (lessonData["uniqueCardShows"] as? Number)?.toInt() ?: 0,
                        totalCardShows = (lessonData["totalCardShows"] as? Number)?.toInt() ?: 0,
                        lastShowDateMs = (lessonData["lastShowDateMs"] as? Number)?.toLong() ?: 0L,
                        intervalStepIndex = (lessonData["intervalStepIndex"] as? Number)?.toInt() ?: 0,
                        completedAtMs = (lessonData["completedAtMs"] as? Number)?.toLong(),
                        shownCardIds = shownCardIds
                    )

                    cache[languageId]!![lessonId] = mastery
                }
            }
        } catch (e: Exception) {
            // При ошибке чтения начинаем с чистого состояния
            cache = mutableMapOf()
        }

        cacheLoaded = true
        return cache
    }

    /**
     * Получить состояние освоения для конкретного урока.
     */
    fun get(lessonId: String, languageId: String): LessonMasteryState? {
        loadAll()
        return cache[languageId]?.get(lessonId)
    }

    /**
     * Сохранить состояние освоения урока.
     */
    fun save(state: LessonMasteryState) {
        loadAll()

        if (!cache.containsKey(state.languageId)) {
            cache[state.languageId] = mutableMapOf()
        }
        cache[state.languageId]!![state.lessonId] = state

        persistToFile()
    }

    /**
     * Записать показ карточки для урока.
     *
     * @param lessonId ID урока
     * @param languageId ID языка
     * @param cardId ID показанной карточки
     */
    fun recordCardShow(lessonId: String, languageId: String, cardId: String) {
        loadAll()

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
            lessonId = lessonId,
            languageId = languageId,
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

        save(updated)
    }

    /**
     * Отметить урок как завершённый (все карточки урока пройдены хотя бы раз).
     */
    fun markLessonCompleted(lessonId: String, languageId: String) {
        val existing = get(lessonId, languageId) ?: return

        if (existing.completedAtMs != null) return // Уже завершён

        val updated = existing.copy(completedAtMs = System.currentTimeMillis())
        save(updated)
    }

    /**
     * Получить или создать состояние для урока.
     */
    fun getOrCreate(lessonId: String, languageId: String): LessonMasteryState {
        return get(lessonId, languageId) ?: LessonMasteryState(
            lessonId = lessonId,
            languageId = languageId
        )
    }

    /**
     * Очистить все данные.
     */
    fun clear() {
        cache.clear()
        cacheLoaded = true
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * Очистить данные для конкретного языка.
     */
    fun clearLanguage(languageId: String) {
        loadAll()
        cache.remove(languageId)
        persistToFile()
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
                    "shownCardIds" to mastery.shownCardIds.toList()
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

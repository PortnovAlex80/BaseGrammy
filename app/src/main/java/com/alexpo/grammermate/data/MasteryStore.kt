package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log

import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import com.alexpo.grammermate.data.validation.DataValidator

interface MasteryStore {

    fun loadAll(): Map<String, Map<String, LessonMasteryState>>

    /** Pack-scoped lookup. Keyed by (packId, lessonId) instead of (languageId, lessonId). */
    fun getForPack(packId: String, lessonId: String): LessonMasteryState?

    /** Save mastery state with pack-scoped key. */
    fun saveForPack(state: LessonMasteryState, packId: String)

    /** Record card show with pack-scoped key. Exposure only — does not advance the ladder. */
    fun recordCardShowForPack(packId: String, lessonId: String, cardId: String)

    /**
     * Record a self-produced answer (voice/keyboard, no reveal) for a lesson.
     * The only place the interval ladder advances.
     */
    fun recordSelfProducedForPack(packId: String, lessonId: String, totalEffortCards: Int)

    /** Sum of totalCardShows across the pack — the effort axis of the forgetting curve. */
    fun totalEffortCardsForPack(packId: String): Int

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
 *
 * Locking/batching (TASK-091, item 1):
 * [mutex] guards the in-memory cache and is never held across the YAML dump +
 * file write; the one exception is the first-load disk read, which runs under
 * [mutex] and is warmed off-main at ViewModel init. Main-thread cache readers
 * are therefore not blocked by a background writer's file I/O.
 *
 * Hot-path mutations ([recordCardShowForPack], [recordCardEncounterForPack],
 * [recordSelfProducedForPack], [markCardsShownForProgressForPack],
 * [markLessonCompletedForPack]) only update the cache under [mutex] and mark it
 * dirty; a single background writer thread ([writer]) dumps a complete snapshot
 * after a [debounceMs] coalescing window, so the three writes per card collapse
 * into one. Each persist writes a complete snapshot, so last-write-wins is safe.
 *
 * [fileMutex] (shared per file path across instances) serializes writers
 * end-to-end to keep write ordering deterministic. [saveForPack] — a rare,
 * non-hot-path call — stays synchronous for durability-on-return.
 *
 * [flush] blocks until pending data is durable: it is called from
 * [com.alexpo.grammermate.ui.TrainingViewModel.onCleared], where the process
 * may die right after it returns. [clear]/[clearPack] absorb the pending
 * deferred write (cancel + clean + [writeEpoch] bump), so a stale dump can
 * never resurrect deleted data, including a retry after a failed write.
 *
 * A new instance over the same file drains other instances' pending writes
 * before its first disk read (see [drainOtherInstances]), so a same-process
 * reopen cannot observe a stale snapshot. Only never-loaded instances drain,
 * and a dirty instance is always already loaded — so drain chains can never
 * form a lock cycle.
 */
class MasteryStoreImpl(
    private val context: Context,
    private val debounceMs: Long = DEFAULT_FLUSH_DEBOUNCE_MS,
) : MasteryStore {

    companion object {
        private const val TAG = "MasteryStore"

        /** Coalescing window for deferred writes. Keep <= 2000 ms (TASK-091 risk bound). */
        const val DEFAULT_FLUSH_DEBOUNCE_MS = 1500L

        /** Per-path file locks shared by all instances in this process. */
        private val fileLocks = ConcurrentHashMap<String, ReentrantLock>()

        /**
         * Instances with pending (not yet durable) writes, keyed by canonical file path.
         * Guarded by [registryLock]; never held across I/O.
         */
        private val registryLock = ReentrantLock()
        private val pendingByPath = LinkedHashMap<String, MutableList<MasteryStoreImpl>>()

        private fun lockForPath(canonicalPath: String): ReentrantLock =
            fileLocks.getOrPut(canonicalPath) { ReentrantLock() }
    }

    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file = File(baseDir, "mastery.yaml")
    private val canonicalPath by lazy { file.canonicalPath }
    private val mutex = ReentrantLock()
    private val fileMutex by lazy { lockForPath(canonicalPath) }

    // Deferred-write state, guarded by [schedLock]. Never held across I/O.
    private val schedLock = ReentrantLock()
    private var dirty = false
    private var registeredPending = false
    private var pendingTask: java.util.concurrent.ScheduledFuture<*>? = null

    /** Bumped by [clear]/[clearPack]/[saveForPack]; a failed write retries only while its epoch is current. */
    private var writeEpoch = 0L

    /** The only thread that ever dumps snapshots for the hot path — keeps write order FIFO. */
    private val writer: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "mastery-store-writer").apply { isDaemon = true }
        }

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
        drainOtherInstances()

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
                            Log.w(TAG, "Using safe default for corrupted mastery data: lesson=$lessonId")
                            cache[languageId]!![lessonId] = validationResult.safeDefault
                        }
                        is com.alexpo.grammermate.data.validation.ValidationResult.Warning -> {
                            cache[languageId]!![lessonId] = validationResult.data
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ${file.name}", e)
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
        return mutex.withLock { cache[packKey]?.get(lessonId) }
    }

    /**
     * Pack-scoped save. Stores mastery under "pack:{packId}" key.
     * Synchronous (rare, non-hot-path): durable on return.
     */
    override fun saveForPack(state: LessonMasteryState, packId: String) {
        mutex.withLock {
            loadAllInternal()
            cache.getOrPut("pack:$packId") { mutableMapOf() }[state.lessonId.value] = state
        }
        // This full-snapshot write covers any deferred mutations already in the
        // cache, so the pending write is absorbed; epoch bump suppresses retries.
        schedLock.withLock {
            dirty = false
            cancelPendingTaskLocked()
            unregisterPendingLocked()
            writeEpoch++
        }
        fileMutex.withLock {
            persistToFileLocked()
        }
    }

    /**
     * Pack-scoped card show recording. Stores under "pack:{packId}" key.
     */
    override fun recordCardShowForPack(packId: String, lessonId: String, cardId: String) {
        mutex.withLock {
            loadAllInternal()

            val packKey = "pack:$packId"
            val existing = cache[packKey]?.get(lessonId)
            val now = System.currentTimeMillis()

            val isNewCard = existing?.shownCardIds?.contains(cardId) != true
            val newShownCardIds = (existing?.shownCardIds ?: emptySet()) + cardId

            // Лестница интервалов здесь НЕ двигается: показ карточки — это
            // экспозиция, а не доказательство воспроизведения. Продвижение живёт
            // в recordSelfProducedForPack (см. спеку forgetting-curve-review-scheduling.md).
            val updated = (existing ?: LessonMasteryState(
                lessonId = LessonId(lessonId),
                languageId = LanguageId("")
            )).copy(
                uniqueCardShows = if (isNewCard) {
                    (existing?.uniqueCardShows ?: 0) + 1
                } else {
                    existing?.uniqueCardShows ?: 0
                },
                totalCardShows = (existing?.totalCardShows ?: 0) + 1,
                lastShowDateMs = now,
                shownCardIds = newShownCardIds
            )

            cache.getOrPut(packKey) { mutableMapOf() }[updated.lessonId.value] = updated
        }
        markDirtyAndScheduleDeferredWrite()
    }

    /**
     * Отметить **самостоятельное воспроизведение** карточки урока.
     *
     * Единственная точка, где двигается лестница интервалов. Вызывается, когда
     * ответ принят, режим ввода считается для мастери (не WORD_BANK) и ответ не
     * был раскрыт через «показать ответ».
     *
     * Правильность ответа как сигнал использовать нельзя: голосовой ввод идёт
     * через ASR с высокой долей ошибок, поэтому непринятый ответ чаще означает
     * промах распознавания, а не забывание. Доступен только бинарный сигнал —
     * человек воспроизвёл карточку или нет.
     *
     * Шаг двигается не чаще раза в календарный день на урок; [lastReviewMs] и
     * [effortAtLastReview] стамповываются при каждом вызове, задавая обе шкалы
     * кривой забывания.
     *
     * @param totalEffortCards суммарные показы карточек по паку на текущий момент
     */
    override fun recordSelfProducedForPack(
        packId: String,
        lessonId: String,
        totalEffortCards: Int
    ) {
        mutex.withLock {
            loadAllInternal()

            val packKey = "pack:$packId"
            val existing = cache[packKey]?.get(lessonId)
            val now = System.currentTimeMillis()

            val daysSinceLastReview = if (existing != null && existing.lastReviewMs > 0) {
                ((now - existing.lastReviewMs) / (24 * 60 * 60 * 1000)).toInt()
            } else {
                0
            }

            val currentStep = existing?.intervalStepIndex ?: 0
            val newStep = if (existing != null && existing.lastReviewMs > 0 && daysSinceLastReview > 0) {
                val wasOnTime = SpacedRepetitionConfig.wasRepetitionOnTime(daysSinceLastReview, currentStep)
                SpacedRepetitionConfig.nextIntervalStep(currentStep, wasOnTime)
            } else {
                currentStep
            }

            val updated = (existing ?: LessonMasteryState(
                lessonId = LessonId(lessonId),
                languageId = LanguageId("")
            )).copy(
                intervalStepIndex = newStep,
                lastReviewMs = now,
                effortAtLastReview = totalEffortCards
            )

            cache.getOrPut(packKey) { mutableMapOf() }[updated.lessonId.value] = updated
        }
        markDirtyAndScheduleDeferredWrite()
    }

    /**
     * Суммарные показы карточек по паку — шкала усилий кривой забывания.
     * Считается по уже загруженной карте mastery: отдельный счётчик не нужен.
     */
    override fun totalEffortCardsForPack(packId: String): Int = mutex.withLock {
        loadAllInternal()
        cache["pack:$packId"]?.values?.sumOf { it.totalCardShows } ?: 0
    }

    /**
     * Pack-scoped card progress marking. Stores under "pack:{packId}" key.
     */
    override fun markCardsShownForProgressForPack(packId: String, lessonId: String, cardIds: Collection<String>) {
        mutex.withLock {
            loadAllInternal()
            if (cardIds.isEmpty()) return

            val packKey = "pack:$packId"
            val existing = cache[packKey]?.get(lessonId) ?: LessonMasteryState(
                lessonId = LessonId(lessonId),
                languageId = LanguageId("")
            )
            val updated = existing.copy(shownCardIds = existing.shownCardIds + cardIds)

            cache.getOrPut(packKey) { mutableMapOf() }[updated.lessonId.value] = updated
        }
        markDirtyAndScheduleDeferredWrite()
    }

    /**
     * Pack-scoped lesson completion marking. Stores under "pack:{packId}" key.
     */
    override fun markLessonCompletedForPack(packId: String, lessonId: String) {
        mutex.withLock {
            loadAllInternal()
            val packKey = "pack:$packId"
            val existing = cache[packKey]?.get(lessonId) ?: return

            if (existing.completedAtMs != null) return

            val updated = existing.copy(completedAtMs = System.currentTimeMillis())

            cache.getOrPut(packKey) { mutableMapOf() }[updated.lessonId.value] = updated
        }
        markDirtyAndScheduleDeferredWrite()
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
    override fun recordCardEncounterForPack(packId: String, lessonId: String, cardId: String): Int {
        val newCount = mutex.withLock {
            loadAllInternal()
            val packKey = "pack:$packId"
            val existing = cache[packKey]?.get(lessonId) ?: LessonMasteryState(
                lessonId = LessonId(lessonId),
                languageId = LanguageId("")
            )
            val currentCount = existing.cardEncounterCounts[cardId] ?: 0
            val nextCount = currentCount + 1
            val updated = existing.copy(
                cardEncounterCounts = existing.cardEncounterCounts + (cardId to nextCount)
            )
            cache.getOrPut(packKey) { mutableMapOf() }[updated.lessonId.value] = updated
            nextCount
        }
        markDirtyAndScheduleDeferredWrite()
        return newCount
    }

    /**
     * Pack-scoped encounter count retrieval. Looks up under "pack:{packId}" key.
     */
    override fun getCardEncounterCountForPack(packId: String, lessonId: String, cardId: String): Int {
        loadAll()
        val packKey = "pack:$packId"
        return mutex.withLock { cache[packKey]?.get(lessonId)?.cardEncounterCounts?.get(cardId) } ?: 0
    }

    /**
     * Очистить все данные. Поглощает отложенную запись: pending-дамп отменяется,
     * [writeEpoch] не даёт повтору неудачной записи воскресить удалённые данные.
     */
    override fun clear() {
        schedLock.withLock {
            dirty = false
            cancelPendingTaskLocked()
            unregisterPendingLocked()
            writeEpoch++
        }
        mutex.withLock {
            cache.clear()
            cacheLoaded = true
        }
        fileMutex.withLock {
            if (file.exists()) {
                file.delete()
            }
        }
    }

    /**
     * Pack-scoped clear: removes all mastery data for a given pack.
     * Absorbs the pending deferred write the same way [clear] does.
     */
    override fun clearPack(packId: String) {
        schedLock.withLock {
            dirty = false
            cancelPendingTaskLocked()
            unregisterPendingLocked()
            writeEpoch++
        }
        mutex.withLock {
            loadAllInternal()
            cache.remove("pack:$packId")
        }
        fileMutex.withLock {
            persistToFileLocked()
        }
    }

    /**
     * Блокирующе сбрасывает отложенные изменения на диск. Вызывается из
     * onCleared()/onAppBackgrounded()/saveProgress(), где после возврата
     * процесс может умереть — асинхронный flush здесь недопустим.
     */
    override fun flush() {
        val task = schedLock.withLock {
            cancelPendingTaskLocked()
            writer.schedule({ writeSnapshotIfDirty() }, 0, TimeUnit.MILLISECONDS)
        }
        try {
            // Single writer thread => FIFO: returns only after every earlier
            // queued/running write and this one (if any) are durable.
            task.get()
        } catch (e: java.util.concurrent.ExecutionException) {
            Log.e(TAG, "Flush task failed for ${file.name}", e.cause ?: e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.e(TAG, "Flush interrupted for ${file.name}", e)
        }
    }

    // ── Deferred-write machinery ──────────────────────────────────────────

    /** Cache-only mutation bookkeeping: mark dirty and (re)arm the debounce timer. */
    private fun markDirtyAndScheduleDeferredWrite() {
        schedLock.withLock {
            dirty = true
            registerPendingLocked()
            if (pendingTask == null) {
                pendingTask = writer.schedule(
                    { writeSnapshotIfDirty() },
                    debounceMs,
                    TimeUnit.MILLISECONDS
                )
            }
        }
    }

    /**
     * Writer-thread body: dump one full snapshot iff the cache is dirty.
     * On failure the data stays dirty and registered, and is retried by the
     * next flush/mutation — unless [writeEpoch] moved on (clear/save wrote a
     * newer state meanwhile), in which case the retry is dropped.
     */
    private fun writeSnapshotIfDirty() {
        val epochAtStart = schedLock.withLock {
            if (!dirty) {
                unregisterPendingLocked()
                return
            }
            dirty = false
            writeEpoch
        }

        try {
            fileMutex.withLock {
                persistToFileLocked()
            }
            schedLock.withLock {
                if (!dirty) unregisterPendingLocked()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Deferred mastery write failed; will retry on next flush", t)
            schedLock.withLock {
                if (!dirty && writeEpoch == epochAtStart) {
                    dirty = true
                    registerPendingLocked()
                }
            }
        }
    }

    /**
     * Before the first disk read of a newly created instance, make every other
     * instance with pending writes over this file durable, in registration
     * order, so the freshest snapshot lands last. Only never-loaded instances
     * drain; dirty instances are always loaded — so no drain cycle is possible.
     */
    private fun drainOtherInstances() {
        val others: List<MasteryStoreImpl> = registryLock.withLock {
            pendingByPath[canonicalPath].orEmpty().filter { it !== this }.toList()
        }
        for (other in others) {
            other.flush()
        }
    }

    // Callers must hold [schedLock].
    private fun registerPendingLocked() {
        if (registeredPending) return
        registryLock.withLock {
            pendingByPath.getOrPut(canonicalPath) { mutableListOf() }.add(this)
        }
        registeredPending = true
    }

    // Callers must hold [schedLock].
    private fun unregisterPendingLocked() {
        if (!registeredPending) return
        registryLock.withLock {
            val list = pendingByPath[canonicalPath] ?: return
            list.remove(this)
            if (list.isEmpty()) pendingByPath.remove(canonicalPath)
        }
        registeredPending = false
    }

    // Callers must hold [schedLock].
    private fun cancelPendingTaskLocked() {
        pendingTask?.cancel(false)
        pendingTask = null
    }

    /**
     * Persist the current cache snapshot. Caller must hold [fileMutex];
     * [mutex] is taken only to build the payload (memory-only) and released
     * before the YAML dump + file write.
     */
    private fun persistToFileLocked() {
        val data = mutex.withLock { buildPersistPayload() }

        try {
            AtomicFileWriter.writeText(file, yaml.dump(data))
            Log.i(TAG, "Successfully persisted mastery data: ${file.name} (${file.length()} bytes)")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to persist mastery data: ${file.name}", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error persisting mastery data: ${file.name}", e)
            throw IOException("Failed to persist mastery data", e)
        }
    }

    private fun buildPersistPayload(): LinkedHashMap<String, Any> {
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
                    "cardEncounterCounts" to mastery.cardEncounterCounts,
                    "lastReviewMs" to mastery.lastReviewMs,
                    "effortAtLastReview" to mastery.effortAtLastReview
                )
            }

            payload[languageId] = lessonsPayload
        }

        return linkedMapOf(
            "schemaVersion" to 2,
            "data" to payload
        )
    }
}

package com.alexpo.grammermate.v2.core.data.audio

import com.k2fsa.sherpa.onnx.OfflineTts

/**
 * Резидентный LRU-кэш нативных моделей Sherpa-ONNX [OfflineTts], ключ — language id.
 *
 * **Зачем.** Background Vocab Listener проигрывает чередующиеся it↔ru сегменты.
 * Перезагрузка ~150 МБ VITS_PIPER модели на каждом переключении языка (1–3 с)
 * делает вокаб-скрипт рваным. Хранение MRU-моделей резидентно в RAM делает
 * переключение языков практически мгновенным (FR-3, NFR-4 latency <100 мс).
 *
 * **Стоимость памяти.** Каждая резидентная VITS-модель занимает ~150 МБ
 * native heap. При [maxSize] = 3 худший случай — ~450 МБ резидентно; дефолт 3
 * позволяет IT + RU + одному дополнительному сосуществовать, вытесняя старые
 * модели под memory pressure.
 *
 * **Потокобезопасность.** Callers ([TtsEngineWrapper]) защищают доступ через
 * `Mutex`. Сам класс **не** синхронизирован внутри — это чистая структура
 * данных без Android-зависимостей, поэтому unit-тестируется на чистом JVM.
 *
 * **Eviction-политика.** Access-ordered [LinkedHashMap]; при превышении [maxSize]
 * в [put] наименее-недавно-используемая запись удаляется, и для неё вызывается
 * [freeFn] чтобы освободить native-хэндл модели.
 *
 * **Generic-носитель.** Кэш параметризован типом [T] модели. В production
 * ([TtsEngineWrapper]) — это `OfflineTts`, и [freeFn] вызывает
 * `OfflineTts.free()`. В unit-тестах носителем выступает любой лёгкий объект
 * (mock/POJO): это обязательно, потому что `OfflineTts` имеет `static {
 * System.loadLibrary("sherpa-onnx-jni") }`, который падает в pure-JVM окружении
 * без Android ABI — instantiate/mock его нельзя. Таким образом LRU-логика и
 * latency-бенчмарк (AC-4, AC-5) проверяются изолированно от native-слоя.
 *
 * Перенос из legacy `data/TtsEngine.kt:68-143` (SRS-003 §2.5).
 *
 * @param T      тип носителя модели (production: [OfflineTts]).
 * @param maxSize максимум резидентных моделей. Должен быть >= 1.
 * @param freeFn  функция освобождения модели. Production: `OfflineTts.free`.
 *                Инжектируется чтобы unit-тесты могли передать fake без native-кода.
 */
internal class ResidentTtsCache<T : Any>(
    private val maxSize: Int = DEFAULT_MAX_SIZE,
    private val freeFn: (T) -> Unit = { _ -> },
) {
    init {
        require(maxSize >= 1) { "maxSize must be >= 1, was $maxSize" }
    }

    /**
     * Access-ordered map: порядок итерации — от LRU к MRU.
     * `removeEldestEntry` намеренно НЕ используется — eviction вызывает [freeFn]
     * с side-effect, поэтому обрабатываем явно в [put] для контроля порядка.
     */
    private val map: LinkedHashMap<String, T> =
        LinkedHashMap(4, 0.75f, true)

    /**
     * Резидентная модель для [lang] или null. Побочный эффект access-order:
     * успешный lookup промоутирует [lang] в MRU.
     */
    fun get(lang: String): T? = map[lang]

    /** true если резидентная модель для [lang] есть. НЕ промоутирует recency. */
    fun contains(lang: String): Boolean = map.containsKey(lang)

    /**
     * Сохранить [tts] для [lang], промоутируя в MRU. Если вставка превышает
     * [maxSize], LRU-модель вытесняется и освобождается через [freeFn]. Если
     * [lang] уже резидентна — предыдущая модель освобождается и заменяется
     * (без двойного счёта против [maxSize]).
     */
    fun put(lang: String, tts: T) {
        val existing = map.remove(lang)
        if (existing != null && existing !== tts) {
            safeFree(existing)
        }
        map[lang] = tts
        while (map.size > maxSize) {
            val lru = map.entries.iterator().next()
            map.remove(lru.key)
            safeFree(lru.value)
        }
    }

    /** Удалить и освободить модель для [lang] если есть. No-op иначе. */
    fun remove(lang: String) {
        val removed = map.remove(lang)
        if (removed != null) safeFree(removed)
    }

    /** Освободить и удалить ВСЕ резидентные модели. */
    fun clear() {
        val snapshot = ArrayList(map.values)
        map.clear()
        snapshot.forEach { safeFree(it) }
    }

    /** Снапшот резидентных language-id (без гарантии порядка для callers). */
    fun keys(): Set<String> = LinkedHashSet(map.keys)

    /** Количество резидентных моделей. */
    fun size(): Int = map.size

    private fun safeFree(tts: T) {
        try {
            freeFn(tts)
        } catch (t: Throwable) {
            // Swallow — освобождение best-effort; никогда не даём ему повредить кэш.
        }
    }

    companion object {
        /** Канонический cap резидентных TTS-моделей (SRS-003 FR-3, NFR-4). */
        const val DEFAULT_MAX_SIZE = 3

        /**
         * Фабрика production-кэша для нативных [OfflineTts] моделей: тип-носитель
         * фиксируется как [OfflineTts], [freeFn] дёргает native `free()`.
         * Используется [TtsEngineWrapper]-ом.
         */
        fun forOfflineTts(maxSize: Int = DEFAULT_MAX_SIZE): ResidentTtsCache<OfflineTts> =
            ResidentTtsCache(maxSize = maxSize) { tts -> tts.free() }
    }
}

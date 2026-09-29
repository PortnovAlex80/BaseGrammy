package com.alexpo.grammermate.data

import com.k2fsa.sherpa.onnx.OfflineTts
import kotlinx.coroutines.sync.Mutex

/**
 * Resident LRU cache of Sherpa-ONNX [OfflineTts] models keyed by language id.
 *
 * Why this exists: the Background Vocab Listener plays alternating it↔ru segments.
 * Reloading a ~150 MB VITS_PIPER model on every language switch (1–3 s) makes the
 * vocab script choppy. Keeping the most-recently-used models resident in RAM makes
 * language switching effectively instant.
 *
 * Memory cost: each resident VITS model occupies roughly ~150 MB of native heap.
 * With [maxSize] = 3 the worst case is ~450 MB resident; the default of 3 lets
 * IT + RU + one extra coexist while evicting older models under memory pressure.
 *
 * Thread-safety: callers (TtsEngine) guard access with [Mutex]. This class itself
 * is not internally synchronized — it is a plain data structure with no Android
 * dependencies so it can be unit-tested on a plain JVM.
 *
 * Eviction policy: access-ordered [LinkedHashMap]; when [put] exceeds [maxSize],
 * the least-recently-used entry is removed and its [OfflineTts.free] is invoked
 * to release the native model handle.
 *
 * @param maxSize maximum number of models to keep resident. Must be >= 1.
 * @param freeFn  function used to release a model. Defaults to [OfflineTts.free].
 *                Injected so unit tests can pass a fake without touching native code.
 */
internal class ResidentTtsCache<T : Any>(
    private val maxSize: Int = DEFAULT_MAX_SIZE,
    private val freeFn: (T) -> Unit
) {
    init {
        require(maxSize >= 1) { "maxSize must be >= 1, was $maxSize" }
    }

    /**
     * Access-ordered map: iteration order is from least- to most-recently-used.
     * `removeEldestEntry` is intentionally NOT used — eviction runs [freeFn] which
     * has side effects, so we handle it explicitly in [put] to control ordering.
     */
    private val map: LinkedHashMap<String, T> =
        LinkedHashMap(4, 0.75f, true)

    /**
     * Returns the resident model for [lang], or null if absent.
     * As a side effect of access-order, a successful lookup promotes [lang] to
     * most-recently-used.
     */
    fun get(lang: String): T? = map[lang]

    /** True iff a resident model exists for [lang]. Does not promote recency. */
    fun contains(lang: String): Boolean = map.containsKey(lang)

    /**
     * Stores [tts] for [lang], promoting it to most-recently-used. If inserting
     * exceeds [maxSize], the least-recently-used model is evicted and freed via
     * [freeFn]. If [lang] was already resident, the previous model is freed and
     * replaced (no double-count against [maxSize]).
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

    /** Removes and frees the model for [lang] if present. No-op otherwise. */
    fun remove(lang: String) {
        val removed = map.remove(lang)
        if (removed != null) safeFree(removed)
    }

    /** Frees and removes ALL resident models. */
    fun clear() {
        val snapshot = ArrayList(map.values)
        map.clear()
        snapshot.forEach { safeFree(it) }
    }

    /** Snapshot of resident language ids (no ordering guarantee for callers). */
    fun keys(): Set<String> = LinkedHashSet(map.keys)

    /** Number of resident models. */
    fun size(): Int = map.size

    private fun safeFree(tts: T) {
        try {
            freeFn(tts)
        } catch (t: Throwable) {
            // Swallow — freeing is best-effort; never let it corrupt the cache.
        }
    }

    companion object {
        const val DEFAULT_MAX_SIZE = 3
    }
}

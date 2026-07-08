package com.alexpo.grammermate.v2.core.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pure-JVM unit tests for [ResidentTtsCache] LRU semantics.
 *
 * Covers:
 *  - **AC-4** — LRU eviction when exceeding `maxSize=3` (the 4th put evicts
 *    the least-recently-used resident model and invokes the free callback).
 *  - **AC-5** — cache hit: switching back to a resident language reuses the
 *    cached model and triggers **zero** native re-inits, well under the 100 ms
 *    latency budget.
 *
 * ## Почему модель — это [FakeTts], а не `com.k2fsa.sherpa.onnx.OfflineTts`
 *
 * `OfflineTts` имеет `static { System.loadLibrary("sherpa-onnx-jni") }`, который
 * **падает** в pure-JVM окружении unit-test (нативная `.so` есть только под
 * Android ABI — arm/x86, не под JVM на хосте). Поэтому [ResidentTtsCache]
 * параметризован типом носителя [T] (см. KDoc класса): в production это
 * `OfflineTts`, в тестах — лёгкий POJO [FakeTts]. Это позволяет проверить
 * LRU-логику и latency-бенчмарк **изолированно** от native-слоя — именно так,
 * как требует SRS-003 §2.5 ("чистая структура данных без Android-зависимостей,
 * unit-тестируется на чистом JVM").
 *
 * Reference AC: `docs/requirements/REQ-003-audio/03-acceptance-criteria.md#AC-4`,
 * `#AC-5`. FR trace: FR-3. SRS: `docs/requirements/REQ-003-audio/02-srs.md`.
 */
class ResidentTtsCacheTest {

    /** Lightweight POJO standing in for a native OfflineTts instance. */
    private data class FakeTts(val label: String)

    /** Tracks free() calls in order, keyed by [FakeTts.label]. */
    private class FreeRecorder {
        val freed: MutableList<String> = mutableListOf()
        fun freeFn(): (FakeTts) -> Unit = { tts -> freed += tts.label }
    }

    /** Allocates a fake model tagged with [label]. */
    private fun model(label: String): FakeTts = FakeTts(label)

    // ── AC-4 — LRU eviction (maxSize=3) ──────────────────────────────────────

    /**
     * AC-4: `put` beyond maxSize=3 evicts the LRU model and frees it.
     *
     * Given the cache holds `it`, `ru`, `en` (full);
     * When `put("de", m4)` is called;
     * Then `size() == 3`, the LRU (`it`) is freed, the rest survive.
     */
    @Test
    fun `AC-4 put over capacity evicts the least recently used model and frees it`() {
        val recorder = FreeRecorder()
        val cache = ResidentTtsCache<FakeTts>(maxSize = 3, freeFn = recorder.freeFn())

        cache.put("it", model("it"))
        cache.put("ru", model("ru"))
        cache.put("en", model("en"))
        assertEquals(3, cache.size())

        // Inserting a 4th evicts the LRU ("it" — inserted first, never accessed).
        cache.put("de", model("de"))

        assertEquals(3, cache.size())
        assertFalse("it should have been evicted", cache.contains("it"))
        assertTrue(cache.contains("ru"))
        assertTrue(cache.contains("en"))
        assertTrue(cache.contains("de"))
        assertEquals(listOf("it"), recorder.freed)
    }

    /**
     * AC-4 (recency): accessing a model promotes it, so the new LRU is evicted.
     *
     * Given `it`, `ru`, `en` resident; When `get("it")` is called then a 4th is
     * inserted; Then the new LRU (`ru`, never accessed after inserts) is evicted.
     */
    @Test
    fun `AC-4 get promotes recency so the accessed model is not evicted next`() {
        val recorder = FreeRecorder()
        val cache = ResidentTtsCache<FakeTts>(maxSize = 3, freeFn = recorder.freeFn())

        cache.put("it", model("it"))
        cache.put("ru", model("ru"))
        cache.put("en", model("en"))

        // Access "it" — promotes it to most-recently-used.
        assertNotNull(cache.get("it"))

        // Now inserting a 4th evicts the new LRU, which is "ru".
        cache.put("de", model("de"))

        assertTrue("it survived because it was accessed", cache.contains("it"))
        assertFalse("ru was evicted as the new LRU", cache.contains("ru"))
        assertEquals(listOf("ru"), recorder.freed)
    }

    /**
     * AC-4: re-putting the same language frees the previous model and does not
     * grow the size (no double-count against maxSize).
     */
    @Test
    fun `AC-4 put on existing key frees the previous model and does not grow size`() {
        val recorder = FreeRecorder()
        val cache = ResidentTtsCache<FakeTts>(maxSize = 3, freeFn = recorder.freeFn())

        val old = model("it-v1")
        cache.put("it", old)
        val fresh = model("it-v2")
        cache.put("it", fresh)

        assertEquals(1, cache.size())
        assertSame(fresh, cache.get("it"))
        assertEquals(listOf("it-v1"), recorder.freed)
    }

    /**
     * AC-4 corner: re-putting the SAME instance must not free it.
     */
    @Test
    fun `AC-4 put same instance on existing key does not free`() {
        val recorder = FreeRecorder()
        val cache = ResidentTtsCache<FakeTts>(maxSize = 3, freeFn = recorder.freeFn())

        val m = model("it")
        cache.put("it", m)
        cache.put("it", m) // identical reference

        assertEquals(1, cache.size())
        assertTrue("free must not be called for the same instance", recorder.freed.isEmpty())
    }

    /**
     * AC-4 contract: `maxSize` must be >= 1 — the cache is invalid otherwise.
     */
    @Test
    fun `AC-4 constructor rejects maxSize smaller than 1`() {
        try {
            ResidentTtsCache<FakeTts>(maxSize = 0) { _ -> }
            fail("Expected IllegalArgumentException for maxSize = 0")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    /**
     * AC-4 robustness: if freeFn throws, the eviction still completes and the
     * cache state is not corrupted.
     */
    @Test
    fun `AC-4 freeFn exceptions are swallowed and do not corrupt the cache`() {
        val boom: (FakeTts) -> Unit = { throw RuntimeException("native free exploded") }
        val cache = ResidentTtsCache<FakeTts>(maxSize = 2, freeFn = boom)

        cache.put("it", model("it"))
        cache.put("ru", model("ru"))
        // Forcing an eviction: free throws, but cache must still evict and accept the new entry.
        cache.put("en", model("en"))

        assertEquals(2, cache.size())
        assertFalse(cache.contains("it"))
        assertTrue(cache.contains("ru"))
        assertTrue(cache.contains("en"))
    }

    // ── AC-5 — cache hit, zero native re-init, < 100 ms ──────────────────────

    /**
     * AC-5 ★ CRITICAL: switching back to a resident language reuses the cached
     * model — **zero** native re-inits on cache hit.
     *
     * Given `it` and `ru` are resident (loaded once each);
     * When alternation `it → ru → it → ru` is performed (via `get`);
     * Then the cache returns the SAME instances each time and no additional
     * native load is triggered (we model the native constructor with a counter:
     * the load counter must stay at 2 = one per language, regardless of how many
     * hits follow).
     *
     * FR-3, NFR-4. Эталон AC-5: "0 вызовов OfflineTts(...) constructor на 2..N-м
     * speak того же языка" — здесь это `nativeLoadCalls`, который не растёт на
     * кэш-хитах.
     */
    @Test
    fun `AC-5 cache hit returns the same resident instance and triggers zero native re-init`() {
        val recorder = FreeRecorder()
        val cache = ResidentTtsCache<FakeTts>(maxSize = 3, freeFn = recorder.freeFn())

        // Simulated native load counter: each `put` here models one OfflineTts
        // constructor call. Cache hits use `get`, which must NOT increment it.
        var nativeLoadCalls = 0
        fun load(lang: String): FakeTts {
            nativeLoadCalls += 1
            return model(lang)
        }

        // Cold-start: load each language once.
        cache.put("it", load("it"))
        cache.put("ru", load("ru"))
        val itInstance = cache.get("it")!!
        val ruInstance = cache.get("ru")!!
        assertEquals("one native load per language", 2, nativeLoadCalls)

        // Alternation: it → ru → it → ru. Every hit must reuse the resident
        // model — no extra OfflineTts(...) construction.
        repeat(20) {
            assertSame(itInstance, cache.get("it"))
            assertSame(ruInstance, cache.get("ru"))
        }
        assertEquals(
            "cache hit must NOT trigger native re-init",
            2,
            nativeLoadCalls,
        )
        assertTrue("no frees during alternation", recorder.freed.isEmpty())
    }

    /**
     * AC-5 ★ CRITICAL: cache-hit latency is well under the 100 ms budget.
     *
     * Given `it` and `ru` resident; When alternation runs; Then the per-hit
     * lookup time is `<< 100 ms` (we assert the amortised lookup time for the
     * full alternation is under 1 ms — generous headroom against the 100 ms
     * requirement from the AC).
     *
     * Эталон AC-5: `(t(Started) − t(speak)) < 100 ms для resident-языков`.
     * Здесь это моделируется как amortised `perLookupNanos < 1_000_000` (1 ms).
     * Этот тест — regression guard: любая регрессия, добавляющая I/O/locking на
     * hit-path, разорвёт бюджет.
     */
    @Test
    fun `AC-5 cache hit latency is well under 100 ms budget`() {
        val cache = ResidentTtsCache<FakeTts>(maxSize = 3) { _ -> }
        cache.put("it", model("it"))
        cache.put("ru", model("ru"))

        val alternations = 1_000
        val start = System.nanoTime()
        repeat(alternations) {
            assertNotNull(cache.get("it"))
            assertNotNull(cache.get("ru"))
        }
        val elapsedNanos = System.nanoTime() - start
        val perLookupNanos = elapsedNanos / (alternations * 2L)

        // The AC budget is 100 ms = 100_000_000 ns per language switch. The
        // amortised hit must be many orders of magnitude below that. We assert
        // < 1 ms (1_000_000 ns) per lookup to leave huge headroom and still
        // catch a regression that, say, hit disk or took a lock under load.
        assertTrue(
            "per-lookup latency $perLookupNanos ns must be < 1_000_000 ns (1 ms); " +
                "AC-5 budget is 100 ms per language switch",
            perLookupNanos < 1_000_000L,
        )
    }

    /**
     * AC-5 supporting behaviour: `contains` does NOT promote recency (only
     * `get`/`put` do). This guards against an accidental promotion that would
     * silently change LRU eviction order during availability probes.
     */
    @Test
    fun `AC-5 contains does not promote recency`() {
        val recorder = FreeRecorder()
        val cache = ResidentTtsCache<FakeTts>(maxSize = 3, freeFn = recorder.freeFn())

        cache.put("it", model("it"))
        cache.put("ru", model("ru"))
        cache.put("en", model("en"))

        // Probe "it" via contains — must NOT promote it.
        assertTrue(cache.contains("it"))

        // Inserting a 4th still evicts the actual LRU ("it"), proving contains
        // did not promote it.
        cache.put("de", model("de"))

        assertFalse("contains did not promote it, so it was evicted", cache.contains("it"))
        assertEquals(listOf("it"), recorder.freed)
    }

    // ── Supporting behaviour (basic cache operations) ────────────────────────

    @Test
    fun `put and get round-trips a single model`() {
        val recorder = FreeRecorder()
        val cache = ResidentTtsCache<FakeTts>(maxSize = 3, freeFn = recorder.freeFn())

        val m = model("it")
        cache.put("it", m)

        assertTrue(cache.contains("it"))
        assertSame(m, cache.get("it"))
        assertEquals(1, cache.size())
        assertTrue(recorder.freed.isEmpty())
    }

    @Test
    fun `get returns null for unknown language`() {
        val cache = ResidentTtsCache<FakeTts>(maxSize = 3) { _ -> }
        assertNull(cache.get("ru"))
        assertFalse(cache.contains("ru"))
        assertEquals(0, cache.size())
    }

    @Test
    fun `remove frees the model for the given language only`() {
        val recorder = FreeRecorder()
        val cache = ResidentTtsCache<FakeTts>(maxSize = 3, freeFn = recorder.freeFn())

        cache.put("it", model("it"))
        cache.put("ru", model("ru"))

        cache.remove("it")

        assertFalse(cache.contains("it"))
        assertTrue(cache.contains("ru"))
        assertEquals(1, cache.size())
        assertEquals(listOf("it"), recorder.freed)
    }

    @Test
    fun `remove unknown language is a no-op`() {
        val recorder = FreeRecorder()
        val cache = ResidentTtsCache<FakeTts>(maxSize = 3, freeFn = recorder.freeFn())
        cache.put("it", model("it"))

        cache.remove("xx") // not present

        assertEquals(1, cache.size())
        assertTrue(recorder.freed.isEmpty())
    }

    @Test
    fun `clear frees all resident models and empties the cache`() {
        val recorder = FreeRecorder()
        val cache = ResidentTtsCache<FakeTts>(maxSize = 3, freeFn = recorder.freeFn())

        cache.put("it", model("it"))
        cache.put("ru", model("ru"))
        cache.put("en", model("en"))

        cache.clear()

        assertEquals(0, cache.size())
        assertTrue(cache.keys().isEmpty())
        assertEquals(3, recorder.freed.size)
        assertTrue(recorder.freed.containsAll(listOf("it", "ru", "en")))
    }

    @Test
    fun `keys returns the set of resident languages`() {
        val cache = ResidentTtsCache<FakeTts>(maxSize = 3) { _ -> }
        cache.put("it", model("it"))
        cache.put("ru", model("ru"))

        val keys = cache.keys()
        assertEquals(2, keys.size)
        assertTrue(keys.contains("it"))
        assertTrue(keys.contains("ru"))
    }

    @Test
    fun `DEFAULT_MAX_SIZE is 3 per SRS FR-3`() {
        assertEquals(3, ResidentTtsCache.DEFAULT_MAX_SIZE)
    }
}

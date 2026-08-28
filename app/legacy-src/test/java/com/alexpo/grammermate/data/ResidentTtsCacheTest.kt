package com.alexpo.grammermate.data

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
 * `OfflineTts` is a native Sherpa-ONNX class that cannot be instantiated in a
 * JVM unit test, so the cache accepts an injected `freeFn`. We pass a fake
 * model object via unchecked casts from [Any] to [OfflineTts]; the cache never
 * dereferences the model's native methods in tests — it only stores it and
 * invokes the injected free function.
 */
class ResidentTtsCacheTest {

    /** A boxed token standing in for a native OfflineTts instance. */
    private data class FakeTts(val label: String)

    /** Tracks free() calls keyed by FakeTts.label, returns the same list. */
    private class FreeRecorder {
        val freed: MutableList<String> = mutableListOf()
        fun freeFn(): (FakeTts) -> Unit = { tts ->
            freed += tts.label
        }
    }

    private fun makeModel(fake: FakeTts): FakeTts = fake

    @Test
    fun `put and get round-trips a single model`() {
        val recorder = FreeRecorder()
        val cache = ResidentTtsCache(maxSize = 3, freeFn = recorder.freeFn())

        val m = makeModel(FakeTts("it"))
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
    fun `put over capacity evicts the least recently used model and frees it`() {
        val recorder = FreeRecorder()
        val cache = ResidentTtsCache(maxSize = 3, freeFn = recorder.freeFn())

        cache.put("it", makeModel(FakeTts("it")))
        cache.put("ru", makeModel(FakeTts("ru")))
        cache.put("en", makeModel(FakeTts("en")))
        assertEquals(3, cache.size())

        // Inserting a 4th evicts the LRU ("it" — inserted first, never accessed).
        cache.put("de", makeModel(FakeTts("de")))

        assertEquals(3, cache.size())
        assertFalse("it should have been evicted", cache.contains("it"))
        assertTrue(cache.contains("ru"))
        assertTrue(cache.contains("en"))
        assertTrue(cache.contains("de"))
        assertEquals(listOf("it"), recorder.freed)
    }

    @Test
    fun `get promotes recency so the accessed model is not evicted next`() {
        val recorder = FreeRecorder()
        val cache = ResidentTtsCache(maxSize = 3, freeFn = recorder.freeFn())

        cache.put("it", makeModel(FakeTts("it")))
        cache.put("ru", makeModel(FakeTts("ru")))
        cache.put("en", makeModel(FakeTts("en")))

        // Access "it" — promotes it to most-recently-used.
        assertNotNull(cache.get("it"))

        // Now inserting a 4th evicts the new LRU, which is "ru".
        cache.put("de", makeModel(FakeTts("de")))

        assertTrue("it survived because it was accessed", cache.contains("it"))
        assertFalse("ru was evicted as the new LRU", cache.contains("ru"))
        assertEquals(listOf("ru"), recorder.freed)
    }

    @Test
    fun `put on existing key frees the previous model and does not grow size`() {
        val recorder = FreeRecorder()
        val cache = ResidentTtsCache(maxSize = 3, freeFn = recorder.freeFn())

        val old = makeModel(FakeTts("it-v1"))
        cache.put("it", old)
        val fresh = makeModel(FakeTts("it-v2"))
        cache.put("it", fresh)

        assertEquals(1, cache.size())
        assertSame(fresh, cache.get("it"))
        assertEquals(listOf("it-v1"), recorder.freed)
    }

    @Test
    fun `put same instance on existing key does not free`() {
        val recorder = FreeRecorder()
        val cache = ResidentTtsCache(maxSize = 3, freeFn = recorder.freeFn())

        val m = makeModel(FakeTts("it"))
        cache.put("it", m)
        cache.put("it", m) // identical reference

        assertEquals(1, cache.size())
        assertTrue("free must not be called for the same instance", recorder.freed.isEmpty())
    }

    @Test
    fun `remove frees the model for the given language only`() {
        val recorder = FreeRecorder()
        val cache = ResidentTtsCache(maxSize = 3, freeFn = recorder.freeFn())

        cache.put("it", makeModel(FakeTts("it")))
        cache.put("ru", makeModel(FakeTts("ru")))

        cache.remove("it")

        assertFalse(cache.contains("it"))
        assertTrue(cache.contains("ru"))
        assertEquals(1, cache.size())
        assertEquals(listOf("it"), recorder.freed)
    }

    @Test
    fun `remove unknown language is a no-op`() {
        val recorder = FreeRecorder()
        val cache = ResidentTtsCache(maxSize = 3, freeFn = recorder.freeFn())
        cache.put("it", makeModel(FakeTts("it")))

        cache.remove("xx") // not present

        assertEquals(1, cache.size())
        assertTrue(recorder.freed.isEmpty())
    }

    @Test
    fun `clear frees all resident models and empties the cache`() {
        val recorder = FreeRecorder()
        val cache = ResidentTtsCache(maxSize = 3, freeFn = recorder.freeFn())

        cache.put("it", makeModel(FakeTts("it")))
        cache.put("ru", makeModel(FakeTts("ru")))
        cache.put("en", makeModel(FakeTts("en")))

        cache.clear()

        assertEquals(0, cache.size())
        assertTrue(cache.keys().isEmpty())
        assertEquals(3, recorder.freed.size)
        assertTrue(recorder.freed.containsAll(listOf("it", "ru", "en")))
    }

    @Test
    fun `keys returns the set of resident languages`() {
        val cache = ResidentTtsCache<FakeTts>(maxSize = 3) { _ -> }
        cache.put("it", makeModel(FakeTts("it")))
        cache.put("ru", makeModel(FakeTts("ru")))

        val keys = cache.keys()
        assertEquals(2, keys.size)
        assertTrue(keys.contains("it"))
        assertTrue(keys.contains("ru"))
    }

    @Test
    fun `constructor rejects maxSize smaller than 1`() {
        try {
            ResidentTtsCache<FakeTts>(maxSize = 0) { _ -> }
            fail("Expected IllegalArgumentException for maxSize = 0")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `freeFn exceptions are swallowed and do not corrupt the cache`() {
        val boom: (FakeTts) -> Unit = { throw RuntimeException("native free exploded") }
        val cache = ResidentTtsCache(maxSize = 2, freeFn = boom)

        cache.put("it", makeModel(FakeTts("it")))
        cache.put("ru", makeModel(FakeTts("ru")))
        // Forcing an eviction: free throws, but cache must still evict and accept the new entry.
        cache.put("en", makeModel(FakeTts("en")))

        assertEquals(2, cache.size())
        assertFalse(cache.contains("it"))
        assertTrue(cache.contains("ru"))
        assertTrue(cache.contains("en"))
    }
}

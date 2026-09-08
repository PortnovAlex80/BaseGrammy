package com.alexpo.grammermate.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Phase 1 (plan item 1.2): AppConfigStoreImpl.load() must serve from an
 * in-memory cache — it is called from composition (uiLanguage,
 * clickableWordHints arguments), and each call used to re-read + SnakeYAML-
 * parse config.yaml on the main thread.
 *
 * Fix contract pinned here:
 * - load() returns the cached value even if the file disappears afterwards;
 * - save() publishes the freshly written value into the cache (and only
 *   after a successful write);
 * - the cache is per-instance, so separate store instances (tests, or a
 *   future multi-owner setup) never observe each other's state.
 */
@RunWith(RobolectricTestRunner::class)
class AppConfigStoreCacheTest {

    @Test
    fun `load serves cached config after the file is gone`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AppConfigStoreImpl(context)

        val first = store.load()
        File(context.filesDir, "grammarmate/config.yaml").delete()

        assertEquals(
            "load() must not re-read the file once cached",
            first,
            store.load()
        )
    }

    @Test
    fun `save publishes the new value into the cache`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AppConfigStoreImpl(context)

        val first = store.load()
        store.save(first.copy(uiLanguage = "ru"))

        assertEquals("ru", store.load().uiLanguage)
    }

    @Test
    fun `cache is per-instance`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val a = AppConfigStoreImpl(context)
        a.save(a.load().copy(uiLanguage = "ru"))
        File(context.filesDir, "grammarmate/config.yaml").delete()

        val b = AppConfigStoreImpl(context)
        assertEquals(
            "a second instance must not observe the first instance's memory cache",
            "system",
            b.load().uiLanguage
        )
        assertEquals(
            "the first instance still serves its own cached value",
            "ru",
            a.load().uiLanguage
        )
    }
}

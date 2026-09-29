package com.alexpo.grammermate.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class VerbTenseInfoRepositoryTest {
    private val repository = VerbTenseInfoRepository(
        RuntimeEnvironment.getApplication().assets
    )

    @Test
    fun parses_current_and_legacy_example_keys_with_defaults() {
        val result = repository.parse(
            """
            tenses:
              - name: Presente
                examples:
                  - target: Io parlo
                    ru: I speak
                  - it: Tu parli
                    note: informal
              - name: Imperfetto
              - short: ignored
            """.trimIndent()
        )

        assertEquals(listOf("Presente", "Imperfetto"), result.keys.toList())
        assertEquals("Presente", result["Presente"]?.short)
        assertEquals("Io parlo", result["Presente"]?.examples?.get(0)?.target)
        assertEquals("Tu parli", result["Presente"]?.examples?.get(1)?.target)
        assertEquals("", result["Presente"]?.examples?.get(1)?.ru)
        assertEquals("Imperfet", result["Imperfetto"]?.short)
    }

    @Test
    fun missing_language_reference_returns_empty_map() {
        assertTrue(repository.load("missing_language").isEmpty())
    }
}

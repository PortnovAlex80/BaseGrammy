package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LessonPackManifestTest {
    @Test
    fun parseValidManifest() {
        val json = """
            {
              "schemaVersion": 1,
              "packId": "EN_Core_A1",
              "packVersion": "v1",
              "language": "en",
              "lessons": [
                { "lessonId": "L01", "order": 1, "title": "Present Simple", "file": "lesson_01.csv" }
              ]
            }
        """.trimIndent()
        val manifest = LessonPackManifest.fromJson(json)
        assertEquals("EN_Core_A1", manifest.packId)
        assertEquals(1, manifest.lessons.size)
        assertEquals("lesson_01.csv", manifest.lessons.first().file)
    }
}

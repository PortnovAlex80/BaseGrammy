package com.alexpo.grammermate.v2.core.data.packimport

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Регрессия LessonPackManifest.fromJson (Фаза 1 плана стабилизации
 * 2026-08-26): legacy-валидация 1:1 (SRS-002 FR-2 / AC-4..AC-6), реализация
 * на kotlinx.serialization — чистый JVM.
 */
class LessonPackManifestTest {

    /** Фикс M-2: невалидный манифест — null, а не IllegalStateException. */
    @Test
    fun invalid_manifest_returnsNull_notException() {
        assertThat(LessonPackManifest.fromJson("{not json")).isNull()
        assertThat(
            LessonPackManifest.fromJson(
                """{"schemaVersion": 3, "packId": "P", "packVersion": "v", "language": "it"}"""
            )
        ).isNull()
        assertThat(
            LessonPackManifest.fromJson(
                """{"schemaVersion": 2, "packId": "", "packVersion": "v", "language": "it"}"""
            )
        ).isNull()
    }

    private val v2Manifest = """
        {
          "schemaVersion": 2,
          "packId": "ITALIAN_SHORT",
          "packVersion": "v6",
          "language": "it",
          "displayName": "Italian Fast Track",
          "verbDrill": { "files": ["it_drill_verbs.csv", " it_drill_nouns.csv "] },
          "backgroundVocab": {
            "file": "bg_vocab_12000.csv",
            "audioDir": "bg_vocab/audio",
            "defaultLanguage": "it",
            "translationLanguage": "ru"
          },
          "chapters": [
            { "chapterId": "chapter_0", "order": 0, "title": "Введение", "subtitle": "До языка",
              "storyFile": "chapter_00.md", "lessons": [] },
            { "chapterId": "chapter_1", "order": 1, "title": "Глава 1", "subtitle": "Настоящее",
              "storyFile": null,
              "lessons": ["lesson_01_A01", "lesson_02_A02"] }
          ]
        }
    """.trimIndent()

    @Test
    fun v2_chaptersDrillsAndBackgroundVocab_parsed() {
        val manifest = LessonPackManifest.fromJson(v2Manifest)
            .also { assertThat(it).isNotNull() }!!

        assertThat(manifest.schemaVersion).isEqualTo(2)
        assertThat(manifest.packId).isEqualTo("ITALIAN_SHORT")
        assertThat(manifest.packVersion).isEqualTo("v6")
        assertThat(manifest.language).isEqualTo("it")
        assertThat(manifest.displayName).isEqualTo("Italian Fast Track")

        assertThat(manifest.chapters).hasSize(2)
        val chapter0 = manifest.chapters[0]
        assertThat(chapter0.chapterId).isEqualTo("chapter_0")
        assertThat(chapter0.storyFile).isEqualTo("chapter_00.md")
        assertThat(chapter0.lessons).isEmpty()
        val chapter1 = manifest.chapters[1]
        assertThat(chapter1.lessons).containsExactly("lesson_01_A01", "lesson_02_A02").inOrder()

        assertThat(manifest.verbDrill!!.files)
            .containsExactly("it_drill_verbs.csv", "it_drill_nouns.csv")
            .inOrder()
        assertThat(manifest.backgroundVocab!!.file).isEqualTo("bg_vocab_12000.csv")
        assertThat(manifest.backgroundVocab!!.audioDir).isEqualTo("bg_vocab/audio")
    }

    /** Фикс M-1: дубли chapterId / висячие chapter.lessons / дубли order — null. */
    @Test
    fun structural_validation_rejectsDuplicatesAndDanglingRefs() {
        val base = """
            {
              "schemaVersion": 2, "packId": "P", "packVersion": "v", "language": "it",
              "lessons": [
                { "lessonId": "l1", "file": "l1.csv", "order": 1 },
                { "lessonId": "l2", "file": "l2.csv", "order": 2 }
              ]
            }
        """.trimIndent()
        fun chapters(vararg ch: String) = base.replace("}", ",\"chapters\": [" + ch.joinToString(",") + "] }", )

        // Дубль chapterId.
        assertThat(LessonPackManifest.fromJson(chapters(
            """{"chapterId": "c1", "order": 1, "title": "t", "subtitle": null, "storyFile": null, "lessons": ["l1"]}""",
            """{"chapterId": "c1", "order": 2, "title": "t", "subtitle": null, "storyFile": null, "lessons": ["l2"]}""",
        ))).isNull()

        // Висячая ссылка chapter.lessons → несуществующий урок.
        assertThat(LessonPackManifest.fromJson(chapters(
            """{"chapterId": "c1", "order": 1, "title": "t", "subtitle": null, "storyFile": null, "lessons": ["ghost"]}""",
        ))).isNull()

        // Валидная структура с главой проходит.
        assertThat(LessonPackManifest.fromJson(chapters(
            """{"chapterId": "c1", "order": 1, "title": "t", "subtitle": null, "storyFile": null, "lessons": ["l1", "l2"]}""",
        ))).isNotNull()
    }

    @Test
    fun v1_rootLessons_parsed() {
        val manifest = LessonPackManifest.fromJson(
            """
            {
              "schemaVersion": 1,
              "packId": "P1", "packVersion": "v1", "language": "en",
              "lessons": [
                { "lessonId": "l1", "file": "l1.csv", "order": 2, "title": "Второй" },
                { "lessonId": "l2", "file": "l2.csv" }
              ]
            }
            """.trimIndent()
        ).also { assertThat(it).isNotNull() }!!

        assertThat(manifest.lessons).hasSize(2)
        assertThat(manifest.lessons[0].order).isEqualTo(2)
        assertThat(manifest.lessons[0].title).isEqualTo("Второй")
        // order по умолчанию — index+1 (legacy optInt("order", i + 1)).
        assertThat(manifest.lessons[1].order).isEqualTo(2)
        assertThat(manifest.chapters).isEmpty()
    }

    /** Фикс M-2: невалидный schemaVersion — null (typed-отказ), не исключение. */
    @Test
    fun unsupportedSchemaVersion_returnsNull() {
        assertThat(
            LessonPackManifest.fromJson(
                """{"schemaVersion": 3, "packId": "p", "packVersion": "v", "language": "it"}"""
            )
        ).isNull()
    }

    /** Фикс M-2: пустые обязательные поля — null (typed-отказ), не исключение. */
    @Test
    fun missingRequiredFields_returnsNull() {
        assertThat(
            LessonPackManifest.fromJson(
                """{"schemaVersion": 2, "packId": "  ", "packVersion": "v", "language": "it", "chapters": []}"""
            )
        ).isNull()
    }

    /** Фикс M-2: пустой манифест без контента — null, не исключение. */
    @Test
    fun v2_withoutContent_returnsNull() {
        assertThat(
            LessonPackManifest.fromJson(
                """{"schemaVersion": 2, "packId": "P", "packVersion": "v", "language": "it"}"""
            )
        ).isNull()
    }

    @Test
    fun v2_emptyChaptersButBackgroundVocab_valid() {
        val manifest = LessonPackManifest.fromJson(
            """
            { "schemaVersion": 2, "packId": "p", "packVersion": "v", "language": "it",
              "chapters": [],
              "backgroundVocab": { "file": "bg.csv" } }
            """.trimIndent()
        ).also { assertThat(it).isNotNull() }!!

        assertThat(manifest.backgroundVocab).isNotNull()
        assertThat(manifest.backgroundVocab!!.defaultLanguage).isEqualTo("it")
        assertThat(manifest.backgroundVocab!!.translationLanguage).isEqualTo("ru")
    }

    @Test
    fun invalidChapterEntry_throws() {
        val ex = assertThrows(IllegalStateException::class.java) {
            LessonPackManifest.fromJson(
                """
                { "schemaVersion": 2, "packId": "p", "packVersion": "v", "language": "it",
                  "chapters": [ { "chapterId": "  ", "order": 0, "title": "x", "lessons": ["l1"] } ] }
                """.trimIndent()
            )
        }
        assertThat(ex).hasMessageThat().contains("Invalid chapter entry")
    }
}

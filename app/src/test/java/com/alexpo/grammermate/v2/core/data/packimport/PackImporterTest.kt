package com.alexpo.grammermate.v2.core.data.packimport

import androidx.test.core.app.ApplicationProvider
import com.alexpo.grammermate.v2.core.data.local.GrammarMateDatabase
import com.alexpo.grammermate.v2.core.data.local.TrainingDbFixture
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.alexpo.grammermate.domain.model.PackId

/**
 * Регрессия PackImporter (Фаза 1 плана стабилизации 2026-08-26): идемпотентный
 * импорт bundled-пака — ZIP → manifest → packs/chapters/lessons/cards в ОДНОЙ
 * Room-транзакции; card PK = `lessonId_index` (префикс урока исключает
 * коллизии `card_N` между уроками).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackImporterTest {

    private lateinit var db: GrammarMateDatabase
    private lateinit var importer: PackImporter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = TrainingDbFixture.inMemory(context)
        importer = PackImporter(db, context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }

    private val manifestJson = """
        {
          "schemaVersion": 2,
          "packId": "TEST_PACK", "packVersion": "v1", "language": "it",
          "displayName": "Test Pack",
          "chapters": [
            { "chapterId": "chapter_1", "order": 1, "title": "Глава 1", "subtitle": null,
              "lessons": ["lesson_01", "lesson_02"] }
          ]
        }
    """.trimIndent()

    private fun lessonCsv(rows: Int, withBadLine: Boolean = false): String {
        val sb = StringBuilder("Урок тестовый\n")
        repeat(rows) { i -> sb.append("Промпт $i;answer $i\n") }
        if (withBadLine) sb.append("лишняя;колонка;здесь\n")
        return sb.toString()
    }


    /** Срез 6 Фазы 4: .md-стори сохраняются в filesDir и читаются портом (M-3). */
    @Test
    fun importPackFromStream_preservesStoryMdReadableByPort() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storyMd = "# Глава 1\n\n{it}Ciao{/it} — начало пути."
        val result = importer.importPackFromStream(
            zip(
                "manifest.json" to manifestJson,
                "lesson_01.csv" to lessonCsv(rows = 1),
                "lesson_02.csv" to lessonCsv(rows = 1),
                "stories/it/chapter_1.md" to storyMd,
            )
        )

        assertThat(result).isInstanceOf(PackImportResult.Success::class.java)

        // Файл сохранён в filesDir/stories/<packId>/<rel>.
        val saved = java.io.File(
            java.io.File(context.filesDir, "stories/TEST_PACK"),
            "stories/it/chapter_1.md",
        )
        assertThat(saved.isFile).isTrue()
        assertThat(saved.readText()).isEqualTo(storyMd)

        // И доступен через доменный порт (ContentRepositoryImpl).
        val repo = com.alexpo.grammermate.v2.core.data.repository.ContentRepositoryImpl(
            contentDao = db.contentDao(),
            drillDao = db.drillDao(),
            context = context,
        )
        assertThat(repo.getStoryText(PackId("TEST_PACK"), "stories/it/chapter_1.md"))
            .isEqualTo(storyMd)
        assertThat(repo.getStoryText(PackId("TEST_PACK"), "missing.md")).isNull()
    }


    /** D3: verb/vocab drill-секции манифеста импортируются в Room. */
    @Test
    fun importPackFromStream_importsDrillContent() = runTest {
        val manifest = """
            {
              "schemaVersion": 2,
              "packId": "TEST_PACK", "packVersion": "v1", "language": "it",
              "verbDrill": { "files": ["it_drill_verbs.csv"] },
              "vocabDrill": { "files": ["vocab_nouns.csv"] },
              "chapters": [
                { "chapterId": "chapter_1", "order": 1, "title": "Глава 1", "subtitle": null,
                  "lessons": ["lesson_01"] }
              ]
            }
        """.trimIndent()
        val verbCsv = "Спряжения\nru;it;verb;tense;group;rank\nя говорю;io parlo;parlare;presente;are;1\n"
        val vocabCsv = "дом;casa\nкнига;libro;hard\n"

        val result = importer.importPackFromStream(
            zip(
                "manifest.json" to manifest,
                "lesson_01.csv" to lessonCsv(rows = 1),
                "it_drill_verbs.csv" to verbCsv,
                "vocab_nouns.csv" to vocabCsv,
            )
        )

        assertThat(result).isInstanceOf(PackImportResult.Success::class.java)
        val verbs = db.drillDao().getVerbDrillCardsForPack("TEST_PACK")
        assertThat(verbs).hasSize(1)
        assertThat(verbs.single().answer).isEqualTo("io parlo")
        assertThat(verbs.single().packId).isEqualTo("TEST_PACK")
        val vocab = db.drillDao().getVocabWordsForPack("TEST_PACK")
        assertThat(vocab.map { it.word }).containsExactly("casa", "libro").inOrder()
        assertThat(vocab.first().pos).isEqualTo("nouns")
    }

    @Test
    fun importPackFromStream_writesPackChaptersLessonsCards() = runTest {
        val result = importer.importPackFromStream(
            zip(
                "manifest.json" to manifestJson,
                "lesson_01.csv" to lessonCsv(rows = 2),
                "lesson_02.csv" to lessonCsv(rows = 1),
            )
        )

        assertThat(result).isInstanceOf(PackImportResult.Success::class.java)

        val pack = db.contentDao().getPack("TEST_PACK")!!
        assertThat(pack.languageId).isEqualTo("it")
        assertThat(pack.version).isEqualTo("v1")
        assertThat(pack.displayName).isEqualTo("Test Pack")

        val chapters = db.contentDao().getChapters("TEST_PACK")
        assertThat(chapters).hasSize(1)
        assertThat(chapters.single().title).isEqualTo("Глава 1")

        val lessons = db.contentDao().getLessons("TEST_PACK")
        assertThat(lessons.map { it.id }).containsExactly("lesson_01", "lesson_02").inOrder()
        assertThat(lessons.map { it.chapterId }).containsExactly("chapter_1", "chapter_1")

        val cards01 = db.contentDao().getCards("lesson_01")
        assertThat(cards01.map { it.id }).containsExactly("lesson_01_0", "lesson_01_1").inOrder()
        assertThat(cards01.map { it.ord }).containsExactly(0, 1).inOrder()
        assertThat(db.contentDao().getCards("lesson_02")).hasSize(1)
    }

    @Test
    fun importPackFromStream_idempotentReimport() = runTest {
        val stream = zip(
            "manifest.json" to manifestJson,
            "lesson_01.csv" to lessonCsv(rows = 2),
            "lesson_02.csv" to lessonCsv(rows = 1),
        )
        importer.importPackFromStream(stream)

        val second = importer.importPackFromStream(
            zip(
                "manifest.json" to manifestJson,
                "lesson_01.csv" to lessonCsv(rows = 2),
                "lesson_02.csv" to lessonCsv(rows = 1),
            )
        )

        assertThat(second).isInstanceOf(PackImportResult.Success::class.java)
        assertThat(db.contentDao().getPacks()).hasSize(1)
        assertThat(db.contentDao().getLessons("TEST_PACK")).hasSize(2)
        assertThat(db.contentDao().getCards("lesson_01")).hasSize(2)
    }

    @Test
    fun importPackFromStream_partialLesson_producesPartialResultAndSkipsBadLine() = runTest {
        val result = importer.importPackFromStream(
            zip(
                "manifest.json" to manifestJson,
                "lesson_01.csv" to lessonCsv(rows = 2, withBadLine = true),
                "lesson_02.csv" to lessonCsv(rows = 1),
            )
        )

        val partial = result as PackImportResult.Partial
        assertThat(partial.errors).hasSize(1)
        // Корректные карты сохранены, упавшая строка пропущена.
        assertThat(db.contentDao().getCards("lesson_01")).hasSize(2)
    }

    @Test
    fun importPackFromStream_missingManifest_failsWithoutWrite() = runTest {
        val result = importer.importPackFromStream(
            zip("lesson_01.csv" to lessonCsv(rows = 1))
        )

        assertThat(result).isInstanceOf(PackImportResult.Failed::class.java)
        assertThat(db.contentDao().getPacks()).isEmpty()
    }

    @Test
    fun importedCards_acceptedAnswersDecodeFromJson() = runTest {
        importer.importPackFromStream(
            zip(
                "manifest.json" to manifestJson,
                "lesson_01.csv" to "Урок\nПривет;ciao+salve\n",
                "lesson_02.csv" to lessonCsv(rows = 1),
            )
        )

        val card = db.contentDao().getCards("lesson_01").single()
        assertThat(card.promptRu).isEqualTo("Привет")
        val answers = Json.decodeFromString(ListSerializer(String.serializer()), card.acceptedAnswersJson)
        assertThat(answers).containsExactly("ciao", "salve").inOrder()
    }
}

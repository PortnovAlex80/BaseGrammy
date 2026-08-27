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

    @Test
    fun manifestStoryBasename_resolvesToStoriesDirectory() = runTest {
        val storyManifest = manifestJson.replace(
            "\"lessons\": [\"lesson_01\", \"lesson_02\"]",
            "\"storyFile\": \"chapter_1.md\", \"lessons\": [\"lesson_01\", \"lesson_02\"]",
        )
        val story = "# Story"

        val result = importer.importPackFromStream(
            zip(
                "manifest.json" to storyManifest,
                "lesson_01.csv" to lessonCsv(rows = 1),
                "lesson_02.csv" to lessonCsv(rows = 1),
                "stories/chapter_1.md" to story,
            ),
        )

        assertThat(result).isInstanceOf(PackImportResult.Success::class.java)
        val chapter = db.contentDao().getChapters("TEST_PACK").single()
        assertThat(chapter.storyFile).isEqualTo("stories/chapter_1.md")
        val repo = com.alexpo.grammermate.v2.core.data.repository.ContentRepositoryImpl(
            contentDao = db.contentDao(),
            drillDao = db.drillDao(),
            context = ApplicationProvider.getApplicationContext(),
        )
        assertThat(repo.getStoryText(PackId("TEST_PACK"), chapter.storyFile!!)).isEqualTo(story)
    }


    /** D3: verb/vocab drill-секции манифеста импортируются в Room. */

    /** D4: два пака с одинаковыми lessonId не затирают друг друга (составные PK). */
    @Test
    fun twoPacks_sameLessonId_bothCoexist() = runTest {
        val manifestA = manifestJson.replace("\"packId\": \"TEST_PACK\"", "\"packId\": \"PACK_A\"")
        val manifestB = manifestJson.replace("\"packId\": \"TEST_PACK\"", "\"packId\": \"PACK_B\"")
        val r1 = importer.importPackFromStream(
            zip("manifest.json" to manifestA, "lesson_01.csv" to lessonCsv(rows = 2), "lesson_02.csv" to lessonCsv(rows = 1))
        )
        val r2 = importer.importPackFromStream(
            zip("manifest.json" to manifestB, "lesson_01.csv" to lessonCsv(rows = 1), "lesson_02.csv" to lessonCsv(rows = 1))
        )
        assertThat(r1).isInstanceOf(PackImportResult.Success::class.java)
        assertThat(r2).isInstanceOf(PackImportResult.Success::class.java)

        // Оба пака: уроки не REPLACE-ились.
        assertThat(db.contentDao().getCards("PACK_A", "lesson_01")).hasSize(2)
        assertThat(db.contentDao().getCards("PACK_B", "lesson_01")).hasSize(1)
        val chaptersA = db.contentDao().getChapters("PACK_A")
        val chaptersB = db.contentDao().getChapters("PACK_B")
        assertThat(chaptersA).hasSize(1)
        assertThat(chaptersB).hasSize(1)
    }

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
    fun twoPacks_sameDrillCardId_doNotOverwriteEachOther() = runTest {
        fun drillManifest(packId: String) = """
            {
              "schemaVersion": 2,
              "packId": "$packId", "packVersion": "v1", "language": "it",
              "verbDrill": { "files": ["verbs.csv"] },
              "chapters": [
                { "chapterId": "chapter_1", "order": 1, "title": "Chapter",
                  "lessons": ["lesson_01"] }
              ]
            }
        """.trimIndent()
        fun verbCsv(answer: String) =
            "Conjugations\nru;it;verb;tense;group;rank\nprompt;$answer;parlare;presente;are;1\n"

        importer.importPackFromStream(
            zip(
                "manifest.json" to drillManifest("PACK_A"),
                "lesson_01.csv" to lessonCsv(rows = 1),
                "verbs.csv" to verbCsv("answer-a"),
            ),
        )
        importer.importPackFromStream(
            zip(
                "manifest.json" to drillManifest("PACK_B"),
                "lesson_01.csv" to lessonCsv(rows = 1),
                "verbs.csv" to verbCsv("answer-b"),
            ),
        )

        val cardA = db.drillDao().getVerbDrillCardsForPack("PACK_A").single()
        val cardB = db.drillDao().getVerbDrillCardsForPack("PACK_B").single()
        assertThat(cardA.id).isEqualTo(cardB.id)
        assertThat(cardA.answer).isEqualTo("answer-a")
        assertThat(cardB.answer).isEqualTo("answer-b")
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

        val cards01 = db.contentDao().getCards("TEST_PACK", "lesson_01")
        assertThat(cards01.map { it.id }).containsExactly("lesson_01_0", "lesson_01_1").inOrder()
        assertThat(cards01.map { it.ord }).containsExactly(0, 1).inOrder()
        assertThat(db.contentDao().getCards("TEST_PACK", "lesson_02")).hasSize(1)
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
        assertThat(db.contentDao().getCards("TEST_PACK", "lesson_01")).hasSize(2)
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
        assertThat(db.contentDao().getCards("TEST_PACK", "lesson_01")).hasSize(2)
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

        val card = db.contentDao().getCards("TEST_PACK", "lesson_01").single()
        assertThat(card.promptRu).isEqualTo("Привет")
        val answers = Json.decodeFromString(ListSerializer(String.serializer()), card.acceptedAnswersJson)
        assertThat(answers).containsExactly("ciao", "salve").inOrder()
    }
}

package com.alexpo.grammermate.v2.core.data.packimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit-тесты на [PartialImportBuilder] (AC-13 — критичный).
 *
 * AC-13 etalon (regression-lock): pack из 10 CSV-файлов, 3 повреждены
 * (`lesson_3.csv`, `lesson_7.csv`, `vocab_extra.csv`) → builder возвращает
 * [PartialImportResult] с `importedFiles.size == 7`, `rejectedFiles.size == 3`,
 * `errors.size == 3`, каждая ошибка обёрнута в [ParseError.WithFileContext]
 * с именем сбойного файла.
 *
 * Pure Kotlin, без Android — `./gradlew :app:testDebugUnitTest --tests *PartialImportBuilderTest`.
 *
 * @see <a href="../../../../../../../../../../docs/requirements/REQ-002-data-room/04-acceptance-criteria.md#AC-13">AC-13</a>
 */
class PartialImportBuilderTest {

    private val pack = LessonPack(
        packId = "italian_1",
        packVersion = "1.0.0",
        languageId = "it",
        importedAtMs = 1_700_000_000_000L,
        displayName = "Italian 1",
    )

    private fun lessonOutcome(
        name: String,
        result: ParseResult<List<SentenceCard>, ParseError>,
    ) = PartialImportBuilder.FileParseOutcome(
        fileName = name,
        contentType = PackContentParsers.ContentType.LESSON_CSV,
        result = result,
        recordCountProvider = { cards: List<SentenceCard> -> cards.size },
    )

    /** AC-13 etalon: 10 файлов, 3 повреждены → 7 imported, 3 rejected, 3 errors. */
    @Test
    fun `AC-13 ten files three broken yields 7 imported 3 rejected 3 errors`() {
        val cards = listOf(
            SentenceCard(id = "card_2", promptRu = "привет", acceptedAnswers = listOf("ciao")),
        )
        val outcomes = (1..10).map { idx ->
            val name = "lesson_$idx.csv"
            if (idx in setOf(3, 7)) {
                // Явно повреждённый файл — Failure (битый header / пустой).
                lessonOutcome(
                    name,
                    ParseResult.failure(
                        listOf(ParseError.InvalidFormat(lineNumber = 0, reason = "malformed header")),
                    ),
                )
            } else if (idx == 9) {
                // vocab_extra.csv — тоже повреждён (3-й сбойный файл AC-13).
                PartialImportBuilder.FileParseOutcome(
                    fileName = "vocab_extra.csv",
                    contentType = PackContentParsers.ContentType.VOCAB_CSV,
                    result = ParseResult.failure(
                        listOf(ParseError.EmptyFile(lineNumber = 0)),
                    ),
                    recordCountProvider = { _: List<VocabRow> -> 0 },
                )
            } else {
                lessonOutcome(name, ParseResult.success(cards))
            }
        }

        val partial = PartialImportBuilder.build(pack, outcomes)

        assertThat(partial.importedFiles).hasSize(7)
        assertThat(partial.rejectedFiles).hasSize(3)
        assertThat(partial.errors).hasSize(3)
        assertThat(partial.needsConfirmation).isTrue()

        // Каждая ошибка обёрнута в WithFileContext с именем сбойного файла (AC-13).
        val errorFileNames = partial.errors.mapNotNull { e ->
            (e as? ParseError.WithFileContext)?.fileName
        }
        assertThat(errorFileNames).containsExactly("lesson_3.csv", "lesson_7.csv", "vocab_extra.csv")

        // rejected-файлы совпадают по именам с ошибками.
        assertThat(partial.rejectedFiles.map { it.fileName })
            .containsExactly("lesson_3.csv", "lesson_7.csv", "vocab_extra.csv")
    }

    /** Все файлы валидны → 0 rejected, errors пуст, но imported заполнен. */
    @Test
    fun `all valid files yields zero rejected and zero errors`() {
        val cards = listOf(
            SentenceCard(id = "card_2", promptRu = "x", acceptedAnswers = listOf("y")),
        )
        val outcomes = (1..5).map { idx ->
            lessonOutcome("lesson_$idx.csv", ParseResult.success(cards))
        }

        val partial = PartialImportBuilder.build(pack, outcomes)

        assertThat(partial.importedFiles).hasSize(5)
        assertThat(partial.rejectedFiles).isEmpty()
        assertThat(partial.errors).isEmpty()
        assertThat(partial.needsConfirmation).isFalse()
        partial.importedFiles.forEach {
            assertThat(it.recordCount).isEqualTo(1)
        }
    }

    /** Partial-парсинг (часть строк упала) → файл остаётся imported + ошибки по строкам. */
    @Test
    fun `partial parse keeps file imported and records per-line errors`() {
        val valid = listOf(SentenceCard(id = "card_2", promptRu = "ok", acceptedAnswers = listOf("ok")))
        val lineErrors = listOf(
            ParseError.MalformedLine(lineNumber = 5, expected = "2 columns", actual = "1 column"),
            ParseError.MalformedLine(lineNumber = 8, expected = "2 columns", actual = "3 columns"),
        )
        val outcomes = listOf(
            lessonOutcome("lesson_1.csv", ParseResult.partial(valid, lineErrors)),
        )

        val partial = PartialImportBuilder.build(pack, outcomes)

        // Файл с partial-парсингом остаётся в imported (валидная часть записана).
        assertThat(partial.importedFiles).hasSize(1)
        assertThat(partial.importedFiles[0].fileName).isEqualTo("lesson_1.csv")
        assertThat(partial.importedFiles[0].recordCount).isEqualTo(1)
        // Не rejected (валидная часть сохранена).
        assertThat(partial.rejectedFiles).isEmpty()
        // Ошибки по битым строкам сохранены с контекстом файла.
        assertThat(partial.errors).hasSize(2)
        partial.errors.forEach { err ->
            assertThat(err).isInstanceOf(ParseError.WithFileContext::class.java)
            assertThat((err as ParseError.WithFileContext).fileName).isEqualTo("lesson_1.csv")
        }
    }

    /** Инвариант AC-13: errors.size == rejectedFiles.size — для Failure. */
    @Test
    fun `failure file produces exactly one error per rejected file`() {
        val outcomes = listOf(
            lessonOutcome(
                "broken.csv",
                ParseResult.failure(
                    listOf(ParseError.InvalidFormat(lineNumber = 0, reason = "bad")),
                ),
            ),
        )

        val partial = PartialImportBuilder.build(pack, outcomes)

        assertThat(partial.rejectedFiles).hasSize(1)
        assertThat(partial.errors).hasSize(1)
    }

    /** Пустой список outcomes → IllegalState (pack без файлов не должен попасть в partial-путь). */
    @Test(expected = IllegalStateException::class)
    fun `empty outcomes throws IllegalStateException`() {
        PartialImportBuilder.build(pack, emptyList())
    }

    /** recordCount отражает число валидных записей в payload. */
    @Test
    fun `record count reflects valid records in payload`() {
        val cards = (2..6).map { idx ->
            SentenceCard(id = "card_$idx", promptRu = "q$idx", acceptedAnswers = listOf("a$idx"))
        }
        val outcomes = listOf(
            lessonOutcome("lesson_1.csv", ParseResult.success(cards)),
        )

        val partial = PartialImportBuilder.build(pack, outcomes)

        assertThat(partial.importedFiles[0].recordCount).isEqualTo(5)
    }
}

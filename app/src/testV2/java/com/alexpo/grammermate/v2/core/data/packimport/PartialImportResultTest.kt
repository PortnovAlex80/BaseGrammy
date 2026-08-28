package com.alexpo.grammermate.v2.core.data.packimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit-тесты на модель [PartialImportResult] и сигналы [ImportConfirmation] (AC-13 — критичный).
 *
 * AC-13 etalon (regression-lock): UI (E13) получает [PartialImportResult] со списком
 * «что импортировано / что отклонено» и показывает confirm-диалог; ответ пользователя —
 * [ImportConfirmation.Proceed] («continue anyway») или [ImportConfirmation.Cancel] («cancel»).
 *
 * Pure Kotlin, без Android — `./gradlew :app:testDebugUnitTest --tests *PartialImportResultTest`.
 *
 * @see <a href="../../../../../../../../../../docs/requirements/REQ-002-data-room/04-acceptance-criteria.md#AC-13">AC-13</a>
 */
class PartialImportResultTest {

    private val pack = LessonPack("italian_1", "1.0.0", "it", 1_700_000_000_000L, "Italian 1")

    private fun imported(name: String, count: Int = 1) =
        ImportedFile(name, PackContentParsers.ContentType.LESSON_CSV, count)

    private fun rejected(name: String) =
        RejectedFile(name, PackContentParsers.ContentType.LESSON_CSV, ParseError.EmptyFile(0))

    private fun errorFor(name: String) =
        ParseError.WithFileContext(name, ParseError.EmptyFile(0))

    /** AC-13 инвариант: errors.size == rejectedFiles.size — соблюдается → конструируется. */
    @Test
    fun `AC-13 invariant errors size equals rejected size holds`() {
        val result = PartialImportResult(
            pack = pack,
            importedFiles = listOf(imported("lesson_1.csv")),
            rejectedFiles = listOf(rejected("lesson_3.csv"), rejected("lesson_7.csv"), rejected("vocab_extra.csv")),
            errors = listOf(errorFor("lesson_3.csv"), errorFor("lesson_7.csv"), errorFor("vocab_extra.csv")),
        )

        assertThat(result.rejectedFiles).hasSize(3)
        assertThat(result.errors).hasSize(3)
        assertThat(result.needsConfirmation).isTrue()
    }

    /** Инвариант нарушен (errors < rejected) → fail-fast при конструировании. */
    @Test(expected = IllegalStateException::class)
    fun `invariant violation errors fewer than rejected throws`() {
        PartialImportResult(
            pack = pack,
            importedFiles = emptyList(),
            rejectedFiles = listOf(rejected("a.csv"), rejected("b.csv")),
            errors = listOf(errorFor("a.csv")), // 1 < 2
        )
    }

    /**
     * Per-line partial-ошибки сверх rejected — это валидное состояние (>= инвариант):
     * imported-файл с partial-парсингом добавляет ошибки по битым строкам.
     */
    @Test
    fun `invariant allows per-line errors beyond rejected files`() {
        val result = PartialImportResult(
            pack = pack,
            importedFiles = listOf(imported("a.csv")),
            rejectedFiles = listOf(rejected("b.csv")),
            // 1 rejected-ошибка + 2 per-line ошибки внутри импортированного a.csv.
            errors = listOf(
                errorFor("b.csv"),
                ParseError.WithFileContext("a.csv", ParseError.MalformedLine(5, "2 columns", "1 column")),
                ParseError.WithFileContext("a.csv", ParseError.MalformedLine(8, "2 columns", "3 columns")),
            ),
        )

        assertThat(result.rejectedFiles).hasSize(1)
        assertThat(result.errors).hasSize(3) // 1 rejected + 2 per-line
    }

    /** Нулевые rejected и errors → needsConfirmation = false (диалог не нужен). */
    @Test
    fun `no rejected files means no confirmation needed`() {
        val result = PartialImportResult(
            pack = pack,
            importedFiles = listOf(imported("lesson_1.csv")),
            rejectedFiles = emptyList(),
            errors = emptyList(),
        )

        assertThat(result.needsConfirmation).isFalse()
    }

    /** summary() — читаемая сводка для логов. */
    @Test
    fun `summary contains pack id and counts`() {
        val result = PartialImportResult(
            pack = pack,
            importedFiles = listOf(imported("a.csv"), imported("b.csv")),
            rejectedFiles = listOf(rejected("c.csv")),
            errors = listOf(errorFor("c.csv")),
        )

        val s = result.summary()

        assertThat(s).contains("italian_1")
        assertThat(s).contains("imported=2")
        assertThat(s).contains("rejected=1")
    }

    /** ImportConfirmation.Proceed хранит packId + принятые rejected-имена. */
    @Test
    fun `proceed confirmation carries pack id and accepted names`() {
        val proceed = ImportConfirmation.Proceed("italian_1", listOf("lesson_3.csv"))

        assertThat(proceed.packId).isEqualTo("italian_1")
        assertThat(proceed.acceptedRejectedFileNames).containsExactly("lesson_3.csv")
    }

    /** ImportConfirmation.Cancel хранит packId. */
    @Test
    fun `cancel confirmation carries pack id`() {
        val cancel = ImportConfirmation.Cancel("italian_1")

        assertThat(cancel.packId).isEqualTo("italian_1")
    }

    /** ImportConfirmation — sealed exhaustive: ровно 2 варианта. */
    @Test
    fun `import confirmation is sealed with proceed and cancel`() {
        val confirmations: List<ImportConfirmation> = listOf(
            ImportConfirmation.Proceed("p1", emptyList()),
            ImportConfirmation.Cancel("p2"),
        )

        val labels = confirmations.map { c ->
            when (c) {
                is ImportConfirmation.Proceed -> "proceed"
                is ImportConfirmation.Cancel -> "cancel"
            }
        }

        assertThat(labels).containsExactly("proceed", "cancel")
    }
}

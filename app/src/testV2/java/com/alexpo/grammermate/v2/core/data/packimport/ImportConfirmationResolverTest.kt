package com.alexpo.grammermate.v2.core.data.packimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit-тесты на [ImportConfirmationResolver] (AC-13 — критичный).
 *
 * AC-13 etalon (regression-lock):
 * - «continue anyway» (Proceed) → сохранить валидные, отбросить сбойные.
 * - «cancel» (Cancel) → rollback всей транзакции, дельта = 0.
 *
 * Pure Kotlin, без Android — `./gradlew :app:testDebugUnitTest --tests *ImportConfirmationResolverTest`.
 *
 * @see <a href="../../../../../../../../../../docs/requirements/REQ-002-data-room/04-acceptance-criteria.md#AC-13">AC-13</a>
 */
class ImportConfirmationResolverTest {

    private val packId = "italian_1"

    private fun partial(rejectedNames: List<String>): PartialImportResult {
        val pack = LessonPack(packId, "1.0.0", "it", 1_700_000_000_000L)
        val imported = listOf(
            ImportedFile("lesson_1.csv", PackContentParsers.ContentType.LESSON_CSV, 5),
        )
        val rejected = rejectedNames.map {
            RejectedFile(it, PackContentParsers.ContentType.LESSON_CSV, ParseError.EmptyFile(0))
        }
        return PartialImportResult(
            pack = pack,
            importedFiles = imported,
            rejectedFiles = rejected,
            errors = rejected.map { ParseError.WithFileContext(it.fileName, ParseError.EmptyFile(0)) },
        )
    }

    /** AC-13 «continue anyway»: Proceed → CommitPartial с валидными файлами. */
    @Test
    fun `AC-13 proceed continues with valid files`() {
        val rejected = listOf("lesson_3.csv", "lesson_7.csv", "vocab_extra.csv")
        val p = partial(rejected)
        val proceed = ImportConfirmation.Proceed(packId, rejected)

        val resolution = ImportConfirmationResolver.resolve(p, proceed)

        assertThat(resolution).isInstanceOf(ImportConfirmationResolver.Resolution.CommitPartial::class.java)
        val commit = resolution as ImportConfirmationResolver.Resolution.CommitPartial
        assertThat(commit.filesToCommit).hasSize(1)
        assertThat(commit.filesToCommit[0].fileName).isEqualTo("lesson_1.csv")
    }

    /** AC-13 «cancel»: Cancel → Rollback, дельта = 0. */
    @Test
    fun `AC-13 cancel rolls back transaction`() {
        val p = partial(listOf("lesson_3.csv"))
        val cancel = ImportConfirmation.Cancel(packId)

        val resolution = ImportConfirmationResolver.resolve(p, cancel)

        assertThat(resolution).isEqualTo(ImportConfirmationResolver.Resolution.Rollback)
    }

    /** Stale-диалог: packId не совпадает → StaleDialog (no-op). */
    @Test
    fun `mismatched packId yields stale dialog resolution`() {
        val p = partial(listOf("lesson_3.csv"))
        val proceedForOtherPack = ImportConfirmation.Proceed("german_1", listOf("lesson_3.csv"))

        val resolution = ImportConfirmationResolver.resolve(p, proceedForOtherPack)

        assertThat(resolution).isEqualTo(ImportConfirmationResolver.Resolution.StaleDialog)
    }

    /** Proceed должен явно принять ВСЕ сбойные файлы, иначе RejectedNotAccepted. */
    @Test
    fun `proceed with incomplete accepted list yields rejected not accepted`() {
        val rejected = listOf("lesson_3.csv", "lesson_7.csv", "vocab_extra.csv")
        val p = partial(rejected)
        // Пользователь «принял» только 2 из 3 — UI должен повторить диалог.
        val partialProceed = ImportConfirmation.Proceed(packId, listOf("lesson_3.csv", "lesson_7.csv"))

        val resolution = ImportConfirmationResolver.resolve(p, partialProceed)

        assertThat(resolution).isEqualTo(ImportConfirmationResolver.Resolution.RejectedNotAccepted)
    }

    /** Proceed с лишним файлом в accepted (не из rejected) → также RejectedNotAccepted. */
    @Test
    fun `proceed with extra accepted file yields rejected not accepted`() {
        val rejected = listOf("lesson_3.csv")
        val p = partial(rejected)
        val proceedWithExtra = ImportConfirmation.Proceed(packId, listOf("lesson_3.csv", "lesson_99.csv"))

        val resolution = ImportConfirmationResolver.resolve(p, proceedWithExtra)

        assertThat(resolution).isEqualTo(ImportConfirmationResolver.Resolution.RejectedNotAccepted)
    }

    /** Proceed с пустым accepted при пустом rejected → CommitPartial (edge: ничего отбрасывать). */
    @Test
    fun `proceed with no rejected files commits`() {
        val p = partial(emptyList())
        val proceed = ImportConfirmation.Proceed(packId, emptyList())

        val resolution = ImportConfirmationResolver.resolve(p, proceed)

        assertThat(resolution).isInstanceOf(ImportConfirmationResolver.Resolution.CommitPartial::class.java)
    }

    /** Cancel при пустом rejected → Rollback (пользователь передумал). */
    @Test
    fun `cancel with no rejected files still rolls back`() {
        val p = partial(emptyList())
        val cancel = ImportConfirmation.Cancel(packId)

        val resolution = ImportConfirmationResolver.resolve(p, cancel)

        assertThat(resolution).isEqualTo(ImportConfirmationResolver.Resolution.Rollback)
    }
}

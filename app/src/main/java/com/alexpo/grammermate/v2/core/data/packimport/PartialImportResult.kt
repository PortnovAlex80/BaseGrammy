package com.alexpo.grammermate.v2.core.data.packimport

/**
 * Богатая доменная сводка partial-импорта пака (FR-8 / AC-13 — критичный).
 *
 * Дополняет [PackImportResult.Partial] структурированными списками «что импортировано»
 * и «что отклонено», чтобы UI (E13) мог построить confirm-диалог без повторного
 * разбора [PackImportResult.Partial.errors]. [PackImportResult.Partial] остаётся
 * минимальным сигнальным контрактом (pack + errors), а эта модель — расширенное
 * «тело» для презентационного слоя: ровно один объект на partial-импорт, готовый
 * к рендеру в диалоге «continue anyway / cancel».
 *
 * **Соответствие AC-13 (regression-lock формулировки):**
 * - Pack из 10 CSV-файлов, 3 повреждены → [PartialImportResult] содержит ровно 3
 *   записи в [rejectedFiles] и ровно 7 — в [importedFiles] (AC-13 etalon: 7 валидных,
 *   3 сбойных). Число [errors] == [rejectedFiles].size.
 * - «continue anyway» → [ImportConfirmation.Proceed] → в БД остаются 7 валидных,
 *   3 сбойных отбрасываются (см. [PackImporter] write-path внутри `withTransaction`).
 * - «cancel» → [ImportConfirmation.Cancel] → rollback всей транзакции, дельта = 0.
 *
 * **Координация с AC-3 (atomic):** partial — это **валидные подмножества**, а не
 * rollback всей транзакции. Полный сбой (невалидный manifest / битый ZIP / I/O)
 * даёт [PackImportResult.Failed] (0 сущностей), а не partial. Таким образом,
 * [PartialImportResult] существует только тогда, когда часть контента реально
 * записана в Room (FR-8 partial-success), и никогда — после rollback (AC-3).
 *
 * Pure Kotlin, без Android-зависимостей — тестируется на чистом JVM.
 *
 * @property pack доменная сводка импортированного пака.
 * @property importedFiles файлы, успешно распознанные и записанные (по одному
 *   элементу на файл пака — lesson CSV / verb CSV / vocab CSV / story JSON / bg-vocab CSV).
 * @property rejectedFiles файлы, отклонённые из-за ошибок парсинга (бита строка,
 *   пустой файл, неверный формат); ровно по одной записи на сбойный файл.
 * @property errors полный список ошибок (по одной на каждый сбойный файл, обёрнуты
 *   в [ParseError.WithFileContext] с именем файла для UI-диалога).
 * @see PartialImportBuilder
 * @see <a href="../../../../../../../../docs/requirements/REQ-002-data-room/04-acceptance-criteria.md#AC-13">AC-13</a>
 */
data class PartialImportResult(
    val pack: LessonPack,
    val importedFiles: List<ImportedFile>,
    val rejectedFiles: List<RejectedFile>,
    val errors: List<ParseError>,
) {
    init {
        // AC-13 инвариант: каждый отклонённый файл сопровождается ≥1 ошибкой
        // (errors.size >= rejectedFiles.size). Дополнительные ошибки сверх rejected
        // — это partial-парсинг (битые строки внутри импортированного файла,
        // см. [PartialImportBuilder]: imported-файл с partial-результатом добавляет
        // per-line ошибки в [errors], оставаясь в [importedFiles]).
        // Этот инвариант гарантирует, что UI confirm-диалог покажет «ровно N» ошибок
        // для N сбойных файлов целиком, и отдельно — per-line ошибки внутри валидных.
        check(errors.size >= rejectedFiles.size) {
            "AC-13 invariant violated: errors.size (${errors.size}) must be >= " +
                "rejectedFiles.size (${rejectedFiles.size}) for pack '${pack.packId}'"
        }
    }

    /**
     * Удобный предикат: есть ли хотя бы один отклонённый файл (диалог нужен).
     * По контракту AC-13 [PartialImportResult] создаётся только когда
     * `rejectedFiles.isNotEmpty()`, но предикат защищает от misuse.
     */
    val needsConfirmation: Boolean get() = rejectedFiles.isNotEmpty()

    /**
     * Сводка для логов / отладки (не для UI — UI использует [ImportConfirmation]).
     */
    fun summary(): String =
        "PartialImport(pack=${pack.packId}, imported=${importedFiles.size}, " +
            "rejected=${rejectedFiles.size})"
}

/**
 * Один успешно импортированный файл пака (запись в [PartialImportResult.importedFiles]).
 *
 * @property fileName имя файла относительно корня пака (например, `"lessons/A07.csv"`).
 * @property contentType тип контента (см. [PackContentParsers.ContentType]) —
 *   определяет, в какие entity-таблицы записан файл.
 * @property recordCount число записанных записей (карт / слов / историй).
 *   `-1` если счётчик неприменим (например, manifest).
 */
data class ImportedFile(
    val fileName: String,
    val contentType: PackContentParsers.ContentType,
    val recordCount: Int,
)

/**
 * Один отклонённый файл пака (запись в [PartialImportResult.rejectedFiles]).
 *
 * @property fileName имя файла относительно корня пака.
 * @property contentType тип контента, который пытались распарсить.
 * @property error ошибка, из-за которой файл отклонён (для UI — [ParseError.toUserMessage]).
 */
data class RejectedFile(
    val fileName: String,
    val contentType: PackContentParsers.ContentType,
    val error: ParseError,
)

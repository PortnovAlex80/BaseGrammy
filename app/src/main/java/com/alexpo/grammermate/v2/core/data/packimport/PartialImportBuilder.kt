package com.alexpo.grammermate.v2.core.data.packimport

/**
 * Pure-function агрегатор результатов парсинга файлов пака → [PartialImportResult]
 * (FR-8 / AC-13 — критичный).
 *
 * Это «сборка» богатой partial-сводки из per-file результатов парсеров: для каждого
 * файла пака [PackImporter] получает [ParseResult] (Success / Partial / Failure),
 * а этот builder классифицирует файл в [ImportedFile] или [RejectedFile] и собирает
 * итоговый [PartialImportResult] с инвариантом AC-13 (errors.size == rejected.size).
 *
 * **AC-13 etalon (regression-lock):** pack из 10 CSV-файлов, 3 повреждены →
 * builder возвращает [PartialImportResult] с `importedFiles.size == 7`,
 * `rejectedFiles.size == 3`, `errors.size == 3`.
 *
 * Файл считается **отклонённым** (rejected), если его [ParseResult] —
 * [ParseResult.Failure] (файл целиком не распознан: пустой / битый header).
 * Файл считается **принятым** (imported), если его результат —
 * [ParseResult.Success] (0 ошибок) или [ParseResult.Partial] (часть строк
 * распознана, часть упала — корректные строки записаны, упавшие как
 * `ParseError.WithFileContext` в [PartialImportResult.errors]). Таким образом,
 * файл с partial-парсингом **остаётся в importedFiles** (его валидная часть
 * записана в Room), но его упавшие строки добавляются в общий список ошибок.
 *
 * Это разделение точно соответствует AC-13 «часть CSV-файлов с ошибками»:
 * повреждённый **файл целиком** (Failure) → rejected; файл с **частью битых строк**
 * (Partial) → imported (валидная часть) + ошибки по битым строкам.
 *
 * Pure Kotlin (`object`), без Android-зависимостей — тестируется на чистом JVM.
 *
 * @see PartialImportResult
 * @see <a href="../../../../../../../../docs/requirements/REQ-002-data-room/04-acceptance-criteria.md#AC-13">AC-13</a>
 */
object PartialImportBuilder {

    /**
     * Одна запись per-file результата парсинга, поступающая в [build].
     *
     * [PackImporter] обходит extracted-каталог пака, для каждого файла применяет
     * нужный парсер (через [PackContentParsers]) и упаковывает результат в этот
     * [FileParseOutcome] — вместе с именем файла, типом контента и числом записей
     * (для imported-сводки).
     *
     * @property fileName имя файла относительно корня пака.
     * @property contentType тип контента файла (см. [PackContentParsers.ContentType]).
     * @property result результат парсера.
     * @property recordCountProvider вычисляет число валидных записей из
     *   `result.data` (Success/Partial) — для [ImportedFile.recordCount]. Для
     *   Failure не вызывается. Ленив, чтобы не материализовать payload дважды.
     */
    data class FileParseOutcome<T>(
        val fileName: String,
        val contentType: PackContentParsers.ContentType,
        val result: ParseResult<T, ParseError>,
        val recordCountProvider: (T) -> Int,
    )

    /**
     * Собрать [PartialImportResult] из списка per-file результатов парсинга.
     *
     * @param pack доменная сводка пака (imported partial всё ещё относится к паку).
     * @param outcomes per-file результаты парсинга (один элемент на файл пака).
     * @return [PartialImportResult] с imported/rejected/errors.
     * @throws IllegalStateException если outcomes пуст (pack без единого файла
     *   — это невалидное состояние, такой pack не должен дойти до partial-пути).
     */
    fun build(
        pack: LessonPack,
        outcomes: List<FileParseOutcome<*>>,
    ): PartialImportResult {
        check(outcomes.isNotEmpty()) {
            "AC-13: cannot build PartialImportResult for pack '${pack.packId}' " +
                "with zero file outcomes — use PackImportResult.Failed instead"
        }

        val imported = mutableListOf<ImportedFile>()
        val rejected = mutableListOf<RejectedFile>()
        val errors = mutableListOf<ParseError>()

        for (outcome in outcomes) {
            when (val result = outcome.result) {
                is ParseResult.Success<*, *> -> {
                    // Файл целиком распознан → imported, 0 ошибок.
                    imported += ImportedFile(
                        fileName = outcome.fileName,
                        contentType = outcome.contentType,
                        recordCount = countOf(outcome, result.data),
                    )
                }
                is ParseResult.Partial<*, *> -> {
                    // Часть строк распознана → валидная часть imported,
                    // упавшие строки → ошибки с контекстом имени файла (AC-13: WithFileContext).
                    imported += ImportedFile(
                        fileName = outcome.fileName,
                        contentType = outcome.contentType,
                        recordCount = countOf(outcome, result.data),
                    )
                    @Suppress("UNCHECKED_CAST")
                    errors += (result.errors as List<ParseError>).map { err ->
                        wrapWithContext(outcome.fileName, err)
                    }
                }
                is ParseResult.Failure<*, *> -> {
                    // Файл целиком не распознан → rejected, 1 ошибка с контекстом файла.
                    @Suppress("UNCHECKED_CAST")
                    val fileErrors = (result.errors as List<ParseError>).map { err ->
                        wrapWithContext(outcome.fileName, err)
                    }
                    // AC-13: 1 rejected-файл → ровно 1 сводная ошибка в errors
                    // (канон: по одной на сбойный файл). Если парсер вернул несколько,
                    // сводная — первая, остальные сохраняются в RejectedFile для аудита.
                    val canonical = fileErrors.firstOrNull()
                        ?: ParseError.WithFileContext(
                            outcome.fileName,
                            ParseError.InvalidFormat(reason = "Unrecognized file"),
                        )
                    rejected += RejectedFile(
                        fileName = outcome.fileName,
                        contentType = outcome.contentType,
                        error = canonical,
                    )
                    errors += canonical
                }
            }
        }

        return PartialImportResult(
            pack = pack,
            importedFiles = imported.toList(),
            rejectedFiles = rejected.toList(),
            errors = errors.toList(),
        )
    }

    /**
     * Безопасно посчитать recordCount через provider, обрабатывая несоответствие
     * типов (provider типизирован под исходный T, а data — после стирания `out T`).
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> countOf(
        outcome: FileParseOutcome<T>,
        data: Any?,
    ): Int = try {
        (outcome.recordCountProvider as (Any?) -> Int).invoke(data)
    } catch (cce: ClassCastException) {
        // Несоответствие типа payload'а и provider'а — возвращаем -1 (unknown),
        // не ломаем сборку partial-сводки (recordCount — вспомогательное поле для UI).
        -1
    }

    /**
     * Обернуть ошибку в [ParseError.WithFileContext], если она ещё не обёрнута —
     * чтобы confirm-диалог показал имя файла (AC-13: «с `fileName` в `WithFileContext`»).
     */
    private fun wrapWithContext(fileName: String, error: ParseError): ParseError =
        if (error is ParseError.WithFileContext) error
        else ParseError.WithFileContext(fileName, error)
}

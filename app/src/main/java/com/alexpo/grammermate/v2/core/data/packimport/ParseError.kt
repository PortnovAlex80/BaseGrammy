package com.alexpo.grammermate.v2.core.data.packimport

/**
 * Ошибка парсинга контента пака.
 *
 * Sealed-иерархия (regression-locked — SRS-002 FR-8, NFR-6). Переносится 1:1 из
 * legacy (`com.alexpo.grammermate.data.ParseError`) без изменения семантики,
 * но без `android.util.Log` и других Android-зависимостей.
 *
 * Варианты:
 * - [InvalidFormat]  — файл не соответствует ожидаемому контракту целиком
 *   (битый JSON, неверная структура manifest, `correctIndex` вне `options.indices`).
 * - [MalformedLine]  — отдельная строка CSV не распознана (не та число колонок).
 * - [EmptyFile]      — файл пуст / содержит только заголовок.
 * - [WithFileContext] — обёртка, добавляющая имя файла к любой другой ошибке
 *   (чтобы partial-import confirm показал, в каком файле упала строка).
 */
sealed interface ParseError {

    /**
     * Файл/запись не соответствует ожидаемому контракту целиком (битый JSON,
     * неверная структура manifest, `correctIndex` вне `options.indices`).
     *
     * @property lineNumber 1-based номер строки (если применимо), либо 0.
     * @property reason человекочитаемое описание проблемы.
     */
    data class InvalidFormat(val lineNumber: Int = 0, val reason: String) : ParseError

    /**
     * Отдельная строка CSV не распознана: фактическое число колонок не совпадает
     * с ожидаемым, либо обязательное поле пусто.
     *
     * @property lineNumber 1-based номер строки в файле (строка заголовка = 1).
     * @property expected что ожидали увидеть (например, `"2 columns"`).
     * @property actual что получили фактически (например, `"3 columns"`).
     */
    data class MalformedLine(
        val lineNumber: Int,
        val expected: String,
        val actual: String,
    ) : ParseError

    /**
     * Файл пуст либо содержит только заголовок без data-строк.
     *
     * @property lineNumber номер строки, на которой обнаружена пустота (0 если файл пуст целиком).
     */
    data class EmptyFile(val lineNumber: Int = 0) : ParseError

    /**
     * Обёртка, добавляющая контекст имени файла к любой другой ошибке — чтобы
     * partial-import confirm (FR-8 / AC-13) показал, в каком файле пака упала
     * конкретная строка.
     *
     * @property fileName имя файла относительно корня пака (например, `"lessons/A07.csv"`).
     * @property cause исходная ошибка.
     */
    data class WithFileContext(
        val fileName: String,
        val cause: ParseError,
    ) : ParseError

    /**
     * Человекочитаемое сообщение для UI (E13 partial-import confirm-диалог).
     */
    fun toUserMessage(): String = when (this) {
        is InvalidFormat -> if (lineNumber > 0) "Line $lineNumber: $reason" else reason
        is MalformedLine -> "Line $lineNumber: expected $expected, got $actual"
        is EmptyFile -> if (lineNumber > 0) "Empty content starting at line $lineNumber" else "Empty file"
        is WithFileContext -> "$fileName: ${cause.toUserMessage()}"
    }
}

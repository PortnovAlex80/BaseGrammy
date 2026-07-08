package com.alexpo.grammermate.v2.core.data.packimport

/**
 * Универсальный результат парсинга одного файла/потока контента.
 *
 * Sealed-иерархия success / partial / failure сохраняет legacy-семантику
 * (SRS-002 NFR-6 — regression-lock): partial возвращает **и** данные, **и** ошибки,
 * чтобы UI (E13) мог показать partial-import confirm-диалог (FR-8 / AC-13):
 * корректные карты сохранены, упавшие — нет.
 *
 * Не использует `data: T?` + `errors: List<E>` в одном классе намеренно —
 * sealed-варианты делают состояние результата явным и проверяемым exhaustively.
 *
 * @param T тип успешного payload (список карт, VocabRow, StoryQuiz и т. п.).
 * @param E тип ошибки (как правило [ParseError]).
 */
sealed interface ParseResult<out T, out E> {

    /**
     * Полный успех: данные распознаны, ошибок нет.
     *
     * @property data распознанный payload.
     */
    data class Success<T, E>(val data: T) : ParseResult<T, E>

    /**
     * Частичный успех: часть данных распознана, часть строк упала.
     * Возвращает **и** [data], **и** список [errors] — корректные карты
     * сохраняются, упавшие попадают в confirm-диалог (FR-8 / SM-3).
     *
     * @property data корректно распознанный payload (без упавших строк).
     * @property errors список ошибок по упавшим строкам/записям.
     */
    data class Partial<T, E>(val data: T, val errors: List<E>) : ParseResult<T, E>

    /**
     * Полная неудача: данных нет, есть только [errors] (например, пустой файл
     * или невалидный header).
     *
     * @property errors список ошибок.
     */
    data class Failure<T, E>(val errors: List<E>) : ParseResult<T, E>

    companion object {
        fun <T, E> success(data: T): ParseResult<T, E> = Success(data)
        fun <T, E> partial(data: T, errors: List<E>): ParseResult<T, E> = Partial(data, errors)
        fun <T, E> failure(errors: List<E>): ParseResult<T, E> = Failure(errors)
    }
}

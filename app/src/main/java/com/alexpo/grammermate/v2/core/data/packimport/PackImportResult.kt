package com.alexpo.grammermate.v2.core.data.packimport

/**
 * Результат импорта пака — public surface [PackImporter] (SRS-002 §5.1, FR-8).
 *
 * Sealed-иерархия отражает три исхода pack-import:
 * - [Success] — пак целиком распознан и записан в Room (0 ошибок).
 * - [Partial] — часть уроков/карт сохранена, часть упала; UI (E13) показывает
 *   partial-import confirm-диалог (FR-8 / AC-13): корректные карты в БД, упавшие — в списке.
 * - [Failed]  — ничего не сохранено (нет манифеста, битый ZIP, и т. п.).
 *
 * **Atomicity note (FR-7, NFR-1):** даже при [Partial] вся write-сторона
 * выполняется в одной `database.withTransaction { … }` — это не противоречит
 * rollback при сбое (SM-2), потому что [Partial] ≠ сбой: это преднамеренный
 * partial-success, где каждая упавшая CSV-строка пропускается (Partial-error),
 * а остальные карты записываются. Полный сбой (исключение вне partial-path)
 * даёт rollback → 0 сущностей → [Failed].
 *
 * @see <a href="../../../../../../../../docs/requirements/REQ-002-data-room/02-srs.md">SRS-002 §5.1 / FR-8</a>
 */
sealed interface PackImportResult {

    /**
     * Полный успех: пак записан в Room, 0 ошибок.
     *
     * @property pack доменная сводка импортированного пака.
     */
    data class Success(val pack: LessonPack) : PackImportResult

    /**
     * Частичный успех: корректные карты/уроки сохранены в Room, упавшие — в [errors].
     * UI (E13) показывает partial-import confirm-диалог со списком [errors]
     * (AC-13 — критичный).
     *
     * @property pack доменная сводка импортированного пака.
     * @property errors список ошибок по упавшим строкам/файлам (для confirm-диалога).
     */
    data class Partial(
        val pack: LessonPack,
        val errors: List<ParseError>,
    ) : PackImportResult

    /**
     * Полная неудача: ничего не сохранено. Причины: нет `manifest.json`,
     * невалидный manifest, битый ZIP, I/O ошибка. Список [errors] объясняет причину.
     *
     * @property ошибки список ошибок.
     */
    data class Failed(val errors: List<ParseError>) : PackImportResult
}

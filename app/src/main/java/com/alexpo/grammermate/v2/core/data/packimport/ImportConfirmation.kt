package com.alexpo.grammermate.v2.core.data.packimport

/**
 * Доменный сигнал подтверждения partial-импорта (FR-8 / AC-13 — критичный).
 *
 * Это «UI/showDialog механизм (или доменный сигнал)» из DoD AC-13: sealed-действие
 * пользователя в ответ на confirm-диалог, показанный по [PartialImportResult].
 * Data-слой возвращает [PackImportResult.Partial] (сигнал «диалог нужен»), UI
 * (E13) рендерит его и возвращает [ImportConfirmation] обратно в [PackImporter]
 * для финализации транзакции:
 *
 * - [Proceed] («continue anyway») → сохранить 7 валидных файлов, 3 сбойных
 *   отбросить; дельта сущностей > 0.
 * - [Cancel] («cancel») → rollback всей транзакции; дельта сущностей = 0
 *   (тот же исход, что и AC-3 Failed, но инициированный пользователем).
 *
 * Sealed-иерархия делает `when` exhaustive на стороне [PackImporter]: невозможно
 * «забыть» обработать одну из веток. Pure Kotlin, без Android-зависимостей.
 *
 * @see PartialImportResult
 * @see <a href="../../../../../../../../docs/requirements/REQ-002-data-room/04-acceptance-criteria.md#AC-13">AC-13</a>
 */
sealed interface ImportConfirmation {

    /**
     * Общий payload — для какой модели (какого импорта) дан ответ.
     * Привязка к конкретному [PartialImportResult] через [PartialImportResult.pack]
     * проверяется на стороне [PackImporter] (packId match), чтобы не «ответить»
     * на устаревший диалог.
     */
    val packId: String

    /**
     * «Continue anyway»: пользователь подтверждает сохранение валидных файлов,
     * отбрасывая сбойные. AC-13: в БД остаются N валидных, M сбойных отброшены.
     *
     * @property packId ID пака, к которому относится подтверждение.
     * @property acceptedRejectedFileNames имена сбойных файлов, которые пользователь
     *   явным образом согласился отбросить (для аудита/логирования). По AC-13 это
     *   ровно [PartialImportResult.rejectedFiles] (все), но список оставлен явно,
     *   чтобы UI не мог «молча» пропустить часть.
     */
    data class Proceed(
        override val packId: String,
        val acceptedRejectedFileNames: List<String>,
    ) : ImportConfirmation

    /**
     * «Cancel»: пользователь отменяет partial-импорт → rollback всей транзакции,
     * дельта сущностей = 0. AC-13: «cancel: rollback всей транзакции, дельта = 0».
     *
     * @property packId ID пака, к которому относится отмена.
     */
    data class Cancel(
        override val packId: String,
    ) : ImportConfirmation
}

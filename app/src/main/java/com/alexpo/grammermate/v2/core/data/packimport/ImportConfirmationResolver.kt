package com.alexpo.grammermate.v2.core.data.packimport

/**
 * Pure-function resolver: применяет [ImportConfirmation] к [PartialImportResult] →
 * финальное решение транзакции (FR-8 / AC-13 — критичный).
 *
 * Это мост между доменным сигналом [ImportConfirmation] (ответ пользователя в
 * confirm-диалоге) и write-стороной [PackImporter]. Resolver **не трогает Room** —
 * он только вычисляет, какие файлы должны быть сохранены и должен ли произойти
 * rollback. Сама транзакция (`database.withTransaction { ... }` / rollback)
 * выполняется в [PackImporter.importPackAtomic] (AC-3, #471) на основании решения
 * этого resolver'а. Разделение чистое: resolver — pure логика решения,
 * [PackImporter] — Room-write побочный эффект.
 *
 * **AC-13 контракты (regression-lock):**
 * - [ImportConfirmation.Proceed] → [Resolution.CommitPartial]: сохранить файлы из
 *   [PartialImportResult.importedFiles], отбросить [rejectedFiles]. Дельта > 0.
 * - [ImportConfirmation.Cancel] → [Resolution.Rollback]: дельта сущностей = 0.
 * - Несовпадение `packId` (ответ на устаревший диалог) → [Resolution.StaleDialog]:
 *   [PackImporter] игнорирует ответ (no-op), ждёт свежий. Защита от race-condition,
 *   когда пользователь переоткрыл импорт, но ответил на старый диалог.
 * - [Proceed] с неполным списком принятых rejected-файлов → [Resolution.RejectedNotAccepted]:
 *   UI обязан явно принять **все** сбойные файлы (AC-13: «continue anyway» = принять
 *   потерю всех N). Это не rollback, но сигнал UI повторить диалог корректно.
 *
 * Pure Kotlin (`object`), без Android-зависимостей — тестируется на чистом JVM.
 *
 * @see PartialImportResult
 * @see ImportConfirmation
 * @see <a href="../../../../../../../../docs/requirements/REQ-002-data-room/04-acceptance-criteria.md#AC-13">AC-13</a>
 */
object ImportConfirmationResolver {

    /**
     * Финальное решение транзакции по [ImportConfirmation].
     *
     * [PackImporter] сопоставляет [Resolution] → Room-действие:
     * - [CommitPartial] → `database.withTransaction { write(importedFiles) }`.
     * - [Rollback] → откатить текущую транзакцию (AC-3 rollback path).
     * - [StaleDialog] / [RejectedNotAccepted] → no-op, ожидать корректный ввод.
     */
    sealed interface Resolution {
        /**
         * Сохранить валидные файлы, отбросить сбойные (AC-13 «continue anyway»).
         *
         * @property filesToCommit файлы для записи (из [PartialImportResult.importedFiles]).
         */
        data class CommitPartial(val filesToCommit: List<ImportedFile>) : Resolution

        /**
         * Откатить транзакцию целиком, дельта = 0 (AC-13 «cancel»).
         */
        object Rollback : Resolution

        /**
         * Ответ относится к другому packId — устаревший диалог, игнорировать.
         */
        object StaleDialog : Resolution

        /**
         * Proceed не покрывает все сбойные файлы — UI должен явно принять потерю всех.
         */
        object RejectedNotAccepted : Resolution
    }

    /**
     * Разрешить [confirmation] в контексте [partial].
     *
     * @param partial исходная partial-сводка (для которой показан диалог).
     * @param confirmation ответ пользователя.
     * @return [Resolution] — действие для [PackImporter].
     */
    fun resolve(
        partial: PartialImportResult,
        confirmation: ImportConfirmation,
    ): Resolution {
        // 1. Защита от устаревшего диалога — packId должен совпадать.
        if (confirmation.packId != partial.pack.packId) {
            return Resolution.StaleDialog
        }

        return when (confirmation) {
            is ImportConfirmation.Cancel -> Resolution.Rollback

            is ImportConfirmation.Proceed -> {
                // 2. UI обязан явно принять ВСЕ сбойные файлы (AC-13 «continue anyway»
                //    = согласие потерять все N повреждённых файлов).
                val rejectedNames = partial.rejectedFiles.map { it.fileName }.toSet()
                val acceptedNames = confirmation.acceptedRejectedFileNames.toSet()
                if (!rejectedNames.equals(acceptedNames)) {
                    return Resolution.RejectedNotAccepted
                }
                // 3. Commit: сохранить валидные, отбросить сбойные.
                Resolution.CommitPartial(partial.importedFiles)
            }
        }
    }
}

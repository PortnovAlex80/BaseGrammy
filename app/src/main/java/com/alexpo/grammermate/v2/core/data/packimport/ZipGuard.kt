package com.alexpo.grammermate.v2.core.data.packimport

import java.io.File
import java.nio.file.Path

/**
 * Защита распаковки ZIP (фикс аудита H-2, AC-2).
 *
 * 1. Path-traversal: посегментная проверка [Path.startsWith] вместо строковой
 *    `canonicalTarget.startsWith(parent + separator)` — строковый вариант
 *    уязвим к sibling-каталогам/symlink-краям; Path-сравнение устойчиво.
 * 2. ZIP-bomb: бюджеты на число entries и суммарный распакованный размер —
 *    до фикса контракт не имел лимитов вовсе.
 */
object ZipGuard {

    /** Максимум entries в паковом ZIP. Реальный потолок контента: флагманский
     * bundled ITALIAN_EXPRESS_SHORT — 8 949 entries (аудит 2026-08-26: лимит
     * 5 000 ломал clean install); 20 000 = 2.2x запас с защитой от бомбы. */
    const val MAX_ENTRIES = 20_000

    /** Максимум суммарного распакованного размера (512 МБ; opus-паки большие). */
    const val MAX_TOTAL_BYTES = 512L * 1024 * 1024

    /** target находится ВНУТРИ dir (посегментно, по canonical-путям). */
    fun isInsideDir(target: File, dir: File): Boolean =
        target.canonicalFile.toPath().startsWith(dir.canonicalFile.toPath())

    /** Бюджет распаковки: entries + суммарные байты. `false` = превышен. */
    class ZipBudget(
        private val maxEntries: Int = MAX_ENTRIES,
        private val maxTotalBytes: Long = MAX_TOTAL_BYTES,
    ) {
        private var entries = 0
        private var bytes = 0L

        /** Засчитать новый entry; false — лимит превышен. */
        fun onEntry(): Boolean {
            entries += 1
            return entries <= maxEntries
        }

        /** Засчитать распакованные байты; false — лимит превышен. */
        fun onBytes(count: Long): Boolean {
            bytes += count
            return bytes <= maxTotalBytes
        }
    }
}

package com.alexpo.grammermate.v2.core.data.packimport

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.zip.ZipInputStream
import org.junit.Test

/**
 * Регрессия clean-install (аудит 2026-08-26): КАЖДЫЙ bundled-пак обязан
 * проходить бюджеты ZipGuard — лимит 5 000 entries ломал импорт
 * ITALIAN_EXPRESS_SHORT (8 949 entries), а синтетические фикстуры этого не
 * видели. Читаем РЕАЛЬНЫЕ ZIP из src/main/assets напрямую (файловый стрим —
 * Robolectric-Asset слой буферизует весь 77МБ пакет в heap и падает OOM).
 */
class ZipGuardBundledPacksTest {

    private fun packsDir(): File = File(System.getProperty("user.dir"))
        .resolve("src/main/assets/grammarmate/packs")

    private fun forEachBundledPack(block: (File, Int, Long) -> Unit) {
        val zips = packsDir().listFiles { f -> f.extension == "zip" }.orEmpty()
        assertThat(zips).isNotEmpty()
        zips.forEach { zipFile ->
            var entries = 0
            var uncompressed = 0L
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    entries += 1
                    uncompressed += entry.size
                    zip.closeEntry()
                }
            }
            block(zipFile, entries, uncompressed)
        }
    }

    @Test
    fun `every bundled pack fits ZipGuard budgets`() {
        forEachBundledPack { zipFile, entries, uncompressed ->
            val budget = ZipGuard.ZipBudget()
            val entriesOk = (1..entries).all { budget.onEntry() }
            val bytesOk = budget.onBytes(uncompressed)

            assertThat(entriesOk).isTrue()
            assertThat(bytesOk).isTrue()
        }
    }

    @Test
    fun `flagship italian pack exceeds old limit and fits new one`() {
        val zipFile = File(packsDir(), "ITALIAN_EXPRESS_SHORT.zip")
        assertThat(zipFile.isFile).isTrue()
        var entries = 0
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            generateSequence { zip.nextEntry }.onEach { zip.closeEntry() }.forEach { _ -> entries += 1 }
        }

        // Прежний лимит 5 000 ломал clean install — ловим регрессию лимита.
        assertThat(entries).isGreaterThan(5_000)
        assertThat(entries).isLessThan(ZipGuard.MAX_ENTRIES)
    }
}

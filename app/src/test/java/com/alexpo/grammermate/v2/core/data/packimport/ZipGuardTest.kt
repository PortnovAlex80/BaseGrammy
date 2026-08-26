package com.alexpo.grammermate.v2.core.data.packimport

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Test

/**
 * ZipGuard (фикс аудита H-2): Path-посегментная проверка против строковой
 * (sibling-каталог не проходит даже когда его путь строково начинается с
 * пути каталога) + бюджеты entries/bytes против ZIP-bomb.
 */
class ZipGuardTest {

    @Test
    fun `nested file inside dir is accepted`() {
        createTempDirectory("zipguard").toFile().useDir { dir ->
            val nested = File(dir, "lessons/lesson_01.csv")

            assertThat(ZipGuard.isInsideDir(nested, dir)).isTrue()
        }
    }

    @Test
    fun `sibling directory is rejected even with string-prefix match`() {
        createTempDirectory("zipguard").toFile().useDir { dir ->
            // Сиблинг, чей путь СТРОКОВО начинается с имени dir без разделителя:
            // dir=".../pack", sibling=".../pack-evil" — старый строковый check
            // с separator-суффиксом такие отсекал, но вариации (без sep) — нет;
            // Path-семантика устойчива в принципе.
            val sibling = File(dir.parentFile, dir.name + "-evil")
            val target = File(sibling, "x.txt")

            assertThat(ZipGuard.isInsideDir(target, dir)).isFalse()
            // Сам каталог — внутри себя (граничный случай).
            assertThat(ZipGuard.isInsideDir(dir, dir)).isTrue()
        }
    }

    @Test
    fun `traversal entry names resolve outside and are rejected`() {
        createTempDirectory("zipguard").toFile().useDir { dir ->
            val escape = File(dir, "../../etc/passwd")

            assertThat(ZipGuard.isInsideDir(escape, dir)).isFalse()
        }
    }

    @Test
    fun `zip budget caps entries and bytes`() {
        val entries = ZipGuard.ZipBudget(maxEntries = 2, maxTotalBytes = 10)

        assertThat(entries.onEntry()).isTrue()
        assertThat(entries.onEntry()).isTrue()
        assertThat(entries.onEntry()).isFalse() // третий — превышение

        val bytes = ZipGuard.ZipBudget(maxEntries = 10, maxTotalBytes = 10)
        assertThat(bytes.onBytes(7)).isTrue()
        assertThat(bytes.onBytes(3)).isTrue()
        assertThat(bytes.onBytes(1)).isFalse() // 11-й байт — превышение
    }

    /** Авто-удаление временного каталога после блока. */
    private fun <T> File.useDir(block: (File) -> T): T = try {
        block(this)
    } finally {
        deleteRecursively()
    }
}

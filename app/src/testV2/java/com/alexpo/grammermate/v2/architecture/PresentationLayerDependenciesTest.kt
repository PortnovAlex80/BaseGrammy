package com.alexpo.grammermate.v2.architecture

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * Dependency-check Фазы 7 плана стабилизации: presentation (каталоги feature
 * и ui) НЕ импортирует data-слой (v2.core.data) — только доменные порты
 * (com.alexpo.grammermate.domain) и core.ui. Граница, доказанная grep'ом
 * при вводе теста, теперь исполняется автоматически.
 */
class PresentationLayerDependenciesTest {

    private val appRoot =
        File(System.getProperty("user.dir")).resolve("src/main/java/com/alexpo/grammermate/v2")

    private fun kotlinFiles(vararg dirs: String): List<File> =
        dirs.flatMap { dir -> appRoot.resolve(dir).walkTopDown().filter { it.extension == "kt" }.toList() }

    @Test
    fun `presentation layer never imports data layer`() {
        val violators = kotlinFiles("feature", "ui")
            .filter { file ->
                file.readLines().any { it.startsWith("import com.alexpo.grammermate.v2.core.data") }
            }
            .map { it.relativeTo(appRoot).path }

        assertThat(violators).isEmpty()
    }

    @Test
    fun `presentation layer never imports room or hilt data internals`() {
        val forbidden = listOf("androidx.room", "android.database.sqlite")
        val violators = kotlinFiles("feature", "ui")
            .filter { file ->
                file.readLines().any { line -> forbidden.any { line.startsWith("import $it") } }
            }
            .map { it.relativeTo(appRoot).path }

        assertThat(violators).isEmpty()
    }
}

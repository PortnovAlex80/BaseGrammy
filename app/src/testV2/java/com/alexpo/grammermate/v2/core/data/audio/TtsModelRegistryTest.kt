package com.alexpo.grammermate.v2.core.data.audio

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit-тесты на **AC-11 — isTtsAvailable(lang): per-language manifest-проверка**.
 *
 * AC-11 etalon (`docs/requirements/REQ-003-audio/03-acceptance-criteria.md#AC-11`):
 *   Given: `TtsModelRegistry.specFor("en")` декларирует `requiredFiles=
 *   ["en_US-amy-low.onnx","tokens.txt"]`, `requiredDirs=["espeak-ng-data"]`;
 *   каталог `filesDir/tts/en_US/`... содержит все требуемые файлы (ненулевой размер).
 *   When:  вызывается `isTtsAvailable(languageId="en")`.
 *   Then:  возвращает `true`. Если удалить `tokens.txt` → `false`.
 *          Если `specFor(lang) == null` (язык не в реестре) → `false`.
 *
 * Тестируется чистая manifest-логика [TtsModelRegistry] на temp-dir fixture
 * (без Robolectric / Android Context) — это и есть слой, на который делегирует
 * `SherpaAudioRepository.isTtsAvailable`. FR-8, derived_from UC-1/UC-8.
 *
 * @see TtsModelRegistry
 */
class TtsModelRegistryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val filesDir: java.io.File by lazy { tmp.newFolder("files") }

    /** Полностью создаёт каталог модели для [lang] со всеми requiredFiles/Dirs (ненулевые). */
    private fun materializeModel(lang: String) {
        val spec = TtsModelRegistry.specFor(lang)!!
        val dir = TtsModelRegistry.modelDir(filesDir, spec)
        check(dir.mkdirs()) { "cannot create ${dir.absolutePath}" }
        for (file in spec.requiredFiles) {
            java.io.File(dir, file).writeText("model-blob-${lang}")
        }
        for (sub in spec.requiredDirs) {
            val d = java.io.File(dir, sub)
            check(d.mkdirs()) { "cannot create ${d.absolutePath}" }
            // requiredDir должен быть непустым (espeak-ng-data содержит файлы).
            java.io.File(d, "_placeholder").writeText("data")
        }
    }

    // ── AC-11 etalon: en, полный манифест → true ──────────────────────────

    @Test
    fun `AC-11 etalon — full en manifest returns true`() {
        materializeModel("en")
        assertThat(TtsModelRegistry.isAvailable(filesDir, "en")).isTrue()
    }

    @Test
    fun `AC-11 specFor en declares etalon requiredFiles and dirs`() {
        val spec = TtsModelRegistry.specFor("en")
        assertThat(spec).isNotNull()
        assertThat(spec!!.requiredFiles)
            .containsExactly("en_US-amy-low.onnx", "tokens.txt")
        assertThat(spec.requiredDirs).containsExactly("espeak-ng-data")
        assertThat(spec.modelDirName).isEqualTo("vits-piper-en_US-amy-low")
    }

    // ── AC-11 etalon: удалить tokens.txt → false ──────────────────────────

    @Test
    fun `AC-11 etalon — deleting tokens_txt for loaded lang returns false`() {
        materializeModel("en")
        val spec = TtsModelRegistry.specFor("en")!!
        val tokens = java.io.File(TtsModelRegistry.modelDir(filesDir, spec), "tokens.txt")
        check(tokens.delete()) { "cannot delete tokens.txt" }
        assertThat(TtsModelRegistry.isAvailable(filesDir, "en")).isFalse()
    }

    @Test
    fun `AC-11 — zero-length required file treated as missing`() {
        materializeModel("en")
        val spec = TtsModelRegistry.specFor("en")!!
        val modelFile = java.io.File(
            TtsModelRegistry.modelDir(filesDir, spec),
            spec.requiredFiles.first(),
        )
        modelFile.writeText("") // length == 0
        assertThat(TtsModelRegistry.isAvailable(filesDir, "en")).isFalse()
    }

    @Test
    fun `AC-11 — missing requiredDir (espeak-ng-data) returns false`() {
        materializeModel("en")
        val spec = TtsModelRegistry.specFor("en")!!
        val espeak = java.io.File(TtsModelRegistry.modelDir(filesDir, spec), "espeak-ng-data")
        espeak.deleteRecursively()
        assertThat(TtsModelRegistry.isAvailable(filesDir, "en")).isFalse()
    }

    @Test
    fun `AC-11 — empty requiredDir (no files inside) returns false`() {
        materializeModel("en")
        val spec = TtsModelRegistry.specFor("en")!!
        val espeak = java.io.File(TtsModelRegistry.modelDir(filesDir, spec), "espeak-ng-data")
        espeak.listFiles()?.forEach { it.delete() } // dir exists but empty
        assertThat(TtsModelRegistry.isAvailable(filesDir, "en")).isFalse()
    }

    // ── AC-11 etalon: specFor(null) → false (язык не в реестре) ───────────

    @Test
    fun `AC-11 etalon — unknown lang returns false`() {
        assertThat(TtsModelRegistry.isAvailable(filesDir, "xx")).isFalse()
    }

    @Test
    fun `AC-11 — specFor unknown lang is null`() {
        assertThat(TtsModelRegistry.specFor("xx")).isNull()
    }

    @Test
    fun `AC-11 — no tts dir at all returns false for known lang`() {
        // nothing materialized
        assertThat(TtsModelRegistry.isAvailable(filesDir, "en")).isFalse()
    }

    // ── per-language: каждый язык из реестра проверяется по своему манифесту ──

    @Test
    fun `AC-11 — per-language isolation, each known lang true independently`() {
        materializeModel("it")
        assertThat(TtsModelRegistry.isAvailable(filesDir, "it")).isTrue()
        // ru не загружен → false (per-language, не глобально):
        assertThat(TtsModelRegistry.isAvailable(filesDir, "ru")).isFalse()
        // en тоже не загружен → false:
        assertThat(TtsModelRegistry.isAvailable(filesDir, "en")).isFalse()
    }

    @Test
    fun `AC-11 — per-language modelDirName differs across langs`() {
        // Реестр per-language: каждый язык имеет свой modelDirName (legacy контракт).
        val dirNames = TtsModelRegistry.models.values.map { it.modelDirName }
        assertThat(dirNames.toSet()).hasSize(dirNames.size) // все уникальны
    }

    @Test
    fun `AC-11 — all six registered langs round-trip true after materialize`() {
        for (lang in TtsModelRegistry.models.keys) {
            materializeModel(lang)
            assertWithMessage("lang=$lang")
                .that(TtsModelRegistry.isAvailable(filesDir, lang))
                .isTrue()
        }
    }

    @Test
    fun `AC-11 — wrong-lang model dir present does not satisfy another lang`() {
        // en загружен, ru — нет; наличие en-каталога не делает ru доступным.
        materializeModel("en")
        assertThat(TtsModelRegistry.isAvailable(filesDir, "ru")).isFalse()
    }
}

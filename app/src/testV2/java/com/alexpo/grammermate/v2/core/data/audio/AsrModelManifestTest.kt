package com.alexpo.grammermate.v2.core.data.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit-тесты на **AC-12 (regression-lock) — isAsrAvailable(): манифест Whisper + VAD**.
 *
 * AC-12 etalon (`docs/requirements/REQ-003-audio/03-acceptance-criteria.md#AC-12`):
 *   Given: skeleton уже реализует isAsrAvailable() (проверка `asr/whisper-small/`
 *          с ASR_REQUIRED_FILES + `asr/vad/silero_vad.onnx`, ненулевой размер).
 *   When:  regression-test.
 *   Then:  поведение сохранено: полный манифест → `true`; удалить
 *          `silero_vad.onnx` → `false`; удалить `small-decoder.int8.onnx` → `false`.
 *
 * Regression-lock замораживает контракт готовой skeleton-проверки (legacy
 * `AsrModelRegistry.isReady()`): Whisper AND VAD presence. Тестируется чистая
 * manifest-логика [AsrModelManifest] на temp-dir fixture (без Robolectric).
 * FR-9, derived_from UC-2 (precondition).
 *
 * @see AsrModelManifest
 */
class AsrModelManifestTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val filesDir: java.io.File by lazy { tmp.newFolder("files") }

    /** Полностью создаёт ASR-манифест: whisper-small файлы + vad/silero_vad.onnx (ненулевые). */
    private fun materializeFullAsr() {
        val whisperDir = AsrModelManifest.whisperDir(filesDir).apply { mkdirs() }
        for (f in AsrModelManifest.whisperRequiredFiles) {
            java.io.File(whisperDir, f).writeText("blob-$f")
        }
        val vadDir = AsrModelManifest.vadDir(filesDir).apply { mkdirs() }
        java.io.File(vadDir, AsrModelManifest.VAD_FILE_NAME).writeText("vad-blob")
    }

    // ── AC-12 etalon: полный манифест → true ─────────────────────────────

    @Test
    fun `AC-12 etalon — full Whisper+VAD manifest returns true`() {
        materializeFullAsr()
        assertThat(AsrModelManifest.isAvailable(filesDir)).isTrue()
    }

    @Test
    fun `AC-12 etalon — Whisper alone (no VAD) returns false`() {
        materializeFullAsr()
        val vad = java.io.File(AsrModelManifest.vadDir(filesDir), AsrModelManifest.VAD_FILE_NAME)
        check(vad.delete()) { "cannot delete silero_vad.onnx" }
        assertThat(AsrModelManifest.isAvailable(filesDir)).isFalse()
    }

    @Test
    fun `AC-12 etalon — VAD alone (no Whisper) returns false`() {
        materializeFullAsr()
        val whisperDir = AsrModelManifest.whisperDir(filesDir)
        whisperDir.deleteRecursively()
        assertThat(AsrModelManifest.isAvailable(filesDir)).isFalse()
    }

    // ── AC-12 etalon: удалить silero_vad.onnx → false ────────────────────

    @Test
    fun `AC-12 etalon — deleting silero_vad onnx returns false`() {
        materializeFullAsr()
        val vad = java.io.File(AsrModelManifest.vadDir(filesDir), AsrModelManifest.VAD_FILE_NAME)
        check(vad.delete())
        // Whisper ещё на месте, но VAD нет → false (Whisper AND VAD):
        assertThat(AsrModelManifest.isWhisperReady(filesDir)).isTrue()
        assertThat(AsrModelManifest.isVadReady(filesDir)).isFalse()
        assertThat(AsrModelManifest.isAvailable(filesDir)).isFalse()
    }

    // ── AC-12 etalon: удалить small-decoder.int8.onnx → false ────────────

    @Test
    fun `AC-12 etalon — deleting small-decoder int8 onnx returns false`() {
        materializeFullAsr()
        val decoder = java.io.File(
            AsrModelManifest.whisperDir(filesDir),
            "small-decoder.int8.onnx",
        )
        check(decoder.delete())
        assertThat(AsrModelManifest.isWhisperReady(filesDir)).isFalse()
        assertThat(AsrModelManifest.isAvailable(filesDir)).isFalse()
    }

    // ── Доп. edge-cases (snapshot текущей логики, как требует AC-12) ──────

    @Test
    fun `AC-12 — zero-length whisper file treated as missing`() {
        materializeFullAsr()
        java.io.File(
            AsrModelManifest.whisperDir(filesDir),
            "small-encoder.int8.onnx",
        ).writeText("")
        assertThat(AsrModelManifest.isAvailable(filesDir)).isFalse()
    }

    @Test
    fun `AC-12 — zero-length VAD file treated as missing`() {
        materializeFullAsr()
        java.io.File(
            AsrModelManifest.vadDir(filesDir),
            AsrModelManifest.VAD_FILE_NAME,
        ).writeText("")
        assertThat(AsrModelManifest.isAvailable(filesDir)).isFalse()
    }

    @Test
    fun `AC-12 — nothing on disk returns false`() {
        assertThat(AsrModelManifest.isAvailable(filesDir)).isFalse()
    }

    @Test
    fun `AC-12 — whisperRequiredFiles matches legacy AsrModelRegistry defaultModel`() {
        // Regression snapshot: три файла Whisper Small (legacy контракт).
        assertThat(AsrModelManifest.whisperRequiredFiles).containsExactly(
            "small-encoder.int8.onnx",
            "small-decoder.int8.onnx",
            "small-tokens.txt",
        ).inOrder()
        assertThat(AsrModelManifest.VAD_FILE_NAME).isEqualTo("silero_vad.onnx")
    }

    @Test
    fun `AC-12 — deleting small-tokens txt returns false`() {
        materializeFullAsr()
        check(
            java.io.File(AsrModelManifest.whisperDir(filesDir), "small-tokens.txt").delete()
        )
        assertThat(AsrModelManifest.isAvailable(filesDir)).isFalse()
    }
}

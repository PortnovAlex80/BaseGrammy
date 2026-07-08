package com.alexpo.grammermate.v2.core.data.audio

import java.io.File

/**
 * **AC-12 (regression-lock) — манифест ASR-модели Whisper + Silero VAD.**
 *
 * ASR использует одну multilingual-модель (Whisper Small) для всех языков,
 * плюс обязательный VAD (Silero) для endpointing. Поэтому манифест стабилен
 * и не зависит от языка (в отличие от [TtsModelRegistry], который per-language).
 *
 * **Regression-lock.** Skeleton-адаптер уже реализовал эту проверку (E03 task #467);
 * AC-12 замораживает её контракт: полный манифест → `true`; удалить
 * `silero_vad.onnx` → `false`; удалить `small-decoder.int8.onnx` → `false`.
 * Соответствует legacy `data/AsrModelRegistry.isReady()` (Whisper files AND VAD).
 *
 * Чистый Kotlin (`java.io.File`) — unit-тестируется на temp-dir без Robolectric.
 */
object AsrModelManifest {

    /** Корневой каталог ASR-моделей: `filesDir/asr/`. */
    fun asrRoot(filesDir: File): File = File(filesDir, "asr")

    /** Каталог Whisper Small: `filesDir/asr/whisper-small/` (legacy AsrModelRegistry.defaultModel). */
    fun whisperDir(filesDir: File): File = File(asrRoot(filesDir), "whisper-small")

    /** Каталог VAD: `filesDir/asr/vad/` (legacy AsrModelRegistry.VAD_DIR_NAME). */
    fun vadDir(filesDir: File): File = File(asrRoot(filesDir), "vad")

    /**
     * Обязательные файлы Whisper Small (legacy `AsrModelRegistry.defaultModel.requiredFiles`).
     * AC-12 etalon: удаление `small-decoder.int8.onnx` → `false`.
     */
    val whisperRequiredFiles: List<String> =
        listOf("small-encoder.int8.onnx", "small-decoder.int8.onnx", "small-tokens.txt")

    /** Имя VAD-файла (legacy `AsrModelRegistry.VAD_FILE_NAME`). AC-12 etalon: удаление → `false`. */
    const val VAD_FILE_NAME: String = "silero_vad.onnx"

    /**
     * Whisper-часть манифеста готова: каталог `asr/whisper-small/` существует
     * и содержит все [whisperRequiredFiles] (ненулевой размер).
     */
    fun isWhisperReady(filesDir: File): Boolean {
        val dir = whisperDir(filesDir)
        if (!dir.isDirectory) return false
        return whisperRequiredFiles.all { f ->
            val file = File(dir, f)
            file.exists() && file.length() > 0L
        }
    }

    /**
     * VAD-часть манифеста готова: `asr/vad/silero_vad.onnx` существует и ненулевой.
     */
    fun isVadReady(filesDir: File): Boolean {
        val file = File(vadDir(filesDir), VAD_FILE_NAME)
        return file.exists() && file.length() > 0L
    }

    /**
     * **AC-12 — isAsrAvailable() = Whisper AND VAD presence.**
     *
     * @param filesDir корень ФС приложения (`context.filesDir` или temp-dir в тестах).
     * @return `true` iff и Whisper-модель, и VAD-модель полностью присутствуют.
     */
    fun isAvailable(filesDir: File): Boolean = isWhisperReady(filesDir) && isVadReady(filesDir)
}

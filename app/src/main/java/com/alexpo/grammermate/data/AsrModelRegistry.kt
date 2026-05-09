package com.alexpo.grammermate.data

data class AsrModelSpec(
    val modelId: String,
    val displayName: String,
    val downloadUrl: String,
    val archivePrefix: String,
    val modelDirName: String,
    val fallbackDownloadSize: Long,
    val minRequiredBytes: Long,
    val requiredFiles: List<String>,
    val requiredDirs: List<String>
)

object AsrModelRegistry {
    val asrSpec = AsrModelSpec(
        modelId = "dolphin-multilingual",
        displayName = "Dolphin CTC Multilingual",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02.tar.bz2",
        archivePrefix = "sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02/",
        modelDirName = "dolphin-base-ctc-multi-lang-int8",
        fallbackDownloadSize = 170L * 1024 * 1024,
        minRequiredBytes = 400L * 1024 * 1024,
        requiredFiles = listOf("model.int8.onnx", "tokens.txt"),
        requiredDirs = emptyList()
    )

    // Silero VAD - single file download (no archive)
    const val VAD_MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
    const val VAD_FILE_NAME = "silero_vad.onnx"
    const val VAD_DIR_NAME = "silero-vad"

    fun asrModelDir(context: android.content.Context) =
        java.io.File(context.filesDir, "asr/${asrSpec.modelDirName}")

    fun vadModelDir(context: android.content.Context) =
        java.io.File(context.filesDir, "asr/$VAD_DIR_NAME")

    fun isAsrReady(context: android.content.Context): Boolean {
        val dir = asrModelDir(context)
        if (!dir.exists()) return false
        return asrSpec.requiredFiles.all { f ->
            val file = java.io.File(dir, f)
            file.exists() && file.length() > 0
        }
    }

    fun isVadReady(context: android.content.Context): Boolean {
        val file = java.io.File(vadModelDir(context), VAD_FILE_NAME)
        return file.exists() && file.length() > 0
    }

    fun isReady(context: android.content.Context) = isAsrReady(context) && isVadReady(context)
}

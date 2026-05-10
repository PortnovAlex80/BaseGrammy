package com.alexpo.grammermate.data

enum class AsrModelType { WHISPER }

data class AsrModelSpec(
    val modelId: String,
    val displayName: String,
    val modelType: AsrModelType,
    val downloadUrl: String,
    val archivePrefix: String,
    val modelDirName: String,
    val fallbackDownloadSize: Long,
    val minRequiredBytes: Long,
    val requiredFiles: List<String>,
)

object AsrModelRegistry {

    val defaultModel: AsrModelSpec = AsrModelSpec(
        modelId = "whisper-small-multilingual",
        displayName = "Whisper Small Multilingual (EN/IT/RU)",
        modelType = AsrModelType.WHISPER,
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-small.tar.bz2",
        archivePrefix = "sherpa-onnx-whisper-small/",
        modelDirName = "whisper-small",
        fallbackDownloadSize = 375L * 1024 * 1024,
        minRequiredBytes = 800L * 1024 * 1024,
        requiredFiles = listOf("small-encoder.int8.onnx", "small-decoder.int8.onnx", "small-tokens.txt")
    )

    // Silero VAD model (always needed alongside the ASR model)
    val vadModel = AsrModelSpec(
        modelId = "silero-vad",
        displayName = "Silero VAD",
        modelType = AsrModelType.WHISPER,
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
        archivePrefix = "",
        modelDirName = "vad",
        fallbackDownloadSize = 2L * 1024 * 1024,
        minRequiredBytes = 5L * 1024 * 1024,
        requiredFiles = listOf("silero_vad.onnx")
    )

    // Legacy aliases for AsrModelManager compatibility
    val asrSpec: AsrModelSpec get() = defaultModel
    const val VAD_MODEL_URL: String = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
    const val VAD_FILE_NAME: String = "silero_vad.onnx"
    const val VAD_DIR_NAME: String = "vad"

    fun asrModelDir(context: android.content.Context) =
        java.io.File(context.filesDir, "asr/${defaultModel.modelDirName}")

    fun vadModelDir(context: android.content.Context) =
        java.io.File(context.filesDir, "asr/$VAD_DIR_NAME")

    fun isAsrReady(context: android.content.Context): Boolean {
        val dir = asrModelDir(context)
        if (!dir.exists()) return false
        return defaultModel.requiredFiles.all { f ->
            val file = java.io.File(dir, f)
            file.exists() && file.length() > 0
        }
    }

    fun isVadReady(context: android.content.Context): Boolean {
        val file = java.io.File(vadModelDir(context), VAD_FILE_NAME)
        return file.exists() && file.length() > 0
    }

    fun isReady(context: android.content.Context) = isAsrReady(context) && isVadReady(context)

    /** Map app language IDs to Whisper language codes. */
    fun whisperLanguageCode(languageId: String): String = when (languageId) {
        "en" -> "en"
        "it" -> "it"
        "ru" -> "ru"
        else -> languageId // Whisper uses ISO 639-1 codes directly
    }
}

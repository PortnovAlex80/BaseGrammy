package com.alexpo.grammermate.data

enum class AsrModelType { DOLPHIN_CTC }

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
        modelId = "dolphin-multilingual",
        displayName = "Dolphin CTC Multilingual (RU/EN/IT)",
        modelType = AsrModelType.DOLPHIN_CTC,
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02.tar.bz2",
        archivePrefix = "sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02/",
        modelDirName = "dolphin-base-ctc-multi-lang-int8",
        fallbackDownloadSize = 170L * 1024 * 1024,
        minRequiredBytes = 400L * 1024 * 1024,
        requiredFiles = listOf("model.onnx", "tokens.txt")
    )

    // Silero VAD model (always needed alongside the ASR model)
    val vadModel = AsrModelSpec(
        modelId = "silero-vad",
        displayName = "Silero VAD",
        modelType = AsrModelType.DOLPHIN_CTC,
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
        archivePrefix = "",
        modelDirName = "vad",
        fallbackDownloadSize = 2L * 1024 * 1024,
        minRequiredBytes = 5L * 1024 * 1024,
        requiredFiles = listOf("silero_vad.onnx")
    )
}

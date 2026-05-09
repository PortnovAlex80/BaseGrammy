package com.alexpo.grammermate.data

enum class TtsModelType { KOKORO, VITS_PIPER }

data class TtsModelSpec(
    val languageId: String,
    val displayName: String,
    val modelType: TtsModelType,
    val downloadUrl: String,
    val archivePrefix: String,
    val modelDirName: String,
    val fallbackDownloadSize: Long,
    val minRequiredBytes: Long,
    val requiredFiles: List<String>,
    val requiredDirs: List<String>,
    val modelFileName: String = "model.onnx"
)

object TtsModelRegistry {

    val models: Map<String, TtsModelSpec> = mapOf(
        "en" to TtsModelSpec(
            languageId = "en",
            displayName = "English",
            modelType = TtsModelType.KOKORO,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_1.tar.bz2",
            archivePrefix = "kokoro-int8-multi-lang-v1_1/",
            modelDirName = "kokoro-int8-multi-lang-v1_1",
            fallbackDownloadSize = 175L * 1024 * 1024,
            minRequiredBytes = 400L * 1024 * 1024,
            requiredFiles = listOf("model.onnx", "tokens.txt", "voices.bin"),
            requiredDirs = listOf("espeak-ng-data")
        ),
        "it" to TtsModelSpec(
            languageId = "it",
            displayName = "Italian",
            modelType = TtsModelType.VITS_PIPER,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-it_IT-paola-medium.tar.bz2",
            archivePrefix = "vits-piper-it_IT-paola-medium/",
            modelDirName = "vits-piper-it_IT-paola-medium",
            fallbackDownloadSize = 65L * 1024 * 1024,
            minRequiredBytes = 150L * 1024 * 1024,
            requiredFiles = listOf("it_IT-paola-medium.onnx", "tokens.txt"),
            requiredDirs = listOf("espeak-ng-data"),
            modelFileName = "it_IT-paola-medium.onnx"
        )
    )

    fun specFor(languageId: String): TtsModelSpec? = models[languageId]
}

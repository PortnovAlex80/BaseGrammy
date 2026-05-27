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
            modelType = TtsModelType.VITS_PIPER,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-low.tar.bz2",
            archivePrefix = "vits-piper-en_US-amy-low/",
            modelDirName = "vits-piper-en_US-amy-low",
            fallbackDownloadSize = 35L * 1024 * 1024,
            minRequiredBytes = 120L * 1024 * 1024,
            requiredFiles = listOf("en_US-amy-low.onnx", "tokens.txt"),
            requiredDirs = listOf("espeak-ng-data"),
            modelFileName = "en_US-amy-low.onnx"
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
        ),
        "ru" to TtsModelSpec(
            languageId = "ru",
            displayName = "Russian",
            modelType = TtsModelType.VITS_PIPER,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-dmitry-medium.tar.bz2",
            archivePrefix = "vits-piper-ru_RU-dmitry-medium/",
            modelDirName = "vits-piper-ru_RU-dmitry-medium",
            fallbackDownloadSize = 65L * 1024 * 1024,
            minRequiredBytes = 150L * 1024 * 1024,
            requiredFiles = listOf("ru_RU-dmitry-medium.onnx", "tokens.txt"),
            requiredDirs = listOf("espeak-ng-data"),
            modelFileName = "ru_RU-dmitry-medium.onnx"
        )
    )

    fun specFor(languageId: String): TtsModelSpec? = models[languageId]
}

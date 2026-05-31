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
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-irina-medium.tar.bz2",
            archivePrefix = "vits-piper-ru_RU-irina-medium/",
            modelDirName = "vits-piper-ru_RU-irina-medium",
            fallbackDownloadSize = 65L * 1024 * 1024,
            minRequiredBytes = 150L * 1024 * 1024,
            requiredFiles = listOf("ru_RU-irina-medium.onnx", "tokens.txt"),
            requiredDirs = listOf("espeak-ng-data"),
            modelFileName = "ru_RU-irina-medium.onnx"
        ),
        "el" to TtsModelSpec(
            languageId = "el",
            displayName = "Greek",
            modelType = TtsModelType.VITS_PIPER,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-el_GR-rapunzelina-low.tar.bz2",
            archivePrefix = "vits-piper-el_GR-rapunzelina-low/",
            modelDirName = "vits-piper-el_GR-rapunzelina-low",
            fallbackDownloadSize = 20L * 1024 * 1024,
            minRequiredBytes = 100L * 1024 * 1024,
            requiredFiles = listOf("el_GR-rapunzelina-low.onnx", "tokens.txt"),
            requiredDirs = listOf("espeak-ng-data"),
            modelFileName = "el_GR-rapunzelina-low.onnx"
        ),
        "de" to TtsModelSpec(
            languageId = "de",
            displayName = "German",
            modelType = TtsModelType.VITS_PIPER,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-de_DE-thorsten-medium.tar.bz2",
            archivePrefix = "vits-piper-de_DE-thorsten-medium/",
            modelDirName = "vits-piper-de_DE-thorsten-medium",
            fallbackDownloadSize = 60L * 1024 * 1024,
            minRequiredBytes = 150L * 1024 * 1024,
            requiredFiles = listOf("de_DE-thorsten-medium.onnx", "tokens.txt"),
            requiredDirs = listOf("espeak-ng-data"),
            modelFileName = "de_DE-thorsten-medium.onnx"
        ),
        "zh" to TtsModelSpec(
            languageId = "zh",
            displayName = "Chinese",
            modelType = TtsModelType.VITS_PIPER,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-zh_CN-huayan-medium.tar.bz2",
            archivePrefix = "vits-piper-zh_CN-huayan-medium/",
            modelDirName = "vits-piper-zh_CN-huayan-medium",
            fallbackDownloadSize = 65L * 1024 * 1024,
            minRequiredBytes = 150L * 1024 * 1024,
            requiredFiles = listOf("zh_CN-huayan-medium.onnx", "tokens.txt"),
            requiredDirs = listOf("espeak-ng-data"),
            modelFileName = "zh_CN-huayan-medium.onnx"
        )
    )

    fun specFor(languageId: String): TtsModelSpec? = models[languageId]
}

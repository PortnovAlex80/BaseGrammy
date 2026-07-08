package com.alexpo.grammermate.v2.core.data.audio

import java.io.File

/**
 * Тип нативной TTS-модели Sherpa-ONNX (FR-4 — RU length-scale override только для VITS_PIPER).
 *
 * Соответствует legacy `data/TtsModelRegistry.kt::TtsModelType` (regression-lock E03).
 */
enum class TtsModelType { KOKORO, VITS_PIPER }

/**
 * Спецификация TTS-модели для конкретного языка: путь на диске, обязательные
 * файлы/каталоги, URL для скачивания, требования к памяти.
 *
 * Манифест per-language (AC-11): именно [requiredFiles] + [requiredDirs] отличают
 * «модель для языка загружена полностью» от «есть какой-то каталог под tts/».
 *
 * @property modelDirName имя каталога под `filesDir/tts/` (например `vits-piper-en_US-amy-low`).
 *   Зависит от языка (legacy `TtsModelRegistry.models[*].modelDirName`).
 * @property requiredFiles обязательные файлы внутри каталога (ненулевой размер).
 *   `model.onnx` эквивалент + `tokens.txt` (legacy: `en_US-amy-low.onnx`, ...).
 * @property requiredDirs обязательные подкаталоги с данными (`espeak-ng-data` для VITS_PIPER).
 * @property modelFileName имя `.onnx` файла модели (legacy: `*.onnx`).
 * @property minRequiredBytes минимум свободного места для download (StatFs pre-check, FR-13/AC-18).
 */
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
    val modelFileName: String = "model.onnx",
)

/**
 * Реестр TTS-моделей по языкам (per-language manifest, AC-11 / FR-8).
 *
 * **Архитектурная роль.** Чистый Kotlin-объект (без Android-зависимостей):
 * отображает [LanguageId] (строковый ISO 639-1) → [TtsModelSpec], даёт
 * детерминированный ответ «есть ли на диске полная модель для языка» через
 * [isAvailable]. Это ядро per-language проверки, на которую полагается
 * [SherpaAudioRepository.isTtsAvailable] (AC-11) и `getTtsModelStatus`.
 *
 * Регрессия: портировано из legacy `data/TtsModelRegistry.kt` (1:1 по спекам
 * `en/it/ru/el/de/zh`), но логика проверки файлов (`requiredFiles`/`requiredDirs`,
 * ненулевой размер) вынесена сюда из адаптера, чтобы её можно было unit-тестировать
 * на temp-dir fixture без Robolectric (AC-11 verification: UNIT, temp-dir).
 *
 * NFR-1 (zero-leakage): класс лежит в data-слое; домен про него не знает —
 * домен получает только `Boolean` через порт [AudioRepository.isTtsAvailable].
 */
object TtsModelRegistry {

    /**
     * Известные модели по языкам. 1:1 с legacy `TtsModelRegistry.models`.
     * AC-11 etalon: `en` → `requiredFiles=["en_US-amy-low.onnx","tokens.txt"]`,
     * `requiredDirs=["espeak-ng-data"]`, `modelDirName="vits-piper-en_US-amy-low"`.
     */
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
            modelFileName = "en_US-amy-low.onnx",
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
            modelFileName = "it_IT-paola-medium.onnx",
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
            modelFileName = "ru_RU-irina-medium.onnx",
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
            modelFileName = "el_GR-rapunzelina-low.onnx",
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
            modelFileName = "de_DE-thorsten-medium.onnx",
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
            modelFileName = "zh_CN-huayan-medium.onnx",
        ),
    )

    /** Спецификация для [languageId] или `null`, если язык не в реестре (AC-11: unknown lang → false). */
    fun specFor(languageId: String): TtsModelSpec? = models[languageId]

    /**
     * Корневой каталог всех TTS-моделей: `filesDir/tts/`.
     * Pure-Kotlin: принимает любой корень (temp-dir в тестах).
     */
    fun ttsRoot(filesDir: File): File = File(filesDir, "tts")

    /**
     * Каталог модели для [spec]: `filesDir/tts/${spec.modelDirName}`.
     */
    fun modelDir(filesDir: File, spec: TtsModelSpec): File =
        File(ttsRoot(filesDir), spec.modelDirName)

    /**
     * **AC-11 — per-language manifest-проверка.** Полная модель для [languageId]
     * присутствует на диске (все [TtsModelSpec.requiredFiles] + все
     * [TtsModelSpec.requiredDirs], ненулевой размер) iff:
     *   1. [specFor] находит spec для [languageId] (иначе `false` — язык не в реестре);
     *   2. каталог `filesDir/tts/${spec.modelDirName}` существует;
     *   3. каждый файл из `requiredFiles` существует и `length() > 0`;
     *   4. каждый каталог из `requiredDirs` существует и непустой.
     *
     * Это **точная** проверка (а не «есть любой каталог под tts/»): удаление
     * `tokens.txt` для загруженного языка → `false` (AC-11 etalon).
     *
     * @param filesDir корень файловой системы приложения (`context.filesDir` или temp-dir в тестах).
     * @param languageId ISO 639-1 код языка.
     * @return `true` iff манифест модели для языка полностью присутствует.
     */
    fun isAvailable(filesDir: File, languageId: String): Boolean {
        val spec = specFor(languageId) ?: return false
        return isSpecAvailable(filesDir, spec)
    }

    /**
     * Та же проверка, что [isAvailable], но по готовой [spec] (без lookup).
     * Используется статус-репозиторием и тестами.
     */
    fun isSpecAvailable(filesDir: File, spec: TtsModelSpec): Boolean {
        val dir = modelDir(filesDir, spec)
        if (!dir.isDirectory) return false
        for (file in spec.requiredFiles) {
            val f = File(dir, file)
            if (!f.exists() || f.length() <= 0L) return false
        }
        for (sub in spec.requiredDirs) {
            val d = File(dir, sub)
            if (!d.isDirectory || (d.listFiles()?.isEmpty() != false)) return false
        }
        return true
    }
}

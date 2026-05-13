package com.alexpo.grammermate.data

import android.content.Context
import org.yaml.snakeyaml.Yaml
import java.io.File

data class AppConfig(
    val testMode: Boolean = false,
    val eliteSizeMultiplier: Double = 1.25,
    val vocabSprintLimit: Int = 20,
    val useOfflineAsr: Boolean = false,
    val hintLevel: HintLevel = HintLevel.EASY,
    val ruTextScale: Float = 1.0f,
    val voiceAutoStart: Boolean = true
)

interface AppConfigStore {

    fun save(config: AppConfig)

    fun load(): AppConfig
}

class AppConfigStoreImpl(private val context: Context) : AppConfigStore {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file = File(baseDir, "config.yaml")

    override fun save(config: AppConfig) {
        baseDir.mkdirs()
        val payload = mapOf(
            "testMode" to config.testMode,
            "eliteSizeMultiplier" to config.eliteSizeMultiplier,
            "vocabSprintLimit" to config.vocabSprintLimit,
            "useOfflineAsr" to config.useOfflineAsr,
            "hintLevel" to config.hintLevel.name,
            "ruTextScale" to config.ruTextScale,
            "voiceAutoStart" to config.voiceAutoStart
        )
        AtomicFileWriter.writeText(file, yaml.dump(payload))
    }

    override fun load(): AppConfig {
        if (!file.exists()) {
            baseDir.mkdirs()
            val seeded = runCatching {
                context.assets.open("grammarmate/config.yaml").use { input ->
                    val text = input.bufferedReader().readText()
                    AtomicFileWriter.writeText(file, text)
                    text
                }
            }.getOrNull()
            if (seeded == null) {
                AtomicFileWriter.writeText(
                    file,
                    yaml.dump(mapOf("testMode" to false, "vocabSprintLimit" to 20))
                )
            }
        }
        val raw = yaml.load<Any>(file.readText()) ?: return AppConfig()
        val data = raw as? Map<*, *> ?: return AppConfig()
        val testMode = data["testMode"] as? Boolean ?: false
        val eliteSizeMultiplier = (data["eliteSizeMultiplier"] as? Number)?.toDouble() ?: 1.25
        val vocabSprintLimit = (data["vocabSprintLimit"] as? Number)?.toInt() ?: 20
        val useOfflineAsr = data["useOfflineAsr"] as? Boolean ?: false
        val hintLevelStr = data["hintLevel"] as? String ?: "EASY"
        val hintLevel = runCatching { HintLevel.valueOf(hintLevelStr) }.getOrDefault(HintLevel.EASY)
        val ruTextScale = ((data["ruTextScale"] as? Number)?.toFloat()?.coerceIn(1.0f, 2.0f)) ?: 1.0f
        val voiceAutoStart = data["voiceAutoStart"] as? Boolean ?: true
        return AppConfig(
            testMode = testMode,
            eliteSizeMultiplier = eliteSizeMultiplier,
            vocabSprintLimit = vocabSprintLimit,
            useOfflineAsr = useOfflineAsr,
            hintLevel = hintLevel,
            ruTextScale = ruTextScale,
            voiceAutoStart = voiceAutoStart
        )
    }
}

package com.alexpo.grammermate.data

import android.content.Context
import org.yaml.snakeyaml.Yaml
import java.io.File

data class AppConfig(
    val testMode: Boolean = false,
    val eliteSizeMultiplier: Double = 1.25,
    val vocabSprintLimit: Int = 20
)

class AppConfigStore(private val context: Context) {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file = File(baseDir, "config.yaml")

    fun save(config: AppConfig) {
        baseDir.mkdirs()
        val payload = mapOf(
            "testMode" to config.testMode,
            "eliteSizeMultiplier" to config.eliteSizeMultiplier,
            "vocabSprintLimit" to config.vocabSprintLimit
        )
        AtomicFileWriter.writeText(file, yaml.dump(payload))
    }

    fun load(): AppConfig {
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
        return AppConfig(
            testMode = testMode,
            eliteSizeMultiplier = eliteSizeMultiplier,
            vocabSprintLimit = vocabSprintLimit
        )
    }
}

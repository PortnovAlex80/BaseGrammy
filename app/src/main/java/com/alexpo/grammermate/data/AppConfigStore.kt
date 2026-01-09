package com.alexpo.grammermate.data

import android.content.Context
import org.yaml.snakeyaml.Yaml
import java.io.File

data class AppConfig(
    val testMode: Boolean = false,
    val eliteSizeMultiplier: Double = 1.25
)

class AppConfigStore(private val context: Context) {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file = File(baseDir, "config.yaml")

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
                AtomicFileWriter.writeText(file, yaml.dump(mapOf("testMode" to false)))
            }
        }
        val raw = yaml.load<Any>(file.readText()) ?: return AppConfig()
        val data = raw as? Map<*, *> ?: return AppConfig()
        val testMode = data["testMode"] as? Boolean ?: false
        val eliteSizeMultiplier = (data["eliteSizeMultiplier"] as? Number)?.toDouble() ?: 1.25
        return AppConfig(testMode = testMode, eliteSizeMultiplier = eliteSizeMultiplier)
    }
}

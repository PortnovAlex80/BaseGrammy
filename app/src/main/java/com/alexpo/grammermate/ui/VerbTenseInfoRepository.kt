package com.alexpo.grammermate.ui

import android.content.res.AssetManager
import android.util.Log
import org.yaml.snakeyaml.Yaml

data class TenseInfo(
    val name: String,
    val short: String,
    val formula: String,
    val usageRu: String,
    val examples: List<TenseExample>
)

data class TenseExample(
    val target: String,
    val ru: String,
    val note: String
)

/** Reads the language-specific tense reference without owning drill UI or session state. */
internal class VerbTenseInfoRepository(private val assets: AssetManager) {
    fun load(languageId: String): Map<String, TenseInfo> {
        val fileName = "grammarmate/tenses/${languageId}_tenses.yaml"
        return try {
            val yaml = assets.open(fileName).bufferedReader().readText()
            val result = parse(yaml)
            Log.d("VerbDrillVM", "Loaded tense info for $languageId: ${result.size} tenses")
            result
        } catch (e: Exception) {
            Log.w("VerbDrillVM", "Failed to load tense info from $fileName", e)
            emptyMap()
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parse(yamlText: String): Map<String, TenseInfo> {
        val data = Yaml().load<Map<String, Any>>(yamlText)
        val tensesList = data["tenses"] as? List<Map<String, Any>> ?: return emptyMap()
        val result = mutableMapOf<String, TenseInfo>()
        for (entry in tensesList) {
            val name = entry["name"] as? String ?: continue
            val short = entry["short"] as? String ?: name.take(8)
            val formula = entry["formula"] as? String ?: ""
            val usageRu = entry["usage_ru"] as? String ?: ""
            val examplesList = entry["examples"] as? List<Map<String, String>> ?: emptyList()
            val examples = examplesList.map { ex ->
                TenseExample(
                    target = ex["target"] ?: ex["it"] ?: "",
                    ru = ex["ru"] ?: "",
                    note = ex["note"] ?: ""
                )
            }
            result[name] = TenseInfo(name, short, formula, usageRu, examples)
        }
        return result
    }
}

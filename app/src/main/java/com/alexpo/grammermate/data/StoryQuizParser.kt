package com.alexpo.grammermate.data

import org.json.JSONArray
import org.json.JSONObject

object StoryQuizParser {
    fun parse(text: String): StoryQuiz {
        val json = JSONObject(text)
        val storyId = json.optString("storyId").trim()
        val lessonId = json.optString("lessonId").trim()
        val phaseRaw = json.optString("phase").trim()
        val textBody = json.optString("text").trim()
        if (storyId.isBlank() || lessonId.isBlank() || phaseRaw.isBlank() || textBody.isBlank()) {
            error("Missing story fields")
        }
        val phase = StoryPhase.valueOf(phaseRaw)
        val questionsJson = json.optJSONArray("questions") ?: JSONArray()
        val questions = mutableListOf<StoryQuestion>()
        for (i in 0 until questionsJson.length()) {
            val entry = questionsJson.optJSONObject(i) ?: continue
            val qId = entry.optString("qId").trim()
            val prompt = entry.optString("prompt").trim()
            val optionsJson = entry.optJSONArray("options") ?: JSONArray()
            val options = mutableListOf<String>()
            for (j in 0 until optionsJson.length()) {
                val option = optionsJson.optString(j).trim()
                if (option.isNotBlank()) options.add(option)
            }
            val correctIndex = entry.optInt("correctIndex", -1)
            val explain = entry.optString("explain").trim().ifBlank { null }
            if (qId.isBlank() || prompt.isBlank() || options.isEmpty() || correctIndex !in options.indices) {
                error("Invalid question at index $i")
            }
            questions.add(
                StoryQuestion(
                    qId = qId,
                    prompt = prompt,
                    options = options,
                    correctIndex = correctIndex,
                    explain = explain
                )
            )
        }
        return StoryQuiz(
            storyId = storyId,
            lessonId = lessonId,
            phase = phase,
            text = textBody,
            questions = questions
        )
    }
}

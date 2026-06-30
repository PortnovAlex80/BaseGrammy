package com.alexpo.grammermate.data

import org.json.JSONArray
import org.json.JSONObject

object StoryQuizParser {
    fun parse(text: String): ParseResult<StoryQuiz, ParseError> {
        val errors = mutableListOf<ParseError>()

        return try {
            val json = JSONObject(text)
            val storyId = json.optString("storyId").trim()
            val lessonId = json.optString("lessonId").trim()
            val phaseRaw = json.optString("phase").trim()
            val textBody = json.optString("text").trim()

            if (storyId.isBlank() || lessonId.isBlank() || phaseRaw.isBlank() || textBody.isBlank()) {
                return ParseResult.failure(
                    listOf(
                        ParseError.InvalidFormat(
                            lineNumber = 1,
                            reason = "Missing required fields: storyId, lessonId, phase, or text"
                        )
                    )
                )
            }

            val phase = try {
                StoryPhase.valueOf(phaseRaw)
            } catch (e: IllegalArgumentException) {
                return ParseResult.failure(
                    listOf(
                        ParseError.InvalidFormat(
                            lineNumber = 1,
                            reason = "Invalid phase value: '$phaseRaw'. Must be one of ${StoryPhase.entries.joinToString()}"
                        )
                    )
                )
            }

            val questionsJson = json.optJSONArray("questions") ?: JSONArray()
            val questions = mutableListOf<StoryQuestion>()

            for (i in 0 until questionsJson.length()) {
                val entry = questionsJson.optJSONObject(i)
                if (entry == null) {
                    errors.add(
                        ParseError.InvalidFormat(
                            lineNumber = i + 2,
                            reason = "Question at index $i is null or not a valid JSON object"
                        )
                    )
                    continue
                }

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
                    errors.add(
                        ParseError.InvalidFormat(
                            lineNumber = i + 2,
                            reason = "Invalid question at index $i: qId='$qId', prompt='$prompt', options.size=${options.size}, correctIndex=$correctIndex"
                        )
                    )
                    continue
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

            when {
                questions.isEmpty() && errors.isEmpty() -> ParseResult.failure(
                    listOf(
                        ParseError.InvalidFormat(
                            lineNumber = 1,
                            reason = "No valid questions found in JSON"
                        )
                    )
                )
                questions.isEmpty() -> ParseResult.failure(errors)
                errors.isEmpty() -> ParseResult.success(
                    StoryQuiz(
                        storyId = storyId,
                        lessonId = LessonId(lessonId),
                        phase = phase,
                        text = textBody,
                        questions = questions
                    )
                )
                else -> ParseResult.partial(
                    StoryQuiz(
                        storyId = storyId,
                        lessonId = LessonId(lessonId),
                        phase = phase,
                        text = textBody,
                        questions = questions
                    ),
                    errors
                )
            }
        } catch (e: Exception) {
            ParseResult.failure(
                listOf(
                    ParseError.InvalidFormat(
                        lineNumber = 1,
                        reason = "JSON parsing failed: ${e.message}"
                    )
                )
            )
        }
    }
}

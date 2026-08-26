package com.alexpo.grammermate.v2.core.data.packimport

import com.alexpo.grammermate.domain.model.StoryPhase
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * StoryQuizParser (срез 6 Фазы 4, AC-10): строгая семантика — неизвестная
 * фаза и correctIndex вне диапазона дают InvalidFormat, а не молчаливые
 * подмены (фиксы аудита M-10 и контракта FR-5).
 */
class StoryQuizParserTest {

    private val validJson = """
        {
          "storyId": "story_ch1",
          "lessonId": "lesson_01_A01",
          "phase": "CHECK_IN",
          "text": "{it}Ciao{/it} {ru}Привет{/ru}",
          "questions": [
            {
              "qId": "q1",
              "prompt": "Chi saluta?",
              "options": ["Maria", "Luca", "Nessuno"],
              "correctIndex": 1,
              "explain": "Luca parla per primo."
            },
            { "qId": "q2", "prompt": "Che ora?", "options": ["Mattina", "Sera"], "correctIndex": 0 }
          ]
        }
    """.trimIndent()

    @Test
    fun `happy path parses quiz with questions`() {
        val result = StoryQuizParser.parse(validJson)

        val quiz = (result as ParseResult.Success).data
        assertThat(quiz.storyId).isEqualTo("story_ch1")
        assertThat(quiz.lessonId.value).isEqualTo("lesson_01_A01")
        assertThat(quiz.phase).isEqualTo(StoryPhase.CHECK_IN)
        assertThat(quiz.text).contains("Ciao")
        assertThat(quiz.questions).hasSize(2)
        assertThat(quiz.questions.first().options).hasSize(3)
        assertThat(quiz.questions.first().explain).isEqualTo("Luca parla per primo.")
        assertThat(quiz.questions[1].explain).isNull()
    }

    @Test
    fun `unknown phase is invalid format not silent check-in (M-10)`() {
        val bad = validJson.replace("\"CHECK_IN\"", "\"CHECK-OUT\"")

        val result = StoryQuizParser.parse(bad)

        val error = (result as ParseResult.Failure).errors.single()
        assertThat(error).isInstanceOf(ParseError.InvalidFormat::class.java)
        assertThat((error as ParseError.InvalidFormat).reason).contains("phase")
    }

    @Test
    fun `parsePhase strict mapping`() {
        assertThat(StoryQuizParser.parsePhase("CHECK_IN")).isEqualTo(StoryPhase.CHECK_IN)
        assertThat(StoryQuizParser.parsePhase(" check_out ")).isEqualTo(StoryPhase.CHECK_OUT)
        assertThat(StoryQuizParser.parsePhase("CHECKOUT")).isNull()
        assertThat(StoryQuizParser.parsePhase(null)).isNull()
    }

    @Test
    fun `correctIndex out of range is invalid`() {
        val bad = validJson.replace("\"correctIndex\": 1", "\"correctIndex\": 5")

        val result = StoryQuizParser.parse(bad)

        val error = (result as ParseResult.Failure).errors.single() as ParseError.InvalidFormat
        assertThat(error.reason).contains("correctIndex")
    }

    @Test
    fun `broken json and missing fields are invalid`() {
        assertThat(StoryQuizParser.parse("{not json")).isInstanceOf(ParseResult.Failure::class.java)

        val noLesson = validJson.replace("\"lessonId\": \"lesson_01_A01\",", "")
        assertThat(StoryQuizParser.parse(noLesson)).isInstanceOf(ParseResult.Failure::class.java)

        val noQuestions = validJson.substringBefore(",\n          \"questions\"") + "\n}"
        assertThat(StoryQuizParser.parse(noQuestions)).isInstanceOf(ParseResult.Failure::class.java)
    }

    @Test
    fun `unknown top-level keys are tolerated (forward compatibility)`() {
        val extended = validJson.replace("{\n", "{\n  \"schemaVersion\": 3, \"futureField\": [1,2],\n")

        assertThat(StoryQuizParser.parse(extended)).isInstanceOf(ParseResult.Success::class.java)
    }
}

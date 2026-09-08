package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoryQuizParserTest {
    @Test
    fun parseValidStory() {
        val json = """
            {
              "storyId": "S_L01_IN",
              "lessonId": "L01_PRESENT_SIMPLE",
              "phase": "CHECK_IN",
              "text": "Tom works from home.",
              "questions": [
                {
                  "qId": "Q1",
                  "prompt": "Where does Tom work?",
                  "options": ["From home", "At the office"],
                  "correctIndex": 0
                }
              ]
            }
        """.trimIndent()
        val result = StoryQuizParser.parse(json)
        assertTrue(result.isSuccess)
        assertEquals(0, result.errors.size)
        val story = result.data!!
        assertEquals("S_L01_IN", story.storyId)
        assertEquals(StoryPhase.CHECK_IN, story.phase)
        assertEquals(1, story.questions.size)
    }

    @Test
    fun parse_invalidJson_returnsFailure() {
        val json = "{ invalid json }"
        val result = StoryQuizParser.parse(json)
        assertTrue(!result.isSuccess)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun parse_missingRequiredField_returnsPartial() {
        val json = """
            {
              "storyId": "S_L01_IN",
              "lessonId": "L01_PRESENT_SIMPLE"
            }
        """.trimIndent()
        val result = StoryQuizParser.parse(json)
        assertTrue(!result.isSuccess || result.isPartial)
    }
}

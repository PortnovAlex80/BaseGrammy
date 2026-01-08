package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Test

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
        val story = StoryQuizParser.parse(json)
        assertEquals("S_L01_IN", story.storyId)
        assertEquals(StoryPhase.CHECK_IN, story.phase)
        assertEquals(1, story.questions.size)
    }
}

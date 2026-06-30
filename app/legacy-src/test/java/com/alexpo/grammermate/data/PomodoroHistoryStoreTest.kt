package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PomodoroHistoryStoreTest {

    @Test
    fun appendAndLoadAll_keepsEveryPomodoroAndFiltersByLanguage() {
        val context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "grammarmate/pomodoro_history.yaml").delete()
        val store = PomodoroHistoryStore(context)

        store.append(
            PomodoroHistoryEntry(
                id = "en_1000",
                languageId = "en",
                packId = "english-pack",
                lessonId = "lesson-1",
                completedAtMs = 1000L,
                durationMinutes = 15,
                totalSeconds = 900,
                remainingSeconds = 0,
                cardsShown = 20,
                cardsCorrect = 18,
                cardsIncorrect = 2,
                wordsPerMinute = 42.0
            )
        )
        store.append(
            PomodoroHistoryEntry(
                id = "it_2000",
                languageId = "it",
                packId = "italian-pack",
                lessonId = "lezione-1",
                completedAtMs = 2000L,
                durationMinutes = 5,
                totalSeconds = 300,
                remainingSeconds = 60,
                cardsShown = 7,
                cardsCorrect = 6,
                cardsIncorrect = 1,
                wordsPerMinute = 30.0
            )
        )

        val all = store.loadAll()
        val english = store.loadAll("en")
        val italian = store.loadAll("it")

        assertEquals(2, all.size)
        assertEquals("it_2000", all.first().id)
        assertEquals(1, english.size)
        assertEquals("english-pack", english.single().packId)
        assertEquals(1, italian.size)
        assertEquals(60, italian.single().remainingSeconds)
    }
}

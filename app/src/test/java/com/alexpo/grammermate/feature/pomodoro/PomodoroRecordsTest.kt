package com.alexpo.grammermate.feature.pomodoro

import android.app.Application
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.alexpo.grammermate.data.PomodoroHistoryStore
import com.alexpo.grammermate.data.PomodoroSessionStats
import com.alexpo.grammermate.data.PomodoroSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PomodoroRecordsTest {
    @get:Rule val temp = TemporaryFolder()

    private fun records(): PomodoroRecords {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val context = object : ContextWrapper(app) {
            override fun getFilesDir(): File = temp.root
        }
        return PomodoroRecords(PomodoroSettingsStore(context), PomodoroHistoryStore(context))
    }

    @Test
    fun `duration and language-scoped completion survive a new records owner`() {
        val first = records()
        assertEquals(20, first.lastDuration.value)
        first.saveDuration(35)
        first.appendCompleted(
            stats = PomodoroSessionStats(
                cardsShown = 4, cardsCorrect = 3, cardsIncorrect = 1,
                wordsPerMinute = 82.5, durationMinutes = 35, completedAtMs = 1_234L
            ),
            remainingSeconds = 30,
            totalSeconds = 2_100,
            languageId = "it",
            packId = "pack-one",
            lessonId = "lesson-one"
        )

        val second = records()
        second.loadDuration()
        second.refreshHistory("it")
        assertEquals(35, second.lastDuration.value)
        assertEquals(1, second.history.value.size)
        val entry = second.history.value.single()
        assertEquals("it_1234", entry.id)
        assertEquals("pack-one", entry.packId)
        assertEquals("lesson-one", entry.lessonId)
        assertEquals(3, entry.cardsCorrect)

        second.refreshHistory("en")
        assertTrue(second.history.value.isEmpty())
        assertEquals(1, second.loadHistory("it").size)
    }

    @Test
    fun `completion without selected language is not persisted`() {
        val owner = records()
        owner.appendCompleted(PomodoroSessionStats(completedAtMs = 1L), 0, 60, null, null, null)

        assertTrue(owner.history.value.isEmpty())
        assertTrue(records().loadHistory("it").isEmpty())
    }
}

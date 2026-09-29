package com.alexpo.grammermate.feature.training

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionActiveTimerTest {
    @Test
    fun `resume is idempotent and pause flushes accumulated time`() = runTest {
        var persistedMs = 1_250L
        val displayTicks = mutableListOf<Long>()
        val writes = mutableListOf<Long>()
        var checkpoints = 0
        val timer = SessionActiveTimer(
            scope = backgroundScope,
            readPersistedMs = { persistedMs },
            writePersistedMs = { writes += it; persistedMs = it },
            onDisplayTick = { displayTicks += it },
            onCheckpoint = { checkpoints++ },
            nowMs = { testScheduler.currentTime }
        )

        timer.resume()
        timer.resume()
        runCurrent()
        advanceTimeBy(500)
        runCurrent()

        assertEquals(listOf(1_750L), displayTicks)
        assertEquals(emptyList<Long>(), writes)
        timer.pause()
        assertEquals(listOf(1_750L), writes)
        assertEquals(0, checkpoints)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(1_750L), displayTicks)
    }

    @Test
    fun `twentieth tick writes state before checkpoint and resume reads saved value`() = runTest {
        var persistedMs = 1_250L
        val events = mutableListOf<String>()
        val timer = SessionActiveTimer(
            scope = backgroundScope,
            readPersistedMs = { persistedMs },
            writePersistedMs = { persistedMs = it; events += "write:$it" },
            onDisplayTick = { events += "display:$it" },
            onCheckpoint = { events += "checkpoint" },
            nowMs = { testScheduler.currentTime }
        )

        timer.resume()
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(20, events.count { it.startsWith("display:") })
        assertEquals(
            listOf("display:11250", "write:11250", "checkpoint"),
            events.takeLast(3)
        )
        timer.pause()
        events.clear()
        timer.resume()
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf("display:11750"), events)
    }
}

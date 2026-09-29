package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.AudioState
import com.alexpo.grammermate.data.BossState
import com.alexpo.grammermate.data.DailyCursorState
import com.alexpo.grammermate.data.DailyPracticeState
import com.alexpo.grammermate.data.FlowerDisplayState
import com.alexpo.grammermate.data.MasteryStore
import com.alexpo.grammermate.data.StoryState
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.VocabSprintState
import com.alexpo.grammermate.feature.progress.ProgressTracker
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingProgressWriterTest {
    private val core = MutableStateFlow(TrainingUiState())
    private val audio = MutableStateFlow(AudioState(ttsSpeed = 1.3f))
    private val story = MutableStateFlow(StoryState())
    private val vocab = MutableStateFlow(VocabSprintState())
    private val daily = MutableStateFlow(DailyPracticeState(dailyCursor = DailyCursorState(sentenceOffset = 2)))
    private val flower = MutableStateFlow(FlowerDisplayState())
    private val boss = MutableStateFlow(BossState())
    private var actualCursor = DailyCursorState(sentenceOffset = 7)
    private val snapshots = TrainingProgressSnapshotSource(
        core, audio, story, vocab, daily, flower, boss, currentCursor = { actualCursor }
    )

    @Test
    fun snapshotReadsAuthoritativeFlowsAndDirectCursor() {
        val state = snapshots.capture()

        assertEquals(1.3f, state.audio.ttsSpeed)
        assertEquals(7, state.daily.dailyCursor.sentenceOffset)
        assertEquals(0, core.value.daily.dailyCursor.sentenceOffset)
    }

    @Test
    fun directSaveFlushesMasteryBeforeBackupAndConsumesRequest() {
        val tracker = mockk<ProgressTracker>()
        val mastery = mockk<MasteryStore>()
        val order = mutableListOf<String>()
        val forceValues = mutableListOf<Boolean>()
        every { tracker.saveProgress(any(), any(), any()) } answers {
            order += "progress"
            forceValues += secondArg<Boolean>()
            true
        }
        every { mastery.flush() } answers { order += "mastery" }
        val writer = TrainingProgressWriter(
            snapshots, tracker, mastery, normalizeEliteSpeeds = { it },
            createBackup = { order += "backup" },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )

        writer.requestBackup()
        writer.save()
        writer.save()

        assertEquals(listOf(true, false), forceValues)
        assertEquals(
            listOf("progress", "mastery", "backup", "progress", "mastery", "backup"), order
        )
    }

    @Test
    fun skippedBackupKeepsRequestForNextSave() {
        val tracker = mockk<ProgressTracker>()
        val mastery = mockk<MasteryStore>(relaxed = true)
        val forceValues = mutableListOf<Boolean>()
        var attempts = 0
        every { tracker.saveProgress(any(), any(), any()) } answers {
            forceValues += secondArg<Boolean>()
            attempts++
            attempts == 2
        }
        var backups = 0
        val writer = TrainingProgressWriter(
            snapshots, tracker, mastery, normalizeEliteSpeeds = { it },
            createBackup = { backups++ },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )

        writer.requestBackup()
        writer.save()
        writer.save()

        assertEquals(listOf(true, true), forceValues)
        assertEquals(1, backups)
    }

    @Test
    fun timerSaveCapturesStateBeforeAsyncWrite() = runBlocking {
        val tracker = mockk<ProgressTracker>()
        val mastery = mockk<MasteryStore>(relaxed = true)
        val saved = CompletableDeferred<TrainingUiState>()
        every { tracker.saveProgress(any(), any(), any()) } answers {
            saved.complete(firstArg())
            false
        }
        val writer = TrainingProgressWriter(
            snapshots, tracker, mastery, normalizeEliteSpeeds = { it },
            createBackup = {}, scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )

        writer.saveFromTimer()
        audio.value = AudioState(ttsSpeed = 1.8f)

        assertEquals(1.3f, withTimeout(5_000) { saved.await() }.audio.ttsSpeed)
    }
}

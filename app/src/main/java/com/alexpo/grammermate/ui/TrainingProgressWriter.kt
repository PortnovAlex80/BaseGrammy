package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.MasteryStore
import com.alexpo.grammermate.feature.progress.ProgressTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Serializes progress and mastery writes and owns the pending-backup request. */
internal class TrainingProgressWriter(
    private val snapshots: TrainingProgressSnapshotSource,
    private val progressTracker: ProgressTracker,
    private val masteryStore: MasteryStore,
    private val normalizeEliteSpeeds: (List<Double>) -> List<Double>,
    private val createBackup: () -> Unit,
    private val scope: CoroutineScope
) {
    // One lane keeps a timer checkpoint from overwriting a newer direct save.
    private val writeLane = Dispatchers.IO.limitedParallelism(1)
    private val pendingTimerWrites = ConcurrentHashMap.newKeySet<Job>()
    @Volatile private var forceBackupOnSave = false

    fun requestBackup() {
        forceBackupOnSave = true
    }

    fun save(): Unit = dispatch(async = false)

    /** Capture before scheduling I/O, while the caller's state is current. */
    fun saveFromTimer(): Unit = dispatch(async = true)

    /** Stop an older timer checkpoint from overwriting newly restored files. */
    fun drainTimerWrites() {
        runBlocking { pendingTimerWrites.toList().joinAll() }
    }

    private fun dispatch(async: Boolean) {
        val state = snapshots.capture()
        val forceBackup = forceBackupOnSave
        val normalizedSpeeds = normalizeEliteSpeeds(state.elite.eliteBestSpeeds)
        val write: suspend () -> Unit = {
            val shouldBackup = progressTracker.saveProgress(
                state = state,
                forceBackup = forceBackup,
                normalizedEliteSpeeds = normalizedSpeeds
            )
            masteryStore.flush()
            if (shouldBackup) {
                forceBackupOnSave = false
                createBackup()
            }
        }
        if (async) {
            val job = scope.launch(writeLane, start = CoroutineStart.LAZY) { write() }
            pendingTimerWrites.add(job)
            job.invokeOnCompletion { pendingTimerWrites.remove(job) }
            job.start()
        } else runBlocking { withContext(writeLane) { write() } }
    }
}

package com.alexpo.grammermate.shared.audio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRouteOwnerTest {
    @Test
    fun oneRecordingOwnsRouteAndCloseIsIdempotent() = runTest {
        var starts = 0
        var stops = 0
        val states = mutableListOf<Boolean>()
        val owner = SpeechRouteOwner({ starts++; true }, { stops++ }, states::add)
        owner.setBluetoothEnabled(true)

        val first = owner.acquire()
        assertNotNull(first)
        assertNull(owner.acquire())
        first!!.close()
        first.close()
        assertEquals(1, starts)
        assertEquals(1, stops)
        assertEquals(listOf(true, false), states)
        val second = owner.acquire()
        assertNotNull(second)
        first.close() // A delayed callback from the old result cannot stop the new attempt.
        assertEquals(1, stops)
        second!!.close()
        assertEquals(2, starts)
        assertEquals(2, stops)
    }

    @Test
    fun disabledSettingClosesActiveRouteAndLateSetupCannotReopenIt() = runTest {
        val ready = CompletableDeferred<Boolean>()
        var stops = 0
        val states = mutableListOf<Boolean>()
        val owner = SpeechRouteOwner({ ready.await() }, { stops++ }, states::add)
        owner.setBluetoothEnabled(true)
        val pending = async { owner.acquire() }
        runCurrent()
        assertNull(owner.acquire())

        owner.setBluetoothEnabled(false)
        ready.complete(true)
        runCurrent()
        val lease = pending.await()
        assertNotNull(lease) // recognition continues with the device microphone
        assertEquals(1, stops)
        assertFalse(states.last())
        lease!!.close()
        assertEquals(1, stops)
    }

    @Test
    fun cancellationAndShutdownDuringSetupLeaveNoRouteOrNewOwner() = runTest {
        val ready = CompletableDeferred<Boolean>()
        var stops = 0
        val owner = SpeechRouteOwner({ ready.await() }, { stops++ }, {})
        owner.setBluetoothEnabled(true)
        val pending = async { owner.acquire() }
        runCurrent()
        owner.shutdown()
        ready.complete(true)
        runCurrent()
        assertNull(pending.await())
        assertEquals(1, stops)
        assertNull(owner.acquire())

        val canceledReady = CompletableDeferred<Boolean>()
        val second = SpeechRouteOwner({ canceledReady.await() }, { stops++ }, {})
        second.setBluetoothEnabled(true)
        val canceled = async { second.acquire() }
        runCurrent()
        canceled.cancel()
        runCurrent()
        assertTrue(canceled.isCancelled)
        assertEquals(2, stops)
        second.setBluetoothEnabled(false)
        assertNotNull(second.acquire())
    }

    @Test
    fun failedBluetoothSetupFallsBackButCleansPartialRoute() = runTest {
        var stops = 0
        val owner = SpeechRouteOwner({ false }, { stops++ }, {})
        owner.setBluetoothEnabled(true)
        val lease = owner.acquire()
        assertNotNull(lease)
        assertEquals(1, stops)
        lease!!.close()
        assertEquals(1, stops)
    }

    @Test
    fun settingOffBeforeAcquireCannotBeOverriddenByAStaleCallerSnapshot() = runTest {
        var starts = 0
        val owner = SpeechRouteOwner({ starts++; true }, {}, {})
        owner.setBluetoothEnabled(true)
        owner.setBluetoothEnabled(false)
        val lease = owner.acquire()
        assertNotNull(lease)
        assertEquals(0, starts)
        lease!!.close()
    }
}

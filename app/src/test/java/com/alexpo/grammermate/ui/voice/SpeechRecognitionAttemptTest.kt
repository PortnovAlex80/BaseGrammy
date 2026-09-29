package com.alexpo.grammermate.ui.voice

import com.alexpo.grammermate.shared.audio.SpeechRouteLease
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRecognitionAttemptTest {
    @Test
    fun resultReleasesRouteBeforeAnswerHandlerCanLaunchAgain() = runTest {
        var closed = 0
        val attempt = SpeechRecognitionAttempt { SpeechRouteLease { closed++ } }
        var launched = 0
        assertTrue(attempt.launch { launched++ })
        assertFalse(attempt.launch { launched++ })
        attempt.finish()
        assertEquals(1, closed)
        assertTrue(attempt.launch { launched++ })
        assertEquals(2, launched)
        attempt.finish()
        assertEquals(2, closed)
    }

    @Test
    fun disposalDuringDelayedSetupPreventsLateLaunchAndClosesLease() = runTest {
        val ready = CompletableDeferred<SpeechRouteLease>()
        var closed = 0
        var launched = false
        val attempt = SpeechRecognitionAttempt { ready.await() }
        val pending = async { attempt.launch { launched = true } }
        runCurrent()
        attempt.dispose()
        ready.complete(SpeechRouteLease { closed++ })
        runCurrent()
        assertFalse(pending.await())
        assertFalse(launched)
        assertEquals(1, closed)
    }

    @Test
    fun launchFailureAndCanceledResultCloseTheRoute() = runTest {
        var closed = 0
        val attempt = SpeechRecognitionAttempt { SpeechRouteLease { closed++ } }
        assertFalse(attempt.launch { error("No recognizer installed") })
        assertEquals(1, closed)
        assertTrue(attempt.launch { })
        attempt.finish() // RESULT_CANCELED takes the same path.
        assertEquals(2, closed)
    }

    @Test
    fun busyAudioOwnerDoesNotLaunchRecognizer() = runTest {
        var launched = false
        val attempt = SpeechRecognitionAttempt { null }
        assertFalse(attempt.launch { launched = true })
        assertFalse(launched)
    }
}

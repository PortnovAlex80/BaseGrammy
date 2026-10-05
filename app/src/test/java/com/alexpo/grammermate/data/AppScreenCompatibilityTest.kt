package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppScreenCompatibilityTest {
    @Test fun retiredAndUnknownPersistedScreensFallBackToHome() {
        listOf("ELITE", "VOCAB", "obsolete-screen").forEach { name ->
            assertEquals(AppScreen.HOME, AppScreen.parse(name))
        }
        assertEquals(AppScreen.VERB_DRILL, AppScreen.parse("VERB_DRILL"))
    }
}

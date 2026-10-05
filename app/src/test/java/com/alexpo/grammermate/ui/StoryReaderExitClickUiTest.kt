package com.alexpo.grammermate.ui

import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.PackId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class StoryReaderExitClickUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun playUsesCurrentReaderContentAndPackAfterRecomposition() {
        val vm = mockk<TrainingViewModel>(relaxed = true)
        val navigation = mockk<NavHostController>(relaxed = true)
        val state = mutableStateOf(TrainingUiState(
            storyReaderChapterTitle = "First", storyReaderContent = "First story",
            storyReaderStoryFile = "first.opus"
        ))
        rule.setContent { MaterialTheme { StoryReaderDestination(state.value, vm, navigation) } }
        rule.runOnIdle {
            state.value = state.value.copy(
                storyReaderChapterTitle = "Second", storyReaderContent = "Second story",
                storyReaderStoryFile = "second.opus",
                navigation = state.value.navigation.copy(activePackId = PackId("second_pack"))
            )
        }

        rule.onNodeWithContentDescription("Play story").performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
        rule.waitForIdle()

        verify(exactly = 1) {
            vm.speakMultilingualStory("Second story", "ru", "second.opus", "second_pack")
        }
    }

    @Test
    fun toolbarBackStopsNarrationStartedAfterOpeningTheReader() {
        val vm = mockk<TrainingViewModel>(relaxed = true)
        val navigation = mockk<NavHostController>(relaxed = true)
        val state = mutableStateOf(TrainingUiState(
            storyReaderChapterTitle = "Review story", storyReaderContent = "Story text"
        ))
        every { vm.speakMultilingualStory(any(), any(), any(), any()) } answers {
            state.value = state.value.copy(audio = state.value.audio.copy(isStoryPlaybackActive = true))
        }
        rule.setContent { MaterialTheme { StoryReaderDestination(state.value, vm, navigation) } }

        rule.onNodeWithContentDescription("Play story").performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
        rule.waitForIdle()
        assertTrue(state.value.audio.isStoryPlaybackActive)
        rule.onNodeWithContentDescription("Back to Roadmap").performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
        rule.waitForIdle()

        verifyOrder {
            vm.stopStoryNarration()
            vm.clearStoryReader()
            navigation.popBackStack()
        }
    }
}

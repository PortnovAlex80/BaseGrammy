package com.alexpo.grammermate.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.shared.AuditLogger
import com.alexpo.grammermate.shared.ScreenLogger
import com.alexpo.grammermate.ui.navigation.Routes
import com.alexpo.grammermate.ui.screens.BackgroundVocabScreen
import com.alexpo.grammermate.ui.screens.ChapterLessonsScreen
import com.alexpo.grammermate.ui.screens.StoryReaderScreen

@Composable
internal fun StoryReaderDestination(
    state: TrainingUiState,
    vm: TrainingViewModel,
    navController: NavHostController
) {
    val chapterTitle = state.storyReaderChapterTitle ?: "Unknown Chapter"
    val content = state.storyReaderContent
    val isCurrentlyPlaying = state.audio.isStoryPlaybackActive || state.audio.ttsState == TtsState.Speaking
    val isStoryPaused = state.audio.isStoryPlaybackPaused

    StoryReaderScreen(
        chapterTitle = chapterTitle,
        markdownContent = content,
        textScale = state.audio.ruTextScale,
        onBack = remember {
            {
                // Stop TTS if playing or paused
                if (isCurrentlyPlaying || isStoryPaused) {
                    vm.stopStoryNarration()
                }
                vm.clearStoryReader()
                // Pop back to whatever launched STORY_READER
                // (HOME showing the roadmap).
                navController.popBackStack()
            }
        },
        onPlayStory = remember {
            {
                // Play story narration: prefer the pre-rendered Opus clip for this
                // chapter (real-voice narration) when present; otherwise fall back to
                // multilingual TTS synthesis of the on-screen content.
                if (content.isNotEmpty()) {
                    vm.speakMultilingualStory(
                        content,
                        defaultLanguageId = "ru",
                        storyFile = state.storyReaderStoryFile,
                        packId = state.navigation.activePackId?.value
                    )
                }
            }
        },
        onStopStory = remember {
            {
                vm.stopStoryNarration()
            }
        },
        onPauseStory = remember {
            {
                vm.pauseStoryPlayback()
            }
        },
        onResumeStory = remember {
            {
                vm.resumeStoryPlayback()
            }
        },
        isPlaying = isCurrentlyPlaying,
        isStoryPaused = isStoryPaused
    )
}

@Composable
internal fun ChapterLessonsDestination(
    state: TrainingUiState,
    vm: TrainingViewModel,
    navController: NavHostController,
    onNavigate: (String) -> Unit,
    settingsVisible: Boolean
) {
    val selectedChapter = state.navigation.selectedChapter
    val packId = state.navigation.activePackId
    // Live progress (Phase 3, item 3.3): the SAME
    // computation the HOME roadmap consumes — the roadmap
    // and this screen can no longer diverge.
    val chapterCards by vm.chapterCards.collectAsStateWithLifecycle()

    if (selectedChapter != null && packId != null) {
        val chapterProgress = chapterCards.firstOrNull {
            it.chapter.chapterId == selectedChapter.chapterId
        }?.progress
        val lessons = vm.getLessonsForChapter(selectedChapter)
        val completedLessonIds = vm.getCompletedLessonIds(selectedChapter)

        ChapterLessonsScreen(
            chapterTitle = selectedChapter.title,
            chapterSubtitle = selectedChapter.subtitle ?: "",
            chapterId = selectedChapter.chapterId,
            lessons = lessons,
            chapterProgress = chapterProgress,
            completedLessonIds = completedLessonIds,
            onLessonClick = remember { { lesson ->
                vm.selectLesson(lesson.id.value, packId.value)
                onNavigate(Routes.LESSON)
            } },
            onBack = remember { { navController.popBackStack(Routes.HOME, inclusive = false) } }
        )
    } else {
        // Fallback if no chapter selected
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
    // Inner BackHandler — higher priority than NavController, so system back
    // works correctly on Android 14+ predictive back.
    BackHandler(enabled = !settingsVisible) {
        Log.d("NavDebug", "BACK: CHAPTER_LESSONS inner handler → HOME")
        ScreenLogger.nav("chapter_lessons", "BACK", trigger = "back_press")
        AuditLogger.getInstanceOrNull()?.backPress("chapter_lessons", "back_to_home")
        navController.popBackStack(Routes.HOME, inclusive = false)
    }
}

@Composable
internal fun BackgroundVocabDestination(
    navController: NavHostController,
    settingsVisible: Boolean
) {
    LaunchedEffect(Unit) {
        ScreenLogger.screenShown("BACKGROUND_VOCAB")
    }
    BackgroundVocabScreen(
        onBack = remember { {
            ScreenLogger.nav("background_vocab", "BACK", trigger = "back_press")
            AuditLogger.getInstanceOrNull()?.backPress("background_vocab", "back_to_home")
            navController.popBackStack(Routes.HOME, inclusive = false)
        } }
    )
    BackHandler(enabled = !settingsVisible) {
        ScreenLogger.nav("background_vocab", "BACK", trigger = "back_press")
        AuditLogger.getInstanceOrNull()?.backPress("background_vocab", "back_to_home")
        navController.popBackStack(Routes.HOME, inclusive = false)
    }
}

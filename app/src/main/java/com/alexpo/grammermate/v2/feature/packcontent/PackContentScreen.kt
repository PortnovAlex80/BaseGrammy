package com.alexpo.grammermate.v2.feature.packcontent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alexpo.grammermate.v2.core.ui.collectState

/** Стабильные test-tags экрана содержимого пака (journey Home → Chapter → Lesson). */
object PackContentTestTags {
    const val CHAPTER_HEADER = "pack_chapter_header"
    const val LESSON_ITEM = "pack_lesson_item"
}

/** Кнопка «История» в заголовке главы со storyFile (срез 6 Фазы 4). */
const val PACK_CHAPTER_STORY_TAG = "pack_chapter_story"

/** Кнопка «Помодоро» в списке пака (срез 7 Фазы 4). */
const val PACK_POMODORO_TAG = "pack_pomodoro"

/**
 * Экран содержимого пака — главы с уроками (Фаза 1 плана стабилизации
 * 2026-08-26: путь Pack → Chapter → Lesson; P0 `lessonId = packId` устранён).
 *
 * Секции = главы по `order` (заголовок + подзаголовок), внутри — кликабельные
 * уроки. Клик по уроку открывает Training(packId, lessonId) с реальным lessonId.
 *
 * @param packId           пак (nav-arg; ViewModel читает из SavedStateHandle).
 * @param onNavigateBack   назад (Home).
 * @param onOpenLesson     клик по уроку: `(lessonId: String)`.
 * @param viewModel        Hilt-injected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackContentScreen(
    packId: String,
    onNavigateBack: () -> Unit,
    onOpenLesson: (String) -> Unit,
    onOpenStory: (chapterId: String) -> Unit = {},
    onOpenPomodoro: () -> Unit = {},
    viewModel: PackContentViewModel = hiltViewModel(),
) {
    val state = collectState(viewModel.state)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = state.pack?.displayName ?: state.pack?.id?.value ?: "Пак",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = viewModel::load) { Text("Повторить") }
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "pomodoro_entry") {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onOpenPomodoro,
                        modifier = Modifier.fillMaxWidth().testTag(PACK_POMODORO_TAG),
                    ) { Text("🍅 Помодоро") }
                }
                state.sections.forEach { section ->
                    if (section.chapter != null) {
                        item(key = "chapter_${section.chapter.id.value}") {
                            ChapterHeader(
                                title = section.chapter.title,
                                subtitle = section.chapter.subtitle,
                                hasStory = section.chapter.storyFile != null,
                                onOpenStory = { onOpenStory(section.chapter.id.value) },
                            )
                        }
                    }
                    items(
                        items = section.lessons,
                        key = { it.id.value },
                    ) { lesson ->
                        LessonItem(
                            title = lesson.title,
                            cefr = lesson.cefrLevel,
                            onClick = { onOpenLesson(lesson.id.value) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterHeader(
    title: String,
    subtitle: String?,
    hasStory: Boolean = false,
    onOpenStory: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag(PackContentTestTags.CHAPTER_HEADER),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (hasStory) {
                androidx.compose.material3.TextButton(
                    onClick = onOpenStory,
                    modifier = Modifier.testTag(PACK_CHAPTER_STORY_TAG),
                ) { Text("📖 История") }
            }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LessonItem(title: String, cefr: String?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PackContentTestTags.LESSON_ITEM),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
            if (cefr != null) {
                Text(
                    text = cefr,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

package com.alexpo.grammermate.v2.feature.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alexpo.grammermate.v2.core.ui.collectState

/** Стабильные test-теги story reader. */
object StoryReaderTestTags {
    const val TEXT = "story_reader_text"
    const val ERROR = "story_reader_error"
}

/**
 * Экран чтения истории главы (срез 6 Фазы 4): маркеры сняты доменным
 * парсером, абзацы — прозаический скролл (TTS-озвучка — Фаза 5/audio).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryReaderScreen(
    onNavigateBack: () -> Unit,
    viewModel: StoryReaderViewModel = hiltViewModel(),
) {
    val state = collectState(viewModel.state)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        (state as? StoryReaderViewState.Content)?.title ?: "История",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        when (val s = state) {
            StoryReaderViewState.Loading -> Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            ) { Text("Загрузка…") }

            is StoryReaderViewState.Error -> Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    s.message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(StoryReaderTestTags.ERROR),
                )
                Button(onClick = viewModel::load) { Text("Повторить") }
            }

            is StoryReaderViewState.Content -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                s.paragraphs.forEach { paragraph ->
                    Text(
                        paragraph,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(StoryReaderTestTags.TEXT),
                    )
                }
            }
        }
    }
}

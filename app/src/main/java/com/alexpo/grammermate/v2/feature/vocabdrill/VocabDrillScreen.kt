package com.alexpo.grammermate.v2.feature.vocabdrill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alexpo.grammermate.v2.core.ui.collectState

/** Стабильные test-теги vocab drill. */
object VocabDrillTestTags {
    const val REVEAL_BUTTON = "vocab_drill_reveal_button"
    const val KNOW_BUTTON = "vocab_drill_know_button"
    const val DONT_KNOW_BUTTON = "vocab_drill_dont_know_button"
    const val DONE_LABEL = "vocab_drill_done_label"
}

/**
 * Экран vocab drill (Фаза 4 срез 3): Anki-style карточка слова —
 * Question (слово) → Revealed (перевод + знаю/не знаю) → … → Done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabDrillScreen(
    onNavigateBack: () -> Unit,
    viewModel: VocabDrillViewModel = hiltViewModel(),
) {
    val state = collectState(viewModel.state)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Словарь", style = MaterialTheme.typography.titleLarge) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = state) {
                VocabDrillViewState.Loading -> Text("Загрузка…")

                is VocabDrillViewState.Empty -> Text(
                    s.message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )

                is VocabDrillViewState.Error -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Не удалось загрузить", color = MaterialTheme.colorScheme.error)
                    Text(s.message, textAlign = TextAlign.Center)
                    Button(onClick = viewModel::loadBatch) { Text("Повторить") }
                }

                is VocabDrillViewState.Question -> {
                    Text(
                        "Слово ${s.index} / ${s.total}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(s.word.word, style = MaterialTheme.typography.displaySmall)
                    Text(
                        s.word.pos,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = viewModel::reveal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(VocabDrillTestTags.REVEAL_BUTTON),
                    ) { Text("Показать перевод") }
                }

                is VocabDrillViewState.Revealed -> {
                    Text(
                        "Слово ${s.index} / ${s.total}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(s.word.word, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        s.word.meaningRu ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Button(
                        onClick = { viewModel.answer(true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(VocabDrillTestTags.KNOW_BUTTON),
                    ) { Text("Знаю") }
                    OutlinedButton(
                        onClick = { viewModel.answer(false) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(VocabDrillTestTags.DONT_KNOW_BUTTON),
                    ) { Text("Не знаю") }
                }

                is VocabDrillViewState.Done -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Батч пройден!",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.testTag(VocabDrillTestTags.DONE_LABEL),
                    )
                    Text("Знаю: ${s.correct} из ${s.reviewed}")
                    Button(onClick = onNavigateBack) { Text("Готово") }
                }
            }
        }
    }
}

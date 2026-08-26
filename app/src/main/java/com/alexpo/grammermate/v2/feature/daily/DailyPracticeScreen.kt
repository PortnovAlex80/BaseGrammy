package com.alexpo.grammermate.v2.feature.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alexpo.grammermate.domain.model.DailyTask
import com.alexpo.grammermate.v2.core.ui.collectState

/** Стабильные test-теги дневной нормы. */
object DailyTestTags {
    const val PROMPT = "daily_prompt"
    const val INPUT_FIELD = "daily_input_field"
    const val CHECK_BUTTON = "daily_check_button"
    const val REVEAL_BUTTON = "daily_reveal_button"
    const val DONT_KNOW_BUTTON = "daily_dont_know_button"
    const val NEXT_BUTTON = "daily_next_button"
    const val DONE_LABEL = "daily_done_label"
}

/**
 * Экран дневной нормы (срез 4 Фазы 4): 3 блока одной лентой —
 * TRANSLATE/VOCAB/VERBS по композитору; прогресс «N/M» сверху.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyPracticeScreen(
    onNavigateBack: () -> Unit,
    viewModel: DailyPracticeViewModel = hiltViewModel(),
) {
    val state = collectState(viewModel.state)
    var answer by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Дневная норма", style = MaterialTheme.typography.titleLarge) },
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
                DailyViewState.Loading -> Text("Загрузка…")

                is DailyViewState.Empty -> Text(
                    s.message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )

                is DailyViewState.Error -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Не удалось загрузить", color = MaterialTheme.colorScheme.error)
                    Text(s.message, textAlign = TextAlign.Center)
                    Button(onClick = viewModel::load) { Text("Повторить") }
                }

                is DailyViewState.Question -> {
                    Header(s.index, s.total)
                    when (val task = s.task) {
                        is DailyTask.TranslateSentence -> {
                            Text(task.card.promptRu, style = MaterialTheme.typography.headlineSmall)
                            AnswerInput(answer, { answer = it }, viewModel, enabled = true)
                        }

                        is DailyTask.ConjugateVerb -> {
                            Text(task.card.promptRu, style = MaterialTheme.typography.headlineSmall)
                            AnswerInput(answer, { answer = it }, viewModel, enabled = true)
                        }

                        is DailyTask.VocabFlashcard -> {
                            Text(task.word.word, style = MaterialTheme.typography.displaySmall)
                            Text(
                                task.word.pos,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = viewModel::revealVocab,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(DailyTestTags.REVEAL_BUTTON),
                            ) { Text("Показать перевод") }
                        }
                    }
                }

                is DailyViewState.Answered -> {
                    Header(s.index, s.total)
                    Text(
                        if (s.correct) "Верно!" else "Неверно",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (s.correct) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                    s.correctAnswer?.let {
                        Text("Правильно: $it", style = MaterialTheme.typography.bodyLarge)
                    }
                    Button(
                        onClick = {
                            viewModel.next()
                            answer = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(DailyTestTags.NEXT_BUTTON),
                    ) { Text("Далее") }
                }

                is DailyViewState.Done -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Дневная норма выполнена!",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.testTag(DailyTestTags.DONE_LABEL),
                    )
                    Text("Верных: ${s.correct} из ${s.reviewed}")
                    Button(onClick = onNavigateBack) { Text("Готово") }
                }
            }
        }
    }
}

@Composable
private fun Header(index: Int, total: Int) {
    if (total > 0) {
        LinearProgressIndicator(
            progress = { (index - 1).toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "$index / $total",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AnswerInput(
    answer: String,
    onChange: (String) -> Unit,
    viewModel: DailyPracticeViewModel,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = answer,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DailyTestTags.INPUT_FIELD),
        label = { Text("Ваш ответ") },
        singleLine = true,
        enabled = enabled,
    )
    Button(
        onClick = { viewModel.submitAnswer(answer) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DailyTestTags.CHECK_BUTTON),
        enabled = enabled && answer.isNotBlank(),
    ) { Text("Проверить") }
}

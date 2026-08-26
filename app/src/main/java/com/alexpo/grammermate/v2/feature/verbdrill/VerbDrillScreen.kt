package com.alexpo.grammermate.v2.feature.verbdrill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alexpo.grammermate.v2.core.ui.collectState

/** Стабильные test-теги verb drill (journey-тесты ищут по тегам, не по тексту). */
object VerbDrillTestTags {
    const val INPUT_FIELD = "verb_drill_input_field"
    const val CHECK_BUTTON = "verb_drill_check_button"
    const val NEXT_BUTTON = "verb_drill_next_button"
    const val COMPLETED_LABEL = "verb_drill_completed_label"
}

/**
 * Экран verb drill (Фаза 4 срез 2): спряжение — промпт RU → ввод формы.
 * Та же FSM-модель, что Training: одна фаза, submit блокируется на commit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerbDrillScreen(
    onNavigateBack: () -> Unit,
    viewModel: VerbDrillViewModel = hiltViewModel(),
) {
    val state = collectState(viewModel.state)
    var answer by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Спряжение", style = MaterialTheme.typography.titleLarge) },
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
                VerbDrillViewState.Loading -> Text("Загрузка…")

                is VerbDrillViewState.Empty -> Text(
                    s.message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )

                is VerbDrillViewState.Error -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Не удалось сохранить", color = MaterialTheme.colorScheme.error)
                    Text(s.message, textAlign = TextAlign.Center)
                    Button(onClick = viewModel::reload) { Text("Повторить") }
                }

                is VerbDrillViewState.Checking -> Text("Проверка…")

                is VerbDrillViewState.Active -> {
                    Text(
                        "Карточка ${s.answeredCards + 1} / ${s.totalCards}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(s.promptRu, style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(VerbDrillTestTags.INPUT_FIELD),
                        label = { Text("Форма глагола") },
                        singleLine = true,
                    )
                    Button(
                        onClick = { viewModel.submitAnswer(answer) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(VerbDrillTestTags.CHECK_BUTTON),
                        enabled = answer.isNotBlank(),
                    ) { Text("Проверить") }
                }

                is VerbDrillViewState.Feedback -> {
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
                            .testTag(VerbDrillTestTags.NEXT_BUTTON),
                    ) { Text("Далее") }
                }

                is VerbDrillViewState.Completed -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Спряжение пройдено!",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.testTag(VerbDrillTestTags.COMPLETED_LABEL),
                    )
                    Text("Верных: ${s.correctCount} из ${s.totalCards}")
                    Button(onClick = onNavigateBack) { Text("Готово") }
                }
            }
        }
    }
}

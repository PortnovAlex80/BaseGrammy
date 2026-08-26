package com.alexpo.grammermate.v2.feature.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alexpo.grammermate.domain.model.Card
import com.alexpo.grammermate.v2.core.ui.collectEffects
import com.alexpo.grammermate.v2.core.ui.collectState

/**
 * Стабильные test-tags основного UX-пути тренировки (Фаза 0 плана
 * стабилизации 2026-08-26: clickable/journey тесты ищут элементы по тегам,
 * а не по тексту локали).
 */
object TrainingTestTags {
    const val PROMPT_CARD = "training_prompt_card"
    const val INPUT_FIELD = "training_input_field"
    const val CHECK_BUTTON = "training_check_button"
    const val NEXT_BUTTON = "training_next_button"
    const val HINT_BUTTON = "training_hint_button"
    const val SKIP_BUTTON = "training_skip_button"
    const val REPORT_BUTTON = "training_report_button"
    const val COMPLETED_LABEL = "training_completed_label"
    const val RETRY_BUTTON = "training_retry_button"
}

/**
 * Экран тренировки — прохождение карточек урока (Фаза 1: golden journey).
 *
 * Presentation-слой: читает FSM [TrainingViewState] из [TrainingViewModel] через
 * [collectState] и рендерит ровно одну фазу. Черновик ответа живёт в state
 * (`Active.draft`, персистится VM в SavedStateHandle) — rotation/process death
 * не теряют ввод. Повторные действия во время commit невозможны: `Checking`
 * не содержит кнопок отправки (правило плана §3.1.5/§3.1.6).
 *
 * @param packId   пак тренировки (из nav-args; ViewModel также читает из SavedStateHandle).
 * @param lessonId урок тренировки (аналогично).
 * @param onNavigateBack колбэк навигации «назад».
 * @param viewModel тренировочный ViewModel (Hilt-injected).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    packId: String,
    lessonId: String,
    onNavigateBack: () -> Unit,
    viewModel: TrainingViewModel = hiltViewModel(),
) {
    val state = collectState(viewModel.state)

    collectEffects(viewModel.effects) { effect ->
        when (effect) {
            TrainingEffect.NavigateBack -> onNavigateBack()
            is TrainingEffect.ShowToast -> { /* TODO(Фаза 3): SnackbarHostState.showSnackbar */ }
            is TrainingEffect.PlayTts -> { /* TODO(Фаза 5): TTS после session commit */ }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Тренировка", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
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
        when (val s = state) {
            TrainingViewState.Loading -> LoadingState(Modifier.padding(innerPadding))

            is TrainingViewState.Empty -> EmptyState(
                message = s.message,
                onBack = { viewModel.navigateBack() },
                modifier = Modifier.padding(innerPadding),
            )

            is TrainingViewState.Active -> TrainingContent(
                phase = s,
                onDraftChange = viewModel::onDraftChange,
                onSubmit = viewModel::submitAnswer,
                onHint = viewModel::requestHint,
                onSkip = viewModel::skip,
                onReport = viewModel::flagCard,
                contentPadding = innerPadding,
            )

            is TrainingViewState.Checking -> CheckingContent(
                phase = s,
                contentPadding = innerPadding,
            )

            is TrainingViewState.Feedback -> FeedbackContent(
                phase = s,
                onNext = viewModel::next,
                onReport = viewModel::flagCard,
                contentPadding = innerPadding,
            )

            is TrainingViewState.Completed -> CompletedState(
                phase = s,
                onFinish = { viewModel.navigateBack() },
                modifier = Modifier.padding(innerPadding),
            )

            is TrainingViewState.Error -> ErrorState(
                message = s.message,
                onRetry = viewModel::reload,
                onBack = { viewModel.navigateBack() },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

// ── Фаза Active ──────────────────────────────────────────────────────────────

/** Активная фаза: прогресс + карточка-промпт + ввод + действия. */
@Composable
private fun TrainingContent(
    phase: TrainingViewState.Active,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onHint: () -> Unit,
    onSkip: () -> Unit,
    onReport: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SessionProgress(phase.answeredCards, phase.totalCards)
        PromptCard(card = phase.card, showHint = phase.showHint, feedback = null)

        OutlinedTextField(
            value = phase.draft,
            onValueChange = onDraftChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TrainingTestTags.INPUT_FIELD),
            label = { Text("Ваш ответ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (phase.draft.isNotBlank()) onSubmit() }),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TrainingTestTags.CHECK_BUTTON),
                enabled = phase.draft.isNotBlank(),
            ) {
                Text("Проверить")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onHint,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TrainingTestTags.HINT_BUTTON),
                ) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = null)
                    Text("  Подсказка")
                }
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TrainingTestTags.SKIP_BUTTON),
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = null)
                    Text("  Пропустить")
                }
            }

            TextButton(
                onClick = onReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TrainingTestTags.REPORT_BUTTON),
            ) {
                Icon(Icons.Filled.Flag, contentDescription = null)
                Text("  Сообщить о проблеме")
            }
        }
    }
}

// ── Фаза Checking ────────────────────────────────────────────────────────────

/** Commit ответа: карточка видна, ввод и кнопки заблокированы. */
@Composable
private fun CheckingContent(
    phase: TrainingViewState.Checking,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SessionProgress(phase.answeredCards, phase.totalCards)
        PromptCard(card = phase.card, showHint = false, feedback = null)
        OutlinedTextField(
            value = "",
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TrainingTestTags.INPUT_FIELD),
            label = { Text("Проверяем…") },
            singleLine = true,
            enabled = false,
            readOnly = true,
        )
        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.padding(4.dp).size(20.dp),
                strokeWidth = 2.dp,
            )
            Text("  Проверяем…")
        }
    }
}

// ── Фаза Feedback ────────────────────────────────────────────────────────────

/** Ответ зафиксирован: ✓/✗ + правильный ответ + Next (или завершение урока). */
@Composable
private fun FeedbackContent(
    phase: TrainingViewState.Feedback,
    onNext: () -> Unit,
    onReport: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SessionProgress(phase.answeredCards, phase.totalCards)
        PromptCard(card = phase.card, showHint = false, feedback = phase.result)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val (icon, text, color) = if (phase.result.correct) {
                Triple(Icons.Filled.Check, "Верно!", MaterialTheme.colorScheme.primary)
            } else {
                Triple(Icons.Filled.Close, "Неверно", MaterialTheme.colorScheme.error)
            }
            Icon(icon, contentDescription = null, tint = color)
            Text(text, style = MaterialTheme.typography.titleMedium, color = color)
        }
        if (!phase.result.correct && phase.correctAnswer != null) {
            Text(
                text = "Правильный ответ: ${phase.correctAnswer}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TrainingTestTags.NEXT_BUTTON),
        ) {
            Icon(Icons.Filled.SkipNext, contentDescription = null)
            Text(if (phase.isLastCard) "  Завершить урок" else "  Далее")
        }

        TextButton(
            onClick = onReport,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TrainingTestTags.REPORT_BUTTON),
        ) {
            Icon(Icons.Filled.Flag, contentDescription = null)
            Text("  Сообщить о проблеме")
        }
    }
}

// ── Общие компоненты ─────────────────────────────────────────────────────────

/** Прогресс сессии: `answered / total` + линейный индикатор. */
@Composable
private fun SessionProgress(answered: Int, total: Int) {
    val progress = if (total > 0) answered.toFloat() / total.toFloat() else 0f
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = "$answered / $total",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Карточка с промптом упражнения + опциональная подсказка/фидбек. */
@Composable
private fun PromptCard(card: Card, showHint: Boolean, feedback: AnswerResult?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TrainingTestTags.PROMPT_CARD),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Переведите:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = card.promptRu,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val meta = listOfNotNull(card.tense, card.verb, card.person)
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }

            if (showHint && card.acceptedAnswers.isNotEmpty()) {
                Text(
                    text = "Подсказка: ${masked(card.acceptedAnswers.first())}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

/** Состояние загрузки — центрированный спиннер. */
@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Урок без карточек — явный результат, без fallback. */
@Composable
private fun EmptyState(message: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(onClick = onBack) { Text("К урокам") }
        }
    }
}

/** Урок завершён: итоги прохода + возврат к списку уроков. */
@Composable
private fun CompletedState(
    phase: TrainingViewState.Completed,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "Урок завершён!",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(TrainingTestTags.COMPLETED_LABEL),
            )
            Text(
                text = "Верных: ${phase.correctCount} из ${phase.totalCards}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Ошибок: ${phase.incorrectCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onFinish) { Text("К урокам") }
        }
    }
}

/** Recoverable-ошибка: Retry повторяет операцию, Back выходит. */
@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "Не удалось сохранить",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.testTag(TrainingTestTags.RETRY_BUTTON),
            ) { Text("Повторить") }
            TextButton(onClick = onBack) { Text("Назад") }
        }
    }
}

/** Маскировка подсказки: первый символ + подчёркивания по длине слова. */
private fun masked(answer: String): String =
    if (answer.length <= 1) answer
    else "${answer.first()}" + "_".repeat(answer.length - 1)

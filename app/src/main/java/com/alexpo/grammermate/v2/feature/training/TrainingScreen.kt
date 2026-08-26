package com.alexpo.grammermate.v2.feature.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
}

/**
 * Экран тренировки — прохождение карточек урока.
 *
 * Presentation-слой чистой архитектуры: читает [TrainingViewState] из
 * [TrainingViewModel] через [collectState] (lifecycle-aware) и обрабатывает
 * one-shot эффекты через [collectEffects] (навигация/snackbar). Локальное UI-state
 * (введённый ответ) хойстится в `remember { mutableStateOf }` — это эфемерный
 * ввод, не требующий персистенции.
 *
 * Цикл взаимодействия:
 *  * `promptRu` карточки → `OutlinedTextField` → **Submit** → проверка →
 *    мгновенная обратная связь (`lastResult`) → **Next** → следующая карточка.
 *  * **Hint** — показать/скрыть подсказку (`showHint`).
 *  * **Skip** — перейти к следующей карточке без засчитывания ответа.
 *  * **Report** — флаг «плохое» предложение (TODO: persist через UserContentRepository).
 *  * **Back** — выход (через [onNavigateBack]).
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

    // Эфемерный ввод ответа — хойстится локально, не персистится.
    var answer by remember { mutableStateOf("") }

    // One-shot эффекты: навигация «назад».
    collectEffects(viewModel.effects) { effect ->
        when (effect) {
            TrainingEffect.NavigateBack -> onNavigateBack()
            is TrainingEffect.ShowToast -> { /* TODO(Фаза 7): SnackbarHostState.showSnackbar */ }
            is TrainingEffect.PlayTts -> { /* TODO(Фаза 7): TTS-движок */ }
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
        when {
            state.isLoading -> LoadingState(Modifier.padding(innerPadding))
            state.error != null -> ErrorState(
                message = state.error!!,
                onRetry = { viewModel.navigateBack() },
                modifier = Modifier.padding(innerPadding),
            )

            else -> TrainingContent(
                state = state,
                answer = answer,
                onAnswerChange = { answer = it },
                onSubmit = {
                    viewModel.onSubmitAnswer(answer)
                },
                onNext = {
                    viewModel.onNextCard()
                    answer = ""
                },
                onHint = { viewModel.requestHint() },
                onSkip = {
                    viewModel.onNextCard()
                    answer = ""
                },
                onReport = { viewModel.flagCard() },
                contentPadding = innerPadding,
            )
        }
    }
}

/**
 * Основной контент тренировки: прогресс + карточка-промпт + ввод + действия.
 */
@Composable
private fun TrainingContent(
    state: TrainingViewState,
    answer: String,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onHint: () -> Unit,
    onSkip: () -> Unit,
    onReport: () -> Unit,
    contentPadding: PaddingValues,
) {
    val card = state.currentCard
    val progress = if (state.totalCards > 0) {
        state.answeredCards.toFloat() / state.totalCards.toFloat()
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Прогресс сессии ───────────────────────────────────────────────────
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${state.answeredCards} / ${state.totalCards}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (card != null) {
            PromptCard(card = card, showHint = state.showHint, lastResult = state.lastResult)

            // ── Поле ввода ответа ──────────────────────────────────────────────
            OutlinedTextField(
                value = answer,
                onValueChange = onAnswerChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TrainingTestTags.INPUT_FIELD),
                label = { Text("Ваш ответ") },
                singleLine = true,
                enabled = state.lastResult == null,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (answer.isNotBlank()) onSubmit() }),
            )

            // ── Мгновенная обратная связь ──────────────────────────────────────
            FeedbackBanner(lastResult = state.lastResult)

            // ── Действия ───────────────────────────────────────────────────────
            ActionRow(
                lastResult = state.lastResult,
                canSubmit = answer.isNotBlank(),
                onSubmit = onSubmit,
                onNext = onNext,
                onHint = onHint,
                onSkip = onSkip,
                onReport = onReport,
            )
        } else {
            Text(
                text = "В уроке нет карточек.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Карточка с промптом упражнения.
 *
 * Показывает `promptRu` (что перевести/спрягать) крупным текстом, контекстные
 * мета-поля (verb/tense/person при наличии) и подсказку (первый принимаемый
 * ответ с замаскированной первой буквой), если `showHint`.
 */
@Composable
private fun PromptCard(card: Card, showHint: Boolean, lastResult: AnswerResult?) {
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

            // Контекстные мета-теги для drill-карточек.
            val meta = listOfNotNull(card.tense, card.verb, card.person)
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }

            // Подсказка: замаскированная подсказка по первому принимаемому ответу.
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

/**
 * Баннер мгновенной обратной связи (✓ верно / ✗ неверно + правильный ответ).
 *
 * Чистая проекция `lastResult` из state: его безопасно перерисовывать.
 */
@Composable
private fun FeedbackBanner(lastResult: AnswerResult?) {
    if (lastResult == null) return
    val contentColor = if (lastResult.correct) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    val icon = if (lastResult.correct) Icons.Filled.Check else Icons.Filled.Close
    val text = if (lastResult.correct) "Верно!" else "Неверно"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = contentColor)
        Text(text, style = MaterialTheme.typography.titleMedium, color = contentColor)
    }
}

/**
 * Ряд действий: Submit/Next + Hint/Skip/Report.
 *
 * Когда ответ отправлен (`lastResult != null`) — главная кнопка становится Next,
 * иначе — Submit. Вторичные действия — текстовые кнопки в ряду ниже.
 */
@Composable
private fun ActionRow(
    lastResult: AnswerResult?,
    canSubmit: Boolean,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onHint: () -> Unit,
    onSkip: () -> Unit,
    onReport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (lastResult == null) {
            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TrainingTestTags.CHECK_BUTTON),
                enabled = canSubmit,
            ) {
                Text("Проверить")
            }
        } else {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TrainingTestTags.NEXT_BUTTON),
            ) {
                Icon(Icons.Filled.SkipNext, contentDescription = null)
                Text("  Далее")
            }
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
                enabled = lastResult == null,
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

/** Состояние загрузки — центрированный спиннер. */
@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Состояние ошибки — сообщение + кнопка возврата. */
@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "Не удалось загрузить тренировку",
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
            OutlinedButton(onClick = onRetry) { Text("Назад") }
        }
    }
}

/** Маскировка подсказки: первый символ + подчёркивания по длине слова. */
private fun masked(answer: String): String =
    if (answer.length <= 1) answer
    else "${answer.first()}" + "_".repeat(answer.length - 1)

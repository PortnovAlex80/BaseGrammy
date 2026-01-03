package com.alexpo.grammermate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingMode
import com.alexpo.grammermate.data.InputMode
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent

@Composable
fun GrammarMateApp() {
    GrammarMateTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val vm: TrainingViewModel = viewModel()
            val state by vm.uiState.collectAsState()
            TrainingScreen(
                state = state,
                onInputChange = vm::onInputChanged,
                onSubmit = vm::submitAnswer,
                onPrev = vm::prevCard,
                onNext = vm::nextCard,
                onTogglePause = vm::togglePause,
                onFinish = vm::finishSession,
                onOpenSettings = vm::pauseSession,
                onSelectLanguage = vm::selectLanguage,
                onSelectLesson = vm::selectLesson,
                onSelectMode = vm::selectMode,
                onSetInputMode = vm::setInputMode,
                onImportLesson = vm::importLesson,
                onDeleteAllLessons = vm::deleteAllLessons
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainingScreen(
    state: TrainingUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> SubmitResult,
    onPrev: () -> Unit,
    onNext: (Boolean) -> Unit,
    onTogglePause: () -> Unit,
    onFinish: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSelectLesson: (String) -> Unit,
    onSelectMode: (TrainingMode) -> Unit,
    onSetInputMode: (InputMode) -> Unit,
    onImportLesson: (android.net.Uri) -> Unit,
    onDeleteAllLessons: () -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val hasCards = state.lessons.any { it.cards.isNotEmpty() }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportLesson(uri)
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "GrammarMate",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showSheet = true }) {
                    onOpenSettings()
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderStats(state)
            ModeSelector(state.mode, onSelectMode)
            CardPrompt(state)
            AnswerBox(state, onInputChange, onSubmit, onSetInputMode)
            ResultBlock(state, onNext, state.inputMode)
            NavigationRow(onPrev, onNext, onTogglePause, onFinish, state.sessionState, hasCards)
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Служебный режим",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LanguageLessonColumn(state, onSelectLanguage, onSelectLesson)
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("text/*", "text/csv")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Импорт урока (CSV)")
                }
                OutlinedButton(
                    onClick = { onDeleteAllLessons() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB00020))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Удалить все уроки")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "CSV формат", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "UTF-8, разделитель ';'.\n" +
                        "Колонка 1: RU предложение.\n" +
                        "Колонка 2: перевод(ы), варианты через '+'.\n" +
                        "Пример: Он не работает из дома;He doesn't work from home+He does not work from home",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Инструкция", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "Play — старт/продолжение, Pause — пауза, Stop — завершение урока и сброс таймера/статистики.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Уроки", style = MaterialTheme.typography.labelLarge)
                LazyColumn(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                    items(state.lessons) { lesson ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = lesson.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (lesson.id == state.selectedLessonId) {
                                Text(
                                    text = "Выбран",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun HeaderStats(state: TrainingUiState) {
    val total = state.lessons.flatMap { it.cards }.size.coerceAtLeast(1)
    val current = (state.currentIndex + 1).coerceAtMost(total)
    val speed = speedPerMinute(state.activeTimeMs, state.correctCount)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = "Прогресс")
            Text(text = "$current из $total", fontWeight = FontWeight.SemiBold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "Время")
            Text(text = formatTime(state.activeTimeMs), fontWeight = FontWeight.SemiBold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "?/мин")
            Text(text = speed, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ModeSelector(mode: TrainingMode, onSelectMode: (TrainingMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeIconButton(
            selected = mode == TrainingMode.LESSON,
            icon = Icons.Default.MenuBook,
            contentDescription = "Урок"
        ) { onSelectMode(TrainingMode.LESSON) }
        ModeIconButton(
            selected = mode == TrainingMode.ALL_SEQUENTIAL,
            icon = Icons.Default.LibraryBooks,
            contentDescription = "Все уроки"
        ) { onSelectMode(TrainingMode.ALL_SEQUENTIAL) }
        ModeIconButton(
            selected = mode == TrainingMode.ALL_MIXED,
            icon = Icons.Default.SwapHoriz,
            contentDescription = "Mixed"
        ) { onSelectMode(TrainingMode.ALL_MIXED) }
    }
}

@Composable
private fun ModeIconButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    if (selected) {
        FilledTonalIconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription)
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}

@Composable
private fun LanguageLessonColumn(
    state: TrainingUiState,
    onSelectLanguage: (String) -> Unit,
    onSelectLesson: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DropdownSelector(
            title = "Язык",
            selected = state.languages.firstOrNull { it.id == state.selectedLanguageId }?.displayName
                ?: "-",
            items = state.languages.map { it.displayName to it.id },
            onSelect = onSelectLanguage
        )
        DropdownSelector(
            title = "Урок",
            selected = state.lessons.firstOrNull { it.id == state.selectedLessonId }?.title ?: "-",
            items = state.lessons.map { it.title to it.id },
            onSelect = onSelectLesson
        )
    }
}

@Composable
private fun DropdownSelector(
    title: String,
    selected: String,
    items: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(text = selected, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (label, id) ->
                DropdownMenuItem(
                    text = { Text(text = label) },
                    onClick = {
                        expanded = false
                        onSelect(id)
                    }
                )
            }
        }
    }
}

@Composable
private fun CardPrompt(state: TrainingUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "RU", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.currentCard?.promptRu ?: "Нет карточек",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AnswerBox(
    state: TrainingUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> SubmitResult,
    onSetInputMode: (InputMode) -> Unit
) {
    val latestState by rememberUpdatedState(state)
    val canLaunchVoice = state.currentCard != null && state.sessionState != SessionState.PAUSED
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                if (latestState.sessionState != SessionState.ACTIVE) return@rememberLauncherForActivityResult
                onInputChange(spoken)
                onSubmit()
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(
        state.currentCard?.id,
        state.inputMode,
        state.sessionState,
        state.voiceTriggerToken
    ) {
        if (state.inputMode == InputMode.VOICE &&
            state.sessionState == SessionState.ACTIVE &&
            state.currentCard != null
        ) {
            kotlinx.coroutines.delay(200)
            launchVoiceRecognition(state.selectedLanguageId, state.currentCard?.promptRu, speechLauncher)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.inputText,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Ваш перевод") },
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (canLaunchVoice) {
                            onSetInputMode(InputMode.VOICE)
                            launchVoiceRecognition(state.selectedLanguageId, state.currentCard?.promptRu, speechLauncher)
                        }
                    },
                    enabled = canLaunchVoice
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice input")
                }
            }
        )
        if (state.inputMode == InputMode.VOICE) {
            Text(
                text = state.currentCard?.promptRu?.let { "Скажите перевод: $it" } ?: "",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(
                    onClick = {
                        if (canLaunchVoice) {
                            onSetInputMode(InputMode.VOICE)
                            launchVoiceRecognition(state.selectedLanguageId, state.currentCard?.promptRu, speechLauncher)
                        }
                    },
                    enabled = canLaunchVoice
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice mode")
                }
                FilledTonalIconButton(onClick = { onSetInputMode(InputMode.KEYBOARD) }) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Keyboard mode")
                }
            }
            Text(
                text = if (state.inputMode == InputMode.VOICE) "Голос" else "Клавиатура",
                style = MaterialTheme.typography.labelMedium
            )
        }
        Button(
            onClick = { onSubmit() },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.inputText.isNotBlank() &&
                state.sessionState == SessionState.ACTIVE &&
                state.currentCard != null
        ) {
            Text(text = "Проверить")
        }
    }
}

@Composable
private fun ResultBlock(state: TrainingUiState, onNext: (Boolean) -> Unit, inputMode: InputMode) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (state.lastResult) {
            true -> Text(text = "Верно", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
            false -> Text(text = "Ошибка", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
            null -> Text(text = "")
        }
        if (!state.hintText.isNullOrBlank()) {
            Text(text = "Подсказка: ${state.hintText}")
            TextButton(onClick = { onNext(inputMode == InputMode.VOICE) }) { Text(text = "Следующая") }
        }
        state.lastRating?.let { rating ->
            Text(text = "Рейтинг: ${String.format("%.1f", rating)} ?/мин")
        }
        Text(text = "Правильных: ${state.correctCount}  Неправильных: ${state.incorrectCount}")
    }
}

@Composable
private fun NavigationRow(
    onPrev: () -> Unit,
    onNext: (Boolean) -> Unit,
    onTogglePause: () -> Unit,
    onFinish: () -> Unit,
    state: SessionState,
    hasCards: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev, enabled = hasCards) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Prev")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onTogglePause, enabled = hasCards) {
                if (state == SessionState.ACTIVE) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
            }
            IconButton(onClick = onFinish, enabled = hasCards) {
                Icon(Icons.Default.StopCircle, contentDescription = "Finish lesson")
            }
            IconButton(onClick = { onNext(false) }, enabled = hasCards) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next")
            }
        }
    }
}

private fun formatTime(activeMs: Long): String {
    val totalSeconds = activeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun speedPerMinute(activeMs: Long, correct: Int): String {
    val minutes = activeMs / 60000.0
    if (minutes <= 0.0) return "-"
    return String.format("%.1f", correct / minutes)
}

private fun launchVoiceRecognition(
    languageId: String,
    prompt: String?,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val languageTag = when (languageId) {
        "it" -> "it-IT"
        else -> "en-US"
    }
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt ?: "Говорите перевод")
    }
    launcher.launch(intent)
}

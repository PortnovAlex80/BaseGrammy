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
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
                onCloseSettings = vm::resumeFromSettings,
                onSelectLanguage = vm::selectLanguage,
                onSelectLesson = vm::selectLesson,
                onSelectMode = vm::selectMode,
                onSetInputMode = vm::setInputMode,
                onShowAnswer = vm::showAnswer,
                onImportLesson = vm::importLesson,
                onResetReload = vm::resetAndImportLesson,
                onDeleteLesson = vm::deleteLesson,
                onDeleteAllLessons = vm::deleteAllLessons,
                onCreateEmptyLesson = vm::createEmptyLesson,
                onAddLanguage = vm::addLanguage
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
    onCloseSettings: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSelectLesson: (String) -> Unit,
    onSelectMode: (TrainingMode) -> Unit,
    onSetInputMode: (InputMode) -> Unit,
    onShowAnswer: () -> Unit,
    onImportLesson: (android.net.Uri) -> Unit,
    onResetReload: (android.net.Uri) -> Unit,
    onDeleteLesson: (String) -> Unit,
    onDeleteAllLessons: () -> Unit,
    onCreateEmptyLesson: (String) -> Unit,
    onAddLanguage: (String) -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val hasCards = state.currentCard != null
    var newLessonTitle by remember { mutableStateOf("") }
    var newLanguageName by remember { mutableStateOf("") }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportLesson(uri)
    }
    val resetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onResetReload(uri)
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
                IconButton(onClick = {
                    onOpenSettings()
                    showSheet = true
                }) {
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
            ModeSelector(state, onSelectMode, onSelectLesson)
            CardPrompt(state)
            AnswerBox(state, onInputChange, onSubmit, onSetInputMode, onShowAnswer, hasCards)
            ResultBlock(state, onNext, state.inputMode)
            NavigationRow(onPrev, onNext, onTogglePause, onFinish, state.sessionState, hasCards)
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                if (hasCards) onCloseSettings()
            },
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
                LanguageLessonColumn(state, onSelectLanguage, onSelectLesson, onDeleteLesson)
                OutlinedTextField(
                    value = newLanguageName,
                    onValueChange = { newLanguageName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Новый язык (название)") }
                )
                OutlinedButton(
                    onClick = {
                        val name = newLanguageName.trim()
                        if (name.isNotEmpty()) {
                            onAddLanguage(name)
                            newLanguageName = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Добавить язык")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("text/*", "text/csv")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Импорт урока (CSV)")
                }
                OutlinedButton(
                    onClick = { resetLauncher.launch(arrayOf("text/*", "text/csv")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Reset/Reload (очистить + импорт)")
                }
                OutlinedTextField(
                    value = newLessonTitle,
                    onValueChange = { newLessonTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Пустой урок (название)") }
                )
                OutlinedButton(
                    onClick = {
                        val title = newLessonTitle.trim()
                        if (title.isNotEmpty()) {
                            onCreateEmptyLesson(title)
                            newLessonTitle = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Создать пустой урок")
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
                    text = "Play - старт/продолжение, Pause - пауза, Stop - сброс прогресса без обнуления рейтинга.",
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
    val total = when (state.mode) {
        TrainingMode.LESSON -> {
            state.lessons.firstOrNull { it.id == state.selectedLessonId }?.cards?.size ?: 0
        }
        TrainingMode.ALL_SEQUENTIAL,
        TrainingMode.ALL_MIXED -> state.lessons.sumOf { it.cards.size }
    }
    val current = if (total > 0) (state.currentIndex + 1).coerceAtMost(total) else 0
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
private fun ModeSelector(
    state: TrainingUiState,
    onSelectMode: (TrainingMode) -> Unit,
    onSelectLesson: (String) -> Unit
) {
    var lessonExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ModeIconButton(
                selected = state.mode == TrainingMode.LESSON,
                icon = Icons.Default.MenuBook,
                contentDescription = "Урок"
            ) {
                onSelectMode(TrainingMode.LESSON)
                lessonExpanded = true
            }
            DropdownMenu(
                expanded = lessonExpanded,
                onDismissRequest = { lessonExpanded = false }
            ) {
                if (state.lessons.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(text = "Нет уроков") },
                        onClick = { lessonExpanded = false }
                    )
                } else {
                    state.lessons.forEach { lesson ->
                        DropdownMenuItem(
                            text = { Text(text = lesson.title) },
                            onClick = {
                                lessonExpanded = false
                                onSelectLesson(lesson.id)
                            }
                        )
                    }
                }
            }
        }
        ModeIconButton(
            selected = state.mode == TrainingMode.ALL_SEQUENTIAL,
            icon = Icons.Default.LibraryBooks,
            contentDescription = "Все уроки"
        ) { onSelectMode(TrainingMode.ALL_SEQUENTIAL) }
        ModeIconButton(
            selected = state.mode == TrainingMode.ALL_MIXED,
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
    onSelectLesson: (String) -> Unit,
    onDeleteLesson: (String) -> Unit
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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "Удалить урок", style = MaterialTheme.typography.labelMedium)
            state.lessons.forEach { lesson ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = lesson.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onDeleteLesson(lesson.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete lesson")
                    }
                }
            }
        }
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
@OptIn(ExperimentalMaterial3Api::class)
private fun AnswerBox(
    state: TrainingUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> SubmitResult,
    onSetInputMode: (InputMode) -> Unit,
    onShowAnswer: () -> Unit,
    hasCards: Boolean
) {
    val latestState by rememberUpdatedState(state)
    val canLaunchVoice = hasCards && state.sessionState == SessionState.ACTIVE
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                if (latestState.sessionState == SessionState.PAUSED) return@rememberLauncherForActivityResult
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
            enabled = hasCards,
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
        if (!hasCards) {
            Text(
                text = "Карточек нет",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (state.inputMode == InputMode.VOICE && state.sessionState == SessionState.ACTIVE) {
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(text = "Показать ответ") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(
                        onClick = { if (hasCards) onShowAnswer() },
                        enabled = hasCards
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = "Показать ответ")
                    }
                }
                Text(
                    text = if (state.inputMode == InputMode.VOICE) "Голос" else "Клавиатура",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Button(
            onClick = { onSubmit() },
            modifier = Modifier.fillMaxWidth(),
            enabled = hasCards &&
                state.inputText.isNotBlank() &&
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
        if (!state.answerText.isNullOrBlank()) {
            Text(text = "Ответ: ${state.answerText}")
        }
        state.lastRating?.let { rating ->
            Text(text = "Рейтинг: ${String.format("%.1f", rating)} ?/мин")
        }
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

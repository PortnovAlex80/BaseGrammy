package com.alexpo.grammermate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.alexpo.grammermate.data.Normalizer
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillSessionState
import com.alexpo.grammermate.data.VerbDrillUiState

@Composable
fun VerbDrillScreen(
    viewModel: VerbDrillViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    if (state.session != null) {
        VerbDrillSessionScreen(
            session = state.session!!,
            onSubmit = viewModel::submitAnswer,
            onNextBatch = viewModel::nextBatch,
            onExit = viewModel::exitSession
        )
    } else {
        VerbDrillSelectionScreen(
            state = state,
            onSelectTense = viewModel::selectTense,
            onSelectGroup = viewModel::selectGroup,
            onStart = viewModel::startSession,
            onBack = onBack
        )
    }
}

@Composable
private fun VerbDrillSelectionScreen(
    state: VerbDrillUiState,
    onSelectTense: (String?) -> Unit,
    onSelectGroup: (String?) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Verb Drill", fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (state.availableTenses.isNotEmpty()) {
            TenseDropdown(
                selectedTense = state.selectedTense,
                availableTenses = state.availableTenses,
                onSelect = onSelectTense
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (state.availableGroups.isNotEmpty()) {
            GroupDropdown(
                selectedGroup = state.selectedGroup,
                availableGroups = state.availableGroups,
                onSelect = onSelectGroup
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (state.totalCards > 0) {
            Text(
                text = "Прогресс: ${state.everShownCount} / ${state.totalCards}"
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Сегодня: ${state.todayShownCount}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = if (state.totalCards > 0) {
                    state.everShownCount.toFloat() / state.totalCards.toFloat()
                } else 0f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (state.allDoneToday) {
            Text(
                text = "На сегодня всё!",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else if (state.totalCards > 0 || state.availableTenses.isNotEmpty() || state.availableGroups.isNotEmpty()) {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (state.todayShownCount > 0) "Продолжить" else "Старт"
                )
            }
        }
    }
}

@Composable
private fun TenseDropdown(
    selectedTense: String?,
    availableTenses: List<String>,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = selectedTense ?: "Все времена"
    Column {
        Text(
            text = "Время:",
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = { expanded = true }) {
            Text(text = label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(text = "Все времена") },
                onClick = {
                    expanded = false
                    onSelect(null)
                }
            )
            availableTenses.forEach { tense ->
                DropdownMenuItem(
                    text = { Text(text = tense) },
                    onClick = {
                        expanded = false
                        onSelect(tense)
                    }
                )
            }
        }
    }
}

@Composable
private fun GroupDropdown(
    selectedGroup: String?,
    availableGroups: List<String>,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = selectedGroup ?: "Все группы"
    Column {
        Text(
            text = "Группа:",
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = { expanded = true }) {
            Text(text = label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(text = "Все группы") },
                onClick = {
                    expanded = false
                    onSelect(null)
                }
            )
            availableGroups.forEach { group ->
                DropdownMenuItem(
                    text = { Text(text = group) },
                    onClick = {
                        expanded = false
                        onSelect(group)
                    }
                )
            }
        }
    }
}

@Composable
private fun VerbDrillSessionScreen(
    session: VerbDrillSessionState,
    onSubmit: (String) -> Unit,
    onNextBatch: () -> Unit,
    onExit: () -> Unit
) {
    // Track the pending answer state locally
    var inputText by remember { mutableStateOf("") }
    var answeredCard by remember { mutableStateOf<VerbDrillCard?>(null) }
    var wasCorrect by remember { mutableStateOf(false) }

    if (session.isComplete) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "🎉", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Отлично!",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Правильных: ${session.correctCount}  |  Ошибок: ${session.incorrectCount}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    answeredCard = null
                    inputText = ""
                    onNextBatch()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Дальше")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Выход")
            }
        }
        return
    }

    val currentCard = session.cards.getOrElse(session.currentIndex) { null }
    val showingResult = answeredCard != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Verb Drill", fontWeight = FontWeight.SemiBold)
        }

        Text(
            text = "${session.currentIndex + 1} / ${session.cards.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        LinearProgressIndicator(
            progress = (session.currentIndex + 1).toFloat() / session.cards.size.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )

        // Show the prompt from the current card (or the answered card while showing result)
        val displayCard = if (showingResult) answeredCard else currentCard
        if (displayCard != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "RU",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = displayCard.promptRu,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (showingResult) {
            // Show result for the answered card
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (wasCorrect) {
                    Text(
                        text = "Правильно",
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "Неправильно",
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = "Ответ: ${answeredCard?.answer}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    answeredCard = null
                    inputText = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Дальше")
            }
        } else if (currentCard != null) {
            // Input mode
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Answer") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (inputText.isNotBlank()) {
                            onSubmit(inputText)
                            answeredCard = currentCard
                            wasCorrect = Normalizer.normalize(inputText) == Normalizer.normalize(currentCard.answer)
                            inputText = ""
                        }
                    }
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        answeredCard = currentCard
                        wasCorrect = Normalizer.normalize(inputText) == Normalizer.normalize(currentCard.answer)
                        onSubmit(inputText)
                        inputText = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = inputText.isNotBlank()
            ) {
                Text(text = "Ответ")
            }
        }
    }
}

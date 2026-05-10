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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import com.alexpo.grammermate.data.AnswerResult
import com.alexpo.grammermate.data.VerbDrillUiState

@Composable
fun VerbDrillScreen(
    viewModel: VerbDrillViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    if (state.session != null) {
        val provider = remember { VerbDrillCardSessionProvider(viewModel) }
        VerbDrillSessionWithCardSession(
            provider = provider,
            viewModel = viewModel,
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
private fun VerbDrillSessionWithCardSession(
    provider: VerbDrillCardSessionProvider,
    viewModel: VerbDrillViewModel,
    onExit: () -> Unit
) {
    TrainingCardSession(
        contract = provider,
        header = {
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
        },
        inputControls = {
            VerbDrillInputControls(
                inputText = inputText,
                onInputChanged = onInputChanged,
                onSubmit = {
                    val input = inputText
                    if (input.isNotBlank()) {
                        provider.submitAnswerWithInput(input)
                        onInputChanged("")
                    }
                }
            )
        },
        resultContent = {
            DefaultVerbDrillResultContent(
                result = lastResult,
                onNext = onNext,
                onSpeak = { contract.speakTts() }
            )
        },
        completionScreen = {
            VerbDrillCompletionScreen(
                viewModel = viewModel,
                onExit = onExit
            )
        },
        onExit = onExit
    )
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

// --- VerbDrill-specific composable pieces ---

@Composable
private fun VerbDrillInputControls(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Answer") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() })
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = inputText.isNotBlank()
        ) {
            Text(text = "Ответ")
        }
    }
}

@Composable
private fun DefaultVerbDrillResultContent(
    result: AnswerResult?,
    onNext: () -> Unit,
    onSpeak: () -> Unit = {}
) {
    if (result == null) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (result.correct) {
                Text(
                    text = "Correct",
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "Incorrect",
                    color = Color(0xFFC62828),
                    fontWeight = FontWeight.Bold
                )
            }
            if (result.displayAnswer.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onSpeak, enabled = true) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "Listen",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Text(
            text = "Answer: ${result.displayAnswer}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Дальше")
        }
    }
}

@Composable
private fun VerbDrillCompletionScreen(
    viewModel: VerbDrillViewModel,
    onExit: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val session = state.session ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎉", // party popper
            fontSize = 48.sp
        )
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
            onClick = { viewModel.nextBatch() },
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
}

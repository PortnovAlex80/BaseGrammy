package com.alexpo.grammermate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillLastSessionState
import com.alexpo.grammermate.data.VerbDrillUiState

/**
 * Verb Drill selection screen.
 *
 * Shows tense/group pickers and a "Continue" button.
 * When the user starts a session, [onStartSession] is called with the
 * filtered cards list, and the parent navigates to TrainingScreen in
 * VERB_DRILL mode.
 */
@Composable
fun VerbDrillScreen(
    viewModel: VerbDrillViewModel,
    onBack: () -> Unit,
    onStartSession: (List<VerbDrillCard>) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.verb_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        return
    }

    VerbDrillSelectionScreen(
        state = state,
        onSelectTense = viewModel::selectTense,
        onSelectGroup = viewModel::selectGroup,
        onToggleSortByFrequency = viewModel::toggleSortByFrequency,
        onStart = {
            viewModel.startSession()
            // After startSession(), read the cards from the updated session state
            val sessionCards = viewModel.uiState.value.session?.cards ?: emptyList()
            if (sessionCards.isNotEmpty()) {
                onStartSession(sessionCards)
            }
        },
        onBack = onBack
    )

    // VD-50: Start Fresh / Resume Dialog
    if (state.showStartFreshResumeDialog) {
        StartFreshResumeDialog(
            lastSessionContext = state.lastSessionContext,
            sessionAge = state.lastSessionContext?.timestamp?.let { viewModel.formatSessionAge(it) },
            onDismiss = {
                viewModel.onDismissDialog()
                onBack()
            },
            onResume = {
                viewModel.onResumeSession()
                // After resume, read the session cards and start the session
                val sessionCards = viewModel.uiState.value.session?.cards ?: emptyList()
                if (sessionCards.isNotEmpty()) {
                    onStartSession(sessionCards)
                }
            },
            onStartFresh = {
                viewModel.onStartFresh()
            }
        )
    }
}

@Composable
private fun VerbDrillSelectionScreen(
    state: VerbDrillUiState,
    onSelectTense: (String?) -> Unit,
    onSelectGroup: (String?) -> Unit,
    onToggleSortByFrequency: () -> Unit,
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
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.verb_content_desc_back))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.verb_drill_title), fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (state.availableTenses.isNotEmpty()) {
            VerbDrillDropdown(
                label = stringResource(R.string.verb_select_tense),
                allLabel = stringResource(R.string.verb_all_tenses),
                selected = state.selectedTense,
                items = state.availableTenses,
                onSelect = onSelectTense
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (state.availableGroups.isNotEmpty()) {
            VerbDrillDropdown(
                label = stringResource(R.string.verb_select_group),
                allLabel = stringResource(R.string.verb_all_groups),
                selected = state.selectedGroup,
                items = state.availableGroups,
                onSelect = onSelectGroup
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = state.sortByFrequency,
                onCheckedChange = { onToggleSortByFrequency() }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = stringResource(R.string.verb_sort_by_frequency))
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.totalCards > 0) {
            Text(
                text = stringResource(R.string.verb_progress, state.everShownCount, state.totalCards)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.verb_today, state.todayShownCount),
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
                text = stringResource(R.string.verb_all_done_today),
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
                    text = if (state.todayShownCount > 0) stringResource(R.string.verb_continue) else stringResource(R.string.verb_start)
                )
            }
        }
    }
}

@Composable
private fun VerbDrillDropdown(
    label: String,
    allLabel: String,
    selected: String?,
    items: List<String>,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(text = "$label:", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = { expanded = true }) {
            Text(text = selected ?: allLabel)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(text = allLabel) },
                onClick = { expanded = false; onSelect(null) }
            )
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(text = item) },
                    onClick = { expanded = false; onSelect(item) }
                )
            }
        }
    }
}

/**
 * VD-50: Start Fresh / Resume Dialog
 *
 * Shown when user opens VerbDrillScreen and a previous incomplete session exists.
 * Displays session context (tense, group, progress, age) to help user decide.
 */
@Composable
private fun StartFreshResumeDialog(
    lastSessionContext: VerbDrillLastSessionState?,
    sessionAge: String?,
    onDismiss: () -> Unit,
    onResume: () -> Unit,
    onStartFresh: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.verb_drill_title))
        },
        text = {
            Column {
                Text(stringResource(R.string.verb_resume_dialog_message))
                Spacer(modifier = Modifier.height(12.dp))
                // Session context display
                if (lastSessionContext != null) {
                    SessionContextInfo(
                        selectedTense = lastSessionContext.selectedTense,
                        selectedGroup = lastSessionContext.selectedGroup,
                        currentIndex = lastSessionContext.currentIndex,
                        totalCards = lastSessionContext.cards.size,
                        correctCount = lastSessionContext.correctCount,
                        incorrectCount = lastSessionContext.incorrectCount,
                        sessionAge = sessionAge
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onResume
            ) {
                Text(stringResource(R.string.verb_resume_dialog_resume))
            }
        },
        dismissButton = {
            Column {
                OutlinedButton(
                    onClick = onStartFresh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.verb_resume_dialog_start_fresh))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        }
    )
}

/**
 * Displays session context information in the Start Fresh / Resume dialog.
 */
@Composable
private fun SessionContextInfo(
    selectedTense: String?,
    selectedGroup: String?,
    currentIndex: Int,
    totalCards: Int,
    correctCount: Int,
    incorrectCount: Int,
    sessionAge: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Tense
        if (selectedTense != null) {
            SessionInfoRow(
                label = stringResource(R.string.verb_resume_dialog_tense),
                value = selectedTense
            )
        }
        // Group
        if (selectedGroup != null) {
            SessionInfoRow(
                label = stringResource(R.string.verb_resume_dialog_group),
                value = selectedGroup
            )
        }
        // Progress
        SessionInfoRow(
            label = stringResource(R.string.verb_resume_dialog_progress),
            value = stringResource(R.string.verb_resume_dialog_progress_value, currentIndex + 1, totalCards)
        )
        // Score
        SessionInfoRow(
            label = stringResource(R.string.verb_resume_dialog_score),
            value = stringResource(R.string.verb_resume_dialog_score_value, correctCount, incorrectCount)
        )
        // Age
        if (sessionAge != null) {
            SessionInfoRow(
                label = stringResource(R.string.verb_resume_dialog_age),
                value = sessionAge
            )
        }
    }
}

/**
 * A single row in the session context info display.
 */
@Composable
private fun SessionInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

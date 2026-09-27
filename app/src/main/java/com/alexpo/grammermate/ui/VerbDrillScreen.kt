package com.alexpo.grammermate.ui

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillLastSessionState
import com.alexpo.grammermate.data.VerbDrillUiState
import com.alexpo.grammermate.shared.AuditLogger
import com.alexpo.grammermate.ui.features.verbdrill.components.VerbDrillSessionCard
import com.alexpo.grammermate.ui.features.verbdrill.components.VerbDrillSessionContextInfo

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

    // VD-50/VD-51: Refresh last session context on screen entry to fix stale cache
    LaunchedEffect(Unit) {
        viewModel.refreshLastSessionContext()
    }

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

    // Box ensures proper layering: selection screen first, dialog on top
    Box(modifier = Modifier.fillMaxSize()) {
        VerbDrillSelectionScreen(
            state = state,
            onSelectTense = viewModel::selectTense,
            onSelectGroup = viewModel::selectGroup,
            onSelectPerson = viewModel::selectPerson,
            onToggleSortByFrequency = viewModel::toggleSortByFrequency,
            onStart = {
                viewModel.startSession()
                // Read session immediately (synchronous read after startSession)
                val sessionCards = viewModel.uiState.value.session?.cards ?: emptyList()
                if (sessionCards.isNotEmpty()) {
                    AuditLogger.getInstanceOrNull()?.verbDrillStart(
                        sessionCards.size,
                        viewModel.uiState.value.selectedTense ?: ""
                    )
                    onStartSession(sessionCards)
                }
            },
            onBack = onBack,
            onRepeat = {
                // VD-51: Repeat mode - replay the last saved card order.
                AuditLogger.getInstanceOrNull()?.dialogAction("verb_session_card", "repeat")
                viewModel.onRepeatSession()
                val sessionCards = viewModel.uiState.value.session?.cards ?: emptyList()
                if (sessionCards.isNotEmpty()) {
                    onStartSession(sessionCards)
                }
            },
            onContinue = {
                AuditLogger.getInstanceOrNull()?.dialogAction("verb_session_card", "continue")
                viewModel.onResumeSession()
                val sessionCards = viewModel.uiState.value.session?.cards ?: emptyList()
                if (sessionCards.isNotEmpty()) {
                    onStartSession(sessionCards)
                }
            },
            onReset = {
                AuditLogger.getInstanceOrNull()?.dialogAction("verb_session_card", "reset")
                viewModel.onStartFresh()
            }
        )

        // Debug button in bottom-right corner
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = { viewModel.showDebugDialog() },
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.BugReport, contentDescription = "Debug")
            }
        }

        // VD-50: Start Fresh / Resume Dialog (rendered on top of selection screen)
        if (state.showStartFreshResumeDialog) {
            StartFreshResumeDialog(
                lastSessionContext = state.lastSessionContext,
                onDismiss = {
                    viewModel.onDismissDialog()
                    onBack()
                },
                onResume = {
                    AuditLogger.getInstanceOrNull()?.dialogAction("verb_fresh_resume", "resume")
                    viewModel.onResumeSession()
                    // After resume, read the session cards and start the session
                    val sessionCards = viewModel.uiState.value.session?.cards ?: emptyList()
                    if (sessionCards.isNotEmpty()) {
                        onStartSession(sessionCards)
                    }
                },
                onStartFresh = {
                    AuditLogger.getInstanceOrNull()?.dialogAction("verb_fresh_resume", "start_fresh")
                    viewModel.onStartFresh()
                }
            )
        }

        // Debug Info Dialog
        if (state.showDebugInfo) {
            DebugInfoDialog(
                debugInfo = state.debugInfo,
                onDismiss = { viewModel.hideDebugDialog() }
            )
        }
    }
}

@Composable
private fun VerbDrillSelectionScreen(
    state: VerbDrillUiState,
    onSelectTense: (String?) -> Unit,
    onSelectGroup: (String?) -> Unit,
    onSelectPerson: (String?) -> Unit,
    onToggleSortByFrequency: () -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    onRepeat: () -> Unit = {},
    onContinue: () -> Unit = {},
    onReset: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                AuditLogger.getInstanceOrNull()?.backPress("verb_drill")
                onBack()
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.verb_content_desc_back))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.verb_drill_title), fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(16.dp))

        val hasSavedSession = state.lastSessionContext != null

        if (!hasSavedSession && state.availableTenses.isNotEmpty()) {
            VerbDrillDropdown(
                label = stringResource(R.string.verb_select_tense),
                allLabel = stringResource(R.string.verb_all_tenses),
                selected = state.selectedTense,
                items = state.availableTenses,
                onSelect = onSelectTense
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (!hasSavedSession && state.availableGroups.isNotEmpty()) {
            VerbDrillDropdown(
                label = stringResource(R.string.verb_select_group),
                allLabel = stringResource(R.string.verb_all_groups),
                selected = state.selectedGroup,
                items = state.availableGroups,
                onSelect = onSelectGroup
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (!hasSavedSession && state.availablePersons.isNotEmpty()) {
            VerbDrillDropdown(
                label = stringResource(R.string.verb_select_person),
                allLabel = stringResource(R.string.verb_all_persons),
                selected = state.selectedPerson,
                items = state.availablePersons,
                onSelect = onSelectPerson
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (!hasSavedSession) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.sortByFrequency,
                    onCheckedChange = { onToggleSortByFrequency() },
                    modifier = Modifier.testTag("sort_by_frequency_checkbox")
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.verb_sort_by_frequency))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!hasSavedSession && state.totalCards > 0) {
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

        // VD-51: Inline session card when last session exists
        if (hasSavedSession) {
            // Boundary owns the saved-session state interpretation: the display
            // organism (ui/features/verbdrill/components) receives plain display
            // values — no VerbDrillLastSessionState, no ViewModel reads.
            val savedContext = state.lastSessionContext!!
            VerbDrillSessionCard(
                selectedTense = savedContext.selectedTense,
                selectedGroup = savedContext.selectedGroup,
                selectedPerson = savedContext.selectedPerson,
                shownCount = savedContext.todayShownCardIds.size,
                onRepeat = onRepeat,
                onContinue = onContinue,
                onReset = onReset
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (state.allDoneToday) {
            Text(
                text = stringResource(R.string.verb_all_done_today),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else if (!hasSavedSession &&
            (state.totalCards > 0 || state.availableTenses.isNotEmpty() || state.availableGroups.isNotEmpty())
        ) {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().testTag("verb_start_button")
            ) {
                Text(text = stringResource(R.string.verb_start))
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
 * Displays session filter context (tense, group) to help user decide.
 * "Resume" loads next cards excluding already shown ones.
 */
@Composable
private fun StartFreshResumeDialog(
    lastSessionContext: VerbDrillLastSessionState?,
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
                // Session context display - only filters
                if (lastSessionContext != null) {
                    VerbDrillSessionContextInfo(
                        selectedTense = lastSessionContext.selectedTense,
                        selectedGroup = lastSessionContext.selectedGroup,
                        selectedPerson = lastSessionContext.selectedPerson
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
 * Debug Info Dialog - shows internal state for debugging progress persistence.
 */
@Composable
private fun DebugInfoDialog(
    debugInfo: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Debug Info")
        },
        text = {
            Text(
                text = debugInfo,
                style = MaterialTheme.typography.bodySmall
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

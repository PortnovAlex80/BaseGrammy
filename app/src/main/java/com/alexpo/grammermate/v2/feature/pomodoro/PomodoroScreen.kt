package com.alexpo.grammermate.v2.feature.pomodoro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alexpo.grammermate.domain.model.PomodoroPreset
import com.alexpo.grammermate.v2.core.ui.collectState

/** Стабильные test-теги pomodoro. */
object PomodoroTestTags {
    const val PRESET_QUICK = "pomodoro_preset_quick"
    const val PRESET_FOCUS = "pomodoro_preset_focus"
    const val PRESET_CLASSIC = "pomodoro_preset_classic"
    const val PAUSE_BUTTON = "pomodoro_pause_button"
    const val RESUME_BUTTON = "pomodoro_resume_button"
    const val FINISH_BUTTON = "pomodoro_finish_button"
    const val TIMER = "pomodoro_timer"
    const val DONE_LABEL = "pomodoro_done_label"
}

/**
 * Экран pomodoro (срез 7 Фазы 4): orchestration wrapper — таймер поверх
 * обычной тренировки (пользователь стартует и тренируется в любом режиме).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(
    onNavigateBack: () -> Unit,
    viewModel: PomodoroViewModel = hiltViewModel(),
) {
    val state = collectState(viewModel.state)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Помодоро", style = MaterialTheme.typography.titleLarge) },
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
                PomodoroViewState.Idle -> {
                    Text("Фокус-сессия", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Стартуйте таймер и тренируйтесь — сессия запишется в историю.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = { viewModel.start(PomodoroPreset.QUICK) },
                        modifier = Modifier.fillMaxWidth().testTag(PomodoroTestTags.PRESET_QUICK),
                    ) { Text("5 минут") }
                    Button(
                        onClick = { viewModel.start(PomodoroPreset.FOCUS) },
                        modifier = Modifier.fillMaxWidth().testTag(PomodoroTestTags.PRESET_FOCUS),
                    ) { Text("15 минут") }
                    Button(
                        onClick = { viewModel.start(PomodoroPreset.CLASSIC) },
                        modifier = Modifier.fillMaxWidth().testTag(PomodoroTestTags.PRESET_CLASSIC),
                    ) { Text("20 минут") }
                }

                is PomodoroViewState.Running -> {
                    Text(
                        formatMmSs(s.remainingMs),
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.testTag(PomodoroTestTags.TIMER),
                    )
                    OutlinedButton(
                        onClick = viewModel::pause,
                        modifier = Modifier.fillMaxWidth().testTag(PomodoroTestTags.PAUSE_BUTTON),
                    ) { Text("Пауза") }
                    Button(
                        onClick = viewModel::finish,
                        modifier = Modifier.fillMaxWidth().testTag(PomodoroTestTags.FINISH_BUTTON),
                    ) { Text("Завершить") }
                }

                is PomodoroViewState.Paused -> {
                    Text(
                        formatMmSs(s.remainingMs),
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.testTag(PomodoroTestTags.TIMER),
                    )
                    Button(
                        onClick = viewModel::resume,
                        modifier = Modifier.fillMaxWidth().testTag(PomodoroTestTags.RESUME_BUTTON),
                    ) { Text("Продолжить") }
                    Button(
                        onClick = viewModel::finish,
                        modifier = Modifier.fillMaxWidth().testTag(PomodoroTestTags.FINISH_BUTTON),
                    ) { Text("Завершить") }
                }

                is PomodoroViewState.Finished -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Сессия записана!",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.testTag(PomodoroTestTags.DONE_LABEL),
                    )
                    Text(
                        "${s.entry.durationMinutes} мин · прошло " +
                            "${s.entry.totalSeconds - s.entry.remainingSeconds} сек",
                    )
                    Button(onClick = viewModel::dismiss) { Text("Новая сессия") }
                }

                is PomodoroViewState.Error -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::dismiss) { Text("К пресетам") }
                }
            }
        }
    }
}

private fun formatMmSs(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

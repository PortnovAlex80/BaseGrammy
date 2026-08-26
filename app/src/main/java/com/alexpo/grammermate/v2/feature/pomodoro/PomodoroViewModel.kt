package com.alexpo.grammermate.v2.feature.pomodoro

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.domain.model.LanguageId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.PomodoroHistoryEntry
import com.alexpo.grammermate.domain.model.PomodoroPreset
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.repository.UserContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * FSM pomodoro (срез 7 Фазы 4): Idle → Running ⇄ Paused → Finished | Idle(cancel).
 */
sealed interface PomodoroViewState {
    /** Выбор пресета (QUICK 5 / FOCUS 15 / CLASSIC 20 минут). */
    data object Idle : PomodoroViewState

    /** Таймер идёт: [remainingMs] тикает; пользователь тренируется параллельно. */
    data class Running(val preset: PomodoroPreset, val remainingMs: Long) : PomodoroViewState

    data class Paused(val preset: PomodoroPreset, val remainingMs: Long) : PomodoroViewState

    /** Сессия завершена (обратный отсчёт или вручную) и ЗАПИСАНА в историю. */
    data class Finished(val entry: PomodoroHistoryEntry) : PomodoroViewState

    data class Error(val message: String) : PomodoroViewState
}

/**
 * ViewModel pomodoro — orchestration wrapper (срез 7 Фазы 4, план §4.7:
 * «не вариант card renderer»): таймер живёт ПОВЕРХ обычной тренировки —
 * пользователь стартует помодоро и тренируется в любом режиме; экран
 * помодоро показывает обратный отсчёт.
 *
 * Durable-состояние — только завершённые сессии ([UserContentRepository.
 * addPomodoroSession]): сам таймер эфемерен (rotation переживает через VM,
 * process death — нет; follow-up строки матрицы). Счётчики карт — нули до
 * интеграции хостинга тренировки внутрь wrapper'а (follow-up).
 */
@HiltViewModel
class PomodoroViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userContentRepository: UserContentRepository,
    private val contentRepository: ContentRepository,
) : ViewModel() {

    private val packId: PackId = PackId(savedStateHandle.get<String>("packId").orEmpty())

    private val _state = MutableStateFlow<PomodoroViewState>(PomodoroViewState.Idle)
    val state: StateFlow<PomodoroViewState> = _state.asStateFlow()

    private var tickJob: Job? = null
    private val startedAtMs = MutableStateFlow(0L)

    fun start(preset: PomodoroPreset) {
        if (_state.value !is PomodoroViewState.Idle) return
        startedAtMs.value = System.currentTimeMillis()
        _state.value = PomodoroViewState.Running(preset, preset.minutes * 60_000L)
        tick(preset)
    }

    fun pause() {
        val running = _state.value as? PomodoroViewState.Running ?: return
        tickJob?.cancel()
        _state.value = PomodoroViewState.Paused(running.preset, running.remainingMs)
    }

    fun resume() {
        val paused = _state.value as? PomodoroViewState.Paused ?: return
        _state.value = PomodoroViewState.Running(paused.preset, paused.remainingMs)
        tick(paused.preset)
    }

    /** Завершить вручную (остаток времени фиксируется в remainingSeconds). */
    fun finish() {
        val s = _state.value
        val preset = when (s) {
            is PomodoroViewState.Running -> s.preset
            is PomodoroViewState.Paused -> s.preset
            else -> return
        }
        tickJob?.cancel()
        val remaining = when (s) {
            is PomodoroViewState.Running -> s.remainingMs
            is PomodoroViewState.Paused -> s.remainingMs
            else -> 0L
        }
        record(preset, remainingMs = remaining)
    }

    fun dismiss() {
        tickJob?.cancel()
        _state.value = PomodoroViewState.Idle
    }

    private fun tick(preset: PomodoroPreset) {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                // Единственный источник remaining — текущий state (пауза просто
                // отменяет job; resume перезапускает с сохранённого значения).
                val current = _state.value as? PomodoroViewState.Running ?: break
                val remaining = current.remainingMs - 1_000
                if (remaining <= 0) {
                    record(preset, remainingMs = 0)
                    break
                }
                _state.value = PomodoroViewState.Running(preset, remaining)
            }
        }
    }

    private fun record(preset: PomodoroPreset, remainingMs: Long) {
        val totalSeconds = preset.minutes * 60
        val remainingSeconds = (remainingMs.coerceAtLeast(0) / 1000L).toInt()
        viewModelScope.launch {
            val languageId = runCatching {
                contentRepository.getPack(packId)?.languageId
            }.getOrNull() ?: LanguageId("unknown")

            val entry = PomodoroHistoryEntry(
                id = "pomodoro_${startedAtMs.value}",
                languageId = languageId,
                packId = packId,
                lessonId = null,
                completedAtMs = System.currentTimeMillis(),
                durationMinutes = preset.minutes,
                totalSeconds = totalSeconds,
                remainingSeconds = remainingSeconds,
                cardsShown = 0, // follow-up: интеграция счётчиков тренировки
                cardsCorrect = 0,
                cardsIncorrect = 0,
                wordsPerMinute = 0.0,
            )
            runCatching { userContentRepository.addPomodoroSession(entry) }
                .onSuccess { _state.value = PomodoroViewState.Finished(entry) }
                .onFailure { e ->
                    _state.value =
                        PomodoroViewState.Error(e.message ?: "Не удалось сохранить сессию")
                }
        }
    }
}

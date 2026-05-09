package com.alexpo.grammermate.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.Normalizer
import com.alexpo.grammermate.data.ProgressStore
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillComboProgress
import com.alexpo.grammermate.data.VerbDrillCsvParser
import com.alexpo.grammermate.data.VerbDrillSessionState
import com.alexpo.grammermate.data.VerbDrillStore
import com.alexpo.grammermate.data.VerbDrillUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class VerbDrillViewModel(application: Application) : AndroidViewModel(application) {

    private val logTag = "VerbDrillVM"
    private val verbDrillStore = VerbDrillStore(application)
    private val lessonStore = LessonStore(application)
    private val progressStore = ProgressStore(application)

    private val _uiState = MutableStateFlow(VerbDrillUiState())
    val uiState: StateFlow<VerbDrillUiState> = _uiState

    private var allCards: List<VerbDrillCard> = emptyList()
    private var progressMap: Map<String, VerbDrillComboProgress> = emptyMap()

    init {
        loadCards()
    }

    private fun loadCards() {
        val languageId = progressStore.load().languageId
        val files = lessonStore.getVerbDrillFiles(languageId)
        val cards = mutableListOf<VerbDrillCard>()

        for (file in files) {
            val content = file.readText()
            val (_, parsed) = VerbDrillCsvParser.parse(content)
            cards.addAll(parsed)
        }

        allCards = cards

        val tenses = cards.mapNotNull { it.tense }.distinct().sorted()
        val groups = cards.mapNotNull { it.group }.distinct().sorted()

        progressMap = verbDrillStore.loadProgress()

        _uiState.update {
            it.copy(
                availableTenses = tenses,
                availableGroups = groups,
                isLoading = false
            )
        }

        Log.d(logTag, "Loaded ${cards.size} verb drill cards for language $languageId")
    }

    fun selectTense(tense: String?) {
        _uiState.update { it.copy(selectedTense = tense, allDoneToday = false) }
        updateProgressDisplay()
    }

    fun selectGroup(group: String?) {
        _uiState.update { it.copy(selectedGroup = group, allDoneToday = false) }
        updateProgressDisplay()
    }

    private fun updateProgressDisplay() {
        val state = _uiState.value
        val comboKey = "${state.selectedGroup ?: ""}|${state.selectedTense ?: ""}"

        val filtered = allCards.filter { card ->
            (state.selectedTense == null || card.tense == state.selectedTense) &&
            (state.selectedGroup == null || card.group == state.selectedGroup)
        }

        val progress = progressMap[comboKey]
        val everShownCount = progress?.everShownCardIds?.size ?: 0
        val todayShownCount = progress?.todayShownCardIds?.size ?: 0

        _uiState.update {
            it.copy(
                totalCards = filtered.size,
                everShownCount = everShownCount,
                todayShownCount = todayShownCount
            )
        }
    }

    fun startSession() {
        val state = _uiState.value
        val comboKey = "${state.selectedGroup ?: ""}|${state.selectedTense ?: ""}"

        val filtered = allCards.filter { card ->
            (state.selectedTense == null || card.tense == state.selectedTense) &&
            (state.selectedGroup == null || card.group == state.selectedGroup)
        }

        if (filtered.isEmpty()) {
            _uiState.update { it.copy(allDoneToday = true) }
            return
        }

        val progress = progressMap[comboKey]
        val todayShownCardIds = progress?.todayShownCardIds ?: emptySet()

        val remaining = filtered.filter { it.id !in todayShownCardIds }

        if (remaining.isEmpty()) {
            _uiState.update { it.copy(allDoneToday = true) }
            return
        }

        val selected = remaining.shuffled().take(10)

        val session = VerbDrillSessionState(cards = selected)
        _uiState.update { it.copy(session = session, allDoneToday = false) }
    }

    fun submitAnswer(input: String) {
        val session = _uiState.value.session ?: return
        if (session.isComplete) return
        if (session.currentIndex >= session.cards.size) return

        val card = session.cards[session.currentIndex]
        val isCorrect = checkAnswer(input, card.answer)

        val updatedCorrect = if (isCorrect) session.correctCount + 1 else session.correctCount
        val updatedIncorrect = if (!isCorrect) session.incorrectCount + 1 else session.incorrectCount
        val nextIndex = session.currentIndex + 1
        val isComplete = nextIndex >= session.cards.size

        _uiState.update { state ->
            state.copy(
                session = session.copy(
                    currentIndex = nextIndex,
                    correctCount = updatedCorrect,
                    incorrectCount = updatedIncorrect,
                    isComplete = isComplete
                )
            )
        }

        // Persist progress for this combo
        val uiState = _uiState.value
        val comboKey = "${uiState.selectedGroup ?: ""}|${uiState.selectedTense ?: ""}"
        val existing = progressMap[comboKey]
        val everShown = (existing?.everShownCardIds ?: emptySet()) + card.id
        val todayShown = (existing?.todayShownCardIds ?: emptySet()) + card.id
        val totalCards = allCards.count { c ->
            (uiState.selectedTense == null || c.tense == uiState.selectedTense) &&
            (uiState.selectedGroup == null || c.group == uiState.selectedGroup)
        }

        val updatedProgress = VerbDrillComboProgress(
            group = uiState.selectedGroup ?: "",
            tense = uiState.selectedTense ?: "",
            totalCards = totalCards,
            everShownCardIds = everShown,
            todayShownCardIds = todayShown,
            lastDate = java.time.LocalDate.now().toString()
        )

        progressMap = progressMap.toMutableMap().apply { this[comboKey] = updatedProgress }
        verbDrillStore.upsertComboProgress(comboKey, updatedProgress)

        updateProgressDisplay()
    }

    fun nextBatch() {
        startSession()
    }

    fun exitSession() {
        _uiState.update { it.copy(session = null) }
    }

    private fun checkAnswer(input: String, expected: String): Boolean {
        return Normalizer.normalize(input) == Normalizer.normalize(expected)
    }
}

package com.alexpo.grammermate.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.AppContainer
import com.alexpo.grammermate.GrammarMateApplication
import com.alexpo.grammermate.data.AuxDrillCard
import com.alexpo.grammermate.data.AuxDrillCatalog
import com.alexpo.grammermate.data.AuxDrillComboProgress
import com.alexpo.grammermate.data.AuxDrillPair
import com.alexpo.grammermate.data.AuxDrillStore
import com.alexpo.grammermate.data.AuxDrillUiState
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillCsvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuxDrillViewModel(application: Application) : AndroidViewModel(application) {

    private val logTag = "AuxDrillVM"
    private val container: AppContainer = when (application) {
        is GrammarMateApplication -> application.container
        else -> AppContainer(application)
    }

    private val lessonStore: LessonStore = container.lessonStore
    private var auxDrillStore: AuxDrillStore = container.auxDrillStore(null)
    private var usingTestStore = false

    /** Test-only constructor with an injected store. */
    constructor(application: Application, testStore: AuxDrillStore) : this(application) {
        auxDrillStore = testStore
        usingTestStore = true
    }

    private val _uiState = MutableStateFlow(
        AuxDrillUiState(availablePairs = AuxDrillCatalog.ALL)
    )
    val uiState: StateFlow<AuxDrillUiState> = _uiState

    private var allCards: List<AuxDrillCard> = emptyList()
    private var progressMap: Map<String, AuxDrillComboProgress> = emptyMap()
    private var currentPackId: String? = null
    private var sessionSize: Int = 10

    init {
        sessionSize = container.configStore.load().sessionSize
    }

    /**
     * Load the shared pool for the given pack. Maps parsed VerbDrillCards into
     * AuxDrillCards (same underlying file, separate model).
     */
    fun reloadForPack(packId: String, languageId: String) {
        sessionSize = container.configStore.load().sessionSize
        currentPackId = packId
        if (!usingTestStore) {
            auxDrillStore = container.auxDrillStore(packId)
        }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch { loadCards(packId, languageId) }
    }

    private suspend fun loadCards(packId: String, languageId: String) {
        if (usingTestStore) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        val ioResult = withContext(Dispatchers.IO) {
            val files = lessonStore.getVerbDrillFiles(packId, languageId)
            val cards = mutableListOf<AuxDrillCard>()
            for (file in files) {
                val parseResult = file.bufferedReader().use { reader ->
                    VerbDrillCsvParser.parse(reader)
                }
                val parsed = parseResult.data ?: continue
                cards.addAll(parsed.map { it.toAux() })
            }
            Triple(cards, auxDrillStore.loadProgress(), languageId)
        }
        allCards = ioResult.first
        progressMap = ioResult.second
        _uiState.update {
            it.copy(
                isLoading = false,
                loadedLanguageId = ioResult.third
            )
        }
        Log.d(logTag, "Loaded ${allCards.size} pool cards for aux drill")
    }

    private fun VerbDrillCard.toAux(): AuxDrillCard = AuxDrillCard(
        id = id,
        promptRu = promptRu,
        answer = answer,
        verb = verb,
        tense = tense,
        group = group,
        person = person,
        rank = rank
    )

    /** Select a (verb×tense) pair from the catalog and compute filtered pool. */
    fun selectPair(pair: AuxDrillPair) {
        val filtered = filteredCards(pair)
        val comboKey = comboKeyFor(pair)
        val progress = progressMap[comboKey]
        _uiState.update {
            it.copy(
                selectedPair = pair,
                totalCards = filtered.size,
                everShownCount = progress?.everShownCardIds?.size ?: 0,
                todayShownCount = progress?.todayShownCardIds?.size ?: 0,
                allDoneToday = false
            )
        }
    }

    private fun filteredCards(pair: AuxDrillPair): List<AuxDrillCard> =
        allCards.filter { it.verb == pair.verb && it.tense == pair.tense }

    internal fun comboKeyFor(pair: AuxDrillPair): String = "aux|${pair.verb}|${pair.tense}"

    // ── Session logic ────────────────────────────────────────────────────

    private var cardShownTimestamp: Long = 0L

    fun setSessionSize(size: Int) {
        sessionSize = size.coerceIn(1, 1000)
    }

    fun startSession() {
        val pair = _uiState.value.selectedPair ?: return
        val filtered = filteredCards(pair)
        if (filtered.isEmpty()) {
            _uiState.update { it.copy(session = null, allDoneToday = true) }
            return
        }
        val comboKey = comboKeyFor(pair)
        val progress = progressMap[comboKey]
        val shownToday = progress?.todayShownCardIds ?: emptySet()
        val remaining = filtered.filter { it.id !in shownToday }

        val pool = if (remaining.isEmpty()) filtered else remaining
        val selected = pool.shuffled().take(sessionSize)

        _uiState.update {
            it.copy(
                session = com.alexpo.grammermate.data.AuxDrillSessionState(cards = selected),
                allDoneToday = false
            )
        }
        cardShownTimestamp = System.currentTimeMillis()
    }

    fun submitCorrectAnswer() {
        val session = _uiState.value.session ?: return
        if (session.isComplete || session.currentIndex >= session.cards.size) return
        val card = session.cards[session.currentIndex]
        val nextIndex = session.currentIndex + 1
        val isComplete = nextIndex >= session.cards.size

        _uiState.update { state ->
            state.copy(
                session = session.copy(
                    currentIndex = nextIndex,
                    correctCount = session.correctCount + 1,
                    isComplete = isComplete
                )
            )
        }
        persistCardProgress(card)
        if (!isComplete) cardShownTimestamp = System.currentTimeMillis()
        updateProgressDisplay()
    }

    fun markCardCompleted() {
        val session = _uiState.value.session ?: return
        if (session.isComplete || session.currentIndex >= session.cards.size) return
        val card = session.cards[session.currentIndex]
        val nextIndex = session.currentIndex + 1
        val isComplete = nextIndex >= session.cards.size

        _uiState.update { state ->
            state.copy(
                session = session.copy(
                    currentIndex = nextIndex,
                    incorrectCount = session.incorrectCount + 1,
                    isComplete = isComplete
                )
            )
        }
        persistCardProgress(card)
        if (!isComplete) cardShownTimestamp = System.currentTimeMillis()
        updateProgressDisplay()
    }

    fun exitSession() {
        auxDrillStore.flush()
        _uiState.update { it.copy(session = null) }
    }

    private fun persistCardProgress(card: AuxDrillCard) {
        val pair = _uiState.value.selectedPair ?: return
        val comboKey = comboKeyFor(pair)
        val existing = progressMap[comboKey]
        val ever = (existing?.everShownCardIds ?: emptySet()) + card.id
        val today = (existing?.todayShownCardIds ?: emptySet()) + card.id
        val total = filteredCards(pair).size
        val updated = AuxDrillComboProgress(
            verb = pair.verb,
            tense = pair.tense,
            totalCards = total,
            everShownCardIds = ever,
            todayShownCardIds = today,
            lastDate = java.time.LocalDate.now().toString()
        )
        progressMap = progressMap.toMutableMap().apply { this[comboKey] = updated }
        auxDrillStore.upsertComboProgress(comboKey, updated)
    }

    private fun updateProgressDisplay() {
        val pair = _uiState.value.selectedPair ?: return
        val comboKey = comboKeyFor(pair)
        val progress = progressMap[comboKey]
        _uiState.update {
            it.copy(
                everShownCount = progress?.everShownCardIds?.size ?: 0,
                todayShownCount = progress?.todayShownCardIds?.size ?: 0
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPair = null, session = null, allDoneToday = false) }
    }

    // ── Test hooks ───────────────────────────────────────────────────────
    internal fun injectPoolForTest(cards: List<VerbDrillCard>) {
        allCards = cards.map { it.toAux() }
        progressMap = auxDrillStore.loadProgress()
        _uiState.update { it.copy(isLoading = false, loadedLanguageId = "it") }
    }

    internal fun allPoolCardsForTest(): List<AuxDrillCard> = allCards

    internal fun currentFilteredCardsForTest(): List<AuxDrillCard> =
        _uiState.value.selectedPair?.let { filteredCards(it) } ?: emptyList()

    internal fun setSessionSizeForTest(size: Int) = setSessionSize(size)

    internal fun auxStoreForTest(): AuxDrillStore = auxDrillStore
}

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

/**
 * ViewModel for the aux (lead-in) drill **menu** screen.
 *
 * Loads the shared verb-drill pool, lets the user pick a (verb×tense) pair, and
 * hands the filtered [VerbDrillCard]s to the shared training session — it does
 * NOT run its own training surface. Training mechanics (input, answer, chips,
 * hint) live in the shared [TrainingViewModel] via
 * [TrainingViewModel.startVerbDrillSession], so aux drill reuses the same
 * training screen as regular Verb Drill.
 *
 * Progress for aux pairs is tracked separately in [AuxDrillStore]
 * (aux_drill_progress.yaml) so it never mixes with regular Verb Drill.
 */
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

    /** Aux cards derived from the pool, used for the menu's progress display. */
    private var allCards: List<AuxDrillCard> = emptyList()

    /** Original VerbDrillCards kept so a pair can be handed to the shared
     *  training session (VerbDrillCard implements SessionCard). */
    private var allSourceCards: List<VerbDrillCard> = emptyList()

    private var progressMap: Map<String, AuxDrillComboProgress> = emptyMap()
    private var currentPackId: String? = null
    private var sessionSize: Int = 10

    init {
        sessionSize = container.configStore.load().sessionSize
    }

    /**
     * Load the shared pool for the given pack. Maps parsed VerbDrillCards into
     * AuxDrillCards (same underlying file, separate model) and keeps the
     * originals for handing to the shared training session.
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
            val auxCards = mutableListOf<AuxDrillCard>()
            val sourceCards = mutableListOf<VerbDrillCard>()
            for (file in files) {
                val parseResult = file.bufferedReader().use { reader ->
                    VerbDrillCsvParser.parse(reader)
                }
                val parsed = parseResult.data ?: continue
                sourceCards.addAll(parsed)
                auxCards.addAll(parsed.map { it.toAux() })
            }
            Triple(auxCards, sourceCards, auxDrillStore.loadProgress())
        }
        allCards = ioResult.first
        allSourceCards = ioResult.second
        progressMap = ioResult.third
        _uiState.update {
            it.copy(
                isLoading = false,
                loadedLanguageId = languageId
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
                todayShownCount = progress?.todayShownCardIds?.size ?: 0
            )
        }
    }

    private fun filteredCards(pair: AuxDrillPair): List<AuxDrillCard> =
        allCards.filter { it.verb == pair.verb && it.tense == pair.tense }

    internal fun comboKeyFor(pair: AuxDrillPair): String = "aux|${pair.verb}|${pair.tense}"

    /**
     * Build a random [VerbDrillCard] deck for the selected pair, capped at
     * [sessionSize]. Caller hands this deck to the shared training session via
     * [TrainingViewModel.startVerbDrillSession].
     *
     * Returns null when the pair has no matching cards (pool not loaded or the
     * verb/tense is absent).
     */
    fun sessionCardsFor(pair: AuxDrillPair): List<VerbDrillCard>? {
        val matching = allSourceCards.filter { it.verb == pair.verb && it.tense == pair.tense }
        if (matching.isEmpty()) return null
        return matching.shuffled().take(sessionSize)
    }

    /**
     * Persist that [cardIds] were shown during an aux training session.
     * Called after the shared training session ends so the menu's progress
     * display and the "shown today" exclusion stay accurate.
     */
    fun recordShown(pair: AuxDrillPair, cardIds: Set<String>) {
        if (cardIds.isEmpty()) return
        val comboKey = comboKeyFor(pair)
        val existing = progressMap[comboKey]
        val ever = (existing?.everShownCardIds ?: emptySet()) + cardIds
        val today = (existing?.todayShownCardIds ?: emptySet()) + cardIds
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
        _uiState.update {
            it.copy(
                everShownCount = updated.everShownCardIds.size,
                todayShownCount = updated.todayShownCardIds.size
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPair = null) }
    }

    // ── Test hooks ───────────────────────────────────────────────────────
    internal fun injectPoolForTest(cards: List<VerbDrillCard>) {
        allCards = cards.map { it.toAux() }
        allSourceCards = cards
        progressMap = auxDrillStore.loadProgress()
        _uiState.update { it.copy(isLoading = false, loadedLanguageId = "it") }
    }

    internal fun allPoolCardsForTest(): List<AuxDrillCard> = allCards

    internal fun currentFilteredCardsForTest(): List<AuxDrillCard> =
        _uiState.value.selectedPair?.let { filteredCards(it) } ?: emptyList()

    internal fun auxStoreForTest(): AuxDrillStore = auxDrillStore
}

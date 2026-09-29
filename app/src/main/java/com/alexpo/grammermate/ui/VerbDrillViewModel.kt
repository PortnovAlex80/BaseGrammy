package com.alexpo.grammermate.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.AppContainer
import com.alexpo.grammermate.GrammarMateApplication
import com.alexpo.grammermate.data.BadSentenceEntry
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.Normalizer
import com.alexpo.grammermate.data.PracticeType
import com.alexpo.grammermate.data.ProgressStore
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillComboProgress
import com.alexpo.grammermate.data.VerbDrillCsvParser
import com.alexpo.grammermate.data.VerbDrillSessionState
import com.alexpo.grammermate.data.VerbDrillStore
import com.alexpo.grammermate.data.VerbDrillUiState
import com.alexpo.grammermate.data.StreakStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VerbDrillViewModel(application: Application) : AndroidViewModel(application) {

    private val logTag = "VerbDrillVM"
    private val application = application
    private val container: AppContainer = when (application) {
        is GrammarMateApplication -> application.container
        else -> AppContainer(application)
    }
    private var verbDrillStore: VerbDrillStore = container.verbDrillStore(null)
    private var usingTestStore = false

    /**
     * Test-only constructor that accepts a fake VerbDrillStore.
     * Usage in tests: VerbDrillViewModel(application, fakeStore)
     */
    constructor(application: Application, testStore: VerbDrillStore) : this(application) {
        verbDrillStore = testStore
        usingTestStore = true
    }

    /**
     * Test-only method to inject cards directly into the ViewModel.
     * Bypasses the normal LessonStore-based card loading for testing.
     */
    private fun extractLanguageIdForTestStore(): String =
        progressStore.load().languageId.value.ifBlank { "it" }

    fun injectTestCards(cards: List<VerbDrillCard>) {
        allCards = cards
        val tenses = cards.mapNotNull { it.tense }.distinct().sorted()
        val groups = cards.mapNotNull { it.group }.distinct().sorted()
        val persons = cards.mapNotNull { it.person }
            .distinct()
            .sortedBy { VerbDrillCsvParser.PERSON_ORDER.indexOf(it) }
        _uiState.update {
            it.copy(
                availableTenses = tenses,
                availableGroups = groups,
                availablePersons = persons,
                isLoading = false,
                loadedLanguageId = "it"
            )
        }
    }

    private val lessonStore = container.lessonStore
    private val progressStore = container.progressStore
    private val badSentenceStore = container.badSentenceStore
    private val badSentences = VerbDrillBadSentences(badSentenceStore)
    private val ttsEngine = container.ttsEngine
    private val streakStore = container.streakStore
    private val cardCatalogLoader by lazy {
        VerbDrillCardCatalogLoader(
            lessonStore, progressStore, badSentenceStore,
            VerbTenseInfoRepository(application.assets)
        )
    }

    private val _uiState = MutableStateFlow(VerbDrillUiState())
    val uiState: StateFlow<VerbDrillUiState> = _uiState

    private var allCards: List<VerbDrillCard> = emptyList()
    private var progressMap: Map<String, VerbDrillComboProgress> = emptyMap()

    /** Active pack ID for pack-scoped drill loading, null for legacy global mode */
    private var currentPackId: String? = null

    /** Flag to track if reloadForPack() has been called. Used to defer last session check. */
    private var reloadForPackCalled = false

    /** Session size from config, replaces hardcoded take(10). */
    private var sessionSize: Int = 10

    // ── Speed tracking ──────────────────────────────────────────────────
    private val speedTracker = VerbDrillSpeedTracker()
    val currentSpeedWpm: Int get() = speedTracker.wordsPerMinute

    /** Called when a new card is displayed to start timing. */
    fun markCardShown() {
        speedTracker.markCardShown()
    }

    /** Called when an answer is submitted to record elapsed time. */
    fun recordAnswerTime() {
        speedTracker.recordAnswer()
    }

    /** Tense reference info keyed by full tense name (e.g. "Passato Prossimo") */
    private var tenseInfoMap: Map<String, TenseInfo> = emptyMap()

    /** Active loadCards coroutine — cancelled on re-entry to prevent double-loading */
    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        sessionSize = container.configStore.load().sessionSize
        // Don't load cards in init — wait for reloadForPack() to set up pack-scoped store
        // Loading cards before reloadForPack() would check for last session in wrong path
    }

    /**
     * Reload cards for the given language.
     * Called when the user navigates to Verb Drill with a potentially changed language.
     * Uses the current [currentPackId] if set, otherwise falls back to global mode.
     */
    fun reloadForLanguage(languageId: String) {
        sessionSize = container.configStore.load().sessionSize
        val currentLang = _uiState.value.loadedLanguageId
        if (currentLang == languageId && allCards.isNotEmpty()) return

        // FIX: Find packId for language that has verb drill files
        val packForLanguage = lessonStore.getInstalledPacks()
            .firstOrNull { pack ->
                pack.languageId.value == languageId &&
                lessonStore.hasVerbDrill(pack.packId.value, languageId)
            }

        val newPackId = packForLanguage?.packId?.value
        if (newPackId != null) {
            // Found a pack with verb drill - use pack-scoped path
            Log.d(logTag, "reloadForLanguage: found pack $newPackId for language $languageId")
            reloadForPack(newPackId)
            return
        }

        // Fallback: no pack found for language, use legacy global store
        if (!usingTestStore) {
            verbDrillStore = container.verbDrillStore(null)
            Log.d(logTag, "reloadForLanguage: no pack found, using global store for language $languageId")
        }

        _uiState.update { it.copy(isLoading = true) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadCards(languageId) }
    }

    /**
     * Switch to a pack-scoped drill and reload cards.
     * Creates a new [VerbDrillStore] scoped to the given packId,
     * then loads cards from [LessonStore.getVerbDrillFiles] with the pack parameter.
     */
    fun reloadForPack(packId: String) {
        Log.d(logTag, "reloadForPack: packId=$packId, currentPackId=$currentPackId, reloadForPackCalled=$reloadForPackCalled")
        sessionSize = container.configStore.load().sessionSize
        reloadForPackCalled = true
        if (currentPackId == packId && allCards.isNotEmpty()) {
            // Cards already loaded, but progress may be stale — force re-read from disk
            viewModelScope.launch {
                progressMap = withContext(Dispatchers.IO) { verbDrillStore.loadProgress() }
                updateProgressDisplay()
                // Check for last session even when cards are already loaded
                checkForLastSessionAndShowDialog()
            }
            return
        }
        currentPackId = packId
        // Only replace verbDrillStore if not using a test store (injected via constructor)
        // This allows tests to inject a fake store without it being overwritten
        if (!usingTestStore) {
            verbDrillStore = container.verbDrillStore(packId)
            Log.d(logTag, "reloadForPack: created new pack-scoped store for packId=$packId")
        }
        _uiState.update { it.copy(isLoading = true) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadCards() }
    }

    /**
     * Refresh lastSessionContext from YAML file.
     * If a loadCards() job is running, waits for it to complete first.
     * Called on VerbDrillScreen entry to ensure SessionCard shows accurate data.
     */
    fun refreshLastSessionContext() {
        val job = loadJob
        if (job != null && job.isActive) {
            // Cards not ready yet — wait for load to finish, then refresh
            viewModelScope.launch {
                job.join()
                val lastSession = loadValidLastSession()
                _uiState.update { it.copy(lastSessionContext = lastSession) }
            }
        } else {
            val lastSession = loadValidLastSession()
            _uiState.update { it.copy(lastSessionContext = lastSession) }
        }
    }

    /**
     * Check for a last session and show the dialog if needed (VD-50).
     * Called after cards are loaded to determine if user should see Start Fresh/Resume dialog.
     * Session is shown regardless of age - context displays how long ago it was saved.
     * VD-51: Dialog disabled - inline SessionCard shown instead.
     */
    private fun checkForLastSessionAndShowDialog() {
        Log.d(logTag, "checkForLastSessionAndShowDialog: currentPackId=$currentPackId, reloadForPackCalled=$reloadForPackCalled")
        val lastSession = loadValidLastSession()
        Log.d(logTag, "checkForLastSession: lastSession = ${lastSession != null}, todayShownCardIds=${lastSession?.todayShownCardIds?.size ?: 0}")
        if (lastSession != null) {
            _uiState.update {
                it.copy(
                    // VD-51: Dialog disabled - use inline SessionCard instead
                    // showStartFreshResumeDialog = true,
                    lastSessionContext = lastSession
                )
            }
            Log.d(logTag, "checkForLastSession: lastSessionContext set, dialog disabled (VD-51)")
        } else {
            // Clear stale lastSessionContext when no session file exists
            _uiState.update {
                it.copy(lastSessionContext = null)
            }
        }
    }

    private suspend fun loadCards(languageId: String? = null) {
        // For tests: read straight from the injected fake store — the real
        // store reads drill CSVs from disk, which tests replace wholesale.
        if (usingTestStore) {
            val packId = currentPackId
            if (allCards.isEmpty() && packId != null) {
                val cards = verbDrillStore.loadAllCardsForPack(packId, extractLanguageIdForTestStore())
                if (cards.isNotEmpty()) {
                    injectTestCards(cards)
                }
            }
            _uiState.update { it.copy(isLoading = false) }
            progressMap = verbDrillStore.loadProgress()
            updateProgressDisplay()
            checkForLastSessionAndShowDialog()
            return
        }

        // Capture the store and pack that belong to this load before entering I/O.
        val packId = currentPackId
        val store = verbDrillStore
        val ioResult = withContext(Dispatchers.IO) {
            cardCatalogLoader.load(languageId, packId, store)
        }

        // State updates on main thread (viewModelScope.launch default dispatcher)
        allCards = ioResult.cards
        badSentences.useCatalog(ioResult.cardToPack, ioResult.packIds)
        progressMap = ioResult.progress
        tenseInfoMap = ioResult.tenseInfo

        _uiState.update {
            it.copy(
                badSentenceCount = ioResult.badCount,
                availableTenses = ioResult.tenses,
                availableGroups = ioResult.groups,
                availablePersons = ioResult.persons,
                isLoading = false,
                loadedLanguageId = ioResult.lang
            )
        }
        updateProgressDisplay()

        Log.d(logTag, "Loaded ${ioResult.cards.size} verb drill cards for language ${ioResult.lang}")

        // Check for last session and show dialog if needed (VD-50)
        checkForLastSessionAndShowDialog()
    }

    private fun loadValidLastSession(): com.alexpo.grammermate.data.VerbDrillLastSessionState? {
        val lastSession = verbDrillStore.loadLastSession() ?: return null
        when (VerbDrillSessionCheckpoint.validity(lastSession, currentPackId, allCards)) {
            VerbDrillSessionCheckpoint.Validity.PACK_MISMATCH -> {
                Log.i(logTag, "Discarding verb drill last session for pack ${lastSession.packId}; current pack is $currentPackId")
                verbDrillStore.deleteLastSession()
                return null
            }
            VerbDrillSessionCheckpoint.Validity.MISSING_CARDS -> {
                Log.i(logTag, "Discarding verb drill last session with card IDs outside current pack")
                verbDrillStore.deleteLastSession()
                return null
            }
            VerbDrillSessionCheckpoint.Validity.VALID -> return lastSession
        }
    }

    fun getTenseInfo(tenseName: String?): TenseInfo? {
        if (tenseName.isNullOrBlank()) return null
        return tenseInfoMap[tenseName]
    }

    fun selectTense(tense: String?) {
        _uiState.update { it.copy(selectedTense = tense, allDoneToday = false) }
        updateProgressDisplay()
    }

    fun selectGroup(group: String?) {
        _uiState.update { it.copy(selectedGroup = group, allDoneToday = false) }
        updateProgressDisplay()
    }

    fun selectPerson(person: String?) {
        _uiState.update { it.copy(selectedPerson = person, allDoneToday = false) }
        updateProgressDisplay()
    }

    fun toggleSortByFrequency() {
        _uiState.update { it.copy(sortByFrequency = !it.sortByFrequency) }
    }

    private fun updateProgressDisplay() {
        val summary = VerbDrillDeckPlanner.progressSummary(
            allCards, progressMap, VerbDrillDeckPlanner.Selection(_uiState.value)
        )

        _uiState.update {
            it.copy(
                totalCards = summary.totalCards,
                everShownCount = summary.everShownCount,
                todayShownCount = summary.todayShownCount
            )
        }
    }

    private fun startSession(
        additionalShownCardIds: Set<String>,
        ignoreTodayShown: Boolean = false
    ) {
        val state = _uiState.value
        val batch = VerbDrillDeckPlanner.planBatch(
            cards = allCards,
            progressMap = progressMap,
            selection = VerbDrillDeckPlanner.Selection(state),
            additionalShownCardIds = additionalShownCardIds,
            ignoreTodayShown = ignoreTodayShown,
            sortByFrequency = state.sortByFrequency,
            sessionSize = sessionSize
        )
        if (batch.cards.isEmpty()) {
            _uiState.update { it.copy(session = null, allDoneToday = true) }
            return
        }

        val selected = batch.cards

        val session = VerbDrillSessionState(cards = selected)
        val firstCard = selected.firstOrNull()
        val firstCardIsBad = firstCard?.let { isCardBad(it) } ?: false

        _uiState.update {
            it.copy(
                session = session,
                allDoneToday = false,
                currentCardIsBad = firstCardIsBad
            )
        }

        // Save last session for resume (VD-51) - save immediately when starting
        val lastSessionState = VerbDrillSessionCheckpoint.snapshot(state, session, batch.excludedCardIds, currentPackId)
        verbDrillStore.saveLastSession(lastSessionState)
        Log.d(logTag, "startSession: saved lastSession with ${batch.excludedCardIds.size} shown cards and ${selected.size} session cards")

        // Reset speed tracking for new session
        speedTracker.startBatch()
    }

    /**
     * Submit a correct answer: advances the card index, increments correct count,
     * persists progress, and marks the next card shown timestamp.
     */
    fun submitCorrectAnswer() {
        val session = _uiState.value.session ?: return
        if (session.isComplete) return
        if (session.currentIndex >= session.cards.size) return

        val card = session.cards[session.currentIndex]

        val updatedCorrect = session.correctCount + 1
        val nextIndex = session.currentIndex + 1
        val isComplete = nextIndex >= session.cards.size

        val nextCard = if (!isComplete) session.cards[nextIndex] else null
        val nextCardIsBad = nextCard?.let { isCardBad(it) } ?: false

        _uiState.update { state ->
            state.copy(
                session = session.copy(
                    currentIndex = nextIndex,
                    correctCount = updatedCorrect,
                    isComplete = isComplete
                ),
                currentCardIsBad = nextCardIsBad
            )
        }

        persistCardProgress(card)
        saveLastSessionState(_uiState.value.session ?: session)

        if (isComplete) {
            recordFireStreakIfCompleted(updatedCorrect)
        } else {
            speedTracker.markCardShown()
        }

        updateProgressDisplay()
    }

    /**
     * Mark a card as completed (hint-shown / skipped): increments incorrect count,
     * advances the card index, and persists progress.
     * Used when a card is "done" but not answered correctly (hint or skip).
     */
    fun markCardCompleted() {
        val session = _uiState.value.session ?: return
        if (session.isComplete) return
        if (session.currentIndex >= session.cards.size) return

        val card = session.cards[session.currentIndex]

        val updatedIncorrect = session.incorrectCount + 1
        val nextIndex = session.currentIndex + 1
        val isComplete = nextIndex >= session.cards.size

        val nextCard = if (!isComplete) session.cards[nextIndex] else null
        val nextCardIsBad = nextCard?.let { isCardBad(it) } ?: false

        _uiState.update { state ->
            state.copy(
                session = session.copy(
                    currentIndex = nextIndex,
                    incorrectCount = updatedIncorrect,
                    isComplete = isComplete
                ),
                currentCardIsBad = nextCardIsBad
            )
        }

        persistCardProgress(card)
        saveLastSessionState(_uiState.value.session ?: session)

        if (!isComplete) {
            speedTracker.markCardShown()
        }

        updateProgressDisplay()
    }

    /**
     * Persist progress for a card to the verb drill store.
     */
    private fun persistCardProgress(card: VerbDrillCard) {
        val uiState = _uiState.value
        val selection = VerbDrillDeckPlanner.Selection(uiState)
        val comboKey = selection.comboKey
        val existing = progressMap[comboKey]
        val everShown = (existing?.everShownCardIds ?: emptySet()) + card.id
        val todayShown = (existing?.todayShownCardIds ?: emptySet()) + card.id
        val totalCards = VerbDrillDeckPlanner.filteredCards(allCards, selection).size

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
    }

    /**
     * Record fire streak for verb drill session completion.
     * A session counts when correctCount >= (sessionSize - badSentenceCount).
     */
    private fun recordFireStreakIfCompleted(correctCount: Int) {
        val badCount = badSentences.count()
        val required = (sessionSize - badCount).coerceAtLeast(1)
        if (correctCount >= required) {
            val languageId = _uiState.value.loadedLanguageId ?: return
            streakStore.recordPracticeTypeCompletion(languageId, PracticeType.VERB)
        }
    }

    fun setSessionSize(size: Int) {
        sessionSize = size.coerceIn(3, 1000)
    }

    fun nextBatch() {
        startSession(emptySet(), ignoreTodayShown = false)
    }

    fun startSession() {
        startSession(emptySet(), ignoreTodayShown = true)
    }

    fun prevCard() {
        val session = _uiState.value.session ?: return
        if (session.currentIndex > 0) {
            val prevIndex = session.currentIndex - 1
            val prevCard = session.cards.getOrElse(prevIndex) { null }
            val prevCardIsBad = prevCard?.let { isCardBad(it) } ?: false
            _uiState.update { state ->
                state.copy(
                    session = session.copy(
                        currentIndex = prevIndex,
                        isComplete = false
                    ),
                    currentCardIsBad = prevCardIsBad
                )
            }
        }
    }

    fun nextCardManual() {
        val session = _uiState.value.session ?: return
        val nextIndex = session.currentIndex + 1
        if (nextIndex < session.cards.size) {
            saveLastSessionState(session)
            _uiState.update { state ->
                state.copy(
                    session = session.copy(currentIndex = nextIndex)
                )
            }
            speedTracker.markCardShown()
        } else {
            saveLastSessionState(session)
            startSession(emptySet())
        }
    }

    fun exitSession() {
        Log.d(logTag, "exitSession: saving last session...")
        val session = _uiState.value.session
        // Always save state for potential resume (both complete and incomplete sessions)
        if (session != null) {
            saveLastSessionState(session)
        }
        verbDrillStore.flush()
        _uiState.update { it.copy(session = null, currentCardIsBad = false) }
    }

    /**
     * Save the current session state for potential resume (VD-50).
     * Called when user exits an incomplete session.
     * Saves filters and todayShownCardIds for loading next cards on resume.
     */
    fun saveLastSessionState(session: VerbDrillSessionState) {
        val state = _uiState.value
        val comboKey = VerbDrillDeckPlanner.Selection(state).comboKey
        val progress = progressMap[comboKey]
        val todayShownCardIds = progress?.todayShownCardIds ?: emptySet()

        val lastSessionState = VerbDrillSessionCheckpoint.snapshot(state, session, todayShownCardIds, currentPackId)
        verbDrillStore.saveLastSession(lastSessionState)
        Log.d(logTag, "saveLastSessionState: saved filters with ${todayShownCardIds.size} shown cards, packId=$currentPackId")
    }

    /**
     * Save current session state for resume.
     * Called from GrammarMateApp when Verb Drill card session ends (complete or mid-session).
     */
    fun persistSessionState() {
        val session = _uiState.value.session ?: return
        saveLastSessionState(session)
    }

    // ── Start Fresh / Resume Dialog (VD-50) ──────────────────────────────────────

    fun onResumeSession() {
        val lastSession = loadValidLastSession() ?: run {
            // No session to resume - should not happen if dialog was shown
            _uiState.update { it.copy(showStartFreshResumeDialog = false) }
            return
        }

        // Restore filters
        _uiState.update { state ->
            state.copy(
                selectedTense = lastSession.selectedTense,
                selectedGroup = lastSession.selectedGroup,
                selectedPerson = lastSession.selectedPerson,
                sortByFrequency = lastSession.sortByFrequency,
                showStartFreshResumeDialog = false
            )
        }

        val excludedCardIds = lastSession.todayShownCardIds
        startSession(excludedCardIds, ignoreTodayShown = false)
    }

    /**
     * Repeat the last session with SAME cards from the start (VD-51).
     * Restores filters but clears todayShownCardIds for a fresh run.
     */
    fun onRepeatSession() {
        val lastSession = loadValidLastSession() ?: run {
            _uiState.update { it.copy(showStartFreshResumeDialog = false) }
            return
        }

        // Restore filters
        _uiState.update { state ->
            state.copy(
                selectedTense = lastSession.selectedTense,
                selectedGroup = lastSession.selectedGroup,
                selectedPerson = lastSession.selectedPerson,
                sortByFrequency = lastSession.sortByFrequency,
                showStartFreshResumeDialog = false
            )
        }

        val cardsById = allCards.associateBy { it.id }
        val repeatCards = lastSession.sessionCardIds.mapNotNull { cardsById[it] }

        if (repeatCards.size == lastSession.sessionCardIds.size && repeatCards.isNotEmpty()) {
            val firstCardIsBad = repeatCards.firstOrNull()?.let { isCardBad(it) } ?: false
            _uiState.update { state ->
                state.copy(
                    session = VerbDrillSessionState(cards = repeatCards),
                    allDoneToday = false,
                    currentCardIsBad = firstCardIsBad
                )
            }
            verbDrillStore.saveLastSession(
                lastSession.copy(
                    sessionCardIds = repeatCards.map { it.id },
                    currentIndex = 0
                )
            )
            speedTracker.startBatch()
        } else {
            // Legacy saved sessions may not have card IDs. In that case repeat
            // starts from the full filtered pool instead of today's remaining pool.
            startSession(emptySet(), ignoreTodayShown = true)
        }

        // Keep last session for potential repeat-again
        // Don't delete - user may want to repeat again
    }

    /**
     * Start fresh - delete last session and return to selection screen (VD-50).
     */
    fun onStartFresh() {
        verbDrillStore.deleteLastSession()
        _uiState.update { state ->
            state.copy(
                showStartFreshResumeDialog = false,
                selectedTense = null,
                selectedGroup = null,
                selectedPerson = null,
                sortByFrequency = false,
                session = null,
                currentCardIsBad = false,
                allDoneToday = false,
                lastSessionContext = null
            )
        }
        updateProgressDisplay()
    }

    /**
     * Dismiss the Start Fresh / Resume dialog (VD-50).
     * Does NOT delete the last session - user may return.
     */
    fun onDismissDialog() {
        _uiState.update { it.copy(showStartFreshResumeDialog = false) }
    }

    // ── Debug Dialog ─────────────────────────────────────────────────────────────

    /**
     * Show debugging information about progress files.
     * Logs and displays the current state of progress data for debugging.
     */
    fun showDebugDialog() {
        viewModelScope.launch {
            val debugInfo = VerbDrillDebugReport.build(currentPackId, progressMap, verbDrillStore)

            // Log the debug info
            Log.d(logTag, "showDebugDialog:\n$debugInfo")

            // Update UI state to show the dialog
            _uiState.update { it.copy(showDebugInfo = true, debugInfo = debugInfo) }
        }
    }

    /**
     * Hide the debugging dialog.
     */
    fun hideDebugDialog() {
        _uiState.update { it.copy(showDebugInfo = false) }
    }

    // ── Bad Sentence Support ──────────────────────────────────────────────

    fun flagBadSentence() {
        val session = _uiState.value.session ?: return
        val index = session.currentIndex
        if (index >= session.cards.size) return
        val card = session.cards[index]
        if (!badSentences.flag(card, _uiState.value.loadedLanguageId ?: "")) return
        _uiState.update {
            it.copy(badSentenceCount = badSentences.count(), currentCardIsBad = true)
        }
    }

    fun unflagBadSentence() {
        val session = _uiState.value.session ?: return
        val index = session.currentIndex
        if (index >= session.cards.size) return
        val card = session.cards[index]
        if (!badSentences.unflag(card)) return
        _uiState.update {
            it.copy(badSentenceCount = badSentences.count(), currentCardIsBad = false)
        }
    }

    fun isBadSentence(): Boolean {
        return _uiState.value.currentCardIsBad
    }

    fun exportBadSentences(): String? = badSentences.export()

    private fun isCardBad(card: VerbDrillCard): Boolean = badSentences.isBad(card)

    // ── Verb Reference ──────────────────────────────────────────────────

    /**
     * Returns all cards from the current session matching the given verb+tense,
     * sorted by their original order in the session.
     */
    fun getConjugationForVerb(verb: String, tense: String): List<VerbDrillCard> {
        val session = _uiState.value.session ?: return emptyList()
        return session.cards.filter { card ->
            card.verb == verb && card.tense == tense
        }
    }

    /**
     * Speaks just the verb infinitive at a slightly slower speed for clarity.
     */
    fun speakVerbInfinitive(verb: String) {
        if (verb.isBlank()) return
        speakTts(verb, speed = 0.8f)
    }

    // ── TTS Support ──────────────────────────────────────────────────────

    val ttsState: StateFlow<TtsState> = ttsEngine.state

    fun speakTts(text: String, speed: Float? = null) {
        if (text.isBlank()) return
        val langId = _uiState.value.loadedLanguageId ?: "it"
        val effectiveSpeed = speed ?: container.configStore.load().ttsSpeed
        viewModelScope.launch {
            try {
                ttsEngine.speak(text, languageId = langId, speed = effectiveSpeed)
            } catch (e: Throwable) {
                Log.e(logTag, "speakTts failed", e)
            }
        }
    }

    fun stopTts() {
        ttsEngine.stop()
    }

    override fun onCleared() {
        verbDrillStore.flush()
        super.onCleared()
    }
}

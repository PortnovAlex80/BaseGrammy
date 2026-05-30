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
import org.yaml.snakeyaml.Yaml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TenseInfo(
    val name: String,
    val short: String,
    val formula: String,
    val usageRu: String,
    val examples: List<TenseExample>
)

data class TenseExample(
    val it: String,
    val ru: String,
    val note: String
)

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
    fun injectTestCards(cards: List<VerbDrillCard>) {
        allCards = cards
        val tenses = cards.mapNotNull { it.tense }.distinct().sorted()
        val groups = cards.mapNotNull { it.group }.distinct().sorted()
        _uiState.update {
            it.copy(
                availableTenses = tenses,
                availableGroups = groups,
                isLoading = false,
                loadedLanguageId = "it"
            )
        }
    }

    private val lessonStore = container.lessonStore
    private val progressStore = container.progressStore
    private val badSentenceStore = container.badSentenceStore
    private val ttsEngine = container.ttsEngine
    private val streakStore = container.streakStore

    private val _uiState = MutableStateFlow(VerbDrillUiState())
    val uiState: StateFlow<VerbDrillUiState> = _uiState

    private var allCards: List<VerbDrillCard> = emptyList()
    private var progressMap: Map<String, VerbDrillComboProgress> = emptyMap()

    /** Active pack ID for pack-scoped drill loading, null for legacy global mode */
    private var currentPackId: String? = null

    /** Flag to track if reloadForPack() has been called. Used to defer last session check. */
    private var reloadForPackCalled = false

    /** Maps card ID to pack ID for bad sentence scoping */
    private var packIdForCardId: Map<String, String> = emptyMap()

    /** Session size from config, replaces hardcoded take(10). */
    private var sessionSize: Int = 10

    // ── Speed tracking ──────────────────────────────────────────────────
    private var cardShownTimestamp: Long = 0L
    private var totalAnswerTimeMs: Long = 0L
    private var totalAnswersForSpeed: Int = 0
    val currentSpeedWpm: Int
        get() {
            val minutes = totalAnswerTimeMs / 60000.0
            if (minutes <= 0.0) return 0
            return (totalAnswersForSpeed / minutes).toInt()
        }

    /** Called when a new card is displayed to start timing. */
    fun markCardShown() {
        cardShownTimestamp = System.currentTimeMillis()
    }

    /** Called when an answer is submitted to record elapsed time. */
    fun recordAnswerTime() {
        if (cardShownTimestamp > 0) {
            totalAnswerTimeMs += System.currentTimeMillis() - cardShownTimestamp
            totalAnswersForSpeed++
            cardShownTimestamp = 0L
        }
    }

    /** Tracks pack IDs that have verb drill cards, for counting bad sentences */
    private var activePackIds: Set<String> = emptySet()

    /** Tense reference info keyed by full tense name (e.g. "Passato Prossimo") */
    private var tenseInfoMap: Map<String, TenseInfo> = emptyMap()

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
        viewModelScope.launch { loadCards(languageId) }
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
        viewModelScope.launch { loadCards() }
    }

    /**
     * Refresh lastSessionContext from YAML file.
     * Called on VerbDrillScreen entry to ensure SessionCard shows accurate data.
     */
    fun refreshLastSessionContext() {
        val lastSession = loadValidLastSession()
        _uiState.update {
            it.copy(lastSessionContext = lastSession)
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
        // For tests: if using test store, skip file I/O
        if (usingTestStore) {
            if (allCards.isNotEmpty()) {
                // Cards already injected via injectTestCards()
                _uiState.update { it.copy(isLoading = false) }
                progressMap = verbDrillStore.loadProgress()
                updateProgressDisplay()
                checkForLastSessionAndShowDialog()
            } else {
                // No cards yet, will be loaded via injectTestCards()
                _uiState.update { it.copy(isLoading = false) }
            }
            return
        }

        // All file I/O and parsing runs on Dispatchers.IO to avoid blocking main thread
        val ioResult = withContext(Dispatchers.IO) {
            val lang = languageId ?: progressStore.load().languageId.value
            val files = if (currentPackId != null) {
                lessonStore.getVerbDrillFiles(currentPackId!!, lang)
            } else {
                @Suppress("DEPRECATION")
                lessonStore.getVerbDrillFiles(lang)
            }
            val cards = mutableListOf<VerbDrillCard>()
            val cardToPack = mutableMapOf<String, String>()
            val packIds = mutableSetOf<String>()

            for (file in files) {
                val parseResult = file.bufferedReader().use { reader ->
                    VerbDrillCsvParser.parse(reader)
                }

                // Only process successfully parsed cards
                val parsed = parseResult.data ?: continue

                // Use the active packId, or resolve from filename in global mode
                val packId = currentPackId ?: run {
                    val fileName = file.nameWithoutExtension
                    val lessonId = fileName.removePrefix("${lang}_")
                    lessonStore.getPackIdForLesson(lessonId) ?: lessonId
                }
                packIds.add(packId)

                for (card in parsed) {
                    cardToPack[card.id] = packId
                }
                cards.addAll(parsed)
            }

            val tenses = cards.mapNotNull { it.tense }.distinct().sorted()
            val groups = cards.mapNotNull { it.group }.distinct().sorted()
            val progress = verbDrillStore.loadProgress()
            val badCount = packIds.sumOf { badSentenceStore.getBadSentenceCount(it) }

            // Load tense reference info (asset file read)
            val tenseInfo = loadTenseInfoInternal(lang)

            LoadCardsResult(cards, cardToPack, packIds, tenses, groups, progress, badCount, lang, tenseInfo)
        }

        // State updates on main thread (viewModelScope.launch default dispatcher)
        allCards = ioResult.cards
        packIdForCardId = ioResult.cardToPack
        activePackIds = ioResult.packIds
        progressMap = ioResult.progress
        tenseInfoMap = ioResult.tenseInfo

        _uiState.update {
            it.copy(
                badSentenceCount = ioResult.badCount,
                availableTenses = ioResult.tenses,
                availableGroups = ioResult.groups,
                isLoading = false,
                loadedLanguageId = ioResult.lang
            )
        }

        Log.d(logTag, "Loaded ${ioResult.cards.size} verb drill cards for language ${ioResult.lang}")

        // Check for last session and show dialog if needed (VD-50)
        checkForLastSessionAndShowDialog()
    }

    private fun loadValidLastSession(): com.alexpo.grammermate.data.VerbDrillLastSessionState? {
        val lastSession = verbDrillStore.loadLastSession() ?: return null
        val currentPack = currentPackId
        if (currentPack != null && lastSession.packId != null && lastSession.packId != currentPack) {
            Log.i(logTag, "Discarding verb drill last session for pack ${lastSession.packId}; current pack is $currentPack")
            verbDrillStore.deleteLastSession()
            return null
        }
        if (lastSession.sessionCardIds.isNotEmpty()) {
            val availableIds = allCards.asSequence().map { it.id }.toSet()
            val allSessionCardsAvailable = lastSession.sessionCardIds.all { it in availableIds }
            if (!allSessionCardsAvailable) {
                Log.i(logTag, "Discarding verb drill last session with card IDs outside current pack")
                verbDrillStore.deleteLastSession()
                return null
            }
        }
        return lastSession
    }

    /** Holds the result of I/O-heavy card loading, returned from Dispatchers.IO */
    private data class LoadCardsResult(
        val cards: List<VerbDrillCard>,
        val cardToPack: Map<String, String>,
        val packIds: Set<String>,
        val tenses: List<String>,
        val groups: List<String>,
        val progress: Map<String, VerbDrillComboProgress>,
        val badCount: Int,
        val lang: String,
        val tenseInfo: Map<String, TenseInfo>
    )

    /**
     * Load tense reference info from assets. Safe to call on any thread.
     * Returns the parsed map; caller assigns to tenseInfoMap on main thread.
     */
    private fun loadTenseInfoInternal(languageId: String): Map<String, TenseInfo> {
        val fileName = "grammarmate/tenses/${languageId}_tenses.yaml"
        return try {
            val yaml = getApplication<Application>().assets.open(fileName).bufferedReader().readText()
            val result = parseTenseInfo(yaml)
            Log.d(logTag, "Loaded tense info for $languageId: ${result.size} tenses")
            result
        } catch (e: Exception) {
            Log.w(logTag, "Failed to load tense info from $fileName", e)
            emptyMap()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTenseInfo(yamlText: String): Map<String, TenseInfo> {
        val yaml = Yaml()
        val data = yaml.load<Map<String, Any>>(yamlText)
        val tensesList = data["tenses"] as? List<Map<String, Any>> ?: return emptyMap()
        val result = mutableMapOf<String, TenseInfo>()
        for (entry in tensesList) {
            val name = entry["name"] as? String ?: continue
            val short = entry["short"] as? String ?: name.take(8)
            val formula = entry["formula"] as? String ?: ""
            val usageRu = entry["usage_ru"] as? String ?: ""
            val examplesList = entry["examples"] as? List<Map<String, String>> ?: emptyList()
            val examples = examplesList.map { ex ->
                TenseExample(
                    it = ex["it"] ?: "",
                    ru = ex["ru"] ?: "",
                    note = ex["note"] ?: ""
                )
            }
            result[name] = TenseInfo(
                name = name,
                short = short,
                formula = formula,
                usageRu = usageRu,
                examples = examples
            )
        }
        return result
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

    fun toggleSortByFrequency() {
        _uiState.update { it.copy(sortByFrequency = !it.sortByFrequency) }
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

    private fun startSession(
        additionalShownCardIds: Set<String>,
        ignoreTodayShown: Boolean = false
    ) {
        val state = _uiState.value
        val comboKey = "${state.selectedGroup ?: ""}|${state.selectedTense ?: ""}"

        val filtered = allCards.filter { card ->
            (state.selectedTense == null || card.tense == state.selectedTense) &&
            (state.selectedGroup == null || card.group == state.selectedGroup)
        }

        if (filtered.isEmpty()) {
            _uiState.update { it.copy(session = null, allDoneToday = true) }
            return
        }

        val progress = progressMap[comboKey]
        val progressTodayShownCardIds = if (ignoreTodayShown) {
            emptySet()
        } else {
            progress?.todayShownCardIds ?: emptySet()
        }

        // Merge todayShownCardIds from progress and additional shown cards (from last session)
        val allShownCardIds = progressTodayShownCardIds + additionalShownCardIds

        val remaining = filtered.filter { it.id !in allShownCardIds }

        if (remaining.isEmpty()) {
            _uiState.update { it.copy(session = null, allDoneToday = true) }
            return
        }

        val selected = if (state.sortByFrequency) {
            remaining.sortedBy { it.rank ?: Int.MAX_VALUE }.take(sessionSize)
        } else {
            remaining.shuffled().take(sessionSize)
        }

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
        val lastSessionState = com.alexpo.grammermate.data.VerbDrillLastSessionState(
            selectedTense = state.selectedTense,
            selectedGroup = state.selectedGroup,
            sortByFrequency = state.sortByFrequency,
            todayShownCardIds = allShownCardIds,
            sessionCardIds = selected.map { it.id },
            currentIndex = 0,
            packId = currentPackId
        )
        verbDrillStore.saveLastSession(lastSessionState)
        Log.d(logTag, "startSession: saved lastSession with ${allShownCardIds.size} shown cards and ${selected.size} session cards")

        // Reset speed tracking for new session
        totalAnswerTimeMs = 0L
        totalAnswersForSpeed = 0
        cardShownTimestamp = System.currentTimeMillis()
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
            cardShownTimestamp = System.currentTimeMillis()
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
            cardShownTimestamp = System.currentTimeMillis()
        }

        updateProgressDisplay()
    }

    /**
     * Persist progress for a card to the verb drill store.
     */
    private fun persistCardProgress(card: VerbDrillCard) {
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
    }

    /**
     * Record fire streak for verb drill session completion.
     * A session counts when correctCount >= (sessionSize - badSentenceCount).
     */
    private fun recordFireStreakIfCompleted(correctCount: Int) {
        val badCount = activePackIds.sumOf { badSentenceStore.getBadSentenceCount(it) }
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
            cardShownTimestamp = System.currentTimeMillis()
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
        val comboKey = "${state.selectedGroup ?: ""}|${state.selectedTense ?: ""}"
        val progress = progressMap[comboKey]
        val todayShownCardIds = progress?.todayShownCardIds ?: emptySet()

        val lastSessionState = com.alexpo.grammermate.data.VerbDrillLastSessionState(
            selectedTense = state.selectedTense,
            selectedGroup = state.selectedGroup,
            sortByFrequency = state.sortByFrequency,
            todayShownCardIds = todayShownCardIds,
            sessionCardIds = session.cards.map { it.id },
            currentIndex = session.currentIndex.coerceIn(0, session.cards.size),
            packId = currentPackId
        )
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
            totalAnswerTimeMs = 0L
            totalAnswersForSpeed = 0
            cardShownTimestamp = System.currentTimeMillis()
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
            // Build debug info string
            val debugInfo = buildString {
                appendLine("=== Verb Drill Debug Info ===")
                appendLine()

                // Pack ID
                appendLine("Pack ID: ${currentPackId ?: "null (legacy mode)"}")
                appendLine()

                // Progress file path (we need to get this from the store)
                appendLine("Progress Map Size: ${progressMap.size} entries")
                if (progressMap.isNotEmpty()) {
                    appendLine("Progress Keys:")
                    progressMap.keys.forEach { key ->
                        val progress = progressMap[key]
                        appendLine("  - $key: everShown=${progress?.everShownCardIds?.size ?: 0}, todayShown=${progress?.todayShownCardIds?.size ?: 0}")
                    }
                }
                appendLine()

                // Last session state
                val lastSession = verbDrillStore.loadLastSession()
                if (lastSession != null) {
                    appendLine("Last Session State:")
                    appendLine("  - Tense: ${lastSession.selectedTense ?: "null"}")
                    appendLine("  - Group: ${lastSession.selectedGroup ?: "null"}")
                    appendLine("  - SortByFrequency: ${lastSession.sortByFrequency}")
                    appendLine("  - TodayShownCardIds: ${lastSession.todayShownCardIds.size}")
                    appendLine("  - SessionCardIds: ${lastSession.sessionCardIds.size}")
                    appendLine("  - CurrentIndex: ${lastSession.currentIndex}")
                } else {
                    appendLine("Last Session State: null")
                }
                appendLine()

                // Store info
                if (verbDrillStore is com.alexpo.grammermate.data.VerbDrillStoreImpl) {
                    // Use reflection to access the private file field
                    try {
                        val fileField = verbDrillStore.javaClass.getDeclaredField("file")
                        fileField.isAccessible = true
                        val file = fileField.get(verbDrillStore) as? java.io.File
                        appendLine("Progress File Path: ${file?.absolutePath ?: "unknown"}")
                        appendLine("Progress File Exists: ${file?.exists() ?: false}")
                        appendLine("Progress File Size: ${if (file?.exists() == true) "${file.length()} bytes" else "N/A"}")

                        val lastSessionFileField = verbDrillStore.javaClass.getDeclaredField("lastSessionFile")
                        lastSessionFileField.isAccessible = true
                        val lastSessionFile = lastSessionFileField.get(verbDrillStore) as? java.io.File
                        appendLine("Last Session File Path: ${lastSessionFile?.absolutePath ?: "unknown"}")
                        appendLine("Last Session File Exists: ${lastSessionFile?.exists() ?: false}")
                        appendLine("Last Session File Size: ${if (lastSessionFile?.exists() == true) "${lastSessionFile.length()} bytes" else "N/A"}")
                    } catch (e: Exception) {
                        appendLine("Error accessing file info: ${e.message}")
                    }
                }
            }

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
        val packId = packIdForCardId[card.id] ?: return

        badSentenceStore.addBadSentence(
            packId = packId,
            cardId = card.id,
            languageId = _uiState.value.loadedLanguageId ?: "",
            sentence = card.promptRu,
            translation = card.answer,
            mode = "verb_drill"
        )
        _uiState.update {
            it.copy(badSentenceCount = activePackIds.sumOf { pid -> badSentenceStore.getBadSentenceCount(pid) }, currentCardIsBad = true)
        }
    }

    fun unflagBadSentence() {
        val session = _uiState.value.session ?: return
        val index = session.currentIndex
        if (index >= session.cards.size) return
        val card = session.cards[index]
        val packId = packIdForCardId[card.id] ?: return

        badSentenceStore.removeBadSentence(packId, card.id)
        _uiState.update {
            it.copy(badSentenceCount = activePackIds.sumOf { pid -> badSentenceStore.getBadSentenceCount(pid) }, currentCardIsBad = false)
        }
    }

    fun isBadSentence(): Boolean {
        return _uiState.value.currentCardIsBad
    }

    fun exportBadSentences(): String? {
        if (activePackIds.isEmpty()) return null
        // Use unified export for all packs
        for (packId in activePackIds) {
            val entries = badSentenceStore.getBadSentences(packId)
            if (entries.isNotEmpty()) {
                val file = badSentenceStore.exportUnified()
                return file.absolutePath
            }
        }
        return null
    }

    private fun isCardBad(card: VerbDrillCard): Boolean {
        val packId = packIdForCardId[card.id] ?: return false
        return badSentenceStore.isBadSentence(packId, card.id)
    }

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

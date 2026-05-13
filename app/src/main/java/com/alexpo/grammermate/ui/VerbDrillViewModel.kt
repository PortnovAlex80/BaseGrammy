package com.alexpo.grammermate.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.data.BadSentenceEntry
import com.alexpo.grammermate.data.BadSentenceStore
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.Normalizer
import com.alexpo.grammermate.data.ProgressStore
import com.alexpo.grammermate.data.TtsProvider
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillComboProgress
import com.alexpo.grammermate.data.VerbDrillCsvParser
import com.alexpo.grammermate.data.VerbDrillSessionState
import com.alexpo.grammermate.data.VerbDrillStore
import com.alexpo.grammermate.data.VerbDrillUiState
import org.yaml.snakeyaml.Yaml
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private var verbDrillStore = VerbDrillStore(application)
    private val lessonStore = LessonStore(application)
    private val progressStore = ProgressStore(application)
    private val badSentenceStore = BadSentenceStore(application)
    private val ttsEngine = TtsProvider.getInstance(application).ttsEngine

    private val _uiState = MutableStateFlow(VerbDrillUiState())
    val uiState: StateFlow<VerbDrillUiState> = _uiState

    private var allCards: List<VerbDrillCard> = emptyList()
    private var progressMap: Map<String, VerbDrillComboProgress> = emptyMap()

    /** Active pack ID for pack-scoped drill loading, null for legacy global mode */
    private var currentPackId: String? = null

    /** Maps card ID to pack ID for bad sentence scoping */
    private var packIdForCardId: Map<String, String> = emptyMap()

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
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch { loadCards() }
    }

    /**
     * Reload cards for the given language.
     * Called when the user navigates to Verb Drill with a potentially changed language.
     * Uses the current [currentPackId] if set, otherwise falls back to global mode.
     */
    fun reloadForLanguage(languageId: String) {
        val currentLang = _uiState.value.loadedLanguageId
        if (currentLang == languageId && allCards.isNotEmpty()) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch { loadCards(languageId) }
    }

    /**
     * Switch to a pack-scoped drill and reload cards.
     * Creates a new [VerbDrillStore] scoped to the given packId,
     * then loads cards from [LessonStore.getVerbDrillFiles] with the pack parameter.
     */
    fun reloadForPack(packId: String) {
        if (currentPackId == packId && allCards.isNotEmpty()) {
            // Cards already loaded, but progress may be stale — force re-read from disk
            progressMap = verbDrillStore.loadProgress()
            updateProgressDisplay()
            return
        }
        currentPackId = packId
        verbDrillStore = VerbDrillStore(application, packId = packId)
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch { loadCards() }
    }

    private fun loadCards(languageId: String? = null) {
        val lang = languageId ?: progressStore.load().languageId
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
            val content = file.readText()
            val (_, parsed) = VerbDrillCsvParser.parse(content)

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

        allCards = cards
        packIdForCardId = cardToPack
        activePackIds = packIds

        val tenses = cards.mapNotNull { it.tense }.distinct().sorted()
        val groups = cards.mapNotNull { it.group }.distinct().sorted()

        progressMap = verbDrillStore.loadProgress()

        // Sum bad sentence counts across all active packs
        val badCount = packIds.sumOf { badSentenceStore.getBadSentenceCount(it) }

        _uiState.update {
            it.copy(badSentenceCount = badCount, availableTenses = tenses, availableGroups = groups, isLoading = false, loadedLanguageId = lang)
        }

        Log.d(logTag, "Loaded ${cards.size} verb drill cards for language $lang")

        // Load tense reference info
        loadTenseInfo(lang)
    }

    private fun loadTenseInfo(languageId: String) {
        val fileName = "grammarmate/tenses/${languageId}_tenses.yaml"
        try {
            val yaml = getApplication<Application>().assets.open(fileName).bufferedReader().readText()
            tenseInfoMap = parseTenseInfo(yaml)
            Log.d(logTag, "Loaded tense info for $languageId: ${tenseInfoMap.size} tenses")
        } catch (e: Exception) {
            Log.w(logTag, "Failed to load tense info from $fileName", e)
            tenseInfoMap = emptyMap()
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

        val selected = if (state.sortByFrequency) {
            remaining.sortedBy { it.rank ?: Int.MAX_VALUE }.take(10)
        } else {
            remaining.shuffled().take(10)
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

        if (!isComplete) {
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

    fun nextBatch() {
        startSession()
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
            // Persist current card as shown even when skipped
            val card = session.cards[session.currentIndex]
            persistCardProgress(card)
            _uiState.update { state ->
                state.copy(
                    session = session.copy(currentIndex = nextIndex)
                )
            }
            cardShownTimestamp = System.currentTimeMillis()
        } else {
            // Persist last card and start new batch
            val card = session.cards[session.currentIndex]
            persistCardProgress(card)
            startSession()
        }
    }

    fun exitSession() {
        _uiState.update { it.copy(session = null, currentCardIsBad = false) }
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

    fun speakTts(text: String, speed: Float = 0.67f) {
        if (text.isBlank()) return
        val langId = _uiState.value.loadedLanguageId ?: "it"
        viewModelScope.launch {
            if (ttsEngine.state.value != TtsState.READY
                || ttsEngine.activeLanguageId != langId) {
                ttsEngine.initialize(langId)
            }
            if (ttsEngine.state.value == TtsState.READY) {
                ttsEngine.speak(text, languageId = langId, speed = speed)
            } else {
                Log.w(logTag, "TTS not ready after initialize, state=${ttsEngine.state.value}")
            }
        }
    }

    fun stopTts() {
        ttsEngine.stop()
    }

    override fun onCleared() {
        super.onCleared()
    }
}

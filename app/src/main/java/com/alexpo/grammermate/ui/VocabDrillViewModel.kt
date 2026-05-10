package com.alexpo.grammermate.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.data.ItalianDrillVocabParser
import com.alexpo.grammermate.data.SpacedRepetitionConfig
import com.alexpo.grammermate.data.TtsEngine
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.VocabDrillCard
import com.alexpo.grammermate.data.VocabDrillSessionState
import com.alexpo.grammermate.data.VocabDrillUiState
import com.alexpo.grammermate.data.VocabWord
import com.alexpo.grammermate.data.WordMasteryState
import com.alexpo.grammermate.data.WordMasteryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VocabDrillViewModel(application: Application) : AndroidViewModel(application) {

    private val logTag = "VocabDrillVM"
    private val masteryStore = WordMasteryStore(application)
    private val ttsEngine = TtsEngine(application)

    private val _uiState = MutableStateFlow(VocabDrillUiState())
    val uiState: StateFlow<VocabDrillUiState> = _uiState

    private var allWords: List<VocabWord> = emptyList()
    private var masteryMap: Map<String, WordMasteryState> = emptyMap()

    init {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch { loadWords() }
    }

    /**
     * Reload words for the given language (called on navigation).
     */
    fun reloadForLanguage(languageId: String) {
        val currentLang = _uiState.value.loadedLanguageId
        if (currentLang == languageId && allWords.isNotEmpty()) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch { loadWords(languageId) }
    }

    private fun loadWords(languageId: String? = null) {
        val lang = languageId ?: "it"
        val context = getApplication<Application>()
        val assetBase = "grammarmate/vocab/$lang"
        val files = listOf(
            "drill_nouns.csv",
            "drill_verbs.csv",
            "drill_adjectives.csv",
            "drill_adverbs.csv"
        )

        val words = mutableListOf<VocabWord>()

        for (fileName in files) {
            try {
                val stream = context.assets.open("$assetBase/$fileName")
                val rows = ItalianDrillVocabParser.parse(stream, fileName)
                stream.close()

                // Derive POS from filename: drill_nouns.csv -> "nouns"
                val pos = fileName
                    .removePrefix("drill_")
                    .removeSuffix(".csv")

                for (row in rows) {
                    // For adjectives, extract forms from the CSV columns
                    val forms = if (pos == "adjectives") {
                        extractAdjectiveForms(fileName, row.word, context, assetBase)
                    } else {
                        emptyMap()
                    }

                    // For adverbs, try to extract meaning_ru
                    val meaningRu = if (pos == "adverbs") {
                        extractAdverbMeaning(fileName, row.rank, context, assetBase)
                    } else {
                        null
                    }

                    words.add(VocabWord(
                        id = "${pos}_${row.rank}_${row.word}",
                        word = row.word,
                        pos = pos,
                        rank = row.rank,
                        meaningRu = meaningRu,
                        collocations = row.collocations,
                        forms = forms
                    ))
                }
            } catch (e: Exception) {
                Log.w(logTag, "Failed to load $fileName", e)
            }
        }

        allWords = words.sortedBy { it.rank }
        masteryMap = masteryStore.loadAll()

        val availablePos = words.map { it.pos }.distinct().sorted()

        _uiState.update {
            it.copy(
                availablePos = availablePos,
                isLoading = false,
                loadedLanguageId = lang
            )
        }

        updateCounts()
        Log.d(logTag, "Loaded ${words.size} vocab words for language $lang")
    }

    /**
     * Extract adjective forms (msg, fsg, mpl, fpl) by re-reading the CSV.
     * The parser returns ItalianDrillRow which doesn't include forms,
     * so we parse the raw CSV again to get the form columns.
     */
    private fun extractAdjectiveForms(
        fileName: String,
        targetWord: String,
        context: Application,
        assetBase: String
    ): Map<String, String> {
        try {
            val stream = context.assets.open("$assetBase/$fileName")
            val reader = java.io.BufferedReader(
                java.io.InputStreamReader(stream, Charsets.UTF_8)
            )
            reader.useLines { lines ->
                val dataLines = lines.drop(1) // skip header
                for (line in dataLines) {
                    val trimmed = line.trim()
                    if (trimmed.isBlank()) continue
                    val columns = trimmed.split(",")
                    if (columns.size < 6) continue
                    val word = columns[1].trim().trim('"')
                    if (word == targetWord) {
                        return mapOf(
                            "msg" to columns[2].trim().trim('"'),
                            "fsg" to columns[3].trim().trim('"'),
                            "mpl" to columns[4].trim().trim('"'),
                            "fpl" to columns[5].trim().trim('"')
                        )
                    }
                }
            }
            stream.close()
        } catch (e: Exception) {
            Log.w(logTag, "Failed to extract adjective forms for $targetWord", e)
        }
        return emptyMap()
    }

    /**
     * Extract meaning_ru for adverbs by re-reading the CSV.
     * Adverb format: rank,adverb,comparative,superlative,meaning_ru,collocations
     */
    private fun extractAdverbMeaning(
        fileName: String,
        targetRank: Int,
        context: Application,
        assetBase: String
    ): String? {
        try {
            val stream = context.assets.open("$assetBase/$fileName")
            val reader = java.io.BufferedReader(
                java.io.InputStreamReader(stream, Charsets.UTF_8)
            )
            reader.useLines { lines ->
                val dataLines = lines.drop(1) // skip header
                for (line in dataLines) {
                    val trimmed = line.trim()
                    if (trimmed.isBlank()) continue
                    val columns = trimmed.split(",")
                    val rank = columns.getOrNull(0)?.trim()?.toIntOrNull() ?: continue
                    if (rank == targetRank) {
                        val meaning = columns.getOrNull(4)?.trim()?.trim('"')
                        return if (!meaning.isNullOrBlank()) meaning else null
                    }
                }
            }
            stream.close()
        } catch (e: Exception) {
            Log.w(logTag, "Failed to extract adverb meaning for rank $targetRank", e)
        }
        return null
    }

    /**
     * Select part of speech filter. null means "all".
     */
    fun selectPos(pos: String?) {
        _uiState.update { it.copy(selectedPos = pos) }
        updateCounts()
    }

    /**
     * Set rank range filter.
     */
    fun setRankRange(min: Int, max: Int) {
        _uiState.update { it.copy(rankMin = min, rankMax = max) }
        updateCounts()
    }

    private fun updateCounts() {
        val now = System.currentTimeMillis()

        val filtered = filteredWords()
        val dueWords = filtered.filter { word ->
            val mastery = masteryMap[word.id]
            mastery == null || mastery.nextReviewDateMs <= now || mastery.lastReviewDateMs == 0L
        }

        _uiState.update {
            it.copy(
                totalCount = filtered.size,
                dueCount = dueWords.size
            )
        }
    }

    private fun filteredWords(): List<VocabWord> {
        val state = _uiState.value
        return allWords.filter { word ->
            (state.selectedPos == null || word.pos == state.selectedPos) &&
            word.rank >= state.rankMin &&
            word.rank <= state.rankMax
        }
    }

    /**
     * Start a drill session with up to 10 due words, sorted by rank.
     */
    fun startSession() {
        val now = System.currentTimeMillis()
        val filtered = filteredWords()

        val dueCards = filtered.mapNotNull { word ->
            val mastery = masteryMap[word.id] ?: WordMasteryState.new(word.id)
            val isDue = mastery.nextReviewDateMs <= now || mastery.lastReviewDateMs == 0L
            if (isDue) VocabDrillCard(word = word, mastery = mastery) else null
        }.sortedBy { it.word.rank }
            .take(10)

        if (dueCards.isEmpty()) {
            _uiState.update { it.copy(session = null) }
            return
        }

        _uiState.update {
            it.copy(session = VocabDrillSessionState(cards = dueCards))
        }
    }

    /**
     * Flip the current card to show/hide the answer.
     */
    fun flipCard() {
        _uiState.update { state ->
            val session = state.session ?: return@update state
            state.copy(session = session.copy(isFlipped = !session.isFlipped))
        }
    }

    /**
     * User knew the word. Advance interval step, update mastery, move to next card.
     */
    fun markCorrect() {
        val session = _uiState.value.session ?: return
        val index = session.currentIndex
        if (index >= session.cards.size) return

        val card = session.cards[index]
        val now = System.currentTimeMillis()

        val newStepIndex = SpacedRepetitionConfig.nextIntervalStep(
            card.mastery.intervalStepIndex, wasOnTime = true
        )
        val newNextReview = WordMasteryState.computeNextReview(now, newStepIndex)
        val isLearned = newStepIndex >= SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1

        val updatedMastery = card.mastery.copy(
            intervalStepIndex = newStepIndex,
            correctCount = card.mastery.correctCount + 1,
            lastReviewDateMs = now,
            nextReviewDateMs = newNextReview,
            isLearned = isLearned
        )

        masteryMap = masteryMap.toMutableMap().apply { this[card.word.id] = updatedMastery }
        masteryStore.upsertMastery(updatedMastery)

        advanceCard(session, correct = true)
    }

    /**
     * User didn't know the word. Reset interval to step 0, update mastery, move to next card.
     */
    fun markWrong() {
        val session = _uiState.value.session ?: return
        val index = session.currentIndex
        if (index >= session.cards.size) return

        val card = session.cards[index]
        val now = System.currentTimeMillis()

        // Reset to step 0
        val newNextReview = WordMasteryState.computeNextReview(now, 0)

        val updatedMastery = card.mastery.copy(
            intervalStepIndex = 0,
            incorrectCount = card.mastery.incorrectCount + 1,
            lastReviewDateMs = now,
            nextReviewDateMs = newNextReview,
            isLearned = false
        )

        masteryMap = masteryMap.toMutableMap().apply { this[card.word.id] = updatedMastery }
        masteryStore.upsertMastery(updatedMastery)

        advanceCard(session, correct = false)
    }

    private fun advanceCard(session: VocabDrillSessionState, correct: Boolean) {
        val nextIndex = session.currentIndex + 1
        val newCorrect = if (correct) session.correctCount + 1 else session.correctCount
        val newIncorrect = if (!correct) session.incorrectCount + 1 else session.incorrectCount
        val isComplete = nextIndex >= session.cards.size

        _uiState.update { state ->
            state.copy(
                session = session.copy(
                    currentIndex = nextIndex,
                    correctCount = newCorrect,
                    incorrectCount = newIncorrect,
                    isComplete = isComplete,
                    isFlipped = false
                )
            )
        }

        updateCounts()
    }

    /**
     * Exit the current session.
     */
    fun exitSession() {
        _uiState.update { it.copy(session = null) }
    }

    // ── TTS Support ──────────────────────────────────────────────────

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
        ttsEngine.release()
    }
}

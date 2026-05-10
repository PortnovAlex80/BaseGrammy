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
import com.alexpo.grammermate.data.VocabDrillDirection
import com.alexpo.grammermate.data.VocabDrillSessionState
import com.alexpo.grammermate.data.VocabDrillUiState
import com.alexpo.grammermate.data.VoiceResult
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
            "drill_adverbs.csv",
            "drill_numbers.csv"
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
                    words.add(VocabWord(
                        id = "${pos}_${row.rank}_${row.word}",
                        word = row.word,
                        pos = pos,
                        rank = row.rank,
                        meaningRu = row.meaningRu,
                        collocations = row.collocations,
                        forms = row.forms
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
            it.copy(session = VocabDrillSessionState(
                cards = dueCards,
                direction = it.drillDirection,
                voiceModeEnabled = it.voiceModeEnabled
            ))
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
    enum class AnswerRating { AGAIN, HARD, GOOD, EASY }

    private val ratingIntervalDelta = mapOf(
        AnswerRating.AGAIN to -100,  // reset to step 0
        AnswerRating.HARD to 0,      // stay same step (review again sooner)
        AnswerRating.GOOD to 1,      // advance 1 step
        AnswerRating.EASY to 2       // advance 2 steps
    )

    fun answerRating(rating: AnswerRating) {
        val session = _uiState.value.session ?: return
        val index = session.currentIndex
        if (index >= session.cards.size) return

        val card = session.cards[index]
        val now = System.currentTimeMillis()
        val currentStep = card.mastery.intervalStepIndex
        val maxStep = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1

        val delta = ratingIntervalDelta[rating] ?: 0
        val newStepIndex = when {
            rating == AnswerRating.AGAIN -> 0
            else -> (currentStep + delta).coerceIn(0, maxStep)
        }
        val newNextReview = WordMasteryState.computeNextReview(now, newStepIndex)
        val isLearned = newStepIndex >= maxStep

        val updatedMastery = card.mastery.copy(
            intervalStepIndex = newStepIndex,
            correctCount = card.mastery.correctCount + (if (rating != AnswerRating.AGAIN) 1 else 0),
            incorrectCount = card.mastery.incorrectCount + (if (rating == AnswerRating.AGAIN) 1 else 0),
            lastReviewDateMs = now,
            nextReviewDateMs = newNextReview,
            isLearned = isLearned
        )

        masteryMap = masteryMap.toMutableMap().apply { this[card.word.id] = updatedMastery }
        masteryStore.upsertMastery(updatedMastery)

        advanceCard(session, correct = rating != AnswerRating.AGAIN)
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
                    isFlipped = false,
                    voiceAttempts = 0,
                    voiceRecognizedText = null,
                    voiceResult = null,
                    voiceCompleted = false
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

    // ── Voice Input Support (via Android RecognizerIntent) ──────────────

    /** Set the drill direction. */
    fun setDirection(direction: VocabDrillDirection) {
        _uiState.update { it.copy(drillDirection = direction) }
        // Also update active session direction if one exists
        _uiState.update { state ->
            val session = state.session ?: return@update state
            state.copy(session = session.copy(direction = direction))
        }
    }

    /** Enable or disable voice mode (auto-launch mic on new cards). */
    fun setVoiceMode(enabled: Boolean) {
        _uiState.update { it.copy(voiceModeEnabled = enabled) }
        // Also update active session if one exists
        _uiState.update { state ->
            val session = state.session ?: return@update state
            state.copy(session = session.copy(voiceModeEnabled = enabled))
        }
    }

    /**
     * Returns true if voice mode is on, card is not flipped, and voice is not completed.
     * Used by the UI to decide whether to auto-launch RecognizerIntent.
     */
    fun shouldAutoStartVoice(): Boolean {
        val state = _uiState.value
        val session = state.session ?: return false
        return session.voiceModeEnabled && !session.isFlipped && !session.voiceCompleted
    }

    /**
     * Handle voice recognition result: match against expected answers.
     * Called from the Screen after RecognizerIntent returns.
     * IT_TO_RU: expected = meaningRu (e.g. "быть/являться")
     * RU_TO_IT: expected = word (Italian, e.g. "essere")
     */
    fun handleVoiceResult(recognizedText: String) {
        val session = _uiState.value.session ?: return
        val index = session.currentIndex
        if (index >= session.cards.size) return

        val card = session.cards[index]
        val direction = session.direction

        // Get expected answer based on direction
        val expectedRaw = when (direction) {
            VocabDrillDirection.IT_TO_RU -> card.word.meaningRu ?: ""
            VocabDrillDirection.RU_TO_IT -> card.word.word
        }

        // Split by "/" to get list of valid answers
        val validAnswers = expectedRaw.split("/").map { normalizeForMatch(it) }
        val normalizedRecognized = normalizeForMatch(recognizedText)

        val isCorrect = validAnswers.any { valid ->
            valid.isNotBlank() && (
                normalizedRecognized == valid ||
                normalizedRecognized.contains(valid)
            )
        }

        if (isCorrect) {
            _uiState.update { state ->
                state.copy(
                    session = session.copy(
                        voiceRecognizedText = recognizedText,
                        voiceResult = VoiceResult.CORRECT,
                        voiceCompleted = true
                    )
                )
            }
        } else {
            val newAttempts = session.voiceAttempts + 1
            val maxAttempts = 3
            if (newAttempts >= maxAttempts) {
                _uiState.update { state ->
                    state.copy(
                        session = session.copy(
                            voiceAttempts = newAttempts,
                            voiceRecognizedText = recognizedText,
                            voiceResult = VoiceResult.WRONG,
                            voiceCompleted = true
                        )
                    )
                }
            } else {
                _uiState.update { state ->
                    state.copy(
                        session = session.copy(
                            voiceAttempts = newAttempts,
                            voiceRecognizedText = recognizedText,
                            voiceResult = VoiceResult.WRONG,
                            voiceCompleted = false
                        )
                    )
                }
            }
        }
    }

    /** Skip voice input for the current card. */
    fun skipVoice() {
        _uiState.update { state ->
            val session = state.session ?: return@update state
            state.copy(
                session = session.copy(
                    voiceResult = VoiceResult.SKIPPED,
                    voiceCompleted = true
                )
            )
        }
    }

    private fun normalizeForMatch(text: String): String {
        return text.trim().lowercase().replace(Regex("[.!?,;:]$"), "")
    }

    override fun onCleared() {
        super.onCleared()
        ttsEngine.release()
    }
}

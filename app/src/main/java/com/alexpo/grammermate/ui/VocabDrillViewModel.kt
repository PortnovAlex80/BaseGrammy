package com.alexpo.grammermate.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.data.ItalianDrillVocabParser
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.SrsRating
import com.alexpo.grammermate.data.SpacedRepetitionConfig
import com.alexpo.grammermate.data.TtsProvider
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.VocabDrillCard
import com.alexpo.grammermate.data.VocabDrillDirection
import com.alexpo.grammermate.data.VocabDrillSessionState
import com.alexpo.grammermate.data.VocabDrillUiState
import com.alexpo.grammermate.data.VoiceResult
import com.alexpo.grammermate.data.VocabWord
import com.alexpo.grammermate.data.WordMasteryState
import com.alexpo.grammermate.data.WordMasteryStore
import com.alexpo.grammermate.data.StoreFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VocabDrillViewModel(application: Application) : AndroidViewModel(application) {

    private val logTag = "VocabDrillVM"
    private val storeFactory = StoreFactory.getInstance(application)
    private val lessonStore = storeFactory.getLessonStore()
    private var masteryStore = storeFactory.getWordMasteryStore(null)
    private val badSentenceStore = storeFactory.getBadSentenceStore()
    private val ttsEngine = TtsProvider.getInstance(application).ttsEngine

    private val _uiState = MutableStateFlow(VocabDrillUiState())
    val uiState: StateFlow<VocabDrillUiState> = _uiState

    private var allWords: List<VocabWord> = emptyList()
    private var masteryMap: Map<String, WordMasteryState> = emptyMap()

    /** Currently active pack ID, null means no pack scoping (legacy mode). */
    private var activePackId: String? = null

    init {
        // Do not auto-load; wait for reloadForPack() or reloadForLanguage() call.
        _uiState.update { it.copy(isLoading = false) }
    }

    /**
     * Reload words for the given pack and language (called on navigation).
     * This is the preferred entry point for pack-scoped drill data.
     */
    fun reloadForPack(packId: String, languageId: String) {
        val currentLang = _uiState.value.loadedLanguageId
        val currentPack = activePackId
        if (currentPack == packId && currentLang == languageId && allWords.isNotEmpty()) return
        activePackId = packId
        // Re-scope mastery store to the pack via shared factory
        masteryStore = storeFactory.getWordMasteryStore(packId)
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch { loadWords(languageId) }
    }

    /**
     * Reload words for the given language (called on navigation).
     * If a pack is active, loads from that pack. Otherwise scans all packs.
     */
    fun reloadForLanguage(languageId: String) {
        val currentLang = _uiState.value.loadedLanguageId
        if (currentLang == languageId && allWords.isNotEmpty()) return
        val pack = activePackId
        if (pack != null) {
            masteryStore = storeFactory.getWordMasteryStore(pack)
        }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch { loadWords(languageId) }
    }

    private fun loadWords(languageId: String? = null) {
        val lang = languageId ?: "it"
        val pack = activePackId
        val words = mutableListOf<VocabWord>()

        val files = if (pack != null) {
            // Pack-scoped: load from the pack's vocab drill directory
            lessonStore.getVocabDrillFiles(pack, lang)
        } else {
            // No active pack: scan all installed packs for vocab drill files
            lessonStore.getInstalledPacks().flatMap { installedPack ->
                lessonStore.getVocabDrillFiles(installedPack.packId.value, lang)
            }
        }

        for (file in files) {
            try {
                val stream = file.inputStream()
                val fileName = file.name
                val rows = ItalianDrillVocabParser.parse(stream, fileName)
                stream.close()

                // Derive POS from filename: drill_nouns.csv -> "nouns", it_drill_nouns.csv -> "nouns"
                val pos = fileName
                    .removePrefix("${lang}_")
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
                Log.w(logTag, "Failed to load ${file.name}", e)
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
        Log.d(logTag, "Loaded ${words.size} vocab words for language $lang, pack=${pack ?: "all"}")
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

        // Count mastered words (isLearned == true)
        val masteredByPos = mutableMapOf<String, Int>()
        for ((wordId, state) in masteryMap) {
            if (!state.isLearned) continue
            val pos = wordId.indexOf('_').let { idx ->
                if (idx > 0) wordId.substring(0, idx) else "unknown"
            }
            masteredByPos[pos] = (masteredByPos[pos] ?: 0) + 1
        }
        val masteredCount = masteredByPos.values.sum()

        _uiState.update {
            it.copy(
                totalCount = filtered.size,
                dueCount = dueWords.size,
                masteredCount = masteredCount,
                masteredByPos = masteredByPos
            )
        }
    }

    private fun filteredWords(): List<VocabWord> {
        val state = _uiState.value
        return allWords.filter { word ->
            (state.selectedPos == null || word.pos == state.selectedPos) &&
            word.rank >= state.rankMin &&
            word.rank <= state.rankMax &&
            // Numbers only appear when explicitly selected via filter
            (state.selectedPos != null || word.pos != "numbers")
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
                direction = it.drillDirection
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
    private val ratingIntervalDelta = mapOf(
        SrsRating.AGAIN to -100,  // reset to step 0
        SrsRating.HARD to 0,      // stay same step (review again sooner)
        SrsRating.GOOD to 1,      // advance 1 step
        SrsRating.EASY to 2       // advance 2 steps
    )

    fun answerRating(rating: SrsRating) {
        val session = _uiState.value.session ?: return
        val index = session.currentIndex
        if (index >= session.cards.size) return

        val card = session.cards[index]
        val now = System.currentTimeMillis()
        val currentStep = card.mastery.intervalStepIndex
        val maxStep = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1

        val delta = ratingIntervalDelta[rating] ?: 0
        val newStepIndex = when {
            rating == SrsRating.AGAIN -> 0
            else -> (currentStep + delta).coerceIn(0, maxStep)
        }
        val newNextReview = WordMasteryState.computeNextReview(now, newStepIndex)
        val isLearned = newStepIndex >= TrainingConfig.LEARNED_THRESHOLD

        val updatedMastery = card.mastery.copy(
            intervalStepIndex = newStepIndex,
            correctCount = card.mastery.correctCount + (if (rating != SrsRating.AGAIN) 1 else 0),
            incorrectCount = card.mastery.incorrectCount + (if (rating == SrsRating.AGAIN) 1 else 0),
            lastReviewDateMs = now,
            nextReviewDateMs = newNextReview,
            isLearned = isLearned
        )

        masteryMap = masteryMap.toMutableMap().apply { this[card.word.id] = updatedMastery }
        masteryStore.upsertMastery(updatedMastery)

        advanceCard(session, correct = rating != SrsRating.AGAIN)
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
            try {
                if (ttsEngine.state.value != TtsState.READY
                    || ttsEngine.activeLanguageId != langId) {
                    ttsEngine.initialize(langId)
                }
                if (ttsEngine.state.value == TtsState.READY) {
                    ttsEngine.speak(text, languageId = langId, speed = speed)
                } else {
                    Log.w(logTag, "TTS not ready after initialize, state=${ttsEngine.state.value}")
                }
            } catch (e: Exception) {
                Log.e(logTag, "speakTts failed", e)
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

    /** Enable or disable voice mode — DEPRECATED, now reads global voiceAutoStart setting. */
    fun setVoiceMode(enabled: Boolean) {
        // No-op: voice auto-start is controlled by global Settings toggle only.
        // Kept to avoid breaking callers during migration.
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

    // ── Bad Sentence Support ──────────────────────────────────────────────

    /**
     * Flag the current vocab card as a bad sentence.
     * Uses activePackId if available, otherwise falls back to "__vocab_drill__".
     */
    fun flagBadSentence() {
        val session = _uiState.value.session ?: return
        val index = session.currentIndex
        if (index >= session.cards.size) return
        val card = session.cards[index]
        val packId = activePackId ?: "__vocab_drill__"
        val languageId = _uiState.value.loadedLanguageId ?: ""

        val word = card.word
        badSentenceStore.addBadSentence(
            packId = packId,
            cardId = word.id,
            languageId = languageId,
            sentence = word.meaningRu ?: word.word,
            translation = word.word,
            mode = "vocab_drill"
        )
    }

    fun unflagBadSentence() {
        val session = _uiState.value.session ?: return
        val index = session.currentIndex
        if (index >= session.cards.size) return
        val card = session.cards[index]
        val packId = activePackId ?: "__vocab_drill__"
        badSentenceStore.removeBadSentence(packId, card.word.id)
    }

    fun isBadSentence(): Boolean {
        val session = _uiState.value.session ?: return false
        val index = session.currentIndex
        if (index >= session.cards.size) return false
        val card = session.cards[index]
        val packId = activePackId ?: "__vocab_drill__"
        return badSentenceStore.isBadSentence(packId, card.word.id)
    }

    fun exportBadSentences(): String? {
        val packId = activePackId ?: "__vocab_drill__"
        val entries = badSentenceStore.getBadSentences(packId)
        if (entries.isEmpty()) return null
        val file = badSentenceStore.exportToTextFile(packId)
        return file.absolutePath
    }

    override fun onCleared() {
        super.onCleared()
    }
}

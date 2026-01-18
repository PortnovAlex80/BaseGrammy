package com.alexpo.grammermate.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.alexpo.grammermate.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.LessonSchedule
import com.alexpo.grammermate.data.MixedReviewScheduler
import com.alexpo.grammermate.data.AppConfigStore
import com.alexpo.grammermate.data.AppConfig
import com.alexpo.grammermate.data.Normalizer
import com.alexpo.grammermate.data.ProgressStore
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.BossType
import com.alexpo.grammermate.data.StoryPhase
import com.alexpo.grammermate.data.StoryQuiz
import com.alexpo.grammermate.data.SubLessonType
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.TrainingMode
import com.alexpo.grammermate.data.TrainingProgress
import com.alexpo.grammermate.data.VocabEntry
import com.alexpo.grammermate.data.MasteryStore
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.ScheduledSubLesson
import com.alexpo.grammermate.data.FlowerCalculator
import com.alexpo.grammermate.data.FlowerVisual
import com.alexpo.grammermate.data.FlowerState
import com.alexpo.grammermate.data.LessonLadderCalculator
import com.alexpo.grammermate.data.StreakStore
import com.alexpo.grammermate.data.StreakData
import com.alexpo.grammermate.data.BackupManager
import com.alexpo.grammermate.data.ProfileStore
import com.alexpo.grammermate.data.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .build()
    private val successSoundId = soundPool.load(application, R.raw.voicy_correct_answer, 1)
    private val errorSoundId = soundPool.load(application, R.raw.voicy_bad_answer, 1)
    private val loadedSounds = mutableSetOf<Int>()
    private val logTag = "GrammarMate"
    private val lessonStore = LessonStore(application)
    private val progressStore = ProgressStore(application)
    private val configStore = AppConfigStore(application)
    private val masteryStore = MasteryStore(application)
    private val streakStore = StreakStore(application)
    private val backupManager = BackupManager(application)
    private val profileStore = ProfileStore(application)
    private var sessionCards: List<SentenceCard> = emptyList()
    private var bossCards: List<SentenceCard> = emptyList()
    private var eliteCards: List<SentenceCard> = emptyList()
    private var vocabSession: List<VocabEntry> = emptyList()
    private var subLessonTotal: Int = 0
    private var subLessonCount: Int = 0
    private var lessonSchedules: Map<String, LessonSchedule> = emptyMap()
    private var scheduleKey: String = ""
    private var timerJob: Job? = null
    private var activeStartMs: Long? = null
    private var forceBackupOnSave: Boolean = false
    private val subLessonSizeMin = TrainingConfig.SUB_LESSON_SIZE_MIN
    private val subLessonSizeMax = TrainingConfig.SUB_LESSON_SIZE_MAX
    private val subLessonSize = TrainingConfig.SUB_LESSON_SIZE_DEFAULT
    private val eliteStepCount = TrainingConfig.ELITE_STEP_COUNT
    private var eliteSizeMultiplier: Double = 1.25

    private val _uiState = MutableStateFlow(TrainingUiState())
    val uiState: StateFlow<TrainingUiState> = _uiState

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSounds.add(sampleId)
            }
        }
        Log.d(logTag, "Update: duolingo sfx, prompt in speech UI, voice loop rules, stop resets progress")
        lessonStore.ensureSeedData()
        val progress = progressStore.load()
        val config = configStore.load()
        val profile = profileStore.load()
        eliteSizeMultiplier = config.eliteSizeMultiplier
        val bossLessonRewards = progress.bossLessonRewards.mapNotNull { (lessonId, reward) ->
            val parsed = runCatching { BossReward.valueOf(reward) }.getOrNull() ?: return@mapNotNull null
            lessonId to parsed
        }.toMap()
        val bossMegaReward = progress.bossMegaReward?.let { reward ->
            runCatching { BossReward.valueOf(reward) }.getOrNull()
        }
        val languages = lessonStore.getLanguages()
        val packs = lessonStore.getInstalledPacks()
        val selectedLanguageId = languages.firstOrNull { it.id == progress.languageId }?.id ?: "en"
        val lessons = lessonStore.getLessons(selectedLanguageId)
        val selectedLessonId = progress.lessonId ?: lessons.firstOrNull()?.id
        val normalizedEliteSpeeds = normalizeEliteSpeeds(progress.eliteBestSpeeds)
        val streakData = streakStore.getCurrentStreak(selectedLanguageId)
        _uiState.update {
            it.copy(
                languages = languages,
                installedPacks = packs,
                selectedLanguageId = selectedLanguageId,
                lessons = lessons,
                selectedLessonId = selectedLessonId,
                mode = progress.mode,
                sessionState = progress.state,
                currentIndex = progress.currentIndex,
                correctCount = progress.correctCount,
                incorrectCount = progress.incorrectCount,
                incorrectAttemptsForCard = progress.incorrectAttemptsForCard,
                activeTimeMs = progress.activeTimeMs,
                voiceActiveMs = progress.voiceActiveMs,
                voiceWordCount = progress.voiceWordCount,
                hintCount = progress.hintCount,
                voicePromptStartMs = null,
                subLessonTotal = 0,
                subLessonCount = 0,
                activeSubLessonIndex = 0,
                completedSubLessonCount = 0,
                subLessonFinishedToken = 0,
                storyCheckInDone = false,
                storyCheckOutDone = false,
                activeStory = null,
                currentVocab = null,
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = 0,
                vocabTotal = 0,
                vocabFinishedToken = 0,
                vocabErrorMessage = null,
                vocabInputMode = InputMode.VOICE,
                vocabVoiceTriggerToken = 0,
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                bossReward = null,
                bossRewardMessage = null,
                bossFinishedToken = 0,
                bossErrorMessage = null,
                bossLessonRewards = bossLessonRewards,
                bossMegaReward = bossMegaReward,
                testMode = config.testMode,
                eliteActive = false,
                eliteStepIndex = progress.eliteStepIndex.coerceIn(0, eliteStepCount - 1),
                eliteBestSpeeds = normalizedEliteSpeeds,
                eliteFinishedToken = 0,
                eliteUnlocked = resolveEliteUnlocked(lessons, config.testMode),
                eliteSizeMultiplier = config.eliteSizeMultiplier,
                vocabSprintLimit = config.vocabSprintLimit,
                currentStreak = streakData.currentStreak,
                longestStreak = streakData.longestStreak,
                userName = profile.userName
            )
        }
        rebuildSchedules(lessons)
        buildSessionCards()
        refreshFlowerStates()
        if (_uiState.value.sessionState == SessionState.ACTIVE && _uiState.value.currentCard != null) {
            resumeTimer()
            _uiState.value.currentCard?.let { recordCardShowForMastery(it) }
            if (_uiState.value.inputMode == InputMode.VOICE) {
                _uiState.update { it.copy(voiceTriggerToken = it.voiceTriggerToken + 1) }
            }
        }
        // Force reload default packs on every app start to ensure latest lesson content
        viewModelScope.launch(Dispatchers.IO) {
            val reloaded = lessonStore.forceReloadDefaultPacks()
            if (!reloaded) return@launch
            val currentLang = _uiState.value.selectedLanguageId
            val languages = lessonStore.getLanguages()
            val selectedLang = languages.firstOrNull { it.id == currentLang }?.id
                ?: languages.firstOrNull()?.id
                ?: "en"
            val lessons = lessonStore.getLessons(selectedLang)
            val currentLessonId = _uiState.value.selectedLessonId
            val selectedLessonId = lessons.firstOrNull { it.id == currentLessonId }?.id
                ?: lessons.firstOrNull()?.id
            val packs = lessonStore.getInstalledPacks()
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        languages = languages,
                        installedPacks = packs,
                        selectedLanguageId = selectedLang,
                        lessons = lessons,
                        selectedLessonId = selectedLessonId,
                        eliteUnlocked = resolveEliteUnlocked(lessons, it.testMode)
                    )
                }
                rebuildSchedules(lessons)
                buildSessionCards()
                refreshFlowerStates()
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update {
            val resetAttempts = it.answerText != null || it.incorrectAttemptsForCard >= 3
            it.copy(
                inputText = text,
                incorrectAttemptsForCard = if (resetAttempts) 0 else it.incorrectAttemptsForCard,
                answerText = if (resetAttempts) null else it.answerText
            )
        }
    }

    fun onVoicePromptStarted() {
        _uiState.update {
            it.copy(voicePromptStartMs = SystemClock.elapsedRealtime())
        }
    }

    fun setInputMode(mode: InputMode) {
        _uiState.update {
            val resetAttempts = it.answerText != null || it.incorrectAttemptsForCard >= 3
            val shouldTriggerVoice = mode == InputMode.VOICE &&
                resetAttempts &&
                it.sessionState == SessionState.ACTIVE
            it.copy(
                inputMode = mode,
                incorrectAttemptsForCard = if (resetAttempts) 0 else it.incorrectAttemptsForCard,
                answerText = if (resetAttempts) null else it.answerText,
                voiceTriggerToken = if (shouldTriggerVoice) it.voiceTriggerToken + 1 else it.voiceTriggerToken,
                voicePromptStartMs = if (mode == InputMode.VOICE) it.voicePromptStartMs else null
            )
        }

        // Update word bank when switching to WORD_BANK mode
        if (mode == InputMode.WORD_BANK) {
            updateWordBank()
        }

        Log.d(logTag, "Input mode changed: $mode")
    }

    fun selectLanguage(languageId: String) {
        pauseTimer()
        vocabSession = emptyList()
        sessionCards = emptyList()
        bossCards = emptyList()
        eliteCards = emptyList()
        scheduleKey = "" // Force rebuild schedules
        val lessons = lessonStore.getLessons(languageId)
        val selectedLessonId = lessons.firstOrNull()?.id
        _uiState.update {
            it.copy(
                selectedLanguageId = languageId,
                lessons = lessons,
                selectedLessonId = selectedLessonId,
                eliteActive = false,
                eliteUnlocked = resolveEliteUnlocked(lessons, it.testMode),
                currentIndex = 0,
                correctCount = 0,
                incorrectCount = 0,
                incorrectAttemptsForCard = 0,
                inputText = "",
                lastResult = null,
                answerText = null,
                inputMode = InputMode.VOICE,
                sessionState = SessionState.PAUSED,
                voicePromptStartMs = null,
                activeSubLessonIndex = 0,
                completedSubLessonCount = 0,
                subLessonFinishedToken = 0,
                storyCheckInDone = false,
                storyCheckOutDone = false,
                activeStory = null,
                storyErrorMessage = null,
                currentVocab = null,
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = 0,
                vocabTotal = 0,
                vocabWordBankWords = emptyList(),
                vocabFinishedToken = 0,
                vocabErrorMessage = null,
                vocabInputMode = InputMode.VOICE,
                vocabVoiceTriggerToken = 0,
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                bossReward = null,
                bossRewardMessage = null,
                bossFinishedToken = 0,
                bossErrorMessage = null,
                bossLessonRewards = emptyMap(),
                bossMegaReward = null,
                lessonFlowers = emptyMap(),
                currentLessonFlower = null,
                wordBankWords = emptyList(),
                selectedWords = emptyList()
            )
        }
        rebuildSchedules(lessons)
        buildSessionCards()
        refreshFlowerStates()
        saveProgress()
    }

    fun selectLesson(lessonId: String) {
        pauseTimer()
        vocabSession = emptyList()

        // Calculate active sub-lesson index based on completed count
        val schedule = lessonSchedules[lessonId]
        val subLessons = schedule?.subLessons.orEmpty()
        val mastery = masteryStore.get(lessonId, _uiState.value.selectedLanguageId)
        val completedCount = calculateCompletedSubLessons(subLessons, mastery, lessonId)
        val nextActiveIndex = completedCount.coerceAtMost((subLessons.size - 1).coerceAtLeast(0))

        _uiState.update {
            it.copy(
                selectedLessonId = lessonId,
                mode = TrainingMode.LESSON,
                currentIndex = 0,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                sessionState = SessionState.PAUSED,
                voicePromptStartMs = null,
                inputMode = InputMode.VOICE,
                activeSubLessonIndex = nextActiveIndex,
                completedSubLessonCount = completedCount,
                subLessonFinishedToken = 0,
                storyCheckInDone = false,
                storyCheckOutDone = false,
                activeStory = null,
                storyErrorMessage = null,
                currentVocab = null,
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = 0,
                vocabTotal = 0,
                vocabWordBankWords = emptyList(),
                vocabFinishedToken = 0,
                vocabErrorMessage = null,
                vocabInputMode = InputMode.VOICE,
                vocabVoiceTriggerToken = 0,
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                bossReward = null,
                bossRewardMessage = null,
                bossFinishedToken = 0,
                bossErrorMessage = null,
                wordBankWords = emptyList(),
                selectedWords = emptyList()
            )
        }
        buildSessionCards()
        refreshFlowerStates()
        saveProgress()
    }

    fun selectMode(mode: TrainingMode) {
        pauseTimer()
        vocabSession = emptyList()
        _uiState.update {
            it.copy(
                mode = mode,
                currentIndex = 0,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                sessionState = SessionState.PAUSED,
                voicePromptStartMs = null,
                inputMode = InputMode.VOICE,
                activeSubLessonIndex = 0,
                completedSubLessonCount = 0,
                subLessonFinishedToken = 0,
                storyCheckInDone = false,
                storyCheckOutDone = false,
                activeStory = null,
                storyErrorMessage = null,
                currentVocab = null,
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = 0,
                vocabTotal = 0,
                vocabWordBankWords = emptyList(),
                vocabFinishedToken = 0,
                vocabErrorMessage = null,
                vocabInputMode = InputMode.VOICE,
                vocabVoiceTriggerToken = 0,
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                bossReward = null,
                bossRewardMessage = null,
                bossFinishedToken = 0,
                bossErrorMessage = null,
                wordBankWords = emptyList(),
                selectedWords = emptyList()
            )
        }
        buildSessionCards()
        saveProgress()
    }

    fun submitAnswer(): SubmitResult {
        val state = _uiState.value
        if (state.sessionState != SessionState.ACTIVE) return SubmitResult(false, false)
        if (state.inputText.isBlank() && !state.testMode) return SubmitResult(false, false)
        val card = currentCard() ?: return SubmitResult(false, false)
        val normalizedInput = Normalizer.normalize(state.inputText)
        val accepted = state.testMode || card.acceptedAnswers.any { Normalizer.normalize(it) == normalizedInput }
        val voiceStartMs = if (state.inputMode == InputMode.VOICE) state.voicePromptStartMs else null
        val voiceDurationMs = voiceStartMs?.let { SystemClock.elapsedRealtime() - it }
        val voiceWords = if (voiceStartMs != null) countMetricWords(state.inputText) else 0
        val shouldAddVoiceMetrics = accepted && voiceDurationMs != null && voiceWords > 0
        var hintShown = false
        if (accepted) {
            playSuccessTone()
            // Record card show for mastery tracking
            recordCardShowForMastery(card)
            val isLastCard = state.currentIndex >= sessionCards.lastIndex
            if (state.bossActive) {
                if (isLastCard) {
                    _uiState.update {
                        it.copy(
                            correctCount = it.correctCount + 1,
                            lastResult = null,
                            incorrectAttemptsForCard = 0,
                            answerText = null,
                            voiceActiveMs = if (shouldAddVoiceMetrics) {
                                it.voiceActiveMs + (voiceDurationMs ?: 0L)
                            } else {
                                it.voiceActiveMs
                            },
                            voiceWordCount = if (shouldAddVoiceMetrics) {
                                it.voiceWordCount + voiceWords
                            } else {
                                it.voiceWordCount
                            },
                            voicePromptStartMs = null
                        )
                    }
                    updateBossProgress(state.bossTotal)
                    finishBoss()
                } else {
                    _uiState.update {
                        it.copy(
                            correctCount = it.correctCount + 1,
                            lastResult = true,
                            incorrectAttemptsForCard = 0,
                            answerText = null,
                            voiceActiveMs = if (shouldAddVoiceMetrics) {
                                it.voiceActiveMs + (voiceDurationMs ?: 0L)
                            } else {
                                it.voiceActiveMs
                            },
                            voiceWordCount = if (shouldAddVoiceMetrics) {
                                it.voiceWordCount + voiceWords
                            } else {
                                it.voiceWordCount
                            },
                            voicePromptStartMs = null
                        )
                    }
                    nextCard(triggerVoice = state.inputMode == InputMode.VOICE)
                }
            } else if (state.eliteActive && isLastCard) {
                pauseTimer()
                val speed = calculateSpeedPerMinute(state.voiceActiveMs, state.voiceWordCount)
                val bestSpeeds = normalizeEliteSpeeds(state.eliteBestSpeeds)
                val stepIndex = state.eliteStepIndex.coerceIn(0, eliteStepCount - 1)
                val currentBest = bestSpeeds.getOrNull(stepIndex) ?: 0.0
                val nextSpeeds = bestSpeeds.toMutableList().apply {
                    if (speed > currentBest) {
                        this[stepIndex] = speed
                    }
                }
                val nextStep = (stepIndex + 1) % eliteStepCount
                _uiState.update {
                    it.copy(
                        correctCount = it.correctCount + 1,
                        lastResult = null,
                        incorrectAttemptsForCard = 0,
                        answerText = null,
                        voiceActiveMs = if (shouldAddVoiceMetrics) {
                            it.voiceActiveMs + (voiceDurationMs ?: 0L)
                        } else {
                            it.voiceActiveMs
                        },
                        voiceWordCount = if (shouldAddVoiceMetrics) {
                            it.voiceWordCount + voiceWords
                        } else {
                            it.voiceWordCount
                        },
                        voicePromptStartMs = null,
                        sessionState = SessionState.PAUSED,
                        currentIndex = 0,
                        eliteActive = false,
                        eliteStepIndex = nextStep,
                        eliteBestSpeeds = nextSpeeds,
                        eliteFinishedToken = it.eliteFinishedToken + 1
                    )
                }
                saveProgress()
            } else if (isLastCard) {
                pauseTimer()

                // Check if current sub-lesson is WARMUP before updating state
                val currentState = _uiState.value
                _uiState.update {
                    val nextCompleted = (it.completedSubLessonCount + 1).coerceAtMost(it.subLessonCount)
                    // Рассчитываем реальный прогресс на основе сохранённых карточек
                    val lessonId = it.selectedLessonId
                    val mastery = lessonId?.let { id -> masteryStore.get(id, it.selectedLanguageId) }
                    val schedule = lessonId?.let { id -> lessonSchedules[id] }
                    val subLessons = schedule?.subLessons.orEmpty()
                    val actualCompletedCount = calculateCompletedSubLessons(subLessons, mastery, lessonId)

                    // Не двигаем индекс назад, если пользователь повторяет старый подурок
                    // nextCompleted - это индекс только что завершённого подурока + 1
                    // actualCompletedCount - это реальный прогресс из сохранённых данных
                    val preservedActiveIndex = maxOf(it.activeSubLessonIndex, actualCompletedCount)
                    val finalActiveIndex = preservedActiveIndex.coerceAtMost((it.subLessonCount - 1).coerceAtLeast(0))

                    it.copy(
                        correctCount = it.correctCount + 1,
                        lastResult = null,
                        incorrectAttemptsForCard = 0,
                        answerText = null,
                        voiceActiveMs = if (shouldAddVoiceMetrics) {
                            it.voiceActiveMs + (voiceDurationMs ?: 0L)
                        } else {
                            it.voiceActiveMs
                        },
                        voiceWordCount = if (shouldAddVoiceMetrics) {
                            it.voiceWordCount + voiceWords
                        } else {
                            it.voiceWordCount
                        },
                        voicePromptStartMs = null,
                        sessionState = SessionState.PAUSED,
                        currentIndex = 0,
                        activeSubLessonIndex = finalActiveIndex,
                        completedSubLessonCount = maxOf(nextCompleted, actualCompletedCount),
                        subLessonFinishedToken = it.subLessonFinishedToken + 1
                    )
                }
                markSubLessonCardsShown(sessionCards)
                buildSessionCards()
                // Check if lesson is completed and update flower states
                checkAndMarkLessonCompleted()
                refreshFlowerStates()
                updateStreak()
                forceBackupOnSave = true
            } else {
                _uiState.update {
                    it.copy(
                        correctCount = it.correctCount + 1,
                        lastResult = true,
                        incorrectAttemptsForCard = 0,
                        answerText = null,
                        voiceActiveMs = if (shouldAddVoiceMetrics) {
                            it.voiceActiveMs + (voiceDurationMs ?: 0L)
                        } else {
                            it.voiceActiveMs
                        },
                        voiceWordCount = if (shouldAddVoiceMetrics) {
                            it.voiceWordCount + voiceWords
                        } else {
                            it.voiceWordCount
                        },
                        voicePromptStartMs = null
                    )
                }
                nextCard(triggerVoice = state.inputMode == InputMode.VOICE)
            }
        } else {
            playErrorTone()
            val nextIncorrect = state.incorrectAttemptsForCard + 1
            val hint = if (nextIncorrect >= 3) card.acceptedAnswers.joinToString(" / ") else null
            hintShown = hint != null
            val shouldTriggerVoice = !hintShown && state.inputMode == InputMode.VOICE
            _uiState.update {
                it.copy(
                    incorrectCount = it.incorrectCount + 1,
                    incorrectAttemptsForCard = if (hintShown) 0 else nextIncorrect,
                    lastResult = false,
                    answerText = hint,
                    inputText = if (state.inputMode == InputMode.VOICE) "" else it.inputText,
                    sessionState = if (hint != null) SessionState.HINT_SHOWN else it.sessionState,
                    voiceTriggerToken = if (shouldTriggerVoice) it.voiceTriggerToken + 1 else it.voiceTriggerToken,
                    voicePromptStartMs = null
                )
            }
            if (hintShown) {
                pauseTimer()
            }
        }
        saveProgress()
        // Refresh flower states after card show is recorded
        if (accepted) {
            refreshFlowerStates()
        }
        Log.d(logTag, "Answer submitted: accepted=$accepted")
        return SubmitResult(accepted, hintShown)
    }

    fun nextCard(triggerVoice: Boolean = false) {
        val state = _uiState.value
        val wasHintShown = state.sessionState == SessionState.HINT_SHOWN
        val nextIndex = (state.currentIndex + 1).coerceAtMost(sessionCards.lastIndex)
        val nextCard = sessionCards.getOrNull(nextIndex)
        val nextProgress = if (state.bossActive) {
            (state.bossProgress.coerceAtLeast(nextIndex)).coerceAtMost(state.bossTotal)
        } else {
            state.bossProgress
        }
        val nextReward = if (state.bossActive) {
            resolveBossReward(nextProgress, state.bossTotal)
        } else {
            state.bossReward
        }
        val isNewReward = state.bossActive && nextReward != null && nextReward != state.bossReward
        val rewardMessage = if (isNewReward) {
            bossRewardMessage(nextReward!!)
        } else {
            state.bossRewardMessage
        }
        val pauseForReward = isNewReward
        if (pauseForReward) {
            pauseTimer()
        }
        _uiState.update {
            val shouldTrigger = triggerVoice && it.inputMode == InputMode.VOICE && !pauseForReward
            it.copy(
                currentIndex = nextIndex,
                currentCard = nextCard,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                sessionState = if (pauseForReward) SessionState.PAUSED else SessionState.ACTIVE,
                voiceTriggerToken = if (shouldTrigger) it.voiceTriggerToken + 1 else it.voiceTriggerToken,
                voicePromptStartMs = null,
                bossProgress = nextProgress,
                bossReward = nextReward ?: it.bossReward,
                bossRewardMessage = rewardMessage
            )
        }
        nextCard?.let { recordCardShowForMastery(it) }

        // Update word bank if in WORD_BANK mode
        if (_uiState.value.inputMode == InputMode.WORD_BANK) {
            updateWordBank()
        }

        if (wasHintShown && !pauseForReward) {
            resumeTimer()
        }
        saveProgress()
    }

    fun prevCard() {
        val prevIndex = (_uiState.value.currentIndex - 1).coerceAtLeast(0)
        val prevCard = sessionCards.getOrNull(prevIndex)
        _uiState.update {
            it.copy(
                currentIndex = prevIndex,
                currentCard = prevCard,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                voicePromptStartMs = null
            )
        }
        prevCard?.let { recordCardShowForMastery(it) }
        saveProgress()
    }

    fun togglePause() {
        if (_uiState.value.sessionState == SessionState.ACTIVE) {
            pauseTimer()
            _uiState.update { it.copy(sessionState = SessionState.PAUSED, voicePromptStartMs = null) }
            saveProgress()
            return
        }
        startSession()
    }

    fun pauseSession() {
        pauseTimer()
        _uiState.update { it.copy(sessionState = SessionState.PAUSED, voicePromptStartMs = null) }
        saveProgress()
    }

    fun finishSession() {
        if (sessionCards.isEmpty()) return
        if (_uiState.value.eliteActive) {
            cancelEliteSession()
            return
        }
        if (_uiState.value.bossActive) {
            finishBoss()
            return
        }
        pauseTimer()
        val state = _uiState.value
        val minutes = state.activeTimeMs / 60000.0
        val rating = if (minutes <= 0.0) 0.0 else state.correctCount / minutes
        val firstCard = sessionCards.firstOrNull()
        _uiState.update {
            it.copy(
                sessionState = SessionState.PAUSED,
                lastRating = rating,
                incorrectAttemptsForCard = 0,
                lastResult = null,
                answerText = null,
                currentIndex = 0,
                currentCard = firstCard,
                inputText = "",
                voicePromptStartMs = null
            )
        }
        saveProgress()
        refreshFlowerStates()
        Log.d(logTag, "Session finished. Rating=$rating")
    }

    fun showAnswer() {
        val card = currentCard() ?: return
        pauseTimer()
        _uiState.update {
            it.copy(
                answerText = card.acceptedAnswers.joinToString(" / "),
                sessionState = SessionState.HINT_SHOWN,
                inputText = if (it.inputMode == InputMode.VOICE) "" else it.inputText,
                hintCount = it.hintCount + 1,
                voicePromptStartMs = null
            )
        }
        saveProgress()
    }

    fun importLesson(uri: Uri) {
        val languageId = _uiState.value.selectedLanguageId
        val lesson = lessonStore.importFromUri(languageId, uri, getApplication<Application>().contentResolver)
        refreshLessons(lesson.id)
    }

    fun importLessonPack(uri: Uri) {
        vocabSession = emptyList()
        try {
            val pack = lessonStore.importPackFromUri(uri, getApplication<Application>().contentResolver)
            val lessons = lessonStore.getLessons(pack.languageId)
            val selectedLessonId = lessons.firstOrNull()?.id
            _uiState.update {
                it.copy(
                    languages = lessonStore.getLanguages(),
                    installedPacks = lessonStore.getInstalledPacks(),
                    selectedLanguageId = pack.languageId,
                    lessons = lessons,
                    selectedLessonId = selectedLessonId,
                    eliteActive = false,
                    eliteUnlocked = resolveEliteUnlocked(lessons, it.testMode),
                    mode = TrainingMode.LESSON,
                    sessionState = SessionState.PAUSED,
                    currentIndex = 0,
                    correctCount = 0,
                    incorrectCount = 0,
                    incorrectAttemptsForCard = 0,
                    inputMode = InputMode.VOICE,
                    activeTimeMs = 0L,
                    voiceActiveMs = 0L,
                    voiceWordCount = 0,
                    hintCount = 0,
                    voicePromptStartMs = null,
                    inputText = "",
                    lastResult = null,
                    answerText = null,
                    activeSubLessonIndex = 0,
                    completedSubLessonCount = 0,
                    subLessonFinishedToken = 0,
                    storyCheckInDone = false,
                    storyCheckOutDone = false,
                    activeStory = null,
                    storyErrorMessage = null,
                    currentVocab = null,
                    vocabInputText = "",
                    vocabAttempts = 0,
                    vocabAnswerText = null,
                    vocabIndex = 0,
                    vocabTotal = 0,
                    vocabWordBankWords = emptyList(),
                    vocabFinishedToken = 0,
                    vocabErrorMessage = null,
                    vocabInputMode = InputMode.VOICE,
                    vocabVoiceTriggerToken = 0,
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                    bossReward = null,
                    bossRewardMessage = null,
                    bossFinishedToken = 0,
                    bossErrorMessage = null,
                    wordBankWords = emptyList(),
                    selectedWords = emptyList()
                )
            }
            rebuildSchedules(lessons)
            buildSessionCards()
            saveProgress()
        } catch (e: Exception) {
            Log.e(logTag, "Lesson pack import failed", e)
        }
    }
    fun resetAndImportLesson(uri: Uri) {
        val languageId = _uiState.value.selectedLanguageId
        lessonStore.deleteAllLessons(languageId)
        val lesson = lessonStore.importFromUri(languageId, uri, getApplication<Application>().contentResolver)
        refreshLessons(lesson.id)
    }

    fun deleteLesson(lessonId: String) {
        val languageId = _uiState.value.selectedLanguageId
        lessonStore.deleteLesson(languageId, lessonId)
        val selected = if (_uiState.value.selectedLessonId == lessonId) null else _uiState.value.selectedLessonId
        refreshLessons(selected)
    }

    fun createEmptyLesson(title: String) {
        val languageId = _uiState.value.selectedLanguageId
        val lesson = lessonStore.createEmptyLesson(languageId, title)
        refreshLessons(lesson.id)
    }

    fun addLanguage(name: String) {
        val language = lessonStore.addLanguage(name)
        vocabSession = emptyList()
        val lessons = lessonStore.getLessons(language.id)
        val selectedLessonId = lessons.firstOrNull()?.id
        _uiState.update {
            it.copy(
                languages = lessonStore.getLanguages(),
                installedPacks = lessonStore.getInstalledPacks(),
                selectedLanguageId = language.id,
                lessons = lessons,
                selectedLessonId = selectedLessonId,
                eliteActive = false,
                eliteUnlocked = resolveEliteUnlocked(lessons, it.testMode),
                mode = TrainingMode.LESSON,
                sessionState = SessionState.PAUSED,
                currentIndex = 0,
                correctCount = 0,
                incorrectCount = 0,
                incorrectAttemptsForCard = 0,
                inputMode = InputMode.VOICE,
                activeTimeMs = 0L,
                voiceActiveMs = 0L,
                voiceWordCount = 0,
                hintCount = 0,
                voicePromptStartMs = null,
                inputText = "",
                lastResult = null,
                answerText = null,
                activeSubLessonIndex = 0,
                completedSubLessonCount = 0,
                subLessonFinishedToken = 0,
                storyCheckInDone = false,
                storyCheckOutDone = false,
                activeStory = null,
                storyErrorMessage = null,
                currentVocab = null,
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = 0,
                vocabTotal = 0,
                vocabWordBankWords = emptyList(),
                vocabFinishedToken = 0,
                vocabErrorMessage = null,
                vocabInputMode = InputMode.VOICE,
                vocabVoiceTriggerToken = 0,
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                bossReward = null,
                bossRewardMessage = null,
                bossFinishedToken = 0,
                bossErrorMessage = null,
                wordBankWords = emptyList(),
                selectedWords = emptyList()
            )
        }
        rebuildSchedules(lessons)
        buildSessionCards()
        saveProgress()
    }

    fun deleteAllLessons() {
        val languageId = _uiState.value.selectedLanguageId
        lessonStore.deleteAllLessons(languageId)
        refreshLessons(null)
        _uiState.update { it.copy(installedPacks = lessonStore.getInstalledPacks()) }
    }

    fun deletePack(packId: String) {
        val pack = lessonStore.getInstalledPacks().firstOrNull { it.packId == packId } ?: return
        lessonStore.deleteAllLessons(pack.languageId)
        if (_uiState.value.selectedLanguageId == pack.languageId) {
            refreshLessons(null)
        }
        _uiState.update { it.copy(installedPacks = lessonStore.getInstalledPacks()) }
    }

    fun toggleTestMode() {
        val newTestMode = !_uiState.value.testMode
        _uiState.update {
            it.copy(
                testMode = newTestMode,
                eliteUnlocked = resolveEliteUnlocked(_uiState.value.lessons, newTestMode)
            )
        }
        configStore.save(
            AppConfig(
                testMode = newTestMode,
                eliteSizeMultiplier = _uiState.value.eliteSizeMultiplier,
                vocabSprintLimit = _uiState.value.vocabSprintLimit
            )
        )
        Log.d(logTag, "Test mode toggled: $newTestMode")
    }

    fun updateVocabSprintLimit(limit: Int) {
        val nextLimit = limit.coerceAtLeast(0)
        _uiState.update { it.copy(vocabSprintLimit = nextLimit) }
        configStore.save(
            AppConfig(
                testMode = _uiState.value.testMode,
                eliteSizeMultiplier = _uiState.value.eliteSizeMultiplier,
                vocabSprintLimit = nextLimit
            )
        )
    }

    private fun refreshLessons(selectedLessonId: String?) {
        pauseTimer()
        vocabSession = emptyList()
        val languageId = _uiState.value.selectedLanguageId
        val lessons = lessonStore.getLessons(languageId)
        val selected = selectedLessonId ?: lessons.firstOrNull()?.id
        _uiState.update {
            it.copy(
                lessons = lessons,
                selectedLessonId = selected,
                eliteActive = false,
                eliteUnlocked = resolveEliteUnlocked(lessons, it.testMode),
                currentIndex = 0,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                sessionState = SessionState.PAUSED,
                voicePromptStartMs = null,
                inputMode = InputMode.VOICE,
                activeSubLessonIndex = 0,
                completedSubLessonCount = 0,
                subLessonFinishedToken = 0,
                storyCheckInDone = false,
                storyCheckOutDone = false,
                activeStory = null,
                storyErrorMessage = null,
                currentVocab = null,
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = 0,
                vocabTotal = 0,
                vocabWordBankWords = emptyList(),
                vocabFinishedToken = 0,
                vocabErrorMessage = null,
                vocabInputMode = InputMode.VOICE,
                vocabVoiceTriggerToken = 0,
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                bossReward = null,
                bossRewardMessage = null,
                bossFinishedToken = 0,
                bossErrorMessage = null,
                wordBankWords = emptyList(),
                selectedWords = emptyList()
            )
        }
        rebuildSchedules(lessons)
        buildSessionCards()
        saveProgress()
    }

    fun resumeFromSettings() {
        if (_uiState.value.sessionState == SessionState.ACTIVE) return
        startSession()
    }

    private fun rebuildSchedules(lessons: List<Lesson>) {
        val lessonKey = lessons.joinToString("|") { "${it.id}:${it.cards.size}" }
        val blockSize = subLessonSize.coerceIn(subLessonSizeMin, subLessonSizeMax)
        val key = "${lessonKey}|${blockSize}"
        if (key == scheduleKey) return
        scheduleKey = key
        lessonSchedules = MixedReviewScheduler(blockSize).build(lessons)
    }

    private fun buildSessionCards() {
        if (_uiState.value.bossActive || _uiState.value.eliteActive) return
        val state = _uiState.value
        val lessons = state.lessons
        if (state.mode == TrainingMode.LESSON) {
            val schedule = lessonSchedules[state.selectedLessonId]
            val subLessons = schedule?.subLessons.orEmpty()
            subLessonCount = subLessons.size

            // Calculate completed sub-lessons based on shown cards
            val mastery = state.selectedLessonId?.let {
                masteryStore.get(it, state.selectedLanguageId)
            }
            val completedCount = calculateCompletedSubLessons(subLessons, mastery, state.selectedLessonId)

            val activeIndex = state.activeSubLessonIndex.coerceIn(0, (subLessonCount - 1).coerceAtLeast(0))
            val subLesson = subLessons.getOrNull(activeIndex)
            sessionCards = subLesson?.cards ?: emptyList()
            subLessonTotal = sessionCards.size
            val safeIndex = _uiState.value.currentIndex.coerceIn(0, (sessionCards.size - 1).coerceAtLeast(0))
            val card = sessionCards.getOrNull(safeIndex)
            if (card == null && state.sessionState == SessionState.ACTIVE) {
                pauseTimer()
            }
            _uiState.update {
                it.copy(
                    currentIndex = safeIndex,
                    currentCard = card,
                    sessionState = if (card == null) SessionState.PAUSED else state.sessionState,
                    subLessonTotal = subLessonTotal,
                    subLessonCount = subLessonCount,
                    activeSubLessonIndex = activeIndex,
                    completedSubLessonCount = completedCount,
                    subLessonTypes = subLessons.map { item -> item.type }
                )
            }
            return
        }

        val lessonCards = when (state.mode) {
            TrainingMode.ALL_SEQUENTIAL -> lessons.flatMap { it.cards }
            // ALL_MIXED (Review) uses a random subset across all cards
            TrainingMode.ALL_MIXED -> {
                val reviewLimit = 300
                lessons.flatMap { it.allCards }.shuffled().take(reviewLimit)
            }
            else -> emptyList()
        }
        val blockSize = subLessonSize.coerceIn(subLessonSizeMin, subLessonSizeMax)
        subLessonCount = if (lessonCards.isEmpty()) 0 else (lessonCards.size + blockSize - 1) / blockSize
        val activeIndex = state.activeSubLessonIndex.coerceIn(0, (subLessonCount - 1).coerceAtLeast(0))
        val blockStart = activeIndex * blockSize
        val block = lessonCards.drop(blockStart).take(blockSize)
        sessionCards = block
        subLessonTotal = block.size
        val safeIndex = _uiState.value.currentIndex.coerceIn(0, (sessionCards.size - 1).coerceAtLeast(0))
        val card = sessionCards.getOrNull(safeIndex)
        if (card == null && state.sessionState == SessionState.ACTIVE) {
            pauseTimer()
        }
        _uiState.update {
            it.copy(
                currentIndex = safeIndex,
                currentCard = card,
                sessionState = if (card == null) SessionState.PAUSED else state.sessionState,
                subLessonTotal = subLessonTotal,
                subLessonCount = subLessonCount,
                activeSubLessonIndex = activeIndex,
                subLessonTypes = emptyList()
            )
        }
    }

    fun selectSubLesson(index: Int) {
        pauseTimer()
        _uiState.update {
            it.copy(
                activeSubLessonIndex = index.coerceAtLeast(0),
                currentIndex = 0,
                inputText = "",
                lastResult = null,
                answerText = null,
                sessionState = SessionState.PAUSED
            )
        }
        buildSessionCards()
        saveProgress()
    }

    fun openEliteStep(index: Int) {
        pauseTimer()
        val stepIndex = index.coerceIn(0, eliteStepCount - 1)
        val cards = buildEliteCards()
        eliteCards = cards
        sessionCards = cards
        val firstCard = cards.firstOrNull()
        _uiState.update {
            it.copy(
                eliteActive = true,
                eliteStepIndex = stepIndex,
                currentIndex = 0,
                currentCard = firstCard,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                correctCount = 0,
                incorrectCount = 0,
                activeTimeMs = 0L,
                voiceActiveMs = 0L,
                voiceWordCount = 0,
                hintCount = 0,
                voicePromptStartMs = null,
                sessionState = SessionState.PAUSED,
                subLessonTotal = cards.size,
                subLessonCount = eliteStepCount,
                activeSubLessonIndex = stepIndex,
                completedSubLessonCount = 0
            )
        }
        saveProgress()
    }

    fun cancelEliteSession() {
        if (!_uiState.value.eliteActive) return
        pauseTimer()
        _uiState.update {
            it.copy(
                eliteActive = false,
                sessionState = SessionState.PAUSED,
                currentIndex = 0,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                voicePromptStartMs = null
            )
        }
        saveProgress()
        refreshFlowerStates()
    }

    fun openStory(phase: StoryPhase) {
        val lessonId = _uiState.value.selectedLessonId ?: return
        val languageId = _uiState.value.selectedLanguageId
        val story = lessonStore.getStoryQuizzes(lessonId, phase, languageId).firstOrNull()
        if (story == null) {
            _uiState.update { it.copy(storyErrorMessage = "Story not found. Please import the pack again.") }
            return
        }
        _uiState.update { it.copy(activeStory = story, storyErrorMessage = null) }
    }

    fun openVocabSprint() {
        val lessonId = _uiState.value.selectedLessonId ?: return
        val languageId = _uiState.value.selectedLanguageId
        val entries = lessonStore.getVocabEntries(lessonId, languageId)
        val shuffled = entries.shuffled()
        val limit = _uiState.value.vocabSprintLimit
        val limited = if (limit <= 0 || limit >= shuffled.size) shuffled else shuffled.take(limit)
        if (limited.isEmpty()) {
            vocabSession = emptyList()
            _uiState.update { it.copy(vocabErrorMessage = "Vocabulary not found. Please import the pack again.") }
            return
        }
        vocabSession = limited
        val vocabWordBank = limited.firstOrNull()?.let { buildVocabWordBank(it, limited) }.orEmpty()
        Log.d(logTag, "openVocabSprint: entries=${entries.size}, limited=${limited.size}, wordBank=${vocabWordBank.size}")
        _uiState.update {
            it.copy(
                currentVocab = limited.firstOrNull(),
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = 0,
                vocabTotal = limited.size,
                vocabWordBankWords = vocabWordBank,
                vocabErrorMessage = null,
                vocabInputMode = InputMode.VOICE,
                vocabVoiceTriggerToken = 0,
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                bossReward = null,
                bossRewardMessage = null,
                bossFinishedToken = 0,
                bossErrorMessage = null
            )
        }
    }

    fun completeStory(phase: StoryPhase, allCorrect: Boolean) {
        val shouldPersist = allCorrect || _uiState.value.testMode
        _uiState.update {
            if (!allCorrect && !it.testMode) {
                return@update it.copy(activeStory = null)
            }
            when (phase) {
                StoryPhase.CHECK_IN -> it.copy(storyCheckInDone = true, activeStory = null)
                StoryPhase.CHECK_OUT -> it.copy(storyCheckOutDone = true, activeStory = null)
            }
        }
        if (shouldPersist) {
            forceBackupOnSave = true
            saveProgress()
        }
    }

    fun clearStoryError() {
        _uiState.update { it.copy(storyErrorMessage = null) }
    }

    fun clearVocabError() {
        _uiState.update { it.copy(vocabErrorMessage = null) }
    }

    fun onVocabInputChanged(text: String) {
        _uiState.update {
            val resetAttempts = it.vocabAnswerText != null || it.vocabAttempts >= 3
            it.copy(
                vocabInputText = text,
                vocabAttempts = if (resetAttempts) 0 else it.vocabAttempts,
                vocabAnswerText = if (resetAttempts) null else it.vocabAnswerText
            )
        }
    }

    fun setVocabInputMode(mode: InputMode) {
        Log.d(logTag, "setVocabInputMode: $mode")
        _uiState.update { it.copy(vocabInputMode = mode) }
        if (mode == InputMode.WORD_BANK) {
            updateVocabWordBank()
            Log.d(logTag, "Word bank updated. Words: ${_uiState.value.vocabWordBankWords.size}")
        }
    }

    fun requestVocabVoice() {
        _uiState.update {
            it.copy(
                vocabInputMode = InputMode.VOICE,
                vocabVoiceTriggerToken = it.vocabVoiceTriggerToken + 1
            )
        }
    }

    fun submitVocabAnswer(inputOverride: String? = null) {
        val state = _uiState.value
        val entry = state.currentVocab ?: return
        val input = inputOverride ?: state.vocabInputText
        if (input.isBlank() && !state.testMode) return
        val normalizedInput = Normalizer.normalize(input)
        val accepted = state.testMode || entry.targetText.split("+")
            .map { Normalizer.normalize(it) }
            .any { it == normalizedInput }
        if (accepted) {
            playSuccessTone()
            moveToNextVocab()
            return
        }
        playErrorTone()
        val nextAttempts = state.vocabAttempts + 1
        if (nextAttempts >= 3) {
            _uiState.update {
                it.copy(
                    vocabAttempts = nextAttempts,
                    vocabAnswerText = entry.targetText,
                    vocabInputText = ""
                )
            }
        } else {
            _uiState.update {
                val nextToken = if (state.vocabInputMode == InputMode.VOICE) {
                    it.vocabVoiceTriggerToken + 1
                } else {
                    it.vocabVoiceTriggerToken
                }
                it.copy(
                    vocabAttempts = nextAttempts,
                    vocabInputText = "",
                    vocabVoiceTriggerToken = nextToken
                )
            }
        }
    }

    fun showVocabAnswer() {
        val entry = _uiState.value.currentVocab ?: return
        _uiState.update {
            it.copy(
                vocabAnswerText = entry.targetText,
                vocabInputText = "",
                vocabAttempts = 3
            )
        }
    }

    private fun moveToNextVocab() {
        val state = _uiState.value
        val nextIndex = state.vocabIndex + 1
        if (nextIndex >= vocabSession.size) {
            _uiState.update {
                it.copy(
                    currentVocab = null,
                    vocabInputText = "",
                    vocabAttempts = 0,
                    vocabAnswerText = null,
                    vocabIndex = nextIndex,
                    vocabTotal = vocabSession.size,
                    vocabWordBankWords = emptyList(),
                    vocabFinishedToken = it.vocabFinishedToken + 1
                )
            }
            forceBackupOnSave = true
            saveProgress()
            return
        }
        val next = vocabSession[nextIndex]
        val vocabWordBank = buildVocabWordBank(next, vocabSession)
        _uiState.update {
            it.copy(
                currentVocab = next,
                vocabInputText = "",
                vocabAttempts = 0,
                vocabAnswerText = null,
                vocabIndex = nextIndex,
                vocabTotal = vocabSession.size,
                vocabWordBankWords = vocabWordBank
            )
        }
    }

    fun startBossLesson() {
        startBoss(BossType.LESSON)
    }

    fun startBossMega() {
        startBoss(BossType.MEGA)
    }

    fun startBossElite() {
        startBoss(BossType.ELITE)
    }

    private fun startBoss(type: BossType) {
        pauseTimer()
        val lessons = _uiState.value.lessons
        val selectedId = _uiState.value.selectedLessonId
        if (type != BossType.ELITE && selectedId == null) {
            _uiState.update { it.copy(bossErrorMessage = "Lesson not selected") }
            return
        }
        val selectedIndex = lessons.indexOfFirst { it.id == selectedId }
        val maxBossCards = 300
        val cards = when (type) {
            BossType.LESSON -> {
                val lessonCards = lessons.firstOrNull { it.id == selectedId }?.cards ?: emptyList()
                lessonCards.shuffled().take(maxBossCards)
            }
            BossType.MEGA -> {
                if (selectedIndex <= 0) emptyList()
                else lessons.take(selectedIndex).flatMap { it.cards }.shuffled().take(maxBossCards)
            }
            BossType.ELITE -> {
                val eliteSize = eliteSubLessonSize() * eliteStepCount
                lessons.flatMap { it.cards }.shuffled().take(eliteSize)
            }
        }
        if (cards.isEmpty()) {
            val message = if (type == BossType.MEGA) {
                "Mega boss is available after the first lesson"
            } else if (type == BossType.ELITE) {
                "Elite boss has no cards"
            } else {
                "Boss has no cards"
            }
            _uiState.update { it.copy(bossErrorMessage = message) }
            return
        }
        bossCards = cards
        sessionCards = bossCards
        subLessonTotal = bossCards.size
        subLessonCount = 1
        val firstCard = bossCards.firstOrNull()
        _uiState.update {
            it.copy(
                bossActive = true,
                bossType = type,
                bossTotal = bossCards.size,
                bossProgress = 0,
                bossReward = null,
                bossRewardMessage = null,
                bossErrorMessage = null,
                currentIndex = 0,
                currentCard = firstCard,
                inputText = "",
                lastResult = null,
                answerText = null,
                incorrectAttemptsForCard = 0,
                correctCount = 0,
                incorrectCount = 0,
                activeTimeMs = 0L,
                voiceActiveMs = 0L,
                voiceWordCount = 0,
                hintCount = 0,
                voicePromptStartMs = null,
                sessionState = SessionState.PAUSED,
                subLessonTotal = bossCards.size,
                subLessonCount = 1,
                activeSubLessonIndex = 0,
                completedSubLessonCount = 0
            )
        }
    }

    fun finishBoss() {
        pauseTimer()
        val state = _uiState.value
        val reward = resolveBossReward(state.bossProgress, state.bossTotal)
        val progress = progressStore.load()
        val restoredLessonId = progress.lessonId ?: state.selectedLessonId
        val updatedLessonRewards = if (state.bossType == BossType.LESSON) {
            val lessonId = state.selectedLessonId
            if (lessonId != null && reward != null) {
                state.bossLessonRewards + (lessonId to reward)
            } else {
                state.bossLessonRewards
            }
        } else {
            state.bossLessonRewards
        }
        val updatedMegaReward = if (state.bossType == BossType.MEGA && reward != null) {
            reward
        } else {
            state.bossMegaReward
        }
        bossCards = emptyList()
        _uiState.update {
            it.copy(
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                bossReward = reward ?: it.bossReward,
                bossRewardMessage = it.bossRewardMessage,
                bossFinishedToken = it.bossFinishedToken + 1,
                bossLastType = state.bossType,
                bossErrorMessage = null,
                bossLessonRewards = updatedLessonRewards,
                bossMegaReward = updatedMegaReward,
                selectedLessonId = restoredLessonId,
                mode = progress.mode,
                currentIndex = progress.currentIndex,
                correctCount = progress.correctCount,
                incorrectCount = progress.incorrectCount,
                incorrectAttemptsForCard = progress.incorrectAttemptsForCard,
                activeTimeMs = progress.activeTimeMs,
                voiceActiveMs = progress.voiceActiveMs,
                voiceWordCount = progress.voiceWordCount,
                hintCount = progress.hintCount,
                voicePromptStartMs = null,
                inputText = "",
                lastResult = null,
                answerText = null,
                sessionState = SessionState.PAUSED
            )
        }
        buildSessionCards()
        saveProgress()
        refreshFlowerStates()
    }

    fun clearBossRewardMessage() {
        val state = _uiState.value
        val shouldResume = state.bossActive &&
            state.sessionState == SessionState.PAUSED &&
            state.currentCard != null
        if (shouldResume) {
            resumeTimer()
        }
        _uiState.update {
            val trigger = if (shouldResume && it.inputMode == InputMode.VOICE) {
                it.voiceTriggerToken + 1
            } else {
                it.voiceTriggerToken
            }
            it.copy(
                bossRewardMessage = null,
                sessionState = if (shouldResume) SessionState.ACTIVE else it.sessionState,
                voiceTriggerToken = trigger,
                inputText = if (shouldResume) "" else it.inputText
            )
        }
    }

    fun clearBossError() {
        _uiState.update { it.copy(bossErrorMessage = null) }
    }

    private fun updateBossProgress(progress: Int) {
        val state = _uiState.value
        val nextProgress = progress.coerceAtMost(state.bossTotal)
        val nextReward = resolveBossReward(nextProgress, state.bossTotal)
        val isNewReward = nextReward != null && nextReward != state.bossReward
        val message = if (isNewReward) {
            bossRewardMessage(nextReward!!)
        } else {
            state.bossRewardMessage
        }
        if (isNewReward) {
            pauseTimer()
        }
        _uiState.update {
            it.copy(
                bossProgress = nextProgress,
                bossReward = nextReward ?: it.bossReward,
                bossRewardMessage = message,
                sessionState = if (isNewReward) SessionState.PAUSED else it.sessionState
            )
        }
    }

    private fun resolveBossReward(progress: Int, total: Int): BossReward? {
        if (total <= 0) return null
        val percent = (progress.toDouble() / total.toDouble()) * 100.0
        return when {
            percent >= 100.0 -> BossReward.GOLD
            percent > 75.0 -> BossReward.SILVER
            percent > 50.0 -> BossReward.BRONZE
            else -> null
        }
    }

    private fun bossRewardMessage(reward: BossReward): String {
        return when (reward) {
            BossReward.BRONZE -> "Bronze reached"
            BossReward.SILVER -> "Silver reached"
            BossReward.GOLD -> "Gold reached"
        }
    }

    private fun startSession() {
        if (!_uiState.value.bossActive && !_uiState.value.eliteActive) {
            buildSessionCards()
        }
        if (sessionCards.isEmpty() || _uiState.value.currentCard == null) {
            pauseTimer()
            _uiState.update { it.copy(sessionState = SessionState.PAUSED) }
            saveProgress()
            return
        }
        pauseTimer()
        resumeTimer()
        _uiState.update {
            val trigger = if (it.inputMode == InputMode.VOICE) it.voiceTriggerToken + 1 else it.voiceTriggerToken
            it.copy(
                sessionState = SessionState.ACTIVE,
                inputText = "",
                voiceTriggerToken = trigger,
                voicePromptStartMs = null
            )
        }
        currentCard()?.let { recordCardShowForMastery(it) }
        saveProgress()
    }

    private fun currentCard(): SentenceCard? {
        if (sessionCards.isEmpty()) return null
        val index = _uiState.value.currentIndex.coerceIn(0, sessionCards.lastIndex)
        return sessionCards.getOrNull(index)
    }

    private fun resumeTimer() {
        if (timerJob?.isActive == true) return
        activeStartMs = SystemClock.elapsedRealtime()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(500)
                val start = activeStartMs ?: continue
                val elapsed = SystemClock.elapsedRealtime() - start
                _uiState.update { it.copy(activeTimeMs = it.activeTimeMs + elapsed) }
                activeStartMs = SystemClock.elapsedRealtime()
                saveProgress()
            }
        }
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        activeStartMs = null
    }

    private fun saveProgress() {
        val state = _uiState.value
        if (state.bossActive && state.bossType != BossType.ELITE) return
        progressStore.save(
            TrainingProgress(
                languageId = state.selectedLanguageId,
                mode = state.mode,
                lessonId = state.selectedLessonId,
                currentIndex = state.currentIndex,
                correctCount = state.correctCount,
                incorrectCount = state.incorrectCount,
                incorrectAttemptsForCard = state.incorrectAttemptsForCard,
                activeTimeMs = state.activeTimeMs,
                state = state.sessionState,
                bossLessonRewards = state.bossLessonRewards.mapValues { it.value.name },
                bossMegaReward = state.bossMegaReward?.name,
                voiceActiveMs = state.voiceActiveMs,
                voiceWordCount = state.voiceWordCount,
                hintCount = state.hintCount,
                eliteStepIndex = state.eliteStepIndex,
                eliteBestSpeeds = normalizeEliteSpeeds(state.eliteBestSpeeds)
            )
        )

        if (forceBackupOnSave) {
            forceBackupOnSave = false
            createProgressBackup()
        }
    }

    private fun resolveEliteUnlocked(lessons: List<Lesson>, testMode: Boolean): Boolean {
        return testMode || lessons.size >= 12
    }

    private fun normalizeEliteSpeeds(speeds: List<Double>): List<Double> {
        return if (speeds.size >= eliteStepCount) {
            speeds.take(eliteStepCount)
        } else {
            speeds + List(eliteStepCount - speeds.size) { 0.0 }
        }
    }

    private fun eliteSubLessonSize(): Int {
        return kotlin.math.ceil(subLessonSize * eliteSizeMultiplier).toInt()
    }

    private fun calculateSpeedPerMinute(activeMs: Long, words: Int): Double {
        val minutes = activeMs / 60000.0
        if (minutes <= 0.0) return 0.0
        return words / minutes
    }

    private fun buildEliteCards(): List<SentenceCard> {
        val cards = _uiState.value.lessons.flatMap { it.cards }
        if (cards.isEmpty()) return emptyList()
        return cards.shuffled().take(eliteSubLessonSize())
    }

    private fun countMetricWords(text: String): Int {
        val normalized = Normalizer.normalize(text)
        if (normalized.isBlank()) return 0
        return normalized.split(" ").count { it.length >= 3 }
    }

    override fun onCleared() {
        soundPool.release()
        super.onCleared()
    }

    private fun playSuccessTone() {
        if (loadedSounds.contains(successSoundId)) {
            soundPool.play(successSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    private fun playErrorTone() {
        if (loadedSounds.contains(errorSoundId)) {
            soundPool.play(errorSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    /**
     * Определить к какому уроку принадлежит карточка.
     * Важно для Mixed-режима где карточки могут быть из разных уроков.
     */
    private fun resolveCardLessonId(card: SentenceCard): String {
        return _uiState.value.lessons
            .find { lesson -> lesson.cards.any { it.id == card.id } }
            ?.id
            ?: _uiState.value.selectedLessonId
            ?: "unknown"
    }

    /**
     * Записать показ карточки для отслеживания прогресса освоения.
     * Word Bank НЕ учитывается для формирования навыка (роста цветка).
     * Учитывается только голосовой ввод и клавиатура.
     */
    private fun recordCardShowForMastery(card: SentenceCard) {
        val lessonId = resolveCardLessonId(card)
        val languageId = _uiState.value.selectedLanguageId

        // Word Bank mode: does NOT count for mastery (flower growth)
        // Only voice and keyboard input count for skill formation
        val isWordBankMode = _uiState.value.inputMode == InputMode.WORD_BANK
        if (isWordBankMode) {
            Log.d(logTag, "Skipping card show record for Word Bank mode - does not count for mastery")
            return
        }

        Log.d(logTag, "Recording card show: lessonId=$lessonId, cardId=${card.id}, mode=${_uiState.value.inputMode}")
        masteryStore.recordCardShow(lessonId, languageId, card.id)
        val mastery = masteryStore.get(lessonId, languageId)
        Log.d(logTag, "After record: uniqueCardShows=${mastery?.uniqueCardShows}, totalShows=${mastery?.totalCardShows}")
    }

    private fun markSubLessonCardsShown(cards: List<SentenceCard>) {
        if (_uiState.value.inputMode != InputMode.WORD_BANK || cards.isEmpty()) return
        val lessonId = _uiState.value.selectedLessonId ?: return
        val lessonCardIds = _uiState.value.lessons
            .firstOrNull { it.id == lessonId }
            ?.cards
            ?.map { it.id }
            ?.toSet()
            ?: return
        val cardIds = cards.map { it.id }.filter { lessonCardIds.contains(it) }
        masteryStore.markCardsShownForProgress(lessonId, _uiState.value.selectedLanguageId, cardIds)
    }

    /**
     * Проверить и отметить урок как завершённый если все под-уроки пройдены.
     */
    private fun checkAndMarkLessonCompleted() {
        val state = _uiState.value
        // Mark lesson as completed after first 15 sublessons (but allow continuing)
        val completedFirstCycle = state.completedSubLessonCount >= 15
        if (completedFirstCycle && state.selectedLessonId != null) {
            masteryStore.markLessonCompleted(state.selectedLessonId, state.selectedLanguageId)
        }
    }

    /**
     * Вычислить количество завершённых под-уроков на основе показанных карточек.
     */
    private fun calculateCompletedSubLessons(
        subLessons: List<ScheduledSubLesson>,
        mastery: LessonMasteryState?,
        lessonId: String?
    ): Int {
        if (lessonId == null || mastery == null || mastery.shownCardIds.isEmpty()) return 0

        val lessonCardIds = _uiState.value.lessons
            .firstOrNull { it.id == lessonId }
            ?.cards
            ?.map { it.id }
            ?.toSet()
            ?: return 0

        var completed = 0
        for (subLesson in subLessons) {
            val allCardsShown = subLesson.cards.all { card ->
                !lessonCardIds.contains(card.id) || mastery.shownCardIds.contains(card.id)
            }
            if (allCardsShown) {
                completed++
            } else {
                // Stop at first incomplete sub-lesson.
                break
            }
        }
        return completed
    }
    /**
     * Генерирует word bank из правильного ответа
     */
    private fun generateWordBank(correctAnswer: String, extraWords: List<String> = emptyList()): List<String> {
        val words = correctAnswer.split(" ").filter { it.isNotBlank() }
        val extras = extraWords.filter { it.isNotBlank() && !words.contains(it) }
        return (words + extras).shuffled()
    }

    /**
     * Обновляет word bank для текущей карточки
     */
    private fun updateWordBank() {
        val card = _uiState.value.currentCard
        if (card == null) {
            _uiState.update {
                it.copy(
                    wordBankWords = emptyList(),
                    selectedWords = emptyList()
                )
            }
            return
        }

        val correctAnswer = card.acceptedAnswers.firstOrNull() ?: ""
        val correctWords = correctAnswer.split(" ").map { it.trim() }.filter { it.isNotBlank() }
        val normalizedCorrect = correctWords.map { Normalizer.normalize(it) }.toSet()
        val distractorPool = _uiState.value.lessons
            .flatMap { it.cards }
            .filter { it.id != card.id }
            .flatMap { it.acceptedAnswers }
            .flatMap { it.split(" ") }
            .map { it.trim() }
            .filter { it.length >= 3 }
            .filter { Normalizer.normalize(it) !in normalizedCorrect }
            .distinct()
        val extraWords = distractorPool.shuffled().take(3)
        val wordBank = generateWordBank(correctAnswer, extraWords)

        _uiState.update {
            it.copy(
                wordBankWords = wordBank,
                selectedWords = emptyList(),
                inputText = ""
            )
        }
    }

    private fun buildVocabWordBank(entry: VocabEntry, pool: List<VocabEntry>): List<String> {
        val correctOption = entry.targetText.split("+")
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: entry.targetText
        val normalizedCorrect = Normalizer.normalize(correctOption)

        // Собираем все возможные варианты из пула словаря
        val poolOptions = pool
            .flatMap { it.targetText.split("+") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        // Убираем правильный ответ из списка вариантов словаря
        val poolDistractors = poolOptions
            .filter { Normalizer.normalize(it) != normalizedCorrect }
            .shuffled()

        // Начинаем с дистракторов из словаря
        val distractors = poolDistractors.take(4).toMutableList()

        // Если дистракторов недостаточно, добираем из всех уроков
        if (distractors.size < 4) {
            val languageId = _uiState.value.selectedLanguageId
            val allVocabFromLessons = _uiState.value.lessons
                .flatMap { lesson ->
                    lessonStore.getVocabEntries(lesson.id, languageId)
                }
                .flatMap { it.targetText.split("+") }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .filter { Normalizer.normalize(it) != normalizedCorrect }
                .filter { !distractors.contains(it) }
                .shuffled()

            val additionalNeeded = 4 - distractors.size
            distractors.addAll(allVocabFromLessons.take(additionalNeeded))
        }

        // Берём до 4 дистракторов + правильный ответ = до 5 вариантов
        val selectedDistractors = distractors.take(4)
        val result = (listOf(correctOption) + selectedDistractors).shuffled()

        Log.d(logTag, "buildVocabWordBank: entry=${entry.nativeText}, correct=$correctOption, pool=${pool.size}, poolDistractors=${poolDistractors.size}, finalDistractors=${selectedDistractors.size}, result=${result.size}, words=$result")
        return result
    }

    private fun updateVocabWordBank() {
        val entry = _uiState.value.currentVocab ?: return
        val options = buildVocabWordBank(entry, vocabSession)
        _uiState.update { it.copy(vocabWordBankWords = options) }
    }

    /**
     * Добавляет слово в выбранные для word bank режима
     */
    fun selectWordFromBank(word: String) {
        val currentSelected = _uiState.value.selectedWords
        val newSelected = currentSelected + word
        val inputText = newSelected.joinToString(" ")

        _uiState.update {
            it.copy(
                selectedWords = newSelected,
                inputText = inputText
            )
        }
    }

    /**
     * Удаляет последнее выбранное слово
     */
    fun removeLastSelectedWord() {
        val currentSelected = _uiState.value.selectedWords
        if (currentSelected.isEmpty()) return

        val newSelected = currentSelected.dropLast(1)
        val inputText = newSelected.joinToString(" ")

        _uiState.update {
            it.copy(
                selectedWords = newSelected,
                inputText = inputText
            )
        }
    }

    /**
     * Обновить состояния цветков для всех уроков.
     */
    private fun refreshFlowerStates() {
        val languageId = _uiState.value.selectedLanguageId
        val lessons = _uiState.value.lessons
        val nowMs = System.currentTimeMillis()

        val flowerStates = lessons.associate { lesson ->
            val mastery = masteryStore.get(lesson.id, languageId)
            val flower = FlowerCalculator.calculate(mastery, lesson.cards.size)
            Log.d(logTag, "Flower for lesson ${lesson.id}: mastery=${mastery?.uniqueCardShows ?: 0}, state=${flower.state}, scale=${flower.scaleMultiplier}")
            lesson.id to flower
        }

        val currentLessonId = _uiState.value.selectedLessonId
        val currentFlower = currentLessonId?.let { flowerStates[it] }
        val currentShownCount = currentLessonId?.let { lessonId ->
            masteryStore.get(lessonId, languageId)?.shownCardIds?.size ?: 0
        } ?: 0
        val ladderRows = lessons.mapIndexed { index, lesson ->
            val mastery = masteryStore.get(lesson.id, languageId)
            val metrics = LessonLadderCalculator.calculate(mastery, nowMs)
            LessonLadderRow(
                index = index + 1,
                lessonId = lesson.id,
                title = lesson.title,
                uniqueCardShows = metrics.uniqueCardShows,
                daysSinceLastShow = metrics.daysSinceLastShow,
                intervalLabel = metrics.intervalLabel
            )
        }

        _uiState.update {
            it.copy(
                lessonFlowers = flowerStates,
                currentLessonFlower = currentFlower,
                currentLessonShownCount = currentShownCount,
                ladderRows = ladderRows
            )
        }
    }

    /**
     * Обновляет streak после завершения подурока
     */
    private fun updateStreak() {
        val languageId = _uiState.value.selectedLanguageId
        val (updatedStreak, isNewStreak) = streakStore.recordSubLessonCompletion(languageId)

        if (isNewStreak && updatedStreak.currentStreak > 0) {
            // Генерируем сообщение о streak
            val message = when {
                updatedStreak.currentStreak == 1 -> "\uD83D\uDD25 Great start! Day 1 streak!"
                updatedStreak.currentStreak == 3 -> "\uD83D\uDD25 3 days streak! You're on fire!"
                updatedStreak.currentStreak == 7 -> "\uD83D\uDD25 7 days streak! One week! Amazing!"
                updatedStreak.currentStreak == 14 -> "\uD83D\uDD25 14 days streak! Two weeks! Incredible!"
                updatedStreak.currentStreak == 30 -> "\uD83D\uDD25 30 days streak! One month! Outstanding!"
                updatedStreak.currentStreak == 100 -> "\uD83D\uDD25 100 days streak! You're a legend!"
                updatedStreak.currentStreak % 10 == 0 -> "\uD83D\uDD25 ${updatedStreak.currentStreak} days streak! Keep it up!"
                else -> "\uD83D\uDD25 ${updatedStreak.currentStreak} days streak!"
            }

            _uiState.update {
                it.copy(
                    currentStreak = updatedStreak.currentStreak,
                    longestStreak = updatedStreak.longestStreak,
                    streakMessage = message,
                    streakCelebrationToken = it.streakCelebrationToken + 1
                )
            }
        } else {
            // Просто обновляем streak без сообщения
            _uiState.update {
                it.copy(
                    currentStreak = updatedStreak.currentStreak,
                    longestStreak = updatedStreak.longestStreak
                )
            }
        }
    }

    /**
     * Закрывает сообщение о streak
     */
    fun dismissStreakMessage() {
        _uiState.update {
            it.copy(streakMessage = null)
        }
    }

    /**
     * Create a backup of current progress data.
     * Should be called periodically to ensure data is not lost on uninstall.
     */
    fun createProgressBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = backupManager.createBackup()
                Log.d(logTag, "Progress backup created: success=$success")
            } catch (e: Exception) {
                Log.e(logTag, "Failed to create progress backup", e)
            }
        }
    }

    /**
     * Update user name in profile and save to storage.
     */
    fun updateUserName(newName: String) {
        val trimmed = newName.trim().take(50)
        if (trimmed.isEmpty()) return

        val profile = UserProfile(userName = trimmed)
        profileStore.save(profile)

        _uiState.update {
            it.copy(userName = trimmed)
        }
    }

    fun saveProgressNow() {
        Log.d(logTag, "Manual progress save requested from settings")
        forceBackupOnSave = true
        saveProgress()
    }

    /**
     * Restore user progress from backup folder.
     */
    fun restoreBackup(backupUri: android.net.Uri) {
        Log.d(logTag, "=== Starting Backup Restore ===")
        Log.d(logTag, "Backup URI: $backupUri")

        val success = backupManager.restoreFromBackupUri(backupUri)
        Log.d(logTag, "Backup restore result: $success")

        if (success) {
            // Reload all data after restore
            Log.d(logTag, "--- Loading Progress Data ---")
            val progress = progressStore.load()
            Log.d(logTag, "Progress: languageId=${progress.languageId}, lessonId=${progress.lessonId}, mode=${progress.mode}")
            Log.d(logTag, "Progress: currentIndex=${progress.currentIndex}, correctCount=${progress.correctCount}, incorrectCount=${progress.incorrectCount}")
            Log.d(logTag, "Progress: state=${progress.state}, bossRewards=${progress.bossLessonRewards.size}")

            Log.d(logTag, "--- Loading Profile Data ---")
            val profile = profileStore.load()
            Log.d(logTag, "Profile: userName=${profile.userName}")

            val selectedLanguageId = progress.languageId ?: "en"
            Log.d(logTag, "Selected language: $selectedLanguageId")

            Log.d(logTag, "--- Loading Lessons ---")
            val lessons = lessonStore.getLessons(selectedLanguageId)
            Log.d(logTag, "Loaded ${lessons.size} lessons for language $selectedLanguageId")

            val selectedLessonId = progress.lessonId ?: lessons.firstOrNull()?.id
            Log.d(logTag, "Selected lesson: $selectedLessonId")

            Log.d(logTag, "--- Loading Streak Data ---")
            val streak = streakStore.getCurrentStreak(selectedLanguageId)
            Log.d(logTag, "Streak: current=${streak.currentStreak}, longest=${streak.longestStreak}, totalCompleted=${streak.totalSubLessonsCompleted}")

            // Parse boss rewards from strings to BossReward enum
            Log.d(logTag, "--- Parsing Boss Rewards ---")
            val bossLessonRewards = progress.bossLessonRewards.mapNotNull { (lessonId, reward) ->
                val parsed = runCatching { BossReward.valueOf(reward) }.getOrNull() ?: return@mapNotNull null
                Log.d(logTag, "Boss reward: $lessonId -> $parsed")
                lessonId to parsed
            }.toMap()
            val bossMegaReward = progress.bossMegaReward?.let { reward ->
                runCatching { BossReward.valueOf(reward) }.getOrNull()
            }
            Log.d(logTag, "Boss Mega reward: $bossMegaReward")

            Log.d(logTag, "--- Updating UI State ---")
            _uiState.update {
                it.copy(
                    selectedLanguageId = selectedLanguageId,
                    lessons = lessons,
                    selectedLessonId = selectedLessonId,
                    mode = progress.mode,
                    sessionState = progress.state,
                    currentIndex = progress.currentIndex,
                    correctCount = progress.correctCount,
                    incorrectCount = progress.incorrectCount,
                    incorrectAttemptsForCard = progress.incorrectAttemptsForCard,
                    activeTimeMs = progress.activeTimeMs,
                    voiceActiveMs = progress.voiceActiveMs,
                    voiceWordCount = progress.voiceWordCount,
                    hintCount = progress.hintCount,
                    currentStreak = streak.currentStreak,
                    longestStreak = streak.longestStreak,
                    bossLessonRewards = bossLessonRewards,
                    bossMegaReward = bossMegaReward,
                    userName = profile.userName,
                    eliteStepIndex = progress.eliteStepIndex.coerceIn(0, eliteStepCount - 1),
                    eliteBestSpeeds = normalizeEliteSpeeds(progress.eliteBestSpeeds)
                )
            }
            Log.d(logTag, "UI state updated")

            // Rebuild session and schedules
            Log.d(logTag, "--- Rebuilding Schedules ---")
            rebuildSchedules(lessons)
            Log.d(logTag, "--- Building Session Cards ---")
            buildSessionCards()
            Log.d(logTag, "--- Refreshing Flower States ---")
            refreshFlowerStates()

            Log.d(logTag, "=== Backup Restore Complete ===")
            Log.d(logTag, "Summary: userName=${profile.userName}, lessons=${lessons.size}, lessonId=${selectedLessonId}, streak=${streak.currentStreak}")
        } else {
            Log.e(logTag, "Failed to restore backup - check restore_log.txt in backup folder")
        }
    }

    fun reloadFromDisk() {
        viewModelScope.launch(Dispatchers.IO) {
            val progress = progressStore.load()
            val profile = profileStore.load()
            val languages = lessonStore.getLanguages()
            val packs = lessonStore.getInstalledPacks()
            val selectedLanguageId = languages.firstOrNull { it.id == progress.languageId }?.id
                ?: languages.firstOrNull()?.id
                ?: "en"
            val lessons = lessonStore.getLessons(selectedLanguageId)
            val selectedLessonId = progress.lessonId ?: lessons.firstOrNull()?.id
            val streak = streakStore.getCurrentStreak(selectedLanguageId)

            val bossLessonRewards = progress.bossLessonRewards.mapNotNull { (lessonId, reward) ->
                val parsed = runCatching { BossReward.valueOf(reward) }.getOrNull() ?: return@mapNotNull null
                lessonId to parsed
            }.toMap()
            val bossMegaReward = progress.bossMegaReward?.let { reward ->
                runCatching { BossReward.valueOf(reward) }.getOrNull()
            }
            val normalizedEliteSpeeds = normalizeEliteSpeeds(progress.eliteBestSpeeds)

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        languages = languages,
                        installedPacks = packs,
                        selectedLanguageId = selectedLanguageId,
                        lessons = lessons,
                        selectedLessonId = selectedLessonId,
                        mode = progress.mode,
                        sessionState = progress.state,
                        currentIndex = progress.currentIndex,
                        correctCount = progress.correctCount,
                        incorrectCount = progress.incorrectCount,
                        incorrectAttemptsForCard = progress.incorrectAttemptsForCard,
                        activeTimeMs = progress.activeTimeMs,
                        voiceActiveMs = progress.voiceActiveMs,
                        voiceWordCount = progress.voiceWordCount,
                        hintCount = progress.hintCount,
                        currentStreak = streak.currentStreak,
                        longestStreak = streak.longestStreak,
                        bossLessonRewards = bossLessonRewards,
                        bossMegaReward = bossMegaReward,
                        userName = profile.userName,
                        eliteStepIndex = progress.eliteStepIndex.coerceIn(0, eliteStepCount - 1),
                        eliteBestSpeeds = normalizedEliteSpeeds,
                        eliteUnlocked = resolveEliteUnlocked(lessons, it.testMode)
                    )
                }
                rebuildSchedules(lessons)
                buildSessionCards()
                refreshFlowerStates()
            }
        }
    }
}

data class SubmitResult(
    val accepted: Boolean,
    val hintShown: Boolean
)

data class TrainingUiState(
    val languages: List<com.alexpo.grammermate.data.Language> = emptyList(),
    val installedPacks: List<com.alexpo.grammermate.data.LessonPack> = emptyList(),
    val selectedLanguageId: String = "en",
    val lessons: List<Lesson> = emptyList(),
    val selectedLessonId: String? = null,
    val mode: TrainingMode = TrainingMode.LESSON,
    val sessionState: SessionState = SessionState.ACTIVE,
    val currentIndex: Int = 0,
    val currentCard: SentenceCard? = null,
    val inputText: String = "",
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val incorrectAttemptsForCard: Int = 0,
    val activeTimeMs: Long = 0L,
    val voiceActiveMs: Long = 0L,
    val voiceWordCount: Int = 0,
    val hintCount: Int = 0,
    val voicePromptStartMs: Long? = null,
    val answerText: String? = null,
    val lastResult: Boolean? = null,
    val lastRating: Double? = null,
    val inputMode: InputMode = InputMode.VOICE,
    val voiceTriggerToken: Int = 0,
    val subLessonTotal: Int = 0,
    val subLessonCount: Int = 0,
    val subLessonTypes: List<SubLessonType> = emptyList(),
    val activeSubLessonIndex: Int = 0,
    val completedSubLessonCount: Int = 0,
    val subLessonFinishedToken: Int = 0,
    val storyCheckInDone: Boolean = false,
    val storyCheckOutDone: Boolean = false,
    val activeStory: StoryQuiz? = null,
    val storyErrorMessage: String? = null,
    val currentVocab: VocabEntry? = null,
    val vocabInputText: String = "",
    val vocabAttempts: Int = 0,
    val vocabAnswerText: String? = null,
    val vocabIndex: Int = 0,
    val vocabTotal: Int = 0,
    val vocabWordBankWords: List<String> = emptyList(),
    val vocabFinishedToken: Int = 0,
    val vocabErrorMessage: String? = null,
    val vocabInputMode: InputMode = InputMode.VOICE,
    val vocabVoiceTriggerToken: Int = 0,
    val bossActive: Boolean = false,
    val bossType: BossType? = null,
    val bossTotal: Int = 0,
    val bossProgress: Int = 0,
    val bossReward: BossReward? = null,
    val bossRewardMessage: String? = null,
    val bossFinishedToken: Int = 0,
    val bossLastType: BossType? = null,
    val bossErrorMessage: String? = null,
    val bossLessonRewards: Map<String, BossReward> = emptyMap(),
    val bossMegaReward: BossReward? = null,
    val testMode: Boolean = false,
    val eliteActive: Boolean = false,
    val eliteStepIndex: Int = 0,
    val eliteBestSpeeds: List<Double> = emptyList(),
    val eliteFinishedToken: Int = 0,
    val eliteUnlocked: Boolean = false,
    val eliteSizeMultiplier: Double = 1.25,
    val vocabSprintLimit: Int = 20,
    // Flower mastery states
    val lessonFlowers: Map<String, FlowerVisual> = emptyMap(),
    val currentLessonFlower: FlowerVisual? = null,
    val currentLessonShownCount: Int = 0,
    // Word bank mode
    val wordBankWords: List<String> = emptyList(),
    val selectedWords: List<String> = emptyList(),
    // Streak tracking
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val streakMessage: String? = null,
    val streakCelebrationToken: Int = 0,
    // User profile
    val userName: String = "GrammarMateUser",
    val ladderRows: List<LessonLadderRow> = emptyList()
)

data class LessonLadderRow(
    val index: Int,
    val lessonId: String,
    val title: String,
    val uniqueCardShows: Int?,
    val daysSinceLastShow: Int?,
    val intervalLabel: String?
)

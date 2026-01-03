package com.alexpo.grammermate.data

data class Language(
    val id: String,
    val displayName: String
)

data class Lesson(
    val id: String,
    val languageId: String,
    val title: String,
    val cards: List<SentenceCard>
)

data class SentenceCard(
    val id: String,
    val promptRu: String,
    val acceptedAnswers: List<String>
)

enum class TrainingMode {
    LESSON,
    ALL_SEQUENTIAL,
    ALL_MIXED
}

enum class SessionState {
    ACTIVE,
    PAUSED,
    AFTER_CHECK,
    HINT_SHOWN
}

data class TrainingProgress(
    val languageId: String = "en",
    val mode: TrainingMode = TrainingMode.LESSON,
    val lessonId: String? = null,
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val incorrectAttemptsForCard: Int = 0,
    val activeTimeMs: Long = 0L,
    val state: SessionState = SessionState.ACTIVE
)

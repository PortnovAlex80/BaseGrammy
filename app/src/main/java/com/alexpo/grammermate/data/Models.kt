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

data class VocabEntry(
    val id: String,
    val lessonId: String,
    val languageId: String,
    val nativeText: String,
    val targetText: String,
    val isHard: Boolean = false
)

data class LessonPack(
    val packId: String,
    val packVersion: String,
    val languageId: String,
    val importedAt: Long
)

enum class StoryPhase {
    CHECK_IN,
    CHECK_OUT
}

data class StoryQuestion(
    val qId: String,
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    val explain: String? = null
)

data class StoryQuiz(
    val storyId: String,
    val lessonId: String,
    val phase: StoryPhase,
    val text: String,
    val questions: List<StoryQuestion>
)

enum class TrainingMode {
    LESSON,
    ALL_SEQUENTIAL,
    ALL_MIXED
}

enum class BossType {
    LESSON,
    MEGA
}

enum class BossReward {
    BRONZE,
    SILVER,
    GOLD
}

enum class SessionState {
    ACTIVE,
    PAUSED,
    AFTER_CHECK,
    HINT_SHOWN
}

enum class InputMode {
    VOICE,
    KEYBOARD
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
    val state: SessionState = SessionState.PAUSED,
    val bossLessonRewards: Map<String, String> = emptyMap(),
    val bossMegaReward: String? = null
)

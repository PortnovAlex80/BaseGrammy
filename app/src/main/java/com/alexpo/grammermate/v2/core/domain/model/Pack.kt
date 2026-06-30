package com.alexpo.grammermate.v2.core.domain.model

/**
 * Domain models — pure Kotlin, zero Android dependencies.
 * Тестируется на чистом JVM. Это источник истины для всех слоёв.
 *
 * Идентификаторы — value classes (type-safety: нельзя перепутать PackId с LessonId).
 */

@JvmInline
value class PackId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class LessonId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class CardId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class LanguageId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class ChapterId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class SessionId(val value: String) {
    override fun toString(): String = value

    companion object {
        /** Стабильный ID сессии по режиму + паку (+ уроку) — возобновляемый. */
        fun forLesson(packId: PackId, lessonId: LessonId): SessionId =
            SessionId("lesson:${packId.value}:${lessonId.value}")
        fun forVerbDrill(packId: PackId): SessionId = SessionId("verb_drill:${packId.value}")
        fun forDailyTranslate(packId: PackId): SessionId = SessionId("daily_translate:${packId.value}")
        fun forDailyVerbs(packId: PackId): SessionId = SessionId("daily_verbs:${packId.value}")
        fun forAuxDrill(packId: PackId): SessionId = SessionId("aux_drill:${packId.value}")
        fun forPomodoro(packId: PackId): SessionId = SessionId("pomodoro:${packId.value}")
    }
}

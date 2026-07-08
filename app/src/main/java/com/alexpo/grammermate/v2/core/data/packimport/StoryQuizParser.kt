package com.alexpo.grammermate.v2.core.data.packimport

import com.alexpo.grammermate.domain.model.StoryPhase
import com.alexpo.grammermate.domain.model.StoryQuiz

/**
 * Парсер Story JSON → [StoryQuiz] (SRS-002 FR-5, AC-10).
 *
 * Story-импорт использует **доменный** `MultilingualStoryParser` (E01 FR-12,
 * `:domain/story/`, pure Kotlin) для разворачивания разметки `{it}…{/it}`, `{en}`,
 * `{ru}`, … в `Segment.Text`/`Segment.Pause(ms)`, и **этот** парсер для
 * JSON-структуры квиза:
 * - `storyId`, `lessonId`, `phase: StoryPhase.CHECK_IN|CHECK_OUT`, `text`, `questions: List<StoryQuestion>`.
 * - Каждый `StoryQuestion` = `qId, prompt, options: List<String>, correctIndex, explain?`.
 * - Валидация: `correctIndex in options.indices`, иначе [ParseError.InvalidFormat].
 *
 * SCAFFOLD: контракт зафиксирован SRS-002 FR-5; реализация — TODO (AC-10).
 * Legacy-тест `StoryQuizParserTest` переносится в v2 без изменения утверждений (NFR-6).
 * Доменный `MultilingualStoryParser` уже существует в `:domain` (E01) — data-слой
 * только вызывает `MultilingualStoryParser.parseSegments(content, defaultLanguageId)`.
 *
 * @see <a href="../../../../../../../../docs/requirements/REQ-002-data-room/02-srs.md">SRS-002 FR-5</a>
 */
object StoryQuizParser {

    /**
     * Распарсить Story JSON в [StoryQuiz].
     *
     * @param text JSON-текст стори (один `.json` файл из пака, кроме `manifest.json`).
     * @return [ParseResult] с `StoryQuiz`; failure — если JSON невалиден или `correctIndex` вне диапазона.
     */
    fun parse(text: String): ParseResult<StoryQuiz, ParseError> {
        TODO("AC-10: реализовать StoryQuizParser.parse (legacy 1:1, regression-locked)")
    }

    /**
     * Фаза стори из строки JSON (`"CHECK_IN"` / `"CHECK_OUT"`).
     *
     * SCAFFOLD: helper для body-задачи; по умолчанию маппит неизвестные строки в `CHECK_IN`.
     */
    fun parsePhase(raw: String?): StoryPhase {
        TODO("AC-10: реализовать StoryQuizParser.parsePhase — map JSON string → StoryPhase")
    }
}

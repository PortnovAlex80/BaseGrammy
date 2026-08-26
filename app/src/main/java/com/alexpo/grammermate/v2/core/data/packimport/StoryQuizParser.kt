package com.alexpo.grammermate.v2.core.data.packimport

import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.StoryPhase
import com.alexpo.grammermate.domain.model.StoryQuestion
import com.alexpo.grammermate.domain.model.StoryQuiz
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Парсер Story JSON → [StoryQuiz] (SRS-002 FR-5, AC-10; срез 6 Фазы 4).
 *
 * JSON-структура квиза:
 * - `storyId`, `lessonId`, `phase: "CHECK_IN"|"CHECK_OUT"`, `text`, `questions: List<StoryQuestion>`.
 * - Каждый `StoryQuestion` = `qId, prompt, options: List<String>, correctIndex, explain?`.
 *
 * Валидация (строгая — фикс аудита M-10):
 * - неизвестная `phase` → [ParseError.InvalidFormat], а НЕ молчаливый CHECK_IN
 *   (финальный квиз главы не должен незаметно превращаться во вводный);
 * - `correctIndex` вне `options.indices` → [ParseError.InvalidFormat];
 * - пустые `options`/`questions`, пустые `storyId`/`lessonId` → InvalidFormat.
 *
 * Story-разметка (`{it}…{/it}`, `{pause:N}`) в `text` НЕ разворачивается здесь —
 * это делает доменный `MultilingualStoryParser` при рендере/TTS (E01 FR-12).
 */
object StoryQuizParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Распарсить Story JSON в [StoryQuiz].
     *
     * @param text JSON-текст стори (один `.json` файл из пака, кроме `manifest.json`).
     * @return [ParseResult.Success] с квизом; [ParseResult.Failure] — битый JSON,
     *         неверная структура, неизвестная фаза или `correctIndex` вне диапазона.
     */
    fun parse(text: String): ParseResult<StoryQuiz, ParseError> {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }
            .getOrElse {
                return ParseResult.failure(
                    listOf(ParseError.InvalidFormat(reason = "Story JSON не распознан: ${it.message}"))
                )
            }

        val storyId = root.stringField("storyId")
            ?: return invalid("отсутствует/пусто 'storyId'")
        val lessonId = root.stringField("lessonId")
            ?: return invalid("отсутствует/пусто 'lessonId'")
        val phase = parsePhase(root.stringField("phase"))
            ?: return invalid("неизвестная 'phase' (ожидается CHECK_IN|CHECK_OUT): ${root.stringField("phase")}")
        val storyText = root.stringField("text")
            ?: return invalid("отсутствует/пусто 'text'")

        val questionsArray = runCatching { root["questions"]?.jsonArray }
            .getOrElse { return invalid("'questions' не является массивом") }
            ?: return invalid("отсутствует 'questions'")
        if (questionsArray.isEmpty()) return invalid("'questions' пуст")

        val questions = mutableListOf<StoryQuestion>()
        questionsArray.forEachIndexed { index, element ->
            val q = runCatching { element.jsonObject }.getOrElse {
                return invalid("question[$index]: не объект")
            }
            val qId = q.stringField("qId") ?: return invalid("question[$index]: отсутствует 'qId'")
            val prompt = q.stringField("prompt") ?: return invalid("question[$index]: отсутствует 'prompt'")
            val options = runCatching { q["options"]?.jsonArray }
                .getOrElse { return invalid("question[$index]: 'options' не массив") }
                ?: return invalid("question[$index]: отсутствует 'options'")
            val optionTexts = options.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
            if (optionTexts.isEmpty()) return invalid("question[$index]: 'options' пуст")
            val correct = q["correctIndex"]?.let {
                runCatching { it.jsonPrimitive.content.toIntOrNull() }.getOrNull()
            } ?: return invalid("question[$index]: отсутствует/не число 'correctIndex'")
            if (correct !in optionTexts.indices) {
                return invalid("question[$index]: correctIndex=$correct вне options.indices=0..${optionTexts.lastIndex}")
            }
            questions += StoryQuestion(
                qId = qId,
                prompt = prompt,
                options = optionTexts,
                correctIndex = correct,
                explain = q.stringField("explain"),
            )
        }

        return ParseResult.Success(
            StoryQuiz(
                storyId = storyId,
                lessonId = LessonId(lessonId),
                phase = phase,
                text = storyText,
                questions = questions,
            )
        )
    }

    /**
     * Фаза стори из строки JSON (`"CHECK_IN"` / `"CHECK_OUT"`).
     *
     * СТРОГО (фикс M-10): неизвестное значение → null → вызывающий возвращает
     * [ParseError.InvalidFormat]; молчаливый маппинг в CHECK_IN запрещён.
     */
    fun parsePhase(raw: String?): StoryPhase? = when (raw?.trim()?.uppercase()) {
        "CHECK_IN" -> StoryPhase.CHECK_IN
        "CHECK_OUT" -> StoryPhase.CHECK_OUT
        else -> null
    }

    // ── Хелперы ──────────────────────────────────────────────────────────────

    private fun invalid(reason: String): ParseResult.Failure<StoryQuiz, ParseError> =
        ParseResult.Failure(listOf(ParseError.InvalidFormat(reason = reason)))

    private fun JsonObject.stringField(key: String): String? =
        this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }?.takeIf { it.isNotBlank() }
}

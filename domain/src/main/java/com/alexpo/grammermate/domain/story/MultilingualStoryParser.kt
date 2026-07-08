package com.alexpo.grammermate.domain.story

import com.alexpo.grammermate.domain.model.Segment
import com.alexpo.grammermate.domain.model.StoryPhase
import com.alexpo.grammermate.domain.model.StoryQuestion
import com.alexpo.grammermate.domain.model.StoryQuiz

/**
 * Parser for multilingual story content with language-specific insertions.
 *
 * Pure-Kotlin domain object (gap #1, FR-12 / AC-15): мигрирован из data-слоя
 * (`app/.../data/MultilingualStoryParser.kt`) без Android-логирования и
 * платформенных IO-типов. Возвращает доменные модели из [com.alexpo.grammermate.domain.model]
 * ([Segment], [StoryQuiz], [StoryQuestion]). Логирование — забота data/UI-слоя;
 * путь к аудиофайлу — [String].
 *
 * Поддерживает маркеры `{it}Italian text{/it}` и т. п. внутри story-контента.
 * Парсер делит текст на сегменты с привязанным языком.
 *
 * Дополнительно поддерживает маркеры `{pause:N}` (N в миллисекундах), которые
 * порождают [Segment.Pause] в [parseSegments]. Legacy entry-point [parseStory]
 * фильтрует паузы, так что существующее воспроизведение story не меняется.
 *
 * Пример:
 * ```
 * This is English. {it}Questo è italiano.{/it} Back to English.
 * ```
 *
 * Даёт:
 * - Segment.Text("This is English. ", "en")
 * - Segment.Text("Questo è italiano.", "it")
 * - Segment.Text(" Back to English.", "en")
 */
object MultilingualStoryParser {

    /**
     * Представляет текстовый сегмент с привязанным языком.
     *
     * Сохранён как top-level data class для обратной совместимости с
     * существующими вызовами [parseStory]. Новые вызовы должны предпочитать
     * [Segment.Text] через [parseSegments].
     */
    data class TextSegment(
        val text: String,
        val languageId: String,
    )

    /**
     * Шаблон языкового маркера: `{lang}content{/lang}`.
     * Поддерживаемые языки: it, en, ru, el, de (немецкий), zh (китайский).
     */
    private val languagePattern =
        Regex("""\{(it|en|ru|el|de|zh)\}(.+?)\{/\1\}""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Шаблон маркера паузы: `{pause:N}`, где N — положительное целое мс.
     * Используется скриптами background-vocab для вставки явных задержек между
     * TTS-сегментами.
     */
    private val pausePattern = Regex("""\{pause:(\d+)\}""")

    /**
     * Определить язык текста по символьному анализу и словесным шаблонам.
     * Возвращает "ru", "en", "it", "el", "de", "zh" либо [defaultLanguageId].
     *
     * Pure-функция (мигрирована из data-слоя, платформенное логирование убрано).
     */
    fun detectLanguage(text: String, defaultLanguageId: String): String {
        val sample = text.take(500) // Анализируем первые 500 символов

        // Подсчёт символьных шаблонов для каждого языка
        val ruChars = sample.count { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
        val enChars = sample.count { it in 'a'..'z' || it in 'A'..'Z' }
        val itChars = sample.count { it in "àèéìòùÀÈÉÌÒÙ" } // Частые итальянские акценты
        val elChars = sample.count { it in 'Ͱ'..'Ͽ' || it in 'ἀ'..'῿' } // Greek and Coptic / Greek Extended
        val deChars = sample.count { it in "äöüßÄÖÜ" } // Немецкие спецсимволы
        val zhChars = sample.count { it.code in 0x4E00..0x9FFF } // CJK Unified Ideographs

        // Подсчёт частых итальянских слов для лучшего определения
        val italianWords = listOf("questo", "quella", "questa", "essere", "avere", "per", "con", "da", "il", "lo", "la", "le", "un", "uno", "una", "in", "su", "a", "ad", "da", "di", "del", "dello", "della", "dei", "degli", "delle", "su", "sul", "sullo", "sulla", "sui", "sugli", "sulle", "tra", "fra", "anche", "ancora", "caso", "cosa", "fare", "dire", "vedere", "parlare", "essere")
        val italianWordMatches = italianWords.count { word -> sample.contains(word, ignoreCase = true) }

        // Подсчёт частых греческих слов
        val greekWords = listOf("είμαι", "έχω", "είναι", "αυτό", "αυτή", "αυτός", "που", "με", "σε", "για", "το", "η", "τα", "τις", "τα", "και", "δεν", "στα", "στην", "στον", "μια", "ένα", "να", "με", "θα", "είναι", "μπορώ", "πρέπει", "ελληνικά")
        val greekWordMatches = greekWords.count { word -> sample.contains(word, ignoreCase = true) }

        // Немецкие частые слова
        val germanWords = listOf("und", "der", "die", "das", "ist", "ein", "eine", "nicht", "ich", "mit", "auf", "für", "sich", "auch", "als", "nach", "wie", "noch", "werden", "haben", "sein", "dieser", "welche", "mich", "dich", "sich", "uns", "euch", "mein", "dein", "kein", "werden", "wurde", "worden", "konnte", "gemacht", "gegangen")
        val germanWordMatches = germanWords.count { word -> sample.contains(word, ignoreCase = true) }

        // Частые английские слова (исключают итальянский)
        @Suppress("ktlint:standard:max-line-length")
        val englishWords = listOf("this", "that", "with", "from", "have", "been", "will", "would", "could", "should", "about", "which", "their", "there", "where", "when", "what", "how", "then", "than", "more", "some", "such", "only", "into", "over", "after", "before", "being", "under", "while", "because", "though", "until", "again", "where", "through", "each", "much", "own", "same", "so", "good", "new", "first", "last", "long", "great", "little", "own", "other", "old", "right", "big", "high", "different", "small", "large", "next", "early", "young", "important", "public", "bad", "able", "free", "best", "better", "during", "enough", "both", "full", "tonight", "always", "anything", "anywhere", "being", "beautiful", "before", "believe", "between", "both", "bring", "build", "business", "but", "by", "call", "came", "can", "come", "could", "course", "develop", "different", "do", "does", "done", "don", "down", "during", "early", "education", "enough", "even", "ever", "every", "example", "face", "family", "far", "fast", "field", "fight", "find", "first", "for", "from", "get", "give", "go", "good", "great", "group", "grow", "had", "has", "have", "he", "head", "help", "her", "here", "high", "history", "home", "how", "however", "if", "important", "in", "include", "into", "is", "it", "its", "just", "keep", "know", "large", "last", "late", "learn", "leave", "life", "like", "line", "little", "long", "look", "make", "man", "many", "may", "me", "member", "might", "mile", "million", "miss", "more", "most", "much", "music", "must", "my", "name", "never", "new", "news", "next", "night", "no", "not", "now", "of", "off", "often", "old", "on", "once", "one", "only", "or", "other", "our", "out", "over", "own", "part", "people", "place", "play", "point", "political", "possible", "present", "president", "problem", "program", "provide", "public", "purpose", "question", "rather", "really", "result", "return", "right", "run", "same", "say", "school", "second", "see", "seem", "see", "service", "set", "several", "should", "since", "small", "so", "social", "some", "something", "special", "start", "statement", "still", "such", "system", "take", "talk", "teach", "tell", "than", "that", "the", "their", "them", "then", "there", "these", "they", "thing", "think", "this", "those", "though", "three", "through", "time", "to", "today", "together", "too", "toward", "travel", "try", "turn", "two", "under", "understand", "unit", "until", "up", "upon", "use", "usually", "value", "very", "want", "way", "we", "week", "well", "west", "what", "whatever", "when", "where", "whether", "which", "while", "white", "who", "whole", "whose", "why", "will", "with", "within", "without", "word", "work", "world", "would", "write", "year", "you", "your", "yours")
        val englishWordMatches = englishWords.count { word -> sample.contains(word, ignoreCase = true) }

        val total = ruChars + enChars + itChars + elChars + deChars + zhChars
        if (total == 0) {
            return defaultLanguageId
        }

        val ruRatio = ruChars.toFloat() / total
        val enRatio = enChars.toFloat() / total
        val itRatio = itChars.toFloat() / total
        val elRatio = elChars.toFloat() / total
        val deRatio = deChars.toFloat() / total

        // Усиленное определение со словесным анализом
        return when {
            // Китайский (CJK-символы очень специфичны)
            zhChars >= 3 -> "zh"

            // Греческий (греческий скрипт очень специфичен)
            elChars >= 3 && greekWordMatches >= 1 -> "el"
            greekWordMatches >= 3 -> "el"
            elRatio > 0.15f && elChars >= 5 -> "el"

            // Итальянский (ВЫСШИЙ ПРИОРИТЕТ — проверять первым)
            // Несколько итальянских слов — это итальянский независимо от кириллицы
            italianWordMatches >= 3 && italianWordMatches > englishWordMatches -> "it"
            italianWordMatches >= 5 -> "it"
            itChars >= 3 && italianWordMatches >= 1 -> "it"
            itRatio > 0.2f && itChars >= 2 -> "it"

            // Немецкий (умляуты + частые немецкие слова)
            deChars >= 2 && germanWordMatches >= 2 -> "de"
            germanWordMatches >= 5 -> "de"
            deRatio > 0.15f && deChars >= 2 -> "de"

            // Русский (кириллица очень специфична)
            ruRatio > 0.1f && ruChars > 10 -> "ru"

            // Английский (по умолчанию для латиницы)
            enRatio > 0.3f -> "en"

            // Фолбэк на default
            else -> defaultLanguageId
        }
    }

    /**
     * Разобрать story-контент и извлечь языково-специфичные сегменты.
     * Авто-определение языка абзацев для корректной обработки языка по умолчанию.
     *
     * @param content полный story-контент (может содержать markdown)
     * @param defaultLanguageId язык по умолчанию для текста без маркеров (напр. "en", "ru")
     * @return список текстовых сегментов с привязанными языками
     */
    fun parseStory(content: String, defaultLanguageId: String = "en"): List<TextSegment> {
        if (content.isBlank()) return emptyList()

        // Делегируем в parseSegments и фильтруем паузы. Story сейчас не содержит
        // маркеров {pause:N}, так что поведение идентично предыдущему.
        // Сохранён legacy-тип TextSegment для существующих вызовов (AudioCoordinator и т. п.).
        return parseSegments(content, defaultLanguageId)
            .mapNotNull { segment ->
                when (segment) {
                    is Segment.Text -> TextSegment(segment.text, segment.languageId)
                    is Segment.Pause -> null
                    // Аудиоклипы не несут озвучиваемого текста; пропускаются на
                    // текстовом story-пути. (parseSegments сегодня не порождает
                    // Audio — story не содержит аудио-разметки — но ветка нужна
                    // для исчерпываемости.)
                    is Segment.Audio -> null
                }
            }
    }

    /**
     * Разобрать контент в смешанный поток [Segment.Text] и [Segment.Pause].
     *
     * Та же логика абзацев/языковых маркеров, что и в [parseStory], но дополнительно
     * распознаёт маркеры `{pause:N}` и порождает [Segment.Pause] в корректной
     * позиции потока (между окружающими текстовыми сегментами).
     *
     * Пример: `{it}casa{/it}{pause:300}{ru}дом{/ru}` даёт
     * `[Text("casa","it"), Pause(300), Text("дом","ru")]`.
     *
     * Текстовые сегменты тримятся, применяются те же правила автоопределения
     * языка по умолчанию, что и в [parseStory]. Порядок пауз сохраняется точно.
     *
     * @param content контент с разметкой (markdown, `{lang}…{/lang}`, `{pause:N}`)
     * @param defaultLanguageId язык по умолчанию для текста без маркеров
     * @return упорядоченный список [Segment]'ов (текст + паузы, чередуясь)
     */
    fun parseSegments(content: String, defaultLanguageId: String = "en"): List<Segment> {
        if (content.isBlank()) return emptyList()

        // Нормализуем CRLF → LF для консистентного разбиения на абзацы
        val normalized = content.replace("\r\n", "\n")

        val segments = mutableListOf<Segment>()

        // Разбиваем на абзацы (двойной newline или markdown-заголовки)
        val paragraphSplitRegex = Regex("""(\n\n+|^#{1,6}\s+.*$)""", RegexOption.MULTILINE)
        val paragraphs = normalized.split(paragraphSplitRegex)

        for (paragraph in paragraphs) {
            if (paragraph.isBlank()) continue

            // Найти все языковые маркеры внутри абзаца
            val langMatches = languagePattern.findAll(paragraph).toList()

            if (langMatches.isEmpty()) {
                // Языковых маркеров нет. Определяем язык всего абзаца, но всё
                // равно разбиваем маркеры {pause:N}, чтобы паузо-только или
                // паузо-чередующийся абзац порождал Pause-сегменты.
                val paraLang = detectLanguage(paragraph, defaultLanguageId)
                emitTextWithPauses(paragraph, paraLang, segments)
            } else {
                // Есть языковые маркеры — немаркированный текст в defaultLanguageId.
                // detectLanguage() на сыром абзаце с {it}-тегами искажает в сторону итальянского.
                val paraLang = defaultLanguageId

                // Идём по абзацу слева направо, чередуя паузы с текстом.
                // Для каждого зазора между маркерами (и до/после серии маркеров)
                // выдаём текст через emitTextWithPauses, чтобы паузы внутри зазоров
                // default-языка сохранялись. Внутренний текст маркеров тоже идёт
                // через emitTextWithPauses, чтобы {pause:N} внутри маркеров
                // (редко, но возможно) тоже обрабатывался.
                var lastIndex = 0

                for (match in langMatches) {
                    val (_, langId, segmentText) = match.groupValues
                    val startIndex = match.range.first

                    // Текст до этого маркера принадлежит языку абзаца.
                    if (startIndex > lastIndex) {
                        val beforeText = paragraph.substring(lastIndex, startIndex)
                        if (beforeText.isNotBlank()) {
                            emitTextWithPauses(beforeText, paraLang, segments)
                        }
                    }

                    // Языково-специфичный сегмент.
                    if (segmentText.isNotBlank()) {
                        emitTextWithPauses(segmentText, langId, segments)
                    }

                    lastIndex = match.range.last + 1
                }

                // Оставшийся текст после последнего маркера.
                if (lastIndex < paragraph.length) {
                    val afterText = paragraph.substring(lastIndex)
                    if (afterText.isNotBlank()) {
                        emitTextWithPauses(afterText, paraLang, segments)
                    }
                }
            }
        }

        return segments
    }

    /**
     * Выдать текст из [raw] в [out], разбивая по маркерам `{pause:N}`.
     *
     * Текст между паузами становится [Segment.Text] с заданным [languageId]
     * (тримится; пустые чанки отбрасываются). Каждый `{pause:N}` становится
     * [Segment.Pause] с N миллисекундами, сохраняя порядок.
     *
     * Возвращает количество добавленных сегментов.
     */
    private fun emitTextWithPauses(raw: String, languageId: String, out: MutableList<Segment>): Int {
        val pauseMatches = pausePattern.findAll(raw).toList()
        if (pauseMatches.isEmpty()) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return 0
            out.add(Segment.Text(trimmed, languageId))
            return 1
        }

        var added = 0
        var lastIndex = 0
        for (match in pauseMatches) {
            val start = match.range.first
            if (start > lastIndex) {
                val chunk = raw.substring(lastIndex, start).trim()
                if (chunk.isNotEmpty()) {
                    out.add(Segment.Text(chunk, languageId))
                    added++
                }
            }
            val msValue = match.groupValues[1].toLong()
            out.add(Segment.Pause(msValue))
            added++
            lastIndex = match.range.last + 1
        }
        // Хвостовой текст после последней паузы.
        if (lastIndex < raw.length) {
            val chunk = raw.substring(lastIndex).trim()
            if (chunk.isNotEmpty()) {
                out.add(Segment.Text(chunk, languageId))
                added++
            }
        }
        return added
    }

    /**
     * Разобрать story-контент и убрать все языковые маркеры, вернув чистый текст.
     * Также убирает маркеры `{pause:N}`, чтобы plain-text фолбэки (copy в буфер,
     * экранный рендеринг) оставались чистыми.
     *
     * @param content story-контент с маркерами
     * @return чистый текст без маркеров
     */
    fun stripMarkers(content: String): String {
        val withoutLang = languagePattern.replace(content) { match ->
            // Извлекаем только текст контента без маркеров
            match.groupValues[2]
        }
        return pausePattern.replace(withoutLang) { "" }
    }

    /**
     * Определить, содержит ли контент какие-либо языковые маркеры.
     *
     * @param content story-контент для проверки
     * @return true, если контент содержит `{lang}...{/lang}` маркеры
     */
    fun hasMarkers(content: String): Boolean {
        return languagePattern.containsMatchIn(content)
    }

    /**
     * Очистить markdown-синтаксис из текста перед TTS-воспроизведением.
     * Убирает заголовки, bold, italic, код-блоки, сохраняя контент.
     *
     * @param markdown текст с markdown-синтаксисом
     * @return чистый текст для TTS
     */
    fun cleanMarkdown(markdown: String): String {
        val normalized = markdown.replace("\r\n", "\n")
        return normalized
            .replace(Regex("""^#+\s+.*$"""), "") // Заголовки
            .replace(Regex("""\*\*([^*]+)\*\*"""), "$1") // Bold
            .replace(Regex("""\*([^*]+)\*"""), "$1") // Italic
            .replace(Regex("""```[^`]*```"""), "") // Code blocks
            .replace(Regex("""```"""), "") // Code block markers
            .replace(Regex("""[-*]\s+"""), "") // List markers
            .replace(Regex("""\n\n+"""), "\n") // Множественные newlines
            .trim()
    }
}

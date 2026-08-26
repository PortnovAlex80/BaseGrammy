package com.alexpo.grammermate.v2.core.data.packimport

import com.alexpo.grammermate.domain.model.WordScript
import java.io.InputStream

/**
 * Реестр парсеров контента пака (SRS-002 §2.1, §5.5).
 *
 * Фиксирует 5 парсеров, которыми [PackImporter] обрабатывает файлы пака по типу
 * контента: Lesson CSV, Verb CSV, Vocab CSV, Story JSON, BgVocab CSV. Это единая
 * точка, откуда body-задачи E02 (AC-7..AC-11) реализуют конкретный парсер, а
 * `PackImporter` (AC-1..AC-3) — диспетчеризацию файлов пака по парсерам.
 *
 * SCAFFOLD: интерфейс зафиксирован как contract; тело парсеров — TODO в
 * соответствующих object'ах (`CsvParser`, `VerbDrillCsvParser`, …). Реестр —
 * просто typed dispatch, без логики самого парсинга.
 *
 * Все методы — pure (object), без Android-зависимостей (контент паков —
 * CSV/JSON/MD, формат не зависит от платформы). Чтение `assets`/SAF выполняет
 * [PackImporter] и передаёт сюда уже открытый [InputStream] / String.
 *
 * @see <a href="../../../../../../../../docs/requirements/REQ-002-data-room/02-srs.md">SRS-002 §2.1 / §5.5</a>
 */
object PackContentParsers {

    /** Максимальное число предложений в bg-vocab скрипте слова (контракт FR-7). */
    const val MAX_BG_VOCAB_SENTENCES = 5

    /**
     * Действие по файлу пака — какой парсер применить и в какую entity-группу
     * смаппить. [PackImporter] строит этот список, обходя extracted-каталог пака
     * и сопоставляя имена/расширения файлов с типом контента.
     *
     * SCAFFOLD: enum зафиксирован как contract; используется [PackImporter]'ом
     * в AC-1 (happy path) и AC-2 (zip traversal).
     */
    enum class ContentType {
        /** `manifest.json` — парсится отдельно [LessonPackManifest.fromJson]. */
        MANIFEST,

        /** Lesson CSV (по `lessons[].file` в manifest) → [CsvParser]. */
        LESSON_CSV,

        /** Verb CSV (из секции `verbDrill.files`) → [VerbDrillCsvParser]. */
        VERB_DRILL_CSV,

        /** Aux CSV (подводящие карточки) → [CsvParser] / TODO aux-обход (FR-4). */
        AUX_DRILL_CSV,

        /** Vocab CSV (префикс `vocab_`, из `vocabDrill.files`) → [VocabCsvParser]. */
        VOCAB_CSV,

        /** Story JSON (`.json`, кроме manifest) → [StoryQuizParser] + доменный MultilingualStoryParser. */
        STORY_JSON,

        /** BgVocab CSV (из секции `backgroundVocab.file`) → [BgVocabCsvParser]. */
        BG_VOCAB_CSV,

        /** Markdown-стория главы (`chapters[].storyFile`) — сохраняется для
         *  runtime-чтения story reader'ом (фикс аудита M-3: не IGNORED). */
        STORY_MD,

        /** Audio / прочее — не парсируется PackImporter'ом (E03 / отображение). */
        IGNORED,
    }

    /**
     * Стартовая точка реестра: применять нужный парсер по типу контента.
     * Делегирует в соответствующий object; возвращает typed result для маппинга.
     *
     * SCAFFOLD: вызывается из [PackImporter] внутри `withTransaction`; конкретная
     * реализация появится вместе с body-парсерами (AC-7..AC-11). Здесь — только
     * фиксация диспетчеризации.
     */
    fun parseLessonCsv(stream: InputStream): ParseResult<Pair<String, List<SentenceCard>>, ParseError> =
        CsvParser.parseLesson(stream)

    fun parseVerbCsv(content: String): ParseResult<List<VerbDrillCard>, ParseError> =
        VerbDrillCsvParser.parse(content)

    fun parseVocabCsv(stream: InputStream): ParseResult<List<VocabRow>, ParseError> =
        VocabCsvParser.parse(stream)

    fun parseStoryJson(text: String): ParseResult<
        com.alexpo.grammermate.domain.model.StoryQuiz,
        ParseError,
        > = StoryQuizParser.parse(text)

    fun parseBgVocabCsv(stream: InputStream): List<WordScript> =
        BgVocabCsvParser.parse(stream)

    /**
     * Классифицировать файл пака по имени/расширению → [ContentType].
     *
     * Эвристика (контракт AC-1/AC-4): `manifest.json` → MANIFEST;
     * `vocab_*.csv` → VOCAB_CSV; `*.json` → STORY_JSON; `*.md` → [STORY_MD]
     * (фикс аудита M-3: стори-файлы глав — markdown, НЕ «ignored» — importer
     * сохраняет их для runtime-чтения story reader'ом); прочие `*.csv` →
     * LESSON_CSV (verb/aux уточняются по секциям manifest на стороне
     * [PackImporter] — имя файла само по себе их не различает);
     * аудио/прочее → IGNORED.
     */
    fun classify(fileName: String): ContentType {
        val name = fileName.substringAfterLast('/').lowercase()
        return when {
            name == "manifest.json" -> ContentType.MANIFEST
            name.startsWith("vocab_") && name.endsWith(".csv") -> ContentType.VOCAB_CSV
            name.endsWith(".json") -> ContentType.STORY_JSON
            name.endsWith(".md") -> ContentType.STORY_MD
            name.endsWith(".csv") -> ContentType.LESSON_CSV
            else -> ContentType.IGNORED
        }
    }
}

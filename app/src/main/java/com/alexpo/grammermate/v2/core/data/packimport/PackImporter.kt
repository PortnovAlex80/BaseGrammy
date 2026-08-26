package com.alexpo.grammermate.v2.core.data.packimport

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.alexpo.grammermate.v2.core.data.local.GrammarMateDatabase
import com.alexpo.grammermate.v2.core.data.local.entity.CardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.ChapterEntity
import com.alexpo.grammermate.v2.core.data.local.entity.LessonEntity
import com.alexpo.grammermate.v2.core.data.local.entity.PackEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.inject.Inject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * PackImporter — единая write-точка входа для импорта контента паков в Room
 * (SRS-002 FR-1, FR-7, §5.1).
 *
 * Точки входа:
 * - [importPackFromUri]     — ZIP через SAF (extract → manifest → withTransaction).
 * - [importPackFromAssets]  — ZIP из `assets` (seed bundled packs, Фаза 1 плана
 *   стабилизации 2026-08-26: fresh install получает контент без ручного импорта).
 * - [importLessonFromUri]   — один CSV-урок без целого пака.
 * - [readPackManifestFromAssets] — чтение манифеста без импорта.
 *
 * **Atomicity (FR-7 / NFR-1, критично — SM-2):** весь pack-import (manifest →
 * chapters → lessons → cards) выполняется в **одной**
 * [GrammarMateDatabase.withTransaction]. Сбой на середине → rollback, **0**
 * новых сущностей в БД (никаких частичных паков).
 *
 * **Partial-import (FR-8 / AC-13):** отдельно упавшая CSV-строка пропускается
 * (partial-error), остальные карты записываются; [PackImportResult.Partial]
 * возвращается с ошибками для confirm-диалога. Это **не** нарушение atomicity —
 * это преднамеренный partial-success в пределах транзакции.
 *
 * **Idempotency (NFR-5):** повторный импорт того же `packId` не дублирует
 * (DAO `OnConflictStrategy.REPLACE` + [com.alexpo.grammermate.v2.core.data.local.dao.ContentDao.replaceLessonCards]).
 *
 * **Область Фазы 1 (normal lesson slice):** импортируются packs/chapters/lessons/
 * cards. Drill-секции (`verbDrill`/`vocabDrill`), `backgroundVocab` и stories
 * манифеста сознательно не импортируются до своих вертикальных срезов
 * (Фаза 4/5 плана) — соответствующие маршруты остаются placeholder'ами и не
 * публикуются (правило плана §9: не публиковать route с TODO).
 *
 * Card PK: `"${lessonId}_${index}"` — префикс урока обязателен: CSV-идентификаторы
 * `card_$lineNumber` коллидируют между уроками. Формат совпадает с legacy
 * (`PackImporter.importLessonFromFile`), стратегия ID/PK — открытый вопрос
 * ревью плана (окно закрывается первым релизом).
 *
 * НЕ добавляет новые методы в доменный порт `ContentRepository` (read-only) —
 * write-side живёт здесь, как data-деталь (SRS-002 §5.1 extension point).
 */
class PackImporter @Inject constructor(
    private val database: GrammarMateDatabase,
    @ApplicationContext private val context: Context,
) {

    /**
     * Импортировать ZIP-пак, открытый через SAF.
     *
     * SCAFFOLD TODO (AC-1 happy path + AC-2 traversal + AC-3 rollback): реализовать
     * когда появится UI импорта (Фаза 5 плана стабилизации). Сейчас метод не
     * reachable ни из одного route.
     */
    suspend fun importPackFromUri(uri: Uri, resolver: ContentResolver): PackImportResult {
        TODO("AC-1/AC-2/AC-3: реализовать importPackFromUri — zip extract + manifest + atomic import")
    }

    /**
     * Импортировать ZIP-пак из `assets` (seed bundled packs при первом запуске).
     *
     * @param assetPath путь в `assets` (например, `"grammarmate/packs/ITALIAN_EXPRESS_SHORT.zip"`).
     * @return [PackImportResult].
     */
    suspend fun importPackFromAssets(assetPath: String): PackImportResult =
        context.assets.open(assetPath).use { importPackFromStream(it) }

    /**
     * Импортировать ZIP-пак из произвольного потока: extract → manifest →
     * [importPackAtomic] → cleanup temp. Тело [importPackFromAssets]; отдельный
     * метод — для тестов (готовый ZIP-поток без Android assets).
     */
    suspend fun importPackFromStream(input: InputStream): PackImportResult {
        val tempDir = extractZipToTemp(input)
        try {
            val manifestFile = File(tempDir, MANIFEST_FILE)
            if (!manifestFile.exists()) {
                return PackImportResult.Failed(
                    listOf(
                        ParseError.InvalidFormat(
                            lineNumber = 0,
                            reason = "Manifest file not found in pack",
                        )
                    )
                )
            }
            val manifest = LessonPackManifest.fromJson(manifestFile.readText())
                ?: return PackImportResult.Failed(
                    errors = listOf(
                        ParseError.InvalidFormat(
                            reason = "manifest.json невалиден (schemaVersion/packId/packVersion/language)",
                        )
                    )
                )
            return importPackAtomic(tempDir, manifest)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Импортировать один CSV-урок без целого пака (legacy `importLessonFromUri`).
     *
     * SCAFFOLD TODO (AC-12): Фаза 5 плана (partial import как явное user decision).
     * Сейчас метод не reachable ни из одного route.
     */
    suspend fun importLessonFromUri(
        languageId: String,
        uri: Uri,
        resolver: ContentResolver,
    ): PackImportResult {
        TODO("AC-12: реализовать importLessonFromUri — single CSV → LessonEntity + cards atomic")
    }

    /**
     * Прочитать манифест пака из `assets` **без** импорта (extract → parse →
     * cleanup, без write в Room).
     */
    suspend fun readPackManifestFromAssets(assetPath: String): LessonPackManifest {
        context.assets.open(assetPath).use { input ->
            val tempDir = extractZipToTemp(input)
            try {
                val manifestFile = File(tempDir, MANIFEST_FILE)
                if (!manifestFile.exists()) {
                    error("Manifest not found")
                }
                return LessonPackManifest.fromJson(manifestFile.readText())
                    ?: error("Manifest invalid")
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    /**
     * ★ Atomic pack-import wrapper (FR-7 / NFR-1, критично — SM-2).
     *
     * Весь write-контент пака (pack → chapters → lessons → cards) выполняется в
     * **одной** [GrammarMateDatabase.withTransaction]. Сбой на середине →
     * rollback, **0** новых сущностей в БД.
     *
     * Источники уроков: root-level `lessons` (manifest v1, только `type != "verb_drill"`)
     * + главные уроки `chapters[].lessons` (manifest v2, файл — `"$lessonId.csv"`),
     * дедупликация по `lessonId`.
     *
     * @param packDir  каталог распакованного пака с `manifest.json` + CSV.
     * @param manifest уже распарсенный манифест (валидный).
     * @return [PackImportResult] — Success / Partial / Failed (rollback = Failed).
     */
    suspend fun importPackAtomic(
        packDir: java.io.File,
        manifest: LessonPackManifest,
    ): PackImportResult {
        val languageId = manifest.language.lowercase().trim()
        val importedAtMs = System.currentTimeMillis()

        // Сбор контента урока ДО транзакции: парсинг CSV — чистый I/O без write.
        val errors = mutableListOf<ParseError>()
        val lessonEntries = collectLessonEntries(manifest)
        val parsedLessons = mutableListOf<ParsedLesson>()

        lessonEntries.forEach { entry ->
            val csvFile = File(packDir, entry.file)
            if (!csvFile.exists()) {
                errors.add(
                    ParseError.InvalidFormat(
                        lineNumber = 0,
                        reason = "Missing lesson file: ${entry.file}",
                    )
                )
                return@forEach
            }
            val parseResult = CsvParser.parseLesson(csvFile.inputStream())
            val (parsedData, parseErrors) = when (parseResult) {
                is ParseResult.Success -> parseResult.data to emptyList()
                is ParseResult.Partial -> parseResult.data to parseResult.errors
                is ParseResult.Failure -> null to parseResult.errors
            }
            errors += parseErrors.map { ParseError.WithFileContext(csvFile.name, it) }
            val data = parsedData ?: return@forEach
            parsedLessons.add(
                ParsedLesson(
                    lessonId = entry.lessonId,
                    chapterId = entry.chapterId,
                    order = entry.order,
                    title = entry.title ?: data.first,
                    cards = data.second,
                )
            )
        }

        val packEntity = PackEntity(
            id = manifest.packId,
            languageId = languageId,
            displayName = manifest.displayName,
            version = manifest.packVersion,
            importedAtMs = importedAtMs,
        )
        val chapterEntities = manifest.chapters.map { ch ->
            ChapterEntity(
                id = ch.chapterId,
                packId = manifest.packId,
                order = ch.order,
                title = ch.title,
                subtitle = ch.subtitle,
                storyFile = ch.storyFile,
            )
        }
        val lessonEntities = parsedLessons.map { it.toEntity(manifest.packId) }
        val cardEntities = parsedLessons.map { parsed ->
            parsed.cards.mapIndexed { index, card ->
                CardEntity(
                    id = "${parsed.lessonId}_$index",
                    packId = manifest.packId,
                    lessonId = parsed.lessonId,
                    ord = index,
                    type = "SENTENCE",
                    promptRu = card.promptRu,
                    acceptedAnswersJson = Json.encodeToString(
                        ListSerializer(String.serializer()),
                        card.acceptedAnswers,
                    ),
                    tense = card.tense,
                    verb = null,
                    verbGroup = null,
                    person = null,
                    frequencyRank = null,
                )
            }
        }

        // Одна транзакция: pack + chapters + lessons + cards всех уроков.
        database.withTransaction {
            val contentDao = database.contentDao()
            contentDao.insertPack(packEntity)
            if (chapterEntities.isNotEmpty()) {
                contentDao.insertChapters(chapterEntities)
            }
            contentDao.insertLessons(lessonEntities)
            cardEntities.forEach { cardsForLesson ->
                // PK карт содержит lessonId → REPLACE-реимпорт урока идемпотентен.
                contentDao.replaceLessonCards(cardsForLesson.first().lessonId, cardsForLesson)
            }
        }

        // Story-контент глав (срез 6 Фазы 4, вход STORY_MD/фикс M-3): .md-файлы
        // копируются в filesDir/stories/<packId>/… ПОСЛЕ успешной DB-транзакции
        // (сбой транзакции → файлов-сирот не остаётся; temp-каталог ещё жив).
        // Ошибка копирования не роняет импорт — story-контент дозагрузится
        // повторным идемпотентным импортом.
        runCatching { preserveStoryFiles(packDir, manifest.packId) }

        val pack = LessonPack(
            packId = manifest.packId,
            packVersion = manifest.packVersion,
            languageId = languageId,
            importedAtMs = importedAtMs,
            displayName = manifest.displayName,
        )
        return when {
            errors.isEmpty() -> PackImportResult.Success(pack)
            else -> PackImportResult.Partial(pack, errors)
        }
    }

    // ── Внутренние хелперы ────────────────────────────────────────────────────

    /**
     * Копировать все `.md`-файлы пака в `filesDir/stories/<packId>/<relPath>`
     * (путь относительно корня пака — совпадает с `chapters[].storyFile` из
     * манифеста, fallback stories/<lang>/… разрешает читатель). Идемпотентно
     * (overwrite) — повторный импорт обновляет контент.
     */
    private fun preserveStoryFiles(packDir: File, packId: String) {
        val destRoot = File(File(context.filesDir, "stories"), packId)
        packDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
            .forEach { md ->
                val dest = File(destRoot, md.relativeTo(packDir).path)
                dest.parentFile?.mkdirs()
                md.copyTo(dest, overwrite = true)
            }
    }

    /** Запись урока манифеста после сбора из v1/v2 секций. */
    private data class LessonEntry(
        val lessonId: String,
        val chapterId: String?,
        val order: Int,
        val title: String?,
        val file: String,
    )

    /** Распарсенный урок: CSV-тайтл/карты + контекст манифеста. */
    private data class ParsedLesson(
        val lessonId: String,
        val chapterId: String?,
        val order: Int,
        val title: String,
        val cards: List<SentenceCard>,
    ) {
        fun toEntity(packId: String): LessonEntity = LessonEntity(
            id = lessonId,
            packId = packId,
            chapterId = chapterId,
            order = order,
            title = title,
            cefrLevel = null,
            grammarChipKey = null,
        )
    }

    /** Уроки из root-level (v1) + глав (v2), дедупликация по lessonId. */
    private fun collectLessonEntries(manifest: LessonPackManifest): List<LessonEntry> {
        val entries = mutableListOf<LessonEntry>()
        manifest.lessons
            .filter { it.type != "verb_drill" }
            .forEach { entries.add(LessonEntry(it.lessonId, null, it.order, it.title, it.file)) }
        manifest.chapters.forEach { chapter ->
            chapter.lessons.forEachIndexed { indexInChapter, lessonId ->
                entries.add(
                    LessonEntry(
                        lessonId = lessonId,
                        chapterId = chapter.chapterId,
                        order = indexInChapter,
                        title = null, // тайтл возьмётся из CSV-заголовка
                        file = "$lessonId.csv",
                    )
                )
            }
        }
        return entries.distinctBy { it.lessonId }
    }

    /**
     * Распаковка ZIP во временный каталог с path-traversal защитой
     * (ZipGuard: Path-посегментно + бюджеты entries/байты — фикс аудита H-2).
     */
    private fun extractZipToTemp(input: InputStream): File {
        val tempDir = File(context.cacheDir, "pack_import_${UUID.randomUUID()}")
        tempDir.mkdirs()
        val budget = ZipGuard.ZipBudget()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!budget.onEntry()) error("Zip has too many entries (>${ZipGuard.MAX_ENTRIES})")
                val outFile = File(tempDir, entry.name)
                if (!ZipGuard.isInsideDir(outFile, tempDir)) {
                    error("Invalid zip entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out ->
                        val copied = zip.copyTo(out)
                        if (!budget.onBytes(copied)) {
                            error("Zip unpacked size exceeds ${ZipGuard.MAX_TOTAL_BYTES} bytes")
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return tempDir
    }

    private companion object {
        const val MANIFEST_FILE = "manifest.json"
    }
}

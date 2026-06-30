package com.alexpo.grammermate.v2.core.data.migration

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.alexpo.grammermate.v2.core.data.local.GrammarMateDatabase
import com.alexpo.grammermate.v2.core.data.local.entity.BadSentenceEntity
import com.alexpo.grammermate.v2.core.data.local.entity.CardEncounterEntity
import com.alexpo.grammermate.v2.core.data.local.entity.DailyCursorEntity
import com.alexpo.grammermate.v2.core.data.local.entity.DrillProgressEntity
import com.alexpo.grammermate.v2.core.data.local.entity.HiddenCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.MasteryStateEntity
import com.alexpo.grammermate.v2.core.data.local.entity.MigrationFlagEntity
import com.alexpo.grammermate.v2.core.data.local.entity.ShownCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.StreakEntity
import com.alexpo.grammermate.v2.core.domain.srs.SrsMigration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.yaml.snakeyaml.Yaml
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Результат одноразовой миграции legacy YAML → Room v2.
 */
sealed interface MigrationResult {
    /** Миграция уже была выполнена ранее (флаг `done = true`) — повторный запуск = no-op. */
    data object NotNeeded : MigrationResult

    /** Миграция выполнена успешно; [counts] отражает число перенесённых записей. */
    data class Migrated(val counts: MigrationCounts) : MigrationResult

    /** Миграция завершилась с неустранимой ошибкой (битый YAML, ошибка БД и т. п.). */
    data class Failed(val error: Throwable) : MigrationResult
}

/**
 * Сводка по числу перенесённых записей в каждую таблицу.
 *
 * @property mastery       число строк [MasteryStateEntity] (по pack+lesson).
 * @property streaks       число [StreakEntity] (по языку).
 * @property hidden        число [HiddenCardEntity].
 * @property badSentences  число [BadSentenceEntity].
 * @property drillProgress число [DrillProgressEntity] (по pack+drillType).
 * @property dailyCursors  число [DailyCursorEntity] (по pack).
 * @property lessonProgress 0 всегда: legacy `lesson_progress_*.yaml` не имеют прямого аналога
 *                          в v2-схеме и пропускаются (см. [YamlToRoomMigrator]).
 */
data class MigrationCounts(
    val mastery: Int,
    val streaks: Int,
    val hidden: Int,
    val badSentences: Int,
    val drillProgress: Int,
    val dailyCursors: Int,
    val lessonProgress: Int,
)

/**
 * Одноразовый транзакционный мигратор прогресса пользователя из legacy YAML-файлов
 * GrammarMate v1 в единую Room БД v2.
 *
 * ## Назначение
 * Запускается **один раз** при первом старте v2 поверх существующей установки v1:
 * читает YAML-файлы прогресса из `filesDir/grammarmate/`, конвертирует в v2 entity и
 * вставляет их **в одной атомарной транзакции** (`database.withTransaction`). Либо все
 * данные перенесены, либо ни одного — это устраняет риск частичной миграции, которым
 * страдала v1 (24 независимых `ReentrantLock` + 24 независимых YAML-файла).
 *
 * ## Идемпотентность
 * Факт выполнения фиксируется в таблице `migratable_files` через [MigrationFlagEntity]
 * с ключом [MIGRATION_KEY]. Повторный запуск — no-op (`NotNeeded`), что безопасно при
 * рестартах процесса, повторных `onCreate` и т. п.
 *
 * ## Отказоустойчивость
 * Каждый отдельный файл читается в своём `try/catch`: битый/мусорный YAML логируется
 * и **пропускается**, не валидируя всю миграцию. Но если упало что-то критическое внутри
 * транзакции (например, нарушение схемы БД), вся транзакция откатывается → [Failed].
 *
 * ## Что НЕ удаляется
 * Старые `.yaml` файлы **остаются на месте** как backup/путь отката (см. ARCHITECTURE.md,
 * «Миграция данных пользователя»). Их можно заархивировать вручную, если нужно.
 *
 * ## Покрытие (канонический список — `BackupFileCollector` + дополнения)
 *  - `mastery.yaml`            → [MasteryStateEntity] + [ShownCardEntity] + [CardEncounterEntity]
 *  - `streak_<lang>.yaml`      → [StreakEntity] (язык из имени файла)
 *  - `hidden_cards.yaml`       → [HiddenCardEntity]
 *  - `bad_sentences.yaml`      → [BadSentenceEntity]
 *  - `daily_cursor_<packId>.yaml` → [DailyCursorEntity]
 *  - `drills/<packId>/verb_drill_progress.yaml` → [DrillProgressEntity] (drillType = VERB)
 *  - `drills/<packId>/word_mastery.yaml`         → ПРОПУСК (нет аналога в v2-схеме)
 *  - `lesson_progress_<packId>.yaml`             → ПРОПУСК (нет аналога в v2-схеме)
 *
 * @property context   контекст приложения (для доступа к [Context.getFilesDir]).
 * @property database  единая Room-БД v2 (и транзакционный контейнер, и DAO-источник).
 */
@Singleton
class YamlToRoomMigrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: GrammarMateDatabase,
) {

    private val yaml = Yaml()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val strListSerializer = ListSerializer(String.serializer())
    private val strSetSerializer = SetSerializer(String.serializer())

    private val baseDir: File get() = File(context.filesDir, "grammarmate")

    /**
     * Выполнить миграцию, если она ещё не была сделана. Все файловые операции — на
     * [Dispatchers.IO]; вызывать можно из любой корутины.
     *
     * @return [MigrationResult]: [NotNeeded] (уже мигрировано или fresh install без YAML),
     *   [Migrated] при успехе, [Failed] при неустранимой ошибке.
     */
    suspend fun migrateIfNeeded(): MigrationResult = withContext(Dispatchers.IO) {
        // 1. Идемпотентность: миграция уже выполнена?
        val alreadyDone = runCatching { database.userContentDao().getMigrationFlag(MIGRATION_KEY) }
            .getOrNull()
        if (alreadyDone == true) {
            Log.i(TAG, "Migration already done (flag $MIGRATION_KEY = true). No-op.")
            return@withContext MigrationResult.NotNeeded
        }

        // 2. Fresh install: если каталога/любого .yaml нет — мигрировать нечего.
        if (!hasAnyLegacyYaml()) {
            Log.i(TAG, "No legacy YAML found under ${baseDir.absolutePath}. Fresh install — nothing to migrate.")
            return@withContext MigrationResult.NotNeeded
        }

        // 3. Собираем entity из всех файлов (вне транзакции — чтение диска, безопасно пофайлово).
        val bag = runCatching { collectEntities() }
        if (bag.isFailure) {
            Log.e(TAG, "Unrecoverable error while collecting legacy data", bag.exceptionOrNull())
            return@withContext MigrationResult.Failed(bag.exceptionOrNull() ?: UnknownError())
        }
        val b = bag.getOrThrow()

        // 4. Одна атомарная транзакция: либо все вставки, либо ни одной.
        val tx = runCatching {
            database.withTransaction {
                val masteryDao = database.masteryDao()
                val progressDao = database.progressDao()
                val userContentDao = database.userContentDao()

                for (m in b.mastery) masteryDao.upsert(m)
                for (sc in b.shownCards) masteryDao.insertShownCard(sc)
                for (ce in b.encounters) masteryDao.upsertEncounter(ce)
                for (s in b.streaks) progressDao.upsertStreak(s)
                for (dp in b.drillProgress) progressDao.upsertDrillProgress(dp)
                for (dc in b.dailyCursors) progressDao.upsertDailyCursor(dc)
                for (hc in b.hidden) userContentDao.hideCard(hc)
                for (bs in b.badSentences) userContentDao.insertBadSentence(bs)

                userContentDao.setMigrationFlag(
                    MigrationFlagEntity(key = MIGRATION_KEY, done = true, migratedAtMs = System.currentTimeMillis())
                )
            }
        }
        tx.onFailure {
            Log.e(TAG, "Transaction failed — no data was committed.", it)
            return@withContext MigrationResult.Failed(it)
        }

        Log.i(
            TAG,
            "Migration committed in one transaction: " +
                "mastery=${b.mastery.size}, streaks=${b.streaks.size}, hidden=${b.hidden.size}, " +
                "badSentences=${b.badSentences.size}, drillProgress=${b.drillProgress.size}, " +
                "dailyCursors=${b.dailyCursors.size} (legacy .yaml left in place as backup)."
        )
        MigrationResult.Migrated(
            MigrationCounts(
                mastery = b.mastery.size,
                streaks = b.streaks.size,
                hidden = b.hidden.size,
                badSentences = b.badSentences.size,
                drillProgress = b.drillProgress.size,
                dailyCursors = b.dailyCursors.size,
                lessonProgress = 0, // не переносится (нет аналога в v2-схеме)
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Сбор entity из всех YAML-файлов (пофайловая отказоустойчивость)
    // ─────────────────────────────────────────────────────────────────────────────

    /** Временный агрегат всех распарсенных entity перед транзакционной вставкой. */
    private class EntityBag {
        val mastery = mutableListOf<MasteryStateEntity>()
        val shownCards = mutableListOf<ShownCardEntity>()
        val encounters = mutableListOf<CardEncounterEntity>()
        val streaks = mutableListOf<StreakEntity>()
        val drillProgress = mutableListOf<DrillProgressEntity>()
        val dailyCursors = mutableListOf<DailyCursorEntity>()
        val hidden = mutableListOf<HiddenCardEntity>()
        val badSentences = mutableListOf<BadSentenceEntity>()
    }

    /**
     * Читает все legacy YAML и собирает v2 entity. Ошибка парсинга отдельного файла
     * логируется и пропускается (остальные файлы мигрируются).
     */
    private fun collectEntities(): EntityBag {
        val bag = EntityBag()
        val now = System.currentTimeMillis()

        parseFileSafely("mastery.yaml") { file -> parseMastery(file, bag, now) }
        parseStreakFiles(bag)
        parseFileSafely("hidden_cards.yaml") { file -> parseHiddenCards(file, bag) }
        parseFileSafely("bad_sentences.yaml") { file -> parseBadSentences(file, bag) }
        parseDailyCursorFiles(bag, now)
        parseVerbDrillProgressFiles(bag, now)
        // Документируемые пропуски (нет аналога в v2-схеме):
        logSkipped("lesson_progress_<packId>.yaml", "no direct v2 entity (PackLessonProgress)")
        logSkipped("drills/<packId>/word_mastery.yaml", "no word-level mastery table in v2 schema")
        return bag
    }

    /** true, если в `filesDir/grammarmate` есть хотя бы один `.yaml` (или каталог drills). */
    private fun hasAnyLegacyYaml(): Boolean {
        val dir = baseDir
        if (!dir.exists()) return false
        val hasYaml = dir.listFiles { f -> f.isFile && f.name.endsWith(".yaml") }?.isNotEmpty() == true
        val hasDrills = File(dir, "drills").let { it.exists() && it.isDirectory }
        return hasYaml || hasDrills
    }

    /**
     * Безопасно прочитать и обработать один файл: при любой ошибке — log + skip.
     */
    private inline fun parseFileSafely(fileName: String, block: (File) -> Unit) {
        val file = File(baseDir, fileName)
        if (!file.exists() || file.length() == 0L) return
        try {
            block(file)
        } catch (e: Exception) {
            Log.w(TAG, "Skipping '$fileName': parse failed (${e.message}). Migration continues.", e)
        }
    }

    private fun logSkipped(filePattern: String, reason: String) {
        Log.i(TAG, "Not migrating '$filePattern': $reason. Legacy file left untouched.")
    }

    // ─── mastery.yaml ──────────────────────────────────────────────────────────────

    /**
     * Разбор `mastery.yaml` (schemaVersion 2). Структура:
     * ```
     * schemaVersion: 2
     * data:
     *   <topKey>:                 # "pack:<packId>" (новый формат) ИЛИ languageId ("zh","it"...)
     *     <lessonId>:
     *       uniqueCardShows: 1
     *       totalCardShows: 2
     *       lastShowDateMs: 1780375959300
     *       intervalStepIndex: 0
     *       completedAtMs: 1780736264111   # или null
     *       shownCardIds: [card_2, card_3]
     *       cardEncounterCounts: {card_2: 1, card_3: 3}
     * ```
     * `[topKey]` детерминированно превращается в packId: убираем префикс `"pack:"`,
     * иначе используем ключ как есть (в реальных файлах там languageId — сохраняем как packId,
     * чтобы не потерять данные; репозиторий v2 работает по packId).
     *
     * `intervalStepIndex` → `srsState`/`dueAtMs` через [SrsMigration.fromLegacyIntervalStep].
     */
    private fun parseMastery(file: File, bag: EntityBag, now: Long) {
        val raw = yaml.load<Any>(file.readText()) ?: return
        val root = raw as? Map<*, *> ?: return
        @Suppress("UNCHECKED_CAST")
        val payload = (root["data"] as? Map<String, Any>) ?: (root as Map<String, Any>)

        for ((topKeyObj, lessonsObj) in payload) {
            val topKey = topKeyObj as? String ?: continue
            val packId = topKey.removePrefix("pack:")
            @Suppress("UNCHECKED_CAST")
            val lessons = lessonsObj as? Map<String, Map<String, Any>> ?: continue

            for ((lessonId, fields) in lessons) {
                try {
                    val uniqueCardShows = fields.intOr("uniqueCardShows", 0)
                    val totalCardShows = fields.intOr("totalCardShows", 0)
                    val lastShowDateMs = fields.longOr("lastShowDateMs", 0L)
                    val intervalStepIndex = fields.intOr("intervalStepIndex", 0)
                    val completedAtMs = fields.longOrNull("completedAtMs")

                    // SRS-миграция: legacy step → FSRS-приближение; берём dueAtMs оттуда.
                    val srsState = SrsMigration.fromLegacyIntervalStep(intervalStepIndex, now)
                    val dueAtMs = srsState.dueAtMs

                    bag.mastery.add(
                        MasteryStateEntity(
                            id = "$packId:$lessonId",
                            packId = packId,
                            lessonId = lessonId,
                            uniqueCardShows = uniqueCardShows,
                            totalCardShows = totalCardShows,
                            lastShowDateMs = lastShowDateMs,
                            intervalStepIndex = intervalStepIndex, // сохраняем legacy-индекс для аудита
                            fsrsStateJson = encodeSrsState(srsState),
                            dueAtMs = dueAtMs,
                            completedAtMs = completedAtMs,
                        )
                    )

                    // shownCardIds → ShownCardEntity
                    fields.stringListOr("shownCardIds").forEach { cardId ->
                        bag.shownCards.add(ShownCardEntity(packId, lessonId, cardId))
                    }

                    // cardEncounterCounts → CardEncounterEntity
                    fields.cardEncounters().forEach { (cardId, count) ->
                        bag.encounters.add(CardEncounterEntity(packId, lessonId, cardId, count))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping mastery entry $packId/$lessonId: ${e.message}")
                }
            }
        }
    }

    // ─── streak_<lang>.yaml ────────────────────────────────────────────────────────

    /** Сканирует `streak_*.yaml` (по одному на язык) и переносит в [StreakEntity]. */
    private fun parseStreakFiles(bag: EntityBag) {
        val files = baseDir.listFiles { f ->
            f.isFile && f.name.startsWith("streak_") && f.name.endsWith(".yaml")
        } ?: return
        for (file in files) {
            try {
                val lang = file.name.removePrefix("streak_").removeSuffix(".yaml")
                if (lang.isBlank()) continue
                val raw = yaml.load<Any>(file.readText()) ?: continue
                val data = raw as? Map<*, *> ?: continue
                bag.streaks.add(
                    StreakEntity(
                        languageId = lang,
                        currentStreak = data.intOr("currentStreak", 0),
                        longestStreak = data.intOr("longestStreak", 0),
                        lastCompletionDateMs = data.longOrNull("lastCompletionDateMs"),
                        totalSubLessonsCompleted = data.intOr("totalSubLessonsCompleted", 0),
                        todayFireCount = data.intOr("todayFireCount", 0),
                        lastFireDateMs = data.longOrNull("lastFireDateMs"),
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping streak file ${file.name}: ${e.message}")
            }
        }
    }

    // ─── hidden_cards.yaml ─────────────────────────────────────────────────────────

    /** `schemaVersion: 1`, `hiddenCardIds: [...]` → [HiddenCardEntity]. */
    private fun parseHiddenCards(file: File, bag: EntityBag) {
        val raw = yaml.load<Any>(file.readText()) ?: return
        val data = raw as? Map<*, *> ?: return
        val ids = data.stringListOr("hiddenCardIds")
        val now = System.currentTimeMillis()
        ids.forEach { cardId -> bag.hidden.add(HiddenCardEntity(cardId = cardId, hiddenAtMs = now)) }
    }

    // ─── bad_sentences.yaml ────────────────────────────────────────────────────────

    /**
     * `schemaVersion: 2`, packs-scoped (`packs: { <packId>: { items: [...] } }`).
     * Совместимо и со старым schema v1 (плоский `items` → pack `__legacy__`).
     */
    private fun parseBadSentences(file: File, bag: EntityBag) {
        val raw = yaml.load<Any>(file.readText()) ?: return
        val data = raw as? Map<*, *> ?: return
        val schemaVersion = (data["schemaVersion"] as? Number)?.toInt() ?: 1

        if (schemaVersion == 1) {
            val items = (data["items"] as? List<*>) ?: return
            emitBadSentenceItems(items, "__legacy__", bag)
        } else {
            val packs = data["packs"] as? Map<*, *> ?: return
            for ((packIdObj, packContent) in packs) {
                val packId = packIdObj as? String ?: continue
                val packMap = packContent as? Map<*, *> ?: continue
                val items = packMap["items"] as? List<*> ?: continue
                emitBadSentenceItems(items, packId, bag)
            }
        }
    }

    private fun emitBadSentenceItems(items: List<*>, packId: String, bag: EntityBag) {
        for (item in items) {
            val m = item as? Map<*, *> ?: continue
            val cardId = m["cardId"] as? String ?: continue
            bag.badSentences.add(
                BadSentenceEntity(
                    packId = packId,
                    cardId = cardId,
                    languageId = m["languageId"] as? String ?: "",
                    sentence = m["sentence"] as? String ?: "",
                    translation = m["translation"] as? String ?: "",
                    mode = m["mode"] as? String ?: "training",
                    addedAtMs = (m["addedAtMs"] as? Number)?.toLong() ?: 0L,
                )
            )
        }
    }

    // ─── daily_cursor_<packId>.yaml ────────────────────────────────────────────────

    /**
     * `schemaVersion: 1`, packId извлекается из имени файла.
     *
     * Замечание: legacy-формат хранит `lastSessionHash`, которого нет в [DailyCursorEntity];
     * это поле осознанно отбрасывается (v2 не использует хэш-инвалидацию, см. ROOM_SCHEMA.md).
     */
    private fun parseDailyCursorFiles(bag: EntityBag, now: Long) {
        val files = baseDir.listFiles { f ->
            f.isFile && f.name.startsWith("daily_cursor_") && f.name.endsWith(".yaml")
        } ?: return
        for (file in files) {
            try {
                val packId = file.name.removePrefix("daily_cursor_").removeSuffix(".yaml")
                if (packId.isBlank()) continue
                val raw = yaml.load<Any>(file.readText()) ?: continue
                val data = raw as? Map<*, *> ?: continue
                bag.dailyCursors.add(
                    DailyCursorEntity(
                        packId = packId,
                        sentenceOffset = data.intOr("sentenceOffset", 0),
                        currentLessonIndex = data.intOr("currentLessonIndex", 0),
                        verbOffset = data.intOr("verbOffset", 0),
                        firstSessionDate = data.stringOrNull("firstSessionDate"),
                        firstSessionSentenceCardIdsJson = encodeStringList(data.stringListOr("firstSessionSentenceCardIds")),
                        firstSessionVerbCardIdsJson = encodeStringList(data.stringListOr("firstSessionVerbCardIds")),
                        firstSessionLessonId = data.stringOrNull("firstSessionLessonId"),
                        updatedAtMs = now,
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping daily cursor ${file.name}: ${e.message}")
            }
        }
    }

    // ─── drills/<packId>/verb_drill_progress.yaml ──────────────────────────────────

    /**
     * Сканирует `drills/<packId>/verb_drill_progress.yaml` → одна [DrillProgressEntity]
     * на пак с `drillType = "VERB"`. PackId берётся из пути.
     *
     * Legacy-файл хранит прогресс по comboKey (`group:tense`) в `data`; v2-схема хранит
     * одну запись на (packId, drillType) (unique index), поэтому объединяем все combo:
     * `everShownCardIds` = объединение показанных за всё время карточек всех combo,
     * `totalCards` = max по combo, `lastDate` = самая свежая дата. `todayShownCardIds`
     * и `cursor` сбрасываются в пустое (v2 пересчитает их при следующей сессии).
     */
    private fun parseVerbDrillProgressFiles(bag: EntityBag, now: Long) {
        val drillsDir = File(baseDir, "drills")
        if (!drillsDir.exists() || !drillsDir.isDirectory) return
        val packDirs = drillsDir.listFiles { f -> f.isDirectory } ?: return
        for (packDir in packDirs) {
            val packId = packDir.name
            val file = File(packDir, "verb_drill_progress.yaml")
            if (!file.exists() || file.length() == 0L) continue
            try {
                val raw = yaml.load<Any>(file.readText()) ?: continue
                val root = raw as? Map<*, *> ?: continue
                @Suppress("UNCHECKED_CAST")
                val payload = (root["data"] as? Map<String, Any>) ?: (root as Map<String, Any>)

                val everShown = mutableSetOf<String>()
                var totalCards = 0
                var lastDate: String? = null
                var lastComboKey: String? = null
                for ((comboKey, value) in payload) {
                    val entry = value as? Map<*, *> ?: continue
                    (entry["everShownCardIds"] as? List<*>)?.mapNotNull { it as? String }?.let { everShown.addAll(it) }
                    entry.intOr("totalCards", 0).let { if (it > totalCards) totalCards = it }
                    (entry["lastDate"] as? String)?.let { d ->
                        if (lastDate == null || d > lastDate!!) lastDate = d
                    }
                    lastComboKey = comboKey as? String
                }
                bag.drillProgress.add(
                    DrillProgressEntity(
                        id = "$packId:VERB",
                        packId = packId,
                        drillType = "VERB",
                        comboKey = lastComboKey,
                        totalCards = totalCards,
                        everShownCardIdsJson = encodeStringSet(everShown),
                        todayShownCardIdsJson = encodeStringSet(emptySet()),
                        lastDate = lastDate,
                        cursor = 0,
                        updatedAtMs = now,
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping verb drill progress for pack $packId: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  JSON-кодирование коллекций (формат, ожидаемый Converters / entity-колонками)
    // ─────────────────────────────────────────────────────────────────────────────

    private fun encodeStringList(list: List<String>): String =
        runCatching { json.encodeToString(strListSerializer, list) }.getOrDefault("[]")

    private fun encodeStringSet(set: Set<String>): String =
        runCatching { json.encodeToString(strSetSerializer, set) }.getOrDefault("[]")

    /** Минимальная JSON-сериализация SRS-состояния для колонки `fsrsStateJson`. */
    private fun encodeSrsState(s: com.alexpo.grammermate.v2.core.domain.srs.SrsCardState): String =
        """{"stability":${s.stability},"difficulty":${s.difficulty},"lastReviewMs":${s.lastReviewMs},"reps":${s.reps},"lapses":${s.lapses},"state":"${s.state.name}","dueAtMs":${s.dueAtMs}}"""

    // ─────────────────────────────────────────────────────────────────────────────
    //  Утилиты безопасного извлечения типизированных значений из YAML-Map.
    //  SnakeYAML грузит числа как Long/Integer/Double — приводим универсально через Number.
    // ─────────────────────────────────────────────────────────────────────────────

    private fun Map<*, *>.intOr(key: String, default: Int): Int =
        ((this[key] as? Number)?.toInt()) ?: default

    private fun Map<*, *>.longOr(key: String, default: Long): Long =
        ((this[key] as? Number)?.toLong()) ?: default

    private fun Map<*, *>.longOrNull(key: String): Long? =
        (this[key] as? Number)?.toLong()

    private fun Map<*, *>.stringOrNull(key: String): String? =
        (this[key] as? String)?.takeIf { it.isNotBlank() }

    private fun Map<*, *>.stringListOr(key: String): List<String> =
        (this[key] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    /** Извлекает `cardEncounterCounts: {cardId: count}` как пары. */
    private fun Map<String, Any>.cardEncounters(): List<Pair<String, Int>> {
        val raw = this["cardEncounterCounts"] as? Map<*, *> ?: return emptyList()
        return raw.mapNotNull { (k, v) ->
            val cardId = k as? String ?: return@mapNotNull null
            val count = (v as? Number)?.toInt() ?: return@mapNotNull null
            cardId to count
        }
    }

    private companion object {
        private const val TAG = "YamlMigrator"
        private const val MIGRATION_KEY = "migration_yaml_v1_to_v2"
    }
}

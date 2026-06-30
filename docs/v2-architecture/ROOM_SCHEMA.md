# GrammarMate v2 — Room Schema

User-state в одной БД `grammarmate_v2.db`. Контент паков (CSV/YAML/аудио) — файлы.
Настройки key-value — DataStore Preferences.

> Принцип: **`currentCardId` — первичный ключ текущей карточки**, никогда не индекс массива.

## Таблицы

### Контент (seed из pack-import, read-mostly)

```kotlin
@Entity(tableName = "packs")
data class PackEntity(
    @PrimaryKey val id: String,           // ITALIAN_SHORT, EN_WORD_ORDER_A1, ...
    val languageId: String,
    val displayName: String?,
    val version: String,
    val importedAtMs: Long,
)

@Entity(
    tableName = "chapters",
    indices = [Index("packId")],
    foreignKeys = [ForeignKey(PackEntity, ["id"], ["packId"], onDelete = CASCADE)]
)
data class ChapterEntity(
    @PrimaryKey val id: String,           // chapter_2
    val packId: String,
    val order: Int,
    val title: String,
    val subtitle: String?,
    val storyFile: String?,
)

@Entity(
    tableName = "lessons",
    indices = [Index("packId"), Index("chapterId")],
    foreignKeys = [ForeignKey(ChapterEntity, ["id"], ["chapterId"], onDelete = CASCADE)]
)
data class LessonEntity(
    @PrimaryKey val id: String,           // lesson_23_B07
    val packId: String,
    val chapterId: String?,               // null для packов без chapters (manifest v1)
    val order: Int,
    val title: String,
    val cefrLevel: String?,
    val grammarChipKey: String?,
)

@Entity(
    tableName = "cards",
    indices = [Index("lessonId"), Index("packId"), Index(value=["lessonId","ord"])]
)
data class CardEntity(
    @PrimaryKey val id: String,           // card_2 ... card_15 (CSV line-based)
    val packId: String,
    val lessonId: String,
    val ord: Int,                         // позиция в CSV (после заголовка)
    val type: String,                     // SENTENCE / VERB_DRILL / AUX_DRILL
    val promptRu: String,
    val acceptedAnswersJson: String,      // JSON array (TypeConverter)
    val tense: String?,
    val verb: String?,
    val verbGroup: String?,
    val person: String?,
    val frequencyRank: Int?,              // для sortByFrequency
)
```

> **Card IDs** (из CSV): `CsvParser` строит `card_<lineNumber>` (строка 1 — заголовок, «съедается»).
> Реальные ID урока: `card_2 … card_15`. Это важно для миграции — `card_1` НЕ существует.

### Сессия (решает баг card_15)

```kotlin
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,           // "<mode>:<packId>:<lessonId>" или "<mode>:<packId>"
    val packId: String,
    val lessonId: String?,
    val mode: String,                     // LESSON / VERB_DRILL / DAILY_TRANSLATE / DAILY_VERBS / AUX_DRILL / POMODORO
    val subLessonIndex: Int,              // активный под-урок
    val cursorIndex: Int,                 // позиция в пуле
    val currentCardId: String?,           // ★ PK текущей карточки (НЕ индекс!)
    val selectedTense: String?,           // для verb drill
    val selectedGroup: String?,
    val selectedPerson: String?,
    val status: String,                   // ACTIVE / PAUSED / COMPLETED
    val state: String,                    // ACTIVE / HINT_SHOWN
    val correctCount: Int,
    val incorrectCount: Int,
    val hintCount: Int,
    val incorrectAttemptsForCard: Int,
    val completedSubLessonCount: Int,
    val activeTimeMs: Long,
    val voiceActiveMs: Long,
    val voiceWordCount: Int,
    val startedAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "session_cards",
    primaryKeys = ["sessionId", "ord"],
    indices = [Index("sessionId"), Index("cardId")],
    foreignKeys = [ForeignKey(SessionEntity, ["id"], ["sessionId"], onDelete = CASCADE)]
)
data class SessionCardEntity(
    val sessionId: String,
    val ord: Int,                         // детерминированный порядок в пуле
    val cardId: String,                   // FK → cards.id
)

@Entity(
    tableName = "session_shown_cards",
    primaryKeys = ["sessionId", "cardId"],
    indices = [Index("cardId")],
    foreignKeys = [ForeignKey(SessionEntity, ["id"], ["sessionId"], onDelete = CASCADE)]
)
data class SessionShownCardEntity(
    val sessionId: String,
    val cardId: String,
    val shownAtMs: Long,
)
```

**Resume = один атомарный SELECT:**
```sql
SELECT s.*, GROUP_CONCAT(sc.cardId) AS poolCardIds
FROM sessions s
LEFT JOIN session_cards sc ON sc.sessionId = s.id
WHERE s.id = :id AND s.status = 'ACTIVE'
GROUP BY s.id
```
`currentCardId` гарантированно есть в пуле (одна транзакция при сохранении). Если пул пересобрался
(hidden/sessionSize) — валидация: если `currentCardId` не в новом пуле → явный recovery, не подмена.

### Mastery / прогресс уроков

```kotlin
@Entity(
    tableName = "mastery_states",
    indices = [Index(value=["packId","lessonId"], unique=true), Index("dueAtMs")]
)
data class MasteryStateEntity(
    @PrimaryKey val id: String,           // "<packId>:<lessonId>"
    val packId: String,
    val lessonId: String,
    val uniqueCardShows: Int,
    val totalCardShows: Int,
    val lastShowDateMs: Long,
    val intervalStepIndex: Int,           // индекс в INTERVAL_LADDER_DAYS (legacy)
    val fsrsStateJson: String?,           // FSRS Card state (для FSRS-Kotlin v6)
    val dueAtMs: Long,                    // ★ индекс для SRS-выборки
    val completedAtMs: Long?,
)

@Entity(
    tableName = "shown_cards",
    primaryKeys = ["packId","lessonId","cardId"],
    indices = [Index("cardId")]
)
data class ShownCardEntity(
    val packId: String,
    val lessonId: String,
    val cardId: String,
)

@Entity(
    tableName = "card_encounters",
    primaryKeys = ["packId","lessonId","cardId"]
)
data class CardEncounterEntity(
    val packId: String,
    val lessonId: String,
    val cardId: String,
    val count: Int,
)
```

### Streak / геймификация

```kotlin
@Entity(tableName = "streaks")
data class StreakEntity(
    @PrimaryKey val languageId: String,
    val currentStreak: Int,
    val longestStreak: Int,
    val lastCompletionDateMs: Long?,
    val totalSubLessonsCompleted: Int,
    val todayFireCount: Int,
    val lastFireDateMs: Long?,
)

@Entity(
    tableName = "streak_practice_today",
    primaryKeys = ["languageId","practiceType"]
)
data class StreakPracticeTodayEntity(
    val languageId: String,
    val practiceType: String,             // TRANSLATION / VOCAB / VERB
)
```

### Прочее user-state

```kotlin
@Entity(tableName = "hidden_cards")
data class HiddenCardEntity(@PrimaryKey val cardId: String, val hiddenAtMs: Long)

@Entity(
    tableName = "bad_sentences",
    primaryKeys = ["packId","cardId"],
    indices = [Index("cardId")]
)
data class BadSentenceEntity(
    val packId: String,
    val cardId: String,
    val languageId: String,
    val sentence: String,
    val translation: String,
    val mode: String,                     // training / verb_drill
    val addedAtMs: Long,
)

@Entity(tableName = "drill_progress", indices = [Index(value=["packId","drillType"], unique=true)])
data class DrillProgressEntity(
    @PrimaryKey val id: String,           // "<packId>:<drillType>"
    val packId: String,
    val drillType: String,                // VERB / AUX / VOCAB
    val comboKey: String?,                // group:tense (verb) / verb:tense (aux)
    val totalCards: Int,
    val everShownCardIdsJson: String,     // Set<String> (для verb/aux combo progress)
    val todayShownCardIdsJson: String,
    val lastDate: String?,                // ISO yyyy-MM-dd
    val cursor: Int,                      // для vocab drill
    val updatedAtMs: Long,
)

@Entity(tableName = "chapter_progress", primaryKeys = ["packId","chapterId"])
data class ChapterProgressEntity(
    val packId: String,
    val chapterId: String,
    val lessonsStarted: Int,
    val lessonsCompleted: Int,
    val lastAccessedMs: Long,
)

@Entity(tableName = "daily_cursors", primaryKeys = ["packId"])
data class DailyCursorEntity(
    val packId: String,
    val sentenceOffset: Int,
    val currentLessonIndex: Int,
    val verbOffset: Int,
    val firstSessionDate: String?,
    val firstSessionSentenceCardIdsJson: String,
    val firstSessionVerbCardIdsJson: String,
    val firstSessionLessonId: String?,
    val updatedAtMs: Long,
)

@Entity(tableName = "bg_vocab_marks", indices = [Index("word", unique=true)])
data class BgVocabMarkEntity(@PrimaryKey val word: String, val mark: String, val updatedAtMs: Long)

@Entity(tableName = "bg_vocab_position")
data class BgVocabPositionEntity(@PrimaryKey val key: String, val word: String, val updatedAtMs: Long)

@Entity(tableName = "pomodoro_history", indices = [Index("completedAtMs")])
data class PomodoroHistoryEntity(
    @PrimaryKey val id: String,
    val languageId: String,
    val packId: String?,
    val lessonId: String?,
    val completedAtMs: Long,
    val durationMinutes: Int,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val cardsShown: Int,
    val cardsCorrect: Int,
    val cardsIncorrect: Int,
    val wordsPerMinute: Double,
)

@Entity(tableName = "migratable_files")
data class MigrationFlagEntity(@PrimaryKey val key: String, val done: Boolean, val migratedAtMs: Long)
```

## Индексы — сводка

| Таблица | Индекс | Назначение |
|---|---|---|
| `mastery_states` | `(packId, lessonId)` unique | точечный lookup |
| `mastery_states` | `dueAtMs` | ★ SRS schedule due |
| `cards` | `(lessonId, ord)` | сбор карт урока по порядку |
| `cards` | `packId`, `lessonId` | фильтры |
| `session_cards` | `sessionId` | resume-выборка пула |
| `session_shown_cards` | `sessionId`, `cardId` | shown-set проверка |
| `streaks` | `languageId` (PK) | один streak на язык |
| `pomodoro_history` | `completedAtMs` | графики |
| `bad_sentences` | `cardId` | отчёт по карте |

## TypeConverters

```kotlin
class Converters {
    @TypeConverter fun listToJson(v: List<String>?): String = v?.let { Json.encodeToString(it) } ?: "[]"
    @TypeConverter fun jsonToList(s: String?): List<String> = s?.let { Json.decodeFromString(it) } ?: emptyList()
    @TypeConverter fun setToJson(v: Set<String>?): String = ...
    @TypeConverter fun jsonToSet(s: String?): Set<String> = ...
    @TypeConverter fun mapToJson(v: Map<String,Int>?): String = ...
    @TypeConverter fun jsonToMap(s: String?): Map<String,Int> = ...
}
```

## WAL режим (производительность + конкурентный доступ)

```kotlin
Room.databaseBuilder(context, GrammarMateDb::class.java, "grammarmate_v2.db")
    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
    .addMigrations(*ALL_MIGRATIONS)
    .build()
```
Писатель не блокирует читателей. Заменяет 24 `ReentrantLock` из v1.

# GrammarMate v2 — Architecture

> Ветка: `rewrite/v2-clean-architecture`. Полный реврайт на чистую архитектуру.
> Главная цель: **прочный регрессионный механизм, ничего не разваливается**, как баг `card_15`
> (текущая карточка терялась при resume из-за `currentIndex` без состава пула).

## Контекст бага, который убивает всё

В v1 сессия = `currentIndex: Int`, а массив карточек — рантайм-вычисление (`buildSessionCards`).
При resume пул пересобирался → индекс начинал указывать на другую карту → card_15 «выпадала».

**Инвариант v2:** текущая карточка идентифицируется по `currentCardId` (первичный ключ),
а не по позиции в массиве. Сессия персистится как **снимок целиком** (курсор + пул + shown-set)
в **одной транзакции** БД.

---

## Стек (зафиксирован)

| Слой | Технология | Версия | Почему |
|---|---|---|---|
| Gradle | wrapper | **8.9** | уже стоит; AGP 8.x совместим |
| AGP | com.android.application | **8.7.3** | стабильная ветка 8.x; НЕ 9.0 (свежая, риски KSP/Hilt) |
| Kotlin | org.jetbrains.kotlin.android | **2.0.21** | нужен для Compose Gradle plugin + Room KSP |
| Compose | androidx.compose (BOM) | **2024.10.00** + Compose Compiler Gradle plugin 2.0.21 | M3 Expressive + Adaptive |
| Persistence | androidx.room | **2.6.1** | user-state, транзакции (убивает card_15) |
| Annotation proc | com.google.devtools.ksp | **2.0.21-1.0.28** | Room + Hilt единый processor-стек |
| DI | dagger.hilt.android | **2.52** | constructor injection, тестируемость |
| Настройки | androidx.datastore | **1.1.1** | Preferences DataStore для key-value |
| SRS | FSRS-Kotlin | **v6** (open-spaced-repetition/FSRS-Kotlin) | современная SRS взамен самописной |
| Локальный AAR | Sherpa-ONNX | `libs/sherpa-onnx-static-link-onnxruntime-1.12.40.aar` | TTS/ASR — оставить как есть |

> Совместимость проверена: AGP 8.7.3 + Kotlin 2.0.21 + KSP 2.0.21-1.0.28 + Room 2.6.1 + Hilt 2.52 —
> выверенный набор (AGP 8.7 поддерживает KSP и Compose Compiler plugin, без перехода на AGP 9).

---

## Архитектура слоёв

```
┌─────────────────────────────────────────────────────┐
│  UI (app:ui)  — Compose M3 Adaptive экраны           │
│  ContainerHost, MviViewModel.consume(intents)        │
├─────────────────────────────────────────────────────┤
│  Presentation MVI                                    │
│  Intent (sealed) → Reducer (pure) → State (single)   │
├─────────────────────────────────────────────────────┤
│  Domain (pure Kotlin, НЕТ Android)                   │
│  UseCase-ы, доменные модели, FSRS scheduler,         │
│  SessionEngine (currentCardId как PK), SrsScheduler  │
├─────────────────────────────────────────────────────┤
│  Data                                                │
│  Room (user-state) | Files (content packs) |         │
│  DataStore (settings) | Repositories impl            │
├─────────────────────────────────────────────────────┤
│  Platform                                            │
│  Room/SQLite | FileSystem | DataStore | Sherpa-ONNX  │
└─────────────────────────────────────────────────────┘
```

### Правила слоёв
- **Domain** — pure Kotlin-модуль, ноль Android-импортов. Тестируется на чистом JVM.
- **Data** — реализует интерфейсы из domain. Room-entities — детали реализации.
- **Presentation** — MVI: `ViewState` immutable, `Intent` sealed, `Reducer` чистая функция.
- **UI** — глупые composables, только `state → UI`, `action → intent`.

---

## Persistence (гибрид) — решение дебата Room vs SQLDelight vs гибрид

Три субагента-дебата проведены. **Выбран гибрид** (медиатор-аргумент):

- **Контент паков (read-only):** CSV уроков, stories, grammar chips, `bg_vocab_*.csv`, аудио `.opus`/`.wav` —
  остаются файлами. Приходят через `PackImporter`, write-once. Грузить 12000 строк вокаба в SQLite
  ради последовательного чтения — хуже потокового CSV-парсера.
- **User-state (мутабельный, транзакционный):** Room, **одна БД** = один transaction boundary.
  Это и есть фикс card_15: завершение под-урока пишет mastery + progress + streak + session
  внутри **одной `@Transaction`**.
- **Настройки (key-value):** DataStore Preferences. `AppConfig`, `Profile`, theme, input-mode flags.

### Room schema (user-state)
См. `ROOM_SCHEMA.md`. Ключевые таблицы: `sessions`, `session_cards`, `mastery_states`,
`shown_cards`, `card_encounters`, `streaks`, `streak_practice_today`, `hidden_cards`,
`bad_sentences`, `drill_progress`, `chapter_progress`, `bg_vocab_marks`, `bg_vocab_position`,
`pomodoro_history`, `settings` (флаги миграции).

**Критично для бага card_15:**
```kotlin
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val currentCardId: String?,   // ← PK карточки, НЕ индекс массива
    val cursorIndex: Int,
    val status: String,           // ACTIVE/PAUSED/COMPLETED
    ...
)
@Entity(tableName = "session_cards", primaryKeys = ["sessionId","ord"])
data class SessionCardEntity(val sessionId: String, val ord: Int, val cardId: String)
```
Resume = один SELECT сессии + её пула. `currentCardId` резолвится по пулу; если его нет —
явное восстановление, а не молчаливая подмена.

---

## MVI: Single Source of Truth

```kotlin
data class TrainingViewState(          // immutable, single source of truth
    val currentCard: CardUiModel?,
    val session: SessionState,
    val progress: ProgressState,
    val inputMode: InputMode,
    val effects: List<UiEffect>,
)

sealed interface TrainingIntent {
    data object StartSession : TrainingIntent
    data class SubmitAnswer(val text: String) : TrainingIntent
    data class FlagCard(val cardId: String) : TrainingIntent
    data object Resume : TrainingIntent
    ...
}
// Reducer — чистая функция, легко тестируется
```

Один редюсер на экран. Никаких 15 feature-объектов, пишущих в общий state через `copy(...)`.

---

## Регрессионная защита (приоритет №1)

- **Domain-тесты на чистом JVM** (без Robolectric): SRS-расчёты, SessionEngine resume.
- **Room-тесты**: `Room.inMemoryDatabaseBuilder`, фикстуры.
- **Критический регрессионный тест** (именно баг card_15):
  старт сессии → snapshot → изменить пул (hidden/sessionSize) → resume →
  `assert currentCardId unchanged`.
- **Migration-тесты**: `MigrationTestHelper`, данные из YAML → Room без потерь.

---

## Миграция данных пользователя (одноразовая, транзакционная)

`YamlToRoomMigrator` запускается при первом запуске v2:
1. Читает старые YAML (`mastery.yaml`, `streak_*.yaml`, `lesson_progress_*.yaml`, etc.)
2. Вставляет в Room **в одной транзакции**.
3. Флаг `migration_yaml_done = true` в DataStore → идемпотентность.
4. Старые YAML архивируются (НЕ удаляются) — путь отката.

Полный список мигрируемых файлов = `BackupFileCollector` + `lesson_progress_*.yaml` +
`daily_cursor_*.yaml` (которые в старом бэкап не входили).

---

## Фазы реализации

| Фаза | Что | Статус |
|---|---|---|
| 0 | Этот документ + ROOM_SCHEMA.md | ✅ done |
| 1 | Build config (KSP/Hilt/Room/DataStore/Kotlin 2.0/AGP 8.7.3) | ✅ done (BUILD SUCCESSFUL) |
| 2 | Domain-слой (18 файлов: модели, FSRS v6, репозитории) | ✅ done |
| 3 | Data-слой (21 entity, 5 DAO, Database WAL, 6 repos, Hilt) | ✅ done |
| 4 | Yaml→Room мигратор (транзакционный, идемпотентный) | ✅ done |
| 5 | MVI core (MviViewModel, Reducer, Compose extensions) | ✅ done |
| 6 | Session engine (currentCardId PK — баг card_15 убит) | ✅ done |
| 7 | UI Compose M3 Adaptive (Theme, Nav, Home, Training) | ✅ done |
| 8 | Регрессионные тесты (72/72 pass) | ✅ done |
| 9 | Аудио/голос (Sherpa-ONNX за чистым интерфейсом) | ✅ done (каркас + TODO) |

## Итог (ветка `rewrite/v2-clean-architecture`)

**Регрессионная защита:** 72 unit-теста, 0 failures.
- `SessionEngineResumeRegressionTest` (11) — баг card_15 мёртв и доказан:
  resume сохраняет `currentCardId` после пересборки пула.
- `SrsSchedulerTest` (33) + `SrsParamsTest` (10) — FSRS v6 формулы верифицированы
  против py-fsrs (R(t=S)=0.9, монотонность, переходы состояний).
- `SrsMigrationTest` (10) — миграция со старой интервальной лестницы.
- `SubLessonSchedulerTest` (8).

**Структура:** 4 checkpoint-коммита. Domain — чистый Kotlin (0 Android),
тестируется на JVM без Robolectric. Data — Room (одна БД = одна транзакция = фикс
card_15). Presentation — MVI с pure reducer. UI — Compose M3 Adaptive.

**Что осталось (фоллоу-ап, НЕ блокирует фундамент):**
- Settings/VerbDrill/DailyPractice/ChapterLessons экраны (сейчас Placeholder).
- Полная реализация TTS/ASR в SherpaAudioRepository (speak/recognizeSpeech —
  каркас с TODO на миграцию логики из legacy AudioCoordinator/TtsEngine/AsrEngine).
- Pack import (CSV/YAML → Room) для загрузки контента паков.
- Backup/Restore через Room snapshot.
- Иконки/ассеты (mipmap) — наследуются от v1.

## Источники решений
- [FSRS-Kotlin v6](https://github.com/open-spaced-repetition/FSRS-Kotlin) — современный SRS.
- [AGP 9.0 compatibility](https://github.com/google/dagger/issues/4944) — почему НЕ 9.0 (KSP/Hilt риски).
- [Room offline-first 2026](https://medium.com/@ramadan123sayed/room-offline-first-architecture-the-complete-guide-for-android-in-2026-962ecd56a9ca).
- [MVI reducer pattern](https://proandroiddev.com/pure-mvi-mvi-with-reducer-and-mui-with-state-machine-43a0ec4b629f).
- [Compose M3 Adaptive](https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive).

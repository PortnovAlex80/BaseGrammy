# GrammarMate — Целевая архитектура (канон)

**Статус:** ACCEPTED — спонсор подтвердил (2026-07-07): v2 сохраняется как base.
**Назначение:** единая точка входа в архитектуру для saga-workers, аналитиков, архитекторов.
**Не дублирует:** детальные документы (они — авторитет). Этот документ — карта и решения.

---

## 1. Что канонично (authority documents)

| Документ | Что фиксирует | Авторитет для |
|---|---|---|
| [`docs/v2-architecture/ARCHITECTURE.md`](../v2-architecture/ARCHITECTURE.md) | Слои, стек, card_15 инвариант, MVI, hybrid persistence, фазы 0-9 | Архитектурные решения |
| [`docs/v2-architecture/ROOM_SCHEMA.md`](../v2-architecture/ROOM_SCHEMA.md) | 29 Room entities, 6 DAO, таблицы sessions/session_cards/mastery_states | Data layer (E02) |
| [`docs/requirements/legacy-coverage-checklist.md`](../requirements/legacy-coverage-checklist.md) | Покрытие legacy-фич → эпики (141 covered + 9 GAP patched) | Coverage gate |
| SRS-001/002/003 (`docs/requirements/REQ-00{1,2,3}-*/02-srs.md`) | Контракты портов, FR/NFR, API contracts | Formalization |

**При конфликте документов:** code is source of truth (конвенция CLAUDE.md). SRS описывает целевые контракты; если код v2 расходится с SRS — код указывает на gap, SRS фиксирует intent.

---

## 2. Решение спонсора: v2 = BASE (Q2, 2026-07-07)

**Контекст:** обзорная оценка v2 как «плохо сделанного, с пропущенным функционалом» потребовала проверки фундамента перед решением сохранить/удалить.

**Проверка фактами (2026-07-07, запущено спонсором):**
```
./gradlew :app:testDebugUnitTest --tests "com.alexpo.grammermate.v2.core.domain.*"
→ BUILD SUCCESSFUL in 1m 52s
→ 314 @Test выполнено, 0 failures, 0 errors, 0 skipped
```

| Компонент | Тесты | Вердикт |
|---|---|---|
| FSRS-v6 (SrsScheduler+SrsParams+SrsMigration) | 53 | ✅ настоящий py-fsrs порт |
| SessionEngine card_15 fix | 11 | ✅ инвариант currentCardId=PK держится |
| 6 калькуляторов прогресса | 106 | ✅ геймификация работает |
| CardSessionStateMachine + HintCalculator + WordBankGenerator + MixedReview | 77 | ✅ тренировочные алгоритмы |
| AnswerValidator + Normalizer | 59 | ✅ валидация ответов |
| SubLessonScheduler | 8 | ✅ детерминированная нарезка |

**Страх «гнилого фундамента» НЕ подтвердился.** v2-домен архитектурно здоров и регрессионно защищён. Удаление = потеря 314 тестов + card_15 fix + FSRS-v6 + 9 портов + Room schema (29 entities).

**Решение:** v2 сохраняется как base. Эпики Wave 0 (E01-E03) завершают пробелы, не переписывают готовое.

---

## 3. Слои (canonical)

```
┌─────────────────────────────────────────────────────────────┐
│  UI (app:ui) — Compose M3 Adaptive экраны                    │
│  ContainerHost, MviViewModel.consume(intents)                │
├─────────────────────────────────────────────────────────────┤
│  Presentation MVI                                            │
│  Intent (sealed) → Reducer (pure) → State (single, immutable)│
├─────────────────────────────────────────────────────────────┤
│  Domain (:domain — pure Kotlin, НЕТ Android) [E01]           │
│  UseCase-ы, доменные модели, FSRS-v6, SessionEngine,         │
│  CardSessionStateMachine, 9 портов, калькуляторы прогресса   │
├─────────────────────────────────────────────────────────────┤
│  Data (app/.../v2/core/data) [E02, E03]                      │
│  Room (user-state) | Files (content packs) |                 │
│  DataStore (settings) | Repositories impl | Audio adapter    │
├─────────────────────────────────────────────────────────────┤
│  Platform                                                    │
│  Room/SQLite | FileSystem | DataStore | Sherpa-ONNX          │
└─────────────────────────────────────────────────────────────┘
```

### Правила слоёв (несокращаемо)
- **Domain** — pure Kotlin, ноль Android-импортов. Тестируется на чистой JVM (без Robolectric).
  Зависимость направлена строго внутрь: `:app → :domain`. `:domain` не зависит ни от `:app`, ни от Android, ни от Room/Hilt/Sherpa.
- **Data** — реализует интерфейсы портов из domain. Room-entities — детали реализации, домен о них не знает.
- **Presentation** — MVI: `ViewState` immutable, `Intent` sealed, `Reducer` чистая функция (без side-effects, без `System.currentTimeMillis()`).
- **UI** — глупые composables, только `state → UI`, `action → intent`.

Подробности: [`v2-architecture/ARCHITECTURE.md` §Архитектура слоёв](../v2-architecture/ARCHITECTURE.md).

---

## 4. card_15 инвариант (первопричина rewrite)

**Баг v1:** текущая карточка сессии идентифицировалась индексом массива (`currentIndex: Int`), а пул пересобирался в рантайме (`buildSessionCards`). При resume пул пересобирался → индекс указывал на чужую карту → «card_15 терялась».

**Инвариант v2 (regression-locked, 11 тестов):**
- Текущая карта = **первичный ключ** (`SessionSnapshot.currentCardId`), не позиция в массиве.
- Сессия персистится как **снимок целиком** (курсор + пул + shown-set) в **одной транзакции** Room.
- Resume = один SELECT сессии + её пула. `currentCardId` резолвится по пулу.
- Если `currentCardId` выпал из пула (hidden/sessionSize change) → **явное восстановление** (re-add to pool tail), не молчаливая подмена.

**AC (E01 AC-5, AC-6):** любое изменение SessionEngine НЕ должно ломать эти 11 тестов. Смотреть `SessionEngineResumeRegressionTest`.

---

## 5. 9 доменных портов (контракты, immutable после E01)

Расположение: `app/src/main/java/com/alexpo/grammermate/v2/core/domain/{repository,audio}/`.

| Порт | Файл | Отвечает за | Используется в |
|---|---|---|---|
| `ContentRepository` | `repository/ContentRepository.kt` | Read-only контент: packs, lessons, cards, chapters, stories, grammar chips | E02 (impl), E04-E09 (consumer) |
| `SessionRepository` | `repository/SessionRepository.kt` | Persist/resume сессий (card_15 инвариант) | E02 (impl), E04 |
| `MasteryRepository` | `repository/MasteryRepository.kt` | LessonMasteryState (uniqueCardShows, intervalStep, encounterCounts) | E02 (impl), E10 |
| `ProgressRepository` | `repository/ProgressRepository.kt` | TrainingProgress, completion flags | E02 (impl), E10 |
| `UserContentRepository` | `repository/UserContentRepository.kt` | Hidden cards, bad sentences | E02 (impl), E04 |
| `VocabDrillRepository` | `repository/VocabDrillRepository.kt` | Vocab sprint progress, WordMastery | E02 (impl), E06 |
| `SettingsRepository` | `repository/SettingsRepository.kt` | AppConfig (15 полей), theme, inputMode | E02 (impl), E13 |
| `AudioRepository` | `audio/AudioRepository.kt` | TTS speak→Flow<AudioEvent>, ASR recognize→Flow<RecognitionEvent>, SoundEffect | E03 (impl), E04-E09 |
| `AudioModelRepository` | `audio/AudioModelRepository.kt` | Download/status TTS+ASR models, Bluetooth mic | E03 (impl), E13 |

**Правило:** сигнатуры портов НЕ изменяются после E01. Любое расширение = отдельная задача с trace `derived_from` к SRS. Wave 1-4 эпики **потребляют** порты, не меняют их.

**GAP C2 (HIGH):** `TrainingStateAccess` successor — cross-cutting доменный порт для feature-модулей (Boss/Daily/Pomodoro/Vocab/Progress/Story/Settings читают/пишут training state). Должен быть определён в E01 ДО старта Wave 1. AC-18 E01.

Подробности контрактов (сигнатуры suspend/Flow, value-class IDs): [`SRS-001 §5 API contract`](../requirements/REQ-001-domain-core/02-srs.md).

---

## 6. Persistence (гибрид — decision locked)

**Три субагента-дебата проведены ранее. Выбран гибрид** (см. `v2-architecture/ARCHITECTURE.md` §Persistence):

| Тип данных | Хранилище | Почему |
|---|---|---|
| **Контент паков (read-only)** | Files (CSV, stories MD, grammar chips JSON, bg_vocab CSV, audio .opus/.wav) | 12000 строк вокаба в SQLite ради sequential read — хуже потокового CSV-парсера. Write-once через PackImporter. |
| **User-state (мутабельный, транзакционный)** | Room (одна БД = один transaction boundary) | Фикс card_15: завершение под-урока пишет mastery + progress + streak + session в одной `@Transaction`. |
| **Настройки (key-value)** | DataStore Preferences | AppConfig (15 полей), Profile, theme, inputMode flags |

**Room schema:** 29 entities, 6 DAO (ContentDao, SessionDao, MasteryDao, ProgressDao, DrillDao, UserContentDao), WAL включён, `exportSchema=true` (schema export в `$projectDir/schemas` для migration-тестов). Подробности: [`ROOM_SCHEMA.md`](../v2-architecture/ROOM_SCHEMA.md).

**Критичная таблица (card_15):**
```kotlin
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val currentCardId: String?,   // ← PK карточки, НЕ индекс массива
    val cursorIndex: Int,
    val status: String,           // ACTIVE/PAUSED/COMPLETED
)
@Entity(tableName = "session_cards", primaryKeys = ["sessionId","ord"])
data class SessionCardEntity(val sessionId: String, val ord: Int, val cardId: String)
```

---

## 7. Стек (зафиксирован, не менять без ADR)

| Слой | Технология | Версия | Почему |
|---|---|---|---|
| Gradle | wrapper | **8.9** | AGP 8.x совместим |
| AGP | com.android.application | **8.7.3** | стабильная ветка 8.x; НЕ 9.0 (KSP/Hilt риски) |
| Kotlin | org.jetbrains.kotlin.android | **2.0.21** | Compose Gradle plugin + Room KSP |
| Compose | androidx.compose BOM | **2024.10.00** + Compiler plugin 2.0.21 | M3 Expressive + Adaptive |
| Persistence | androidx.room | **2.6.1** | user-state, транзакции (убивает card_15) |
| Annotation proc | com.google.devtools.ksp | **2.0.21-1.0.28** | Room + Hilt единый processor |
| DI | dagger.hilt.android | **2.52** | constructor injection, тестируемость |
| Настройки | androidx.datastore | **1.1.1** | Preferences DataStore |
| SRS | FSRS-Kotlin | **v6** | современная SRS (не Ebbinghaus из legacy) |
| Audio | Sherpa-ONNX | local AAR `libs/sherpa-onnx-static-link-onnxruntime-1.12.40.aar` | TTS/ASR offline |

**compileSdk/targetSdk 35, minSdk 26, versionName 2.0.0.**

**Build (конвенция CLAUDE.md):** Windows wrapper (НЕ gradlew):
```
"C:/Users/user/.jdks/corretto-21.0.8/bin/java.exe" \
  -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" \
  org.gradle.wrapper.GradleWrapperMain <task>
```
JAVA_HOME = `C:/Users/user/.jdks/corretto-21.0.8` (JBR не имеет jlink).

---

## 8. Регрессионная защита (приоритет №1)

**314 @Test функций / 17 классов — все зелёные (verified 2026-07-07).**

| Категория | Тестов | Защищает |
|---|---|---|
| FSRS-v6 (SrsScheduler, SrsParams, SrsMigration) | 53 | SRS-формулы против py-fsrs |
| SessionEngine resume (card_15) | 11 | Баг v1 не возвращается |
| 6 калькуляторов прогресса | 106 | CEFR/Flower/Streak/Ladder/Completion/Progress |
| CardSessionStateMachine + Hint + WordBank + MixedReview | 77 | Тренировочные алгоритмы |
| AnswerValidator + Normalizer | 59 | Валидация ответов |
| SubLessonScheduler | 8 | Детерминированная нарезка по PK |

**Правило:** любой PR, ломающий эти тесты = changes_requested. Тесты переносятся в `:domain/src/test` при E01 без изменения утверждений.

---

## 9. MVI pattern (Presentation layer)

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
}
// Reducer — чистая функция (MviReducer<S, I>), легко тестируется
```

**Правила:**
- Один редюсер на экран.
- `Reducer` — pure function: нет I/O, нет `System.currentTimeMillis()` (clock injected в ViewModel/Engine).
- `MviEffect` — one-shot side-effects (navigation, snackbar, TTS trigger), НЕ живут в state (не переэмитятся на recomposition).
- `MviViewModel` — `StateFlow<S>` (atomic via `MutableStateFlow.update`) + `Channel<E>(BUFFERED)` → `receiveAsFlow()`.

Подробности: `app/.../v2/core/ui/{MviIntent,MviReducer,MviViewModel,UiState,ComposeExtensions}.kt`.

---

## 10. Связь с saga-эпиками (Wave 0-4)

| Эпик | Архитектурный scope | Ссылка |
|---|---|---|
| **E01** Domain core + `:domain` module | Завершение домена, извлечение `:domain` Gradle модуля, GAP C2 TrainingStateAccess | [SRS-001](../requirements/REQ-001-domain-core/02-srs.md) |
| **E02** Data/Room + PackImport + Backup | PackImporter→Room, Backup/Restore Room snapshot, lesson_progress, LanguageManager seed, migrator deprecate | [SRS-002](../requirements/REQ-002-data-room/02-srs.md) |
| **E03** Audio adapter | Завершение SherpaAudioRepository (TTS VITS-Piper+Kokoro LRU, ASR Whisper+SileroVad, SegmentPlayer, MemoryChecker) | [SRS-003](../requirements/REQ-003-audio/02-srs.md) |
| **E04-E07** Drill MVP (Training/Verb/Vocab/Daily) | Feature-эпики, потребляют доменные порты + audio + content | Formalization TBD |
| **E08-E09** Audio MVP (BgVocab/Story) | Foreground service, story narration (SegmentPlayer shared с E03) | Formalization TBD |
| **E10-E12** Gamification (Progress/Boss/Pomodoro) | Калькуляторы прогресса, boss battles, pomodoro timer | Formalization TBD |
| **E13** Content mgmt + Settings + AppShell | SettingsSheet (35 rows), NavHost 14 routes, Backup UI, GrammarChips, route back-compat | Formalization TBD |

**Зависимости (DAG):** Wave 0 (E01→E02,E03) → Wave 1 (E04→E05,E06,E07) → Wave 2 (E03→E08,E09) → Wave 3 (E01,E04→E10,E11,E12) → Wave 4 (E02→E13).

---

## 11. Известный техдолг (не «гниль», а незавершённость)

Все пункты уже как FR с AC в formalization — они запланированы к исправлению, не скрыты:

| Техдолг | FR | Эпик | Статус |
|---|---|---|---|
| `MultilingualStoryParser` в data-слое с `android.util.Log` + `java.io.File` | FR-12 | E01 | Перенести в `:domain` как pure Kotlin |
| `YamlToRoomMigrator` (мёртвый код, Q3 greenfield) | FR-13 | E02 | `@Deprecated` / удалить |
| Папка `v2/` в структуре пакетов | structural | E01 | Переименовать в `com.alexpo.grammermate.{domain,data,ui}` при извлечении `:domain` |
| `TrainingViewModel` skeleton (flagCard no-op, нет TTS/VOICE) | scope | E04 | Завершить в Wave 1 |
| `SherpaAudioRepository` skeleton (Started→Failed TODOs) | scope | E03 | Завершить в Wave 0 |
| 2 экрана из 14 (Home + Training-minimal) | scope | E04-E13 | Постепенно в Wave 1-4 |

**Ни один пункт не является архитектурной ошибкой.** Все — незавершённость (scope) или техдолг с планом исправления.

---

## 12. Конвенции проекта (несокращаемо)

- **Документы на русском** (размышлять можно на английском). Зафиксировано спонсором.
- **Git:** features в `feature/xxx`, NEVER commit to `main`, NEVER push без approval, `Co-Authored-By` footer в коммитах.
- **Mandatory subagent delegation** (CLAUDE.md): ≥3 файлов / new class / >30 lines / spec+code / TrainingViewModel / sequential deps → делегировать subagent.
- **Business rules:** WORD_BANK ≠ mastery (только VOICE/KEYBOARD растят цветы); Learned = step ≥ 3; ChapterStatus только ACTIVE/DONE (LOCKED удалён); pack-scoped drills (hasVerbDrill проверяет active pack manifest).
- **Spec authority:** spec ≠ code → code is source of truth, update spec after changes.
- **VerbDrill click-test rules:** drive UI через `verb_start_button`/`input_field`/`check_button`/`next_button`/`prev_button`/`session_card`; state только для assertions; never вызывать внутренние методы напрямую.

---

*Документ обновляется при архитектурных решениях (ADR). Последнее обновление: 2026-07-07 (solution v2=base confirmed, 314/314 tests green).*

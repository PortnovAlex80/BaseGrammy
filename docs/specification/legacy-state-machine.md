# Спецификация: Машина состояний навигации

## Проблема

Текущая навигация хранит `currentScreen` в `progress.yaml` и восстанавливает его при старте.
Из-за этого приложение может стартовать на TRAINING, DAILY_PRACTICE, STORY и других экранах,
где сессия уже не активна — пользователь видит вечный загрузчик или сломанный UI.

## Решение

Разделить понятия:
- **AppScreen** — текущий экран (только в памяти, НЕ персистится)
- **SessionState** — состояние активной сессии (персистится, но не определяет стартовый экран)
- **AppStartRule** — при старте ВСЕГДА HOME, без исключений

---

## 1. Допустимые переходы

```
HOME ────────────────> LESSON
HOME ────────────────> DAILY_PRACTICE
HOME ────────────────> VERB_DRILL
HOME ────────────────> VOCAB_DRILL
HOME ───(Settings)───> LADDER
HOME ───(Settings)───> STORY            (test mode only)

LESSON ──────────────> HOME             (back)
LESSON ──────────────> TRAINING         (start sub-lesson / boss / drill)
LESSON ──────────────> TRAINING         (resume drill)

TRAINING ────────────> LESSON           (exit / sub-lesson complete / boss complete)
TRAINING ───(Settings)> LADDER

DAILY_PRACTICE ──────> HOME             (exit / complete)
DAILY_PRACTICE ──────> HOME             (back)

VERB_DRILL ──────────> HOME             (back / exit)
VOCAB_DRILL ─────────> HOME             (back / exit)

STORY ───────────────> LESSON           (complete / close)

LADDER ──────────────> {caller}         (back → HOME or TRAINING)
```

### Запрещённые переходы

```
HOME ──────> TRAINING        (только через LESSON)
HOME ──────> STORY           (только через LESSON, или test mode)
HOME ──────> LADDER          (только через Settings)
* ─────────> HOME            (только по явному exit/back/complete)
App start ─> *               (старт ВСЕГДА на HOME)
```

---

## 2. Старт приложения (AppStartRule)

### Правило

При любом запуске приложения (холодный старт, восстановление после убийства процесса,
возврат из background после уничтожения Activity) — **начальный экран HOME**.

### Реализация

`TrainingProgress.currentScreen` **больше не сохраняется** в YAML.
Поле `initialScreen` в `TrainingUiState` всегда равно `"HOME"`.

Персистируются только данные сессии (lessonId, mode, sessionState, currentIndex и т.д.),
но не сам экран. ViewModel восстанавливает состояние сессии, но пользователь видит HOME
и сам решает, куда идти.

### Почему

1. Сессия может быть неактивна (истёк таймер, данные устарели, packs обновились).
2. При убийстве процесса UI-состояние (Compose mutableStateOf) теряется — его нельзя восстановить.
3. HOME — безопасный экран, который всегда корректно рендерится.

---

## 3. Состояния сессий по режимам

Каждый режим тренировки имеет свой набор состояний. ViewModel управляет переходами
внутри сессии, но навигация между экранами всегда явная.

### 3.1 Lesson Training (TRAINING)

```
IDLE ──(startSubLesson)──> ACTIVE ──(all cards done)──> COMPLETE ──(auto)──> IDLE
                                     │
                                     └──(user exit)──> IDLE
```

**Старт:** `LESSON → TRAINING` через `startSubLesson()`, `startBossLesson()`, `startBossMega()`, `startDrill()`
**Выход:** `TRAINING → LESSON` через exit dialog, sub-lesson complete, boss complete

**Гвард входа (обязательная проверка перед переходом TRAINING):**
- `state.currentCard != null` — есть карточка для отображения
- `state.sessionState == SessionState.ACTIVE` — сессия активна

**Если гвард не пройден** → оставаться на LESSON, не переходить на TRAINING.

**Токены завершения (текущий механизм):**
- `subLessonFinishedToken` меняется → `TRAINING → LESSON`
- `bossFinishedToken` меняется → `TRAINING → LESSON`

### 3.2 Daily Practice (DAILY_PRACTICE)

```
IDLE ──(startDailyPractice)──> ACTIVE ──(all sub-lessons done)──> FINISHED ──(sparkle)──> COMPLETE_SCREEN
                                       │
                                       └──(user exit)──> IDLE → HOME
```

**Старт:** `HOME → DAILY_PRACTICE` через `startDailyPractice()`
**Выход:** `DAILY_PRACTICE → HOME` через exit, complete, back

**Гвард входа:**
- `dailySession.active == true`
- `getDailyCurrentCards().isNotEmpty()`

**Если гвард не пройден** → оставаться на HOME, показать Toast "No cards available".

### 3.3 Verb Drill (VERB_DRILL)

```
LOADING → SELECTION ──(user taps "Старт")──> ACTIVE_SESSION ──(batch done)──> COMPLETE
                                                                         │
                                                                         └──(!allDoneToday)──> SELECTION
```

**Старт:** `HOME → VERB_DRILL` — просто навигация, VerbDrillViewModel управляет своим состоянием.
**Выход:** `VERB_DRILL → HOME` через back, exit button.

VerbDrillViewModel — отдельная ViewModel с собственным `VerbDrillUiState`.
Не зависит от TrainingViewModel.

### 3.4 Vocab Drill (VOCAB_DRILL)

```
LOADING → SELECTION ──(user taps "Start")──> CARD_SCREEN ──(all reviewed)──> COMPLETE
```

**Старт:** `HOME → VOCAB_DRILL` — просто навигация, VocabDrillViewModel управляет своим состоянием.
**Выход:** `VOCAB_DRILL → HOME` через back, exit button.

VocabDrillViewModel — отдельная ViewModel с собственным `VocabWordUiState`.
Не зависит от TrainingViewModel.

### 3.5 Story (STORY)

```
IDLE ──(story triggered from lesson)──> ACTIVE ──(complete / close)──> IDLE
```

**Старт:** `LESSON → STORY` — запускается как часть урока (check-in/check-out quiz).
**Выход:** `STORY → LESSON` через complete, close.

### 3.6 Ladder (LADDER)

Без состояния — экран справки. Показывает таблицу интервалов.
**Старт:** из Settings (HOME или TRAINING).
**Выход:** back → вызывающий экран.

---

## 4. Сохранение и восстановление прогресса

### Что персистируется (progress.yaml)

```yaml
languageId: "it"
mode: LESSON
lessonId: "lesson_3"
currentIndex: 5
sessionState: PAUSED
# ... числовые счётчики ...
activePackId: "pack_italian"
# Данные Daily Practice для resume:
dailyLessonIndex: 2
dailySubLessonIndex: 3
```

### ЧТО НЕ персистируется

- `currentScreen` — **удаляется из progress.yaml**
- `initialScreen` — **всегда "HOME" в памяти**

### Восстановление при старте

1. `TrainingViewModel.init` загружает `ProgressStore.load()`
2. `initialScreen = "HOME"` (захардкожено)
3. Данные сессии (lessonId, mode, currentIndex) загружаются, но пользователь видит HOME
4. На HOME кнопка "Continue Learning" ведёт на LESSON с восстановленным уроком
5. Пользователь сам решает: продолжить текущий урок, начать daily practice или drill

---

## 5. Миграция

### Шаг 1 — Удалить currentScreen из персистенции

**ProgressStore.kt:**
- Удалить `currentScreen` из `save()` (перестать писать поле в YAML)
- Удалить чтение `currentScreen` из `load()`
- Вернуть `"HOME"` как значение по умолчанию

**TrainingViewModel.kt (init):**
- Убрать `restoredScreen` логику
- `initialScreen = "HOME"` — безусловно

**TrainingUiState:**
- `initialScreen: String = "HOME"` — оставить, но никогда не менять
- `currentScreen: String = "HOME"` — оставить для ViewModel, но не персистировать

### Шаг 2 — Добавить гварды переходов

**GrammarMateApp.kt:**

| Переход | Гвард | Действие при fail |
|---------|-------|-------------------|
| `HOME → DAILY_PRACTICE` | `startDailyPractice() == true` | Toast, остаться на HOME |
| `LESSON → TRAINING` | `currentCard != null` после старта | Остаться на LESSON |
| `any → LADDER` | Нет (справочный экран) | — |

Гварды уже частично реализованы (isLoadingDaily для DAILY_PRACTICE).
Нужно добавить проверку для TRAINING.

### Шаг 3 — Очистить AppScreen enum

Удалить `ELITE` и `VOCAB` из enum — они перенаправляют на HOME и больше не используются:

```kotlin
enum class AppScreen {
    HOME,
    LESSON,
    TRAINING,
    DAILY_PRACTICE,
    STORY,
    LADDER,
    VERB_DRILL,
    VOCAB_DRILL
}
```

> **Внимание:** `parseScreen()` уже возвращает `HOME` для неизвестных значений.
> Но старые сохранённые `"ELITE"` / `"VOCAB"` в progress.yaml больше не будут читаться,
> т.к. `currentScreen` удаляется из персистенции (Шаг 1).

---

## 6. Диаграмма состояний (полная)

```
                        ┌──────────────────────────┐
                        │       App Start          │
                        │   (cold / warm / hot)    │
                        └────────────┬─────────────┘
                                     │ ALWAYS
                                     ▼
                              ┌─────────────┐
                 ┌────────────│    HOME      │────────────┐
                 │            └──────┬───────┘            │
                 │               │   │   │               │
                 │    Settings   │   │   │  Settings     │
                 │       │      │   │   │     │          │
                 ▼       ▼      │   │   │     ▼          ▼
              LADDER  LESSON◄───┘   │   └──►VERB_DRILL  VOCAB_DRILL
                │       │           │       │              │
                │       │           │       │              │
              back    start        │     back           back
                │       │           │       │              │
                │       ▼           │       │              │
                │    TRAINING◄──────┘       │              │
                │       │                   │              │
                │    complete/exit          │              │
                │       │                   │              │
                │       ▼                   │              │
                │     LESSON               │              │
                │                          │              │
                │       DailyPracticeTile  │              │
                │            │             │              │
                │            ▼             │              │
                │    DAILY_PRACTICE         │              │
                │            │             │              │
                │     exit/complete         │              │
                │            │             │              │
                │            ▼             │              │
                │          HOME            │              │
                │                          │              │
                └──────────────────────────┘──────────────┘
                            (all roads lead to HOME)
```

---

## 7. Чеклист реализации

- [ ] `ProgressStore.kt` — убрать `currentScreen` из save/load
- [ ] `TrainingViewModel.init` — убрать `restoredScreen`, захардкодить `initialScreen = "HOME"`
- [ ] `TrainingUiState` — убедиться `initialScreen` не читается из персистенции
- [ ] `GrammarMateApp.kt` — удалить редиректы `ELITE → HOME` и `VOCAB → HOME`
- [ ] `GrammarMateApp.kt` — добавить гвард на `LESSON → TRAINING` (проверка `currentCard != null`)
- [ ] `GrammarMateApp.kt` — добавить Toast при fail старта Daily Practice
- [ ] `AppScreen` enum — удалить `ELITE` и `VOCAB`
- [ ] Тест: холодный старт → HOME
- [ ] Тест: HOME → LESSON → TRAINING → exit → LESSON → back → HOME
- [ ] Тест: HOME → DAILY_PRACTICE → exit → HOME
- [ ] Тест: HOME → VERB_DRILL → back → HOME
- [ ] Тест: убийство процесса → restart → HOME (не TRAINING)

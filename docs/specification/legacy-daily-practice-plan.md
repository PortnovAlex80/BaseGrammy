# Daily Practice v2 — Sequential Sub-Lesson Pipeline

## Концепция

Daily Practice = автоматический последовательный проход подуроков.
Тот же контент, те же правила mastery, та же система интервалов — но без ручного выбора.

Пользователь открывает Daily → видит следующий непройденный подурок → проходит → следующий.
Как "авто-плей" для всей дорожки уроков.

---

## Архитектура: что меняется

### Сейчас (v1)

```
DailyPracticeScreen
  ├─ Block 1: 5 случайных SentenceCard из одного урока
  ├─ Block 2: 5 VocabWord из drill-файлов
  └─ Block 3: 5 VerbDrillCard из drill-файлов
```

### Будет (v2)

```
DailyPracticeScreen
  └─ Sub-lesson pipeline (последовательно):
      ├─ Урок 1, подурок 1  (NEW_ONLY, 10 карточек)
      ├─ Урок 1, подурок 2  (NEW_ONLY, 10 карточек)
      ├─ ...
      ├─ Урок 1, подурок N  (MIXED, 10 карточек)
      ├─ Урок 2, подурок 1  (NEW_ONLY, 10 карточек)
      ├─ ...
      └─ Урок N, подурок M
```

---

## Ключевые принципы

1. **Те же данные** — используется `MixedReviewScheduler` для генерации подуроков
2. **Тот же mastery** — `MasteryStore.recordCardShow()` вызывается при VOICE/KEYBOARD ответе
3. **WORD_BANK не считается** — как и в обычных подуроках
4. **Последовательность** — подуроки идут по порядку, без пропусков
5. **Персистенция** — текущая позиция (lessonIndex + subLessonIndex) сохраняется
6. **Взаимная видимость** — пройденное в Daily видно в обычных уроках и наоборот
7. **Resume** — при повторном входе продолжает с места остановки

---

## Связанные системы (не меняются)

| Система | Как используется |
|---------|-----------------|
| `MixedReviewScheduler` | Генерирует расписание подуроков для каждого урока |
| `MasteryStore` | Записывает uniqueCardShows, shownCardIds, интервалы |
| `SpacedRepetitionConfig` | Лестница интервалов [1,2,4,7,10,14,20,28,42,56] |
| `FlowerCalculator` | Вычисляет состояние цветка на основе mastery |
| `LessonLadderCalculator` | Определяет порядок разблокировки уроков |
| `SubLessonType` | NEW_ONLY (только новые), MIXED (новые + обзор) |

---

## Файлы для изменения

### data/

| Файл | Изменение |
|------|-----------|
| `Models.kt` | Обновить `DailySessionState`: поля `currentLessonIndex`, `currentSubLessonIndex` вместо `taskIndex`, `blockIndex` |
| `ProgressStore.kt` | Сохранять `dailyLessonIndex`, `dailySubLessonIndex` вместо `dailyLevel`, `dailyTaskIndex` |

### ui/helpers/

| Файл | Изменение |
|------|-----------|
| `DailySessionHelper.kt` | Управление позицией в пайплайне: какой урок, какой подурок |
| `DailySessionComposer.kt` | Переписать: вместо случайных карточек — брать карточки из `MixedReviewScheduler.build()` для текущего подурока |
| `DailyPracticeSessionProvider.kt` | Адаптировать: `blockCards` = карточки из текущего подурока (10 шт), `onBlockComplete` = переход к следующему подуроку |

### ui/

| Файл | Изменение |
|------|-----------|
| `DailyPracticeScreen.kt` | Убрать 3-блочную структуру, рендерить один поток подуроков. Показывать метку текущего урока/подурока. Sparkle при завершении подурока. |
| `TrainingViewModel.kt` | `startDailyPractice()` → найти следующий непройденный подурок, загрузить его карточки. `advanceDailyTask()` → перейти к следующему подуроку. Вызывать `recordCardShowForMastery()` при ответах. |
| `GrammarMateApp.kt` | Обновить колбэки для Daily Practice. Передать `onRecordMastery` в Daily. |

---

## Пошаговый план реализации

### Шаг 1: DailySessionState — новая структура позиции

```kotlin
data class DailySessionState(
    val active: Boolean = false,
    val lessons: List<Lesson> = emptyList(),       // все уроки для пайплайна
    val lessonIndex: Int = 0,                       // текущий урок (0-based)
    val subLessonIndex: Int = 0,                    // текущий подурок внутри урока
    val schedules: Map<String, LessonSchedule> = emptyMap(), // от MixedReviewScheduler
    val finishedToken: Boolean = false
)
```

### Шаг 2: DailySessionHelper — навигация по пайплайну

```kotlin
class DailySessionHelper(...) {

    fun startDailyPipeline(lessons: List<Lesson>, schedules: Map<String, LessonSchedule>) {
        // Найти первый непройденный подурок
        // Установить lessonIndex, subLessonIndex
    }

    fun getCurrentSubLessonCards(): List<SentenceCard> {
        // Вернуть карточки текущего подурока из schedule
    }

    fun advanceToNextSubLesson(): Boolean {
        // subLessonIndex++
        // Если подуроки закончились → lessonIndex++
        // Если уроки закончились → endSession()
    }

    fun calculateResumePosition(masteryStore: MasteryStore) {
        // Пройти по всем подурокам последовательно
        // Найти первый подурок, где не все карточки в shownCardIds
    }
}
```

### Шаг 3: DailySessionComposer — генерация сессии из подурока

```kotlin
class DailySessionComposer(...) {

    fun buildSubLessonSession(
        cards: List<SentenceCard>
    ): List<DailyTask> {
        // Каждый DailyTask = одна карточка подурока
        // Режим ввода ротируется: VOICE → KEYBOARD → WORD_BANK
        return cards.mapIndexed { index, card ->
            val mode = when (index % 3) {
                0 -> InputMode.VOICE
                1 -> InputMode.KEYBOARD
                else -> InputMode.WORD_BANK
            }
            DailyTask.TranslateSentence(
                id = "sub_${card.id}",
                card = card,
                inputMode = mode
            )
        }
    }
}
```

### Шаг 4: Mastery tracking — запись при ответе

В `DailyPracticeSessionProvider.onAnswerChecked` (или в TrainingViewModel):

```kotlin
// При правильном ответе VOICE/KEYBOARD:
if (correct && inputMode != InputMode.WORD_BANK) {
    masteryStore.recordCardShow(card)
}

// При завершении подурока (для WORD_BANK карточек):
masteryStore.markCardsShownForProgress(wordBankCardIds)
```

### Шаг 5: Resume — позиция из mastery

```kotlin
fun findResumePosition(
    lessons: List<Lesson>,
    schedules: Map<String, LessonSchedule>,
    masteryStore: MasteryStore
): Pair<Int, Int> {
    for ((lessonIdx, lesson) in lessons.withIndex()) {
        val schedule = schedules[lesson.id] ?: continue
        val mastery = masteryStore.loadMastery(lesson.id)

        for ((subIdx, subLesson) in schedule.subLessons.withIndex()) {
            val lessonCardIds = lesson.cards.map { it.id }.toSet()
            val allShown = subLesson.cards
                .filter { it.id in lessonCardIds }
                .all { it.id in mastery.shownCardIds }

            if (!allShown) return lessonIdx to subIdx
        }
    }
    // Всё пройдено
    return -1 to -1
}
```

### Шаг 6: UI — один поток

DailyPracticeScreen:
- Header: "Daily Practice — Урок {N}, Подурок {M}"
- Progress: глобальный прогресс по всем подурокам (текущий / всего)
- CardSessionBlock: тот же TrainingCardSession с карточками подурока
- Sparkle при завершении подурока (2 сек) → авто-переход к следующему
- После последнего подурока → CompletionScreen

### Шаг 7: Персистенция

ProgressStore сохраняет:
```yaml
dailyLessonIndex: 3
dailySubLessonIndex: 7
```

При resume:
1. Загрузить lessons и schedules
2. Вызвать `findResumePosition()` на основе mastery
3. Если сохранённая позиция < mastery-позиции → использовать mastery-позицию
4. Начать с рассчитанной позиции

---

## Порядок выполнения

1. **Шаг 1** — обновить `DailySessionState` в Models.kt
2. **Шаг 2** — переписать `DailySessionHelper` (pipeline navigation)
3. **Шаг 3** — обновить `DailySessionComposer` (sub-lesson → tasks)
4. **Шаг 4** — добавить mastery tracking в `DailyPracticeSessionProvider`
5. **Шаг 5** — обновить `TrainingViewModel.startDailyPractice()` (использовать MixedReviewScheduler)
6. **Шаг 6** — упростить `DailyPracticeScreen` (один поток)
7. **Шаг 7** — обновить PersistanceStore и GrammarMateApp
8. **Тест** — сборка, ручная проверка

---

## Что удаляется

- `DailyBlockType` enum (TRANSLATE, VOCAB, VERBS) — больше нет 3 блоков
- `VocabFlashcardBlock` из DailyPracticeScreen — vocab остаётся отдельным режимом
- `DailyTask.VocabFlashcard`, `DailyTask.ConjugateVerb` — только `TranslateSentence`
- `TENSE_LADDER` из DailySessionComposer — времена определяются уроком
- Vocab/Verb блоки из Daily — они остаются как отдельные режимы (VocabDrillScreen, VerbDrillScreen)

---

## Риски и ограничения

1. **Большой рефакторинг** — затрагивает Models, Helper, Composer, Provider, Screen, ViewModel
2. **MasteryStore threading** — запись из Daily должна быть потокобезопасной
3. **Производительность** — MixedReviewScheduler.build() для всех уроков может быть медленным. Кэшировать schedules.
4. **Обратная совместимость** — сохранённый dailyLevel/dailyTaskIndex нужно мигрировать или сбросить
5. **Vocab/Verb drills** — остаются отдельными экранами, не входят в Daily pipeline

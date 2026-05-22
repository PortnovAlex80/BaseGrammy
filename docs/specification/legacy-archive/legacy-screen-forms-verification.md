# Экранные формы. Отчёт верификации

**Дата:** 2026-05-12
**Спецификация:** Экранные формы.Спецификация.md
**Метод:** построчная сверка каждого элемента спецификации с исходным кодом

---

## Сводка по батчам

| Батч | Экраны | Проверено | Полностью совпадают | Расхождения |
|---|---|---|---|---|
| 1 | StartupScreen + HomeScreen | 34 | 34 | 0 |
| 2 | LessonRoadmapScreen | 14 | 13 | 1 |
| 3 | TrainingScreen | 70 | 62 | 8 |
| 4 | TrainingCardSession + DailyPracticeScreen | 75 | 60 | 15 |
| 5 | VerbDrillScreen + VocabDrillScreen | 105 | ~97 | ~8 |
| 6 | LadderScreen + Общие диалоги | 26 | 26 | 0 |
| **ИТОГО** | **Все экраны** | **324** | **~292** | **~32** |

**Точность спецификации: ~90%**

---

## Батч 1 — StartupScreen + HomeScreen

Результат: **34/34 полностью совпадают. Расхождений нет.**

---

## Батч 2 — LessonRoadmapScreen

Результат: **13/14 совпадают, 1 расхождение.**

| # | Элемент | Расхождение | Детали |
|---|---|---|---|
| 1 | "Exercise X of Y" | Условие | Спецификация описывает глобальный счётчик упражнений. Код реализует **поблочный (циклический) счётчик** внутри блоков по 15: `displayIndex = (completed % 15) + 1`, `displayTotal = min(15, total - cycleStart)`. При 30 упражнениях пользователь увидит "Exercise 1 of 15", а после 15-го — "Exercise 1 of 15" снова. Спецификация не описывает этот циклический механизм. |

---

## Батч 3 — TrainingScreen

Результат: **62/70 совпадают, 8 расхождений.**

| # | Элемент | Поле | Спецификация | Код |
|---|---|---|---|---|
| 1 | DrillProgressRow | Условие отображения | "Всегда" | Не отображается при `bossActive` или `eliteActive` — вместо него текстовые заголовки. Хотя DrillProgressRow вызывается во всех ветках, контекст различается. |
| 2 | Текст вопроса (promptRu) | Размер шрифта | "20sp" | `(20f * state.ruTextScale).sp` — масштабируется через слайдер в настройках. По умолчанию 20sp, но может отличаться. |
| 3 | Voice (Mic) кнопка | enabled | "Всегда" | `enabled = canLaunchVoice` (hasCards && sessionState == ACTIVE) |
| 4 | Keyboard кнопка | enabled | "Всегда" | `enabled = canSelectInputMode` (hasCards && sessionState == ACTIVE) |
| 5 | Word Bank кнопка | enabled | "Всегда" | `enabled = canSelectInputMode` (hasCards && sessionState == ACTIVE) |
| 6 | Next кнопка | Поведение | "Если complete → onComplete()" | Код вызывает `onNext(false)` — логика complete/onComplete обрабатывается внутри ViewModel, не на уровне UI. |
| 7 | "Reset/Reload" текст | Текст кнопки | "Reset/Reload" | Код: "Reset/Reload (clear + import)" |
| 8 | HeaderStats | — | Не описан в спецификации | Определён в коде (строки 2619-2666), но **не вызывается** — мёртвый код. Спецификация корректно его не описывает. |

---

## Батч 4 — TrainingCardSession + DailyPracticeScreen

Результат: **60/75 совпадают, 15 расхождений.**

### Раздел 4a — TrainingCardSession (общие компоненты)

| # | Элемент | Поле | Спецификация | Код |
|---|---|---|---|---|
| 1 | DefaultHeader promptRu | Размер/вес шрифта | "20sp, SemiBold" | `18.sp, Medium`. Также код очищает скобки через regex — не описано в спецификации. |
| 2 | DefaultCardContent TtsSpeakerButton | Поведение | "Если модель не готова → TtsDownloadDialog" | В TrainingCardSession.kt этого перехода нет, только иконка ошибки (ReportProblem). Переход к TtsDownloadDialog реализован на уровне TrainingScreen (GrammarMateApp.kt). |
| 3 | DefaultInputControls: Hint Answer Card | Существование | Описан как часть DefaultInputControls | **НЕ существует** в DefaultInputControls. Реализован только в DailyInputControls (DailyPracticeScreen.kt) и DefaultVerbDrillInputControls (VerbDrillScreen.kt). |
| 4 | DefaultInputControls: TTS в подсказке | Существование | Описан как часть DefaultInputControls | Только в DailyInputControls и VerbDrillInputControls. |
| 5 | DefaultInputControls: "Incorrect" | Существование | Описан как часть DefaultInputControls | Только в DailyInputControls и VerbDrillInputControls. |
| 6 | DefaultInputControls: "X attempts left" | Существование | Описан как часть DefaultInputControls | Только в DailyInputControls и VerbDrillInputControls. |
| 7 | DefaultInputControls Mic trailingIcon | Поведение | "Клик → InputMode.VOICE" | Код: клик сразу запускает speechLauncher (распознавание речи), а не только смену режима. |
| 8 | DefaultInputControls "Say translation" | Условие | VOICE | VOICE (без проверки sessionActive в DefaultInputControls). В DailyInputControls добавлена проверка sessionActive. |
| 9 | DefaultInputControls Show Answer | enabled | `hasCards` | `hasCards` (без проверки hintAnswer). В DailyInputControls добавлена проверка `hintAnswer == null`. |
| 10 | DefaultInputControls Check | enabled | `hasCards && inputNotBlank && ACTIVE` | `hasCards && inputNotBlank` (без проверки sessionActive). |
| 11 | DefaultCompletionScreen статистика | Содержимое | "Correct X, Wrong Y" | Код: только `progressText` (например "5 / 10"), без разбивки correct/wrong. |

### Раздел 5 — DailyPracticeScreen

| # | Элемент | Поле | Спецификация | Код |
|---|---|---|---|---|
| 12 | DefaultProgressIndicator | Использование | "Использует DefaultProgressIndicator" | DailyProgressBar отображается ВНЕ TrainingCardSession + DefaultProgressIndicator внутри TrainingCardSession. **Два индикатора прогресса одновременно** — не описано в спецификации. |
| 13 | Show Answer enabled | enabled | "hasCards" | `hasCards && hintAnswer == null` — логичнее, но расходится. |
| 14 | Check button | Поведение/enabled | "submitAnswerWithInput, enabled = hasCards && inputNotBlank && ACTIVE" | Использует `provider.submitAnswerWithInput(input)` напрямую + доп. проверки `sessionActive && currentCard != null`. |
| 15 | Report Sentence | Существование | Описан в DefaultInputControls | **Отсутствует** в DailyInputControls. `supportsFlagging = false` в DailyPracticeSessionProvider. Корректно не описан в разделе 5, но ошибочно присутствует в разделе 4a. |

---

## Батч 5 — VerbDrillScreen + VocabDrillScreen

Результат: **~97/105 совпадают, ~8 расхождений.**

### VerbDrillScreen

| # | Элемент | Поле | Спецификация | Код |
|---|---|---|---|---|
| 1 | Back-кнопка в header (Active Session) | Поведение | "Клик → диалог 'End session?'" | `onExit` (= viewModel::exitSession) **напрямую**, без диалога подтверждения. Диалог есть только через кнопку Exit/StopCircle в DefaultNavigationControls. |
| 2 | CompletionScreen | Описание | Упоминает "Использует DefaultCompletionScreen" | Полностью **кастомная** VerbDrillCompletionScreen. DefaultCompletionScreen не используется. |
| 3 | TTS кнопка в Card Content | Иконка | "Icons.Default.VolumeUp" | 4 визуальных состояния: VolumeUp (IDLE), StopCircle (SPEAKING), CircularProgressIndicator (INITIALIZING), ReportProblem (ERROR). Спецификация описывает только IDLE. |
| 4 | Show Answer enabled | enabled | "hasCards" | `hasCards && hintAnswer == null` — точнее в коде. |

### VocabDrillScreen

| # | Элемент | Поле | Спецификация | Код |
|---|---|---|---|---|
| 5 | Back-кнопка в CardScreen | Поведение | "Клик → диалог 'End session?'" | `viewModel::exitSession` **напрямую**, без диалога подтверждения. |
| 6 | TTS кнопка на CardFront | Иконка | "Icons.Default.VolumeUp" | 4 состояния аналогично VerbDrill. |
| 7 | VoiceResultFeedback | Описание | "Check / Close" | Включает дополнительный статус SKIPPED с текстом "Skipped". |
| 8 | FilterChip Direction | Текст | "IT -> RU" / "RU -> IT" | Код: "IT → RU" (Unicode стрелка `→` вместо `->`). |

---

## Батч 6 — LadderScreen + Общие диалоги

Результат: **26/26 найдены, 25/26 иконок верны, 0 behavioural расхождений.**

| # | Элемент | Поле | Спецификация | Код |
|---|---|---|---|---|
| 1 | Streak Dialog "??" | Тип | Тип: Emoji | `Text(text = "??", fontSize = 48.sp)` — обычный Text, не emoji-литерал. Косметическое различие, на экране идентично. |

---

## Топ-5 критичных расхождений (рекомендуется исправить в спецификации)

1. **Hint Answer Card, Incorrect, Attempts — НЕ часть DefaultInputControls (раздел 4a).** Это кастомные элементы DailyInputControls и VerbDrillInputControls. Раздел 4a нужно поправить: убрать эти элементы из DefaultInputControls и перенести в описания DailyPractice и VerbDrill.

2. **Back-кнопки в VerbDrill/VocabDrill активных сессий** — вызывают `exitSession` напрямую, без диалога подтверждения. Спецификация описывает диалог "End session?".

3. **Enabled-условия кнопок Voice/Keyboard/WordBank** — спецификация пишет "Всегда", но код ставит `enabled = canLaunchVoice` / `canSelectInputMode`. Кнопки могут быть disabled.

4. **VerbDrill CompletionScreen** — спецификация вводит в заблуждение фразой "Использует DefaultCompletionScreen из TrainingCardSession". Реально полностью кастомная реализация.

5. **Двойной прогресс-бар в DailyPractice** — DailyProgressBar снаружи + DefaultProgressIndicator внутри TrainingCardSession. Спецификация не упоминает двойной показ.

---

## Источники

- `app/src/main/java/com/alexpo/grammermate/ui/AppRoot.kt` — StartupScreen
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` — HomeScreen, LessonRoadmapScreen, TrainingScreen, LadderScreen, все диалоги, SettingsSheet
- `app/src/main/java/com/alexpo/grammermate/ui/DailyPracticeScreen.kt` — DailyPracticeScreen
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillScreen.kt` — VerbDrillScreen
- `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillScreen.kt` — VocabDrillScreen
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingCardSession.kt` — общие компоненты

---

Отчёт верификации создан без изменений в исходном коде приложения.

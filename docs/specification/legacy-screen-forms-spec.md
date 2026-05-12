# Экранные формы. Спецификация

## Навигационная карта

```
[Startup] ──(restore done)──> [HOME]
                                 │
                                 ├── Avatar / Primary Action Card / "Continue Learning" / LessonTile ──> [LESSON]
                                 │       │
                                 │       ├── LessonTile ──> [TRAINING] ──(exit)──> [LESSON]
                                 │       ├── DrillTile ──> [TRAINING] (drill mode)
                                 │       ├── BossTile "Review" ──> [TRAINING] (boss mode)
                                 │       ├── BossTile "Mega" ──> [TRAINING] (boss mega mode)
                                 │       └── DrillStartDialog ──> [TRAINING] (drill resume/fresh)
                                 │
                                 ├── DailyPracticeEntryTile ──> [DAILY_PRACTICE] ──(exit)──> [HOME]
                                 │
                                 ├── VerbDrillEntryTile ──> [VERB_DRILL] ──(exit/back)──> [HOME]
                                 │       (Selection → Session → Completion)
                                 │
                                 ├── VocabDrillEntryTile ──> [VOCAB_DRILL] ──(exit/back)──> [HOME]
                                 │       (Selection → CardScreen → Completion)
                                 │
                                 └── Settings (шестерёнка) ──> SettingsSheet
                                         └── "Показать лестницу" ──> [LADDER] ──(back)──> [HOME]

[TRAINING] SettingsSheet ──> "Показать лестницу" ──> [LADDER] ──(back)──> [TRAINING]

Общие диалоги (могут вызываться из нескольких экранов):
  WelcomeDialog, Streak Dialog, Boss Reward Dialog, Boss Error Dialog, Story Error Dialog
```

---

## 1. StartupScreen

**AppScreen:** нет (не экран — состояние инициализации в AppRoot)
**Источник:** старт приложения
**Файл:** AppRoot.kt

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| CircularProgressIndicator | Спиннер | Центр экрана | Нет | Нет | restoreState.status != DONE |
| Текст статуса | Текст | Центр, под индикатором | Нет | Нет | Всегда. Текст: "Restoring backup..." (IN_PROGRESS), "Waiting for backup folder..." (NEEDS_USER), "Preparing..." (другие) |

**Переход:** restoreState.status == DONE → переход на [HOME]

---

## 2. HomeScreen

**AppScreen:** `HOME`
**Источник:** начальный экран, возврат из LESSON / DAILY_PRACTICE / VERB_DRILL / VOCAB_DRILL
**Файл:** GrammarMateApp.kt:745

### БЛОК 1 — Верхняя панель

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Аватар пользователя | Контейнер | Верх, лево (круг 40dp, фон primary, инициалы) | Нет | Нет | Всегда |
| Имя пользователя | Текст | Верх, лево, правее аватара (SemiBold) | Нет | Нет | state.userName отображается |
| LanguageSelector | Кнопка | Верх, право (TextButton, languageCode uppercase) | Нет | Клик → DropdownMenu со списком языков. Выбор → onSelectLanguage(id) | Всегда |
| Settings (шестерёнка) | Кнопка | Верх, право | Icons.Default.Settings | Клик → SettingsSheet (ModalBottomSheet) | Всегда |

### БЛОК 2 — Primary Action Card

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Card контейнер | Card | Верх, под Header, fillMaxWidth | Нет | Клик → переход на [LESSON] | Всегда |
| Primary Label | Текст | Внутри Card, верх (activePackDisplayName / "Continue Learning" / "Start learning") | Нет | Нет | Всегда |
| Next Hint | Текст | Внутри Card, под Primary Label ("Lesson N. Exercise X/Y") | Нет | Нет | state.lessons.isNotEmpty() |

### БЛОК 3 — Grammar Roadmap

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Заголовок "Grammar Roadmap" | Текст | Середина | Нет | Нет | Всегда |
| LazyVerticalGrid (4 столбца) | Контейнер | Середина | Нет | Нет | Всегда |
| LessonTile (Card 72dp) | Card | В сетке, каждая ячейка | Зависит от состояния | Клик: LOCKED → диалог "Lesson locked" или "Start early?", UNLOCKED/SEED/SPROUT/FLOWER → переход на [LESSON] | Всегда (12 ячеек) |
| Номер урока ("{tile.index + 1}") | Текст | Внутри Tile, верх, центр | Нет | Нет | Всегда |
| Emoji состояния | Emoji | Внутри Tile, центр (🔒/🔓/●/🌱/🌿/🌸/🥀/🍂/⚫) | Нет | Нет | Всегда |
| Процент мастерства ("{X}%") | Текст | Внутри Tile, под emoji | Нет | Нет | masteryPercent > 0 и не LOCKED/UNLOCKED/EMPTY |
| LessonTile LOCKED (emoji 🔒) | Card | В сетке | Нет | Клик → диалог "Lesson locked" или "Start early?" | Урок заблокирован |
| LessonTile EMPTY (фон surfaceVariant alpha 0.5, emoji ●) | Card | В сетке | Нет | Нет (не кликабельный) | Нет урока на позиции |

### БЛОК 4 — Drill Tiles

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Row контейнер | Контейнер | Середина, под сеткой | Нет | Нет | hasVerbDrill или hasVocabDrill |
| VerbDrillEntryTile (Card 64dp) | Card | Row, weight(1f) | Icons.Default.FitnessCenter (20dp) | Клик → переход на [VERB_DRILL] | hasVerbDrill |
| VocabDrillEntryTile (Card 64dp) | Card | Row, weight(1f) | Icons.Default.MenuBook (20dp) | Клик → переход на [VOCAB_DRILL] | hasVocabDrill |
| Vocab mastered count ("{X} mastered") | Текст | Внутри VocabDrillEntryTile, право | Нет | Нет | masteredCount > 0 |

### БЛОК 5 — Daily Practice

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| DailyPracticeEntryTile (Card 64dp) | Card | Под Drill Row, fillMaxWidth | Icons.Default.PlayArrow | Клик → переход на [DAILY_PRACTICE] | Всегда |

### БЛОК 6 — Легенда

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Заголовок "Legend:" | Текст | Низ | Нет | Нет | Всегда |
| "🌱 seed • 🌿 growing • 🌸 bloom" | Текст | Низ | Нет | Нет | Всегда |
| "🥀 wilting • 🍂 wilted • ⚫ forgotten" | Текст | Низ | Нет | Нет | Всегда |

### БЛОК 7 — Кнопки

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| OutlinedButton "How This Training Works" | Кнопка | Низ, fillMaxWidth | Нет | Клик → диалог "How This Training Works" | Всегда |
| Button "Continue Learning" | Кнопка | Низ, fillMaxWidth | Нет | Клик → переход на [LESSON] | Всегда |

### БЛОК 8 — Диалоги

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Диалог "How This Training Works" | Контейнер | AlertDialog | Нет | "OK" → закрывает | showMethod == true |
| Диалог "Lesson locked" | Контейнер | AlertDialog | Нет | "OK" → закрывает | showLockedLessonHint == true |
| Диалог "Start early?" | Контейнер | AlertDialog | Нет | "Yes" → переход на [LESSON] (selectLesson), "No" → закрывает | earlyStartLessonId != null |

---

## 3. LessonRoadmapScreen

**AppScreen:** `LESSON`
**Источник:** HomeScreen → Continue Learning, Select Lesson
**Файл:** GrammarMateApp.kt:1104

### БЛОК 1 — Верхняя панель

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Кнопка Back | Кнопка | Верх, лево | Icons.Default.ArrowBack | Клик → переход на [HOME] | Всегда |
| Заголовок урока | Текст | Верх, центр | Нет | Нет | Всегда |
| Spacer (балансир) | Контейнер | Верх, право | Нет | Нет | Всегда |

### БЛОК 2 — Прогресс

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| LinearProgressIndicator | Прогресс-бар | Под заголовком, fillMaxWidth | Нет | Нет | Всегда |
| "Exercise X of Y" | Текст | Под прогресс-баром, центр | Нет | Нет | Всегда |
| "Cards: X of Y" | Текст | Под текстом, центр | Нет | Нет | Всегда |

### БЛОК 3 — Сетка плиток (LazyVerticalGrid, 4 столбца)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| DrillTile (Card 72dp) | Card | Первая ячейка | Icons.Default.FitnessCenter | Клик → DrillStartDialog. Фон: primaryContainer если enabled | drillCards.isNotEmpty() |
| LessonTile (номер, emoji, "NEW"/"MIX") | Card | Ячейки в сетке | Зависит от состояния | Клик: canEnter → переход на [TRAINING] (startSubLesson), заблокирован → диалог "Start early?" | Всегда для Training записей |
| BossTile "Review" | Card | Предпоследняя ячейка | Lock / EmojiEvents | Клик: bossUnlocked → переход на [TRAINING] (boss mode), заблокирован → диалог "Locked" | Всегда |
| BossTile "Mega" | Card | Последняя ячейка | Lock / EmojiEvents | Клик: bossUnlocked → переход на [TRAINING] (boss mega mode), заблокирован → диалог "Locked" | lessonIndex > 0 |

### БЛОК 4 — Кнопка действия

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Button "Start Lesson" / "Continue Lesson" | Кнопка | Низ, fillMaxWidth | Нет | Клик → переход на [TRAINING] (startSubLesson) | Всегда |

### БЛОК 5–7 — Диалоги

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Диалог "Start early?" | Контейнер | AlertDialog | Нет | "Yes" → переход на [TRAINING] (startSubLesson), "No" → закрывает | earlyStartSubLessonIndex != null |
| Диалог "Locked" | Контейнер | AlertDialog | Нет | "OK" → закрывает | bossLockedMessage != null |
| DrillStartDialog | Контейнер | AlertDialog | Нет | "Continue" → переход на [TRAINING] (resume drill), "Start" → переход на [TRAINING] (fresh drill), "Cancel" → закрывает | drillShowStartDialog |

---

## 4. TrainingScreen

**AppScreen:** `TRAINING`
**Источник:** LessonRoadmapScreen → Start Sub-Lesson, Start Boss, Drill
**Файл:** GrammarMateApp.kt:2470

> **Примечание:** TrainingScreen имеет собственную реализацию и НЕ использует TrainingCardSession.

#### Sub-screen: Активная тренировка (по умолчанию)

Состояние по умолчанию при входе на экран. Отображается, пока пользователь не нажмёт Exit.

### БЛОК 1 — TopBar

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Заголовок "GrammarMate" | Текст | Верх, лево | Нет | Нет | Всегда |
| Кнопка Settings | Кнопка | Верх, право | Icons.Default.Settings | Клик → SettingsSheet (ModalBottomSheet) | Всегда |

### БЛОК 2 — Заголовок сессии

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| "Review Session" | Текст | Верх | Нет | Нет | bossActive |
| "Refresh Session" | Текст | Верх | Нет | Нет | eliteActive (и не boss) |

### БЛОК 3 — DrillProgressRow (прогресс + спидометр)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Прогресс-бар (зелёный, 70% ширины) | Прогресс-бар | Середина, лево | Нет (Box зелёный) | Нет | Всегда |
| Текст "X / Y" на прогресс-баре | Текст | Центр прогресс-бара | Нет | Нет | Всегда |
| Спидометр (Canvas arc, 30% ширины) | Контейнер | Середина, право | Нет (Canvas) | Нет | Всегда. Цвет: красный ≤20wpm, жёлтый ≤40, зелёный >40 |
| Число WPM | Текст | Центр спидометра | Нет | Нет | Всегда |

### БЛОК 4 — CardPrompt (карточка вопроса)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Card контейнер | Card | Середина, fillMaxWidth | Нет | Нет | Всегда |
| Метка "RU" | Текст | Card, верх-лево | Нет | Нет | Всегда |
| Текст вопроса (promptRu, 20sp, SemiBold) | Текст | Card, под "RU" | Нет | Нет | Всегда |
| TtsSpeakerButton | Кнопка | Card, верх-право | VolumeUp / StopCircle / CircularProgressIndicator / ReportProblem | Клик → onSpeak() (TTS). Если модель не готова → TtsDownloadDialog | Всегда |

### БЛОК 5 — AnswerBox (поле ввода)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| OutlinedTextField "Your translation" | Поле ввода | Середина, fillMaxWidth | Нет | Ввод текста | Всегда |
| Mic (trailingIcon) | Кнопка | Правый край поля | Icons.Default.Mic | Клик → InputMode.VOICE | Всегда; enabled = canLaunchVoice |
| "No cards" | Текст | Под полем | Нет | Нет | !hasCards |
| "Say translation: ..." | Текст | Под полем | Нет | Нет | inputMode == VOICE && ACTIVE |

### БЛОК 6 — ASR Status

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Пульсирующая точка + "Listening..." | Контейнер | Под полем | Нет (Box красный 10dp) | Нет | useOfflineAsr && RECORDING |
| Спиннер + "Processing..." | Спиннер | Под полем | CircularProgressIndicator 12dp | Нет | useOfflineAsr && RECOGNIZING |
| "Recognition error" | Текст | Под полем | Нет | Нет | useOfflineAsr && ERROR |

### БЛОК 7 — Word Bank

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| "Tap words in correct order:" | Текст | Под ASR status | Нет | Нет | WORD_BANK && wordBankWords.isNotEmpty() |
| FilterChip слова | Чип | FlowRow | Нет | Клик → selectWordFromBank(word) | WORD_BANK && wordBankWords.isNotEmpty() |
| "Selected: X / Y" | Текст | Под FlowRow, лево | Нет | Нет | WORD_BANK && selectedWords.isNotEmpty() |
| "Undo" | Кнопка | Под FlowRow, право | Нет | Клик → removeLastSelectedWord() | WORD_BANK && selectedWords.isNotEmpty() |

### БЛОК 8 — Панель режимов

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Voice (Mic) | Кнопка | Row, лево | Icons.Default.Mic | Клик → VOICE | Всегда; FilledTonalIconButton |
| Keyboard | Кнопка | Row, лево | Icons.Default.Keyboard | Клик → KEYBOARD | Всегда; FilledTonalIconButton |
| Word Bank | Кнопка | Row, лево | Icons.Default.LibraryBooks | Клик → WORD_BANK | Всегда; FilledTonalIconButton |
| Show Answer (Eye + tooltip) | Кнопка | Row, право | Icons.Default.Visibility | Клик → showAnswer() | Всегда; enabled = hasCards |
| Report Sentence (tooltip) | Кнопка | Row, право | Icons.Default.ReportProblem | Клик → Card Options Report Sheet (ModalBottomSheet) | Всегда; enabled = hasCards |
| Label режима | Текст | Row, право | Нет | Нет | Всегда |

### БЛОК 9 — Кнопка Check

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Button "Check" | Кнопка | fillMaxWidth | Нет | Клик → onSubmit() | Всегда; enabled = hasCards && inputNotBlank && ACTIVE |

### БЛОК 10 — ResultBlock

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| "Correct" / "Incorrect" | Текст | Середина, Row | Нет | Нет | lastResult != null. Correct=#2E7D32, Incorrect=#C62828 |
| TtsSpeakerButton | Кнопка | В Row рядом | VolumeUp / StopCircle / etc | Клик → onSpeak() | answerText не пустой |
| "Answer: ..." | Текст | Под Row | Нет | Нет | answerText не пустой |

### БЛОК 11 — NavigationRow

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Prev | Кнопка | Низ, лево | Icons.Default.ArrowBack | Клик → onPrev(). NavIconButton 44dp | Всегда |
| Pause/Play | Кнопка | Низ, центр | Pause / PlayArrow | Клик → togglePause() | Всегда |
| Exit | Кнопка | Низ, центр | Icons.Default.StopCircle | Клик → диалог "End session?" | Всегда |
| Next | Кнопка | Низ, право | Icons.Default.ArrowForward | Клик → onNext(). Если complete → onComplete() | Всегда |

### БЛОК 12 — Card Options Report Sheet

**AppScreen:** нет (ModalBottomSheet)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Заголовок "Card options" | Текст | Верх sheet | Нет | Нет | showReportSheet |
| Текст promptRu карточки | Текст | Под заголовком | Нет | Нет | showReportSheet && reportCard != null |
| "Add to bad sentences list" | Кнопка | TextButton | Icons.Default.ReportProblem | Клик → onFlagBadSentence() | !cardIsBad |
| "Remove from bad sentences list" | Кнопка | TextButton | Icons.Default.ReportProblem (error) | Клик → onUnflagBadSentence() | cardIsBad |
| "Hide this card from lessons" | Кнопка | TextButton | Icons.Default.VisibilityOff | Клик → onHideCard() | Всегда |
| "Export bad sentences to file" | Кнопка | TextButton | Icons.Default.Download | Клик → onExportBadSentences() | Всегда |
| "Copy text" | Кнопка | TextButton | Icons.Default.ContentCopy | Клик → clipboard | Всегда |

### БЛОК 13 — Exit Dialog

**AppScreen:** нет (Dialog)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| "End session?" | Контейнер | AlertDialog | Нет | "Exit" → переход на [LESSON] (finishSession). "Cancel" → закрывает | showExitDialog |

### БЛОК 14 — TtsDownloadDialog

**AppScreen:** нет (Dialog)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| "Download pronunciation model?" | Контейнер | AlertDialog | Нет | "Download" → старт загрузки. Downloading/Extracting → LinearProgressIndicator + "Continue in background". Done → "Model ready!" + "OK". Error → текст ошибки + "OK" | showTtsDownloadDialog |

### БЛОК 15 — MeteredNetworkDialog (TTS)

**AppScreen:** нет (Dialog)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| "Metered network detected" | Контейнер | AlertDialog | Нет | "Download anyway" → подтвердить. "Cancel" → отмена. Текст "~346 MB" | ttsMeteredNetwork |

### БЛОК 16 — AsrMeteredNetworkDialog

**AppScreen:** нет (Dialog)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| "Metered network detected" | Контейнер | AlertDialog | Нет | "Download anyway" / "Cancel". Текст "~375 MB" | asrMeteredNetwork |

### БЛОК 17 — SettingsSheet

**AppScreen:** нет (ModalBottomSheet)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Test Mode (Switch + описание) | Switch | Row | Нет | Клик → onToggleTestMode() | Всегда |
| "Показать лестницу" | Кнопка | OutlinedButton | Icons.Default.Insights | Клик → переход на [LADDER] | Всегда |
| "Vocabulary Sprint limit" | Поле ввода | OutlinedTextField | Нет | Ввод цифр → onUpdateVocabLimit() | Всегда |
| Pronunciation speed (Slider 0.5–1.5x) | Слайдер | Row | Нет (Slider) | Клик → onSetTtsSpeed() | Всегда |
| Voice recognition (Switch) | Switch | Row | Нет | Клик → onSetUseOfflineAsr(). Если модель не готова → AsrMeteredNetworkDialog | Всегда |
| ASR download progress | Прогресс-бар | LinearProgressIndicator | Нет | Нет | Downloading/Extracting |
| Размер текста перевода (Slider 1.0–2.0x) | Слайдер | Row | Нет | Клик → onSetRuTextScale() | Всегда |
| Language DropdownSelector | Dropdown | Column | Нет | Клик → DropdownMenu → onSelectLanguage() | Всегда |
| Pack DropdownSelector | Dropdown | Column | Нет | Клик → DropdownMenu → onSelectPack() | Всегда |
| "New language" + "Add language" | Поле ввода | TextField + Button | Нет | Клик → onAddLanguage() | Всегда |
| "Import lesson pack (ZIP)" | Кнопка | OutlinedButton | Icons.Default.Upload | Клик → файловый выборщик | Всегда |
| "Import lesson (CSV)" | Кнопка | OutlinedButton | Icons.Default.Upload | Клик → файловый выборщик | Всегда |
| "Reset/Reload" | Кнопка | OutlinedButton | Icons.Default.Upload | Клик → файловый выборщик | Всегда |
| "Create empty lesson" | Поле ввода | TextField + Button | Нет | Клик → onCreateEmptyLesson() | Всегда |
| "Delete all lessons" | Кнопка | OutlinedButton (красный) | Icons.Default.Delete | Клик → onDeleteAllLessons() | Всегда |
| Packs список (Row с Delete) | Контейнер | Column | Icons.Default.Delete | Клик → onDeletePack() | installedPacks.isNotEmpty() |
| Profile — "Your Name" + "Save Name" | Поле ввода | TextField + Button | Нет | Клик → onUpdateUserName() | Всегда |
| Backup — "Save progress now" | Кнопка | OutlinedButton | Icons.Default.Upload | Клик → onSaveProgress() | Всегда |
| "Restore from backup" | Кнопка | OutlinedButton | Icons.Default.Download | Клик → OpenDocumentTree | Всегда |

### БЛОК 18 — Фоновый прогресс TTS

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| LinearProgressIndicator (2dp) | Прогресс-бар | Самый верх, AnimatedVisibility | Нет | Нет | bgTtsDownloading |

---

## 4a. Общие компоненты: TrainingCardSession

**AppScreen:** нет (встроенный компонент)
**Используется в:** VerbDrillScreen, DailyPracticeScreen
**Описание:** Переиспользуемый набор слотов для карточных тренировок. Каждый экран подключает нужные слоты и может переопределять их кастомными реализациями.

---

### Слот DefaultHeader

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Label времени (tense) | Текст | Card, верх-лево | Нет | Нет | tense != null |
| Текст промпта (promptRu, 20sp, SemiBold) | Текст | Card, под label | Нет | Нет | Всегда |

### Слот DefaultProgressIndicator

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Прогресс-бар (зелёный, 70% ширины) | Прогресс-бар | Середина, лево (Row) | Нет (Box зелёный) | Нет | Всегда |
| Текст "X / Y" | Текст | Центр прогресс-бара | Нет | Нет | Всегда |
| Спидометр (Canvas arc, 30% ширины) | Контейнер | Середина, право (Row) | Нет (Canvas) | Нет | Всегда. Цвет: красный <=20wpm, жёлтый <=40, зелёный >40 |
| Число WPM | Текст | Центр спидометра | Нет | Нет | Всегда |

### Слот DefaultCardContent

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Card контейнер | Контейнер | Середина, fillMaxWidth | Нет | Нет | Всегда |
| Метка "RU" | Текст | Card, верх-лево | Нет | Нет | Всегда |
| Текст вопроса (promptRu, 20sp, SemiBold) | Текст | Card, под "RU" | Нет | Нет | Всегда |
| TtsSpeakerButton | Кнопка | Card, верх-право | VolumeUp / StopCircle / CircularProgressIndicator / ReportProblem | Клик -> onSpeak() (TTS). Если модель не готова -> TtsDownloadDialog | Всегда |

### Слот DefaultResultContent

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| "Correct" / "Incorrect" | Текст | Середина, Row (лево) | Нет | Нет | lastResult != null. Correct=#2E7D32, Incorrect=#C62828 |
| TtsSpeakerButton | Кнопка | Row, справа | VolumeUp / StopCircle / etc | Клик -> onSpeak() | answerText не пустой |
| "Answer: ..." | Текст | Под Row | Нет | Нет | answerText не пустой |

### Слот DefaultNavigationControls

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Prev | Кнопка | Низ, лево | Icons.Default.ArrowBack | Клик -> prevCard() | Всегда. NavIconButton 44dp |
| Pause/Play | Кнопка | Низ, центр | Pause / PlayArrow | Клик -> togglePause() | Всегда |
| Exit | Кнопка | Низ, центр | Icons.Default.StopCircle | Клик -> диалог "End session?" | Всегда |
| Next | Кнопка | Низ, право | Icons.Default.ArrowForward | Клик -> nextCard(). Если complete -> onComplete() | Всегда |

### Слот DefaultInputControls

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Hint Answer Card ("Answer: ...") | Контейнер | Верх, fillMaxWidth | Нет | Нет | hintAnswer != null |
| TTS кнопка в подсказке | Кнопка | Card, право | Icons.Default.VolumeUp (красный) | Клик -> speakTts() | hintAnswer != null |
| "Incorrect" | Текст | Row, лево | Нет | Нет | showIncorrectFeedback |
| "X attempt(s) left" | Текст | Row, право | Нет | Нет | showIncorrectFeedback |
| OutlinedTextField "Your translation" | Поле ввода | fillMaxWidth | Нет | Ввод текста | Всегда |
| Mic (trailingIcon) | Кнопка | Правый край поля | Icons.Default.Mic | Клик -> InputMode.VOICE | Всегда; enabled = canLaunchVoice |
| "Say translation: ..." | Текст | Под полем | Нет | Нет | VOICE && ACTIVE |
| Word Bank "Tap words..." | Текст | Под подсказкой | Нет | Нет | WORD_BANK && wordBankWords.isNotEmpty() |
| FilterChip слова | Чип | FlowRow | Нет | Клик -> selectWordFromBank(word) | WORD_BANK && wordBankWords.isNotEmpty() |
| "Selected: X / Y" | Текст | Под FlowRow, лево | Нет | Нет | WORD_BANK && selectedWords.isNotEmpty() |
| "Undo" | Кнопка | Под FlowRow, право | Нет | Клик -> removeLastSelectedWord() | WORD_BANK && selectedWords.isNotEmpty() |
| Voice (Mic) | Кнопка | Row, лево | Icons.Default.Mic | Клик -> InputMode.VOICE | Всегда; FilledTonalIconButton |
| Keyboard | Кнопка | Row, лево | Icons.Default.Keyboard | Клик -> InputMode.KEYBOARD | Всегда; FilledTonalIconButton |
| Word Bank | Кнопка | Row, лево | Icons.Default.LibraryBooks | Клик -> InputMode.WORD_BANK | Всегда; FilledTonalIconButton |
| Show Answer (Eye + tooltip) | Кнопка | Row, право | Icons.Default.Visibility | Клик -> showAnswer() | Всегда; enabled = hasCards |
| Report Sentence (tooltip) | Кнопка | Row, право | Icons.Default.ReportProblem | Клик -> CardOptionsReportSheet (ModalBottomSheet) | Всегда; enabled = hasCards |
| Label режима | Текст | Row, право | Нет | Нет | Всегда |
| Button "Check" | Кнопка | fillMaxWidth | Нет | Клик -> submitAnswerWithInput() | Всегда; enabled = hasCards && inputNotBlank && ACTIVE |

### Слот DefaultCompletionScreen

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Emoji | Emoji | Центр, 48sp | Нет | Нет | isComplete |
| "Well done!" (или аналог, 24sp Bold) | Текст | Центр | Нет | Нет | isComplete |
| Статистика (Correct X, Wrong Y, ...) | Текст | Центр | Нет | Нет | isComplete |
| Button "Done" / "Back to Home" | Кнопка | fillMaxWidth | Нет | Клик -> onExit() | isComplete |

---

## 5. DailyPracticeScreen

**AppScreen:** `DAILY_PRACTICE`
**Источник:** HomeScreen -> Daily Practice tile
**Файл:** DailyPracticeScreen.kt

### Диаграмма состояний

```
Loading ──(cards loaded)──> Active Session ──(all sub-lessons complete)──> CompletionSparkle ──(2 сек)──> CompletionScreen
```

---

#### Sub-screen: Loading

**Условие перехода:** `!state.active || cards.isEmpty()` -> при готовности переходит в Active Session

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| CircularProgressIndicator | Спиннер | Центр | Нет | Нет | !state.active \|\| cards.isEmpty() |
| "Loading..." | Текст | Центр, под спиннером | Нет | Нет | !state.active \|\| cards.isEmpty() |

---

#### Sub-screen: Active Session

**Условие перехода:** `state.active && cards.isNotEmpty()` -> при завершении всех sub-lessons переходит в CompletionSparkle

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Кнопка Back | Кнопка | Верх, лево | Icons.Default.ArrowBack | Клик -> диалог "Exit practice?" | Всегда |
| "Daily Practice" | Текст | Верх, центр-право | Нет | Нет | Всегда |
| "{lessonTitle} - {subLessonLabel}" | Текст | Верх, под "Daily Practice" | Нет | Нет | Всегда |

**Progress:** Использует DefaultProgressIndicator из TrainingCardSession (см. раздел 4a), обёрнутый в DailyProgressBar с LinearProgressIndicator (Row weight(1f)) + "X/Y" (totalSubLessons > 0).

**Card Content:** Использует DefaultCardContent из TrainingCardSession (см. раздел 4a).

**Input Controls:** Использует DefaultInputControls из TrainingCardSession (см. раздел 4a).

**Result:** Использует DefaultResultContent из TrainingCardSession (см. раздел 4a).

**Navigation:** Использует DefaultNavigationControls из TrainingCardSession (см. раздел 4a).

##### Кастомные элементы Active Session

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| "Exit practice?" диалог | Контейнер | AlertDialog | Нет | "Stay" -> закрывает диалог. "Exit" -> переход на [HOME] | showExitDialog |

---

#### Sub-screen: CompletionSparkle

**Условие перехода:** `!active && finishedToken && !hasShownSparkle` -> автоматически через 2 сек переходит в CompletionScreen

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Полупрозрачный чёрный фон | Контейнер | fillMaxSize | Нет | Нет | !active && finishedToken && !hasShownSparkle |
| Card "All sub-lessons complete!" | Контейнер | Центр | Нет | Нет | !active && finishedToken && !hasShownSparkle |
| "Great job today!" | Текст | Card, под заголовком | Нет | Нет | !active && finishedToken && !hasShownSparkle |
| Авто-dismiss через 2 сек | — | — | — | onDismiss -> переход в CompletionScreen | Встроенный |

---

#### Sub-screen: CompletionScreen

**Условие перехода:** `!active && finishedToken && hasShownSparkle` -> кнопка "Back to Home" -> переход на [HOME]

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| "Session Complete!" | Текст | Центр, 28sp Bold | Нет | Нет | !active && finishedToken && hasShownSparkle |
| "Great job! You practiced..." | Текст | Центр | Нет | Нет | !active && finishedToken && hasShownSparkle |
| Button "Back to Home" | Кнопка | fillMaxWidth, отступ 32dp | Нет | Клик -> переход на [HOME] | !active && finishedToken && hasShownSparkle |

---

## 6. VerbDrillScreen

**AppScreen:** `VERB_DRILL`
**Источник:** HomeScreen -> Verb Drill tile
**Файл:** VerbDrillScreen.kt

### Диаграмма состояний

```
Loading ──(data loaded)──> SelectionScreen ──(user taps "Старт")──> Active Session ──(batch complete)──> CompletionScreen
                                                                                                        │
                                                                                                        └──(auto, 1 сек, !allDoneToday)──> SelectionScreen (next batch)
```

---

#### Sub-screen: Loading

**Условие перехода:** `isLoading` -> при загрузке данных переходит в SelectionScreen

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| CircularProgressIndicator | Спиннер | Центр | Нет | Нет | isLoading |
| "Loading..." | Текст | Центр, под спиннером | Нет | Нет | isLoading |

---

#### Sub-screen: SelectionScreen

**Условие перехода:** `!isLoading && !isInSession && !isComplete` -> кнопка "Старт" -> переход в Active Session

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Кнопка Back | Кнопка | Верх, лево | Icons.Default.ArrowBack | Клик -> переход на [HOME] | Всегда |
| "Verb Drill" | Текст | Верх, центр-право | Нет | Нет | Всегда |
| TenseDropdown (метка "Время:" + TextButton + DropdownMenu) | Dropdown | Под заголовком | Нет | Выбор -> onSelect(tense) | availableTenses.isNotEmpty() |
| GroupDropdown (метка "Группа:" + TextButton + DropdownMenu) | Dropdown | Под Tense | Нет | Выбор -> onSelect(group) | availableGroups.isNotEmpty() |
| Checkbox "По частотности" | Кнопка | Row | Material Checkbox | Клик -> onToggleSortByFrequency() | Всегда |
| "Прогресс: X / Y" | Текст | Под checkbox | Нет | Нет | totalCards > 0 |
| "Сегодня: X" | Текст | Под прогрессом | Нет | Нет | totalCards > 0 |
| LinearProgressIndicator | Прогресс-бар | fillMaxWidth | Нет | Нет | totalCards > 0 |
| "На сегодня всё!" | Текст | Центр | Нет | Нет | allDoneToday |
| Button "Старт" / "Продолжить" | Кнопка | fillMaxWidth | Нет | Клик -> переход в Active Session | !allDoneToday |

---

#### Sub-screen: Active Session (VerbDrillSessionWithCardSession)

**Условие перехода:** пользователь нажал "Старт" на SelectionScreen -> по завершении batch -> переход в CompletionScreen

##### Header (кастомный)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Back | Кнопка | Верх, лево | Icons.Default.ArrowBack | Клик -> диалог "End session?" -> подтверждение -> переход на [HOME] | Всегда |
| "Verb Drill" | Текст | Верх, центр-право | Нет | Нет | Всегда |

##### Progress

Использует DefaultProgressIndicator из TrainingCardSession (см. раздел 4a).

##### Card Content (кастомный)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Card контейнер | Контейнер | fillMaxWidth | Нет | Нет | Всегда |
| Метка "RU" | Текст | Card, верх-лево | Нет | Нет | Всегда |
| Текст промпта (20sp) | Текст | Card, под "RU" | Нет | Нет | Всегда |
| TTS кнопка | Кнопка | Card, верх-право | Icons.Default.VolumeUp | Клик -> speakTts() | promptRu.isNotBlank() |
| SuggestionChip глагола (verb + #rank) | Чип | Card, под промптом | Icons.Default.ChevronRight | Клик -> VerbReferenceBottomSheet | verbText != null |
| SuggestionChip времени (abbreviateTense) | Чип | Card, рядом с глаголом | Нет | Клик -> TenseInfoBottomSheet | tense != null |

##### Input Controls (DefaultVerbDrillInputControls)

Использует DefaultInputControls из TrainingCardSession (см. раздел 4a), с добавлением Report Sentence (Icons.Default.ReportProblem -> CardOptionsReportSheet), доступной при supportsFlagging.

##### Result

Использует DefaultResultContent из TrainingCardSession (см. раздел 4a).

##### Navigation

Использует DefaultNavigationControls из TrainingCardSession (см. раздел 4a).

##### Exit Dialog (TrainingCardSession)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| "End session?" | Контейнер | AlertDialog | Нет | "End" -> переход на [HOME]. "Cancel" -> закрывает диалог | showExitDialog |

##### Report Sheet (ModalBottomSheet)

**AppScreen:** нет (ModalBottomSheet)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| "Card options" + promptRu | Текст | Верх sheet | Нет | Нет | showReportSheet |
| "Add to bad" / "Remove from bad" | Кнопка | TextButton | Icons.Default.ReportProblem | flag / unflag | cardIsBad (оба варианта) |
| "Hide card" | Кнопка | TextButton | Icons.Default.VisibilityOff | hideCurrentCard() | Всегда |
| "Export bad" | Кнопка | TextButton | Icons.Default.Download | exportFlaggedCards() | Всегда |
| "Copy text" | Кнопка | TextButton | Icons.Default.ContentCopy | clipboard | Всегда |

##### Export Dialog

**AppScreen:** нет (Dialog)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| "Export" + результат | Контейнер | AlertDialog | Нет | "OK" -> закрывает диалог | exportMessage != null |

---

#### VerbReferenceBottomSheet (вызывается из Active Session)

**AppScreen:** нет (ModalBottomSheet)
**Триггер:** клик по SuggestionChip глагола в Card Content

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Название глагола (20sp Bold) | Текст | Верх, лево | Нет | Нет | showVerbSheet |
| TTS кнопка глагола | Кнопка | Верх, право | Icons.Default.VolumeUp | Клик -> speakVerbInfinitive() | showVerbSheet |
| "Группа: [group]" | Текст | Под глаголом | Нет | Нет | group != null |
| "Время: [tense]" | Текст | Под группой | Нет | Нет | tense != null |
| Разделитель | Контейнер | Под "Время" | Нет | Нет | conjugation.isNotEmpty() |
| "Спряжение:" | Текст | Под разделителем | Нет | Нет | conjugation.isNotEmpty() |
| Формы спряжения | Текст | Column | Нет | Нет | conjugation.isNotEmpty() |

---

#### TenseInfoBottomSheet (вызывается из Active Session)

**AppScreen:** нет (ModalBottomSheet)
**Триггер:** клик по SuggestionChip времени в Card Content

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Icon Info + название времени (20sp Bold) | Текст | Верх, лево | Icons.Default.Info | Нет | showTenseSheet && tenseInfo != null |
| Fallback "Справочная информация недоступна" | Текст | Центр | Нет | Нет | showTenseSheet && tenseInfo == null |
| Card "Формула" | Контейнер | fillMaxWidth | Нет | Нет | tenseInfo != null |
| "Когда использовать" + текст | Текст | Под Card | Нет | Нет | tenseInfo != null |
| "Примеры" + Cards | Контейнер | Column | Нет | Нет | tenseInfo.examples.isNotEmpty() |
| Итальянский текст (bodyLarge) | Текст | Внутри Card примера | Нет | Нет | tenseInfo.examples.isNotEmpty() |
| Русский перевод (bodyMedium, серый) | Текст | Внутри Card примера | Нет | Нет | tenseInfo.examples.isNotEmpty() |
| Заметка (bodySmall, primary) | Текст | Внутри Card примера | Нет | Нет | tenseInfo.examples.isNotEmpty() |

---

#### Sub-screen: CompletionScreen (VerbDrill)

**Условие перехода:** batch завершён (`isComplete`) -> автоматически через 1 сек -> возврат в SelectionScreen (если !allDoneToday). Кнопка "Выход" -> переход на [HOME].

Использует DefaultCompletionScreen из TrainingCardSession (см. раздел 4a) со следующими кастомными значениями:

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Emoji "🎉" | Emoji | Центр, 48sp | Нет | Нет | isComplete |
| "Отлично!" (24sp Bold) | Текст | Центр | Нет | Нет | isComplete |
| "Правильных: X \| Ошибок: Y" | Текст | Центр | Нет | Нет | isComplete |
| OutlinedButton "Выход" | Кнопка | fillMaxWidth | Нет | Клик -> переход на [HOME] | isComplete |
| Авто nextBatch через 1 сек | — | — | — | переход в SelectionScreen (следующий batch) | !allDoneToday. Встроенный |

---

## 7. VocabDrillScreen

**AppScreen:** `VOCAB_DRILL`
**Источник:** HomeScreen -> Flashcards tile
**Файл:** VocabDrillScreen.kt

### Диаграмма состояний

```
Loading ──(data loaded)──> SelectionScreen ──(user taps "Start")──> CardScreen ──(all cards reviewed)──> CompletionScreen
```

---

#### Sub-screen: Loading

**Условие перехода:** `isLoading` -> при загрузке данных переходит в SelectionScreen

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| CircularProgressIndicator | Спиннер | Центр | Нет | Нет | isLoading |
| "Loading..." | Текст | Центр, под спиннером | Нет | Нет | isLoading |

---

#### Sub-screen: SelectionScreen

**Условие перехода:** данные загружены -> кнопка "Start" -> переход в CardScreen

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Back | Кнопка | Верх, лево | Icons.Default.ArrowBack | Клик -> переход на [HOME] | Всегда |
| "Flashcards" | Текст | Верх, центр-право | Нет | Нет | Всегда |
| "Direction" label | Текст | Под заголовком | Нет | Нет | Всегда |
| FilterChip "IT -> RU" / "RU -> IT" | Чип | Row, под label | Нет | Клик -> onSetDirection() | Всегда |
| "Voice input" Switch | Switch | Row | Icons.Default.Mic (декоративная) | Клик -> onSetVoiceMode() | Всегда |
| "Part of speech" label | Текст | Row | Нет | Нет | Всегда |
| FilterChip "All" / "Nouns" / "Verbs" / etc. | Чип | Row | Нет | Клик -> onSelectPos() | Всегда |
| "Word frequency" label | Текст | Row | Нет | Нет | Всегда |
| FilterChip "Top 100" / "Top 500" / "Top 1000" / "All" | Чип | Row | Нет | Клик -> onSetRankRange() | Всегда |
| Card Stats ("Due: X / Y") | Текст | fillMaxWidth | Нет | Нет | totalCount > 0 |
| "Mastered: X words" | Текст | fillMaxWidth | Нет | Нет | totalCount > 0 |
| POS разбивка | Текст | fillMaxWidth | Нет | Нет | totalCount > 0 |
| LinearProgressIndicator | Прогресс-бар | fillMaxWidth | Нет | Нет | totalCount > 0 |
| "No words loaded" | Текст | fillMaxWidth, центр | Нет | Нет | totalCount == 0 |
| Button "Start (X due)" | Кнопка | fillMaxWidth | Нет | Клик -> переход в CardScreen | dueCount > 0 |

---

#### Sub-screen: CardScreen (VocabDrillCardScreen)

**Условие перехода:** пользователь нажал "Start" на SelectionScreen -> после просмотра всех карточек -> переход в CompletionScreen

##### Верхняя панель

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Back | Кнопка | Верх, лево | Icons.Default.ArrowBack | Клик -> диалог "End session?" -> подтверждение -> переход на [HOME] | Всегда |
| "Flashcards" | Текст | Верх, центр-право | Нет | Нет | Всегда |
| "N/M" счётчик | Текст | Верх, право | Нет | Нет | Всегда |
| LinearProgressIndicator | Прогресс-бар | fillMaxWidth | Нет | Нет | Всегда |

##### CardFront (isFlipped == false)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| PosBadge (noun/verb/adj./adv. pill) | Чип | Верх карточки, лево | Нет | Нет | Всегда |
| RankBadge (#N pill) | Чип | Верх карточки, право | Нет | Нет | Всегда |
| Главное слово (32sp Bold) | Текст | Центр | Нет | Нет | Всегда |
| TTS кнопка | Кнопка | Под словом | Icons.Default.VolumeUp | Клик -> onSpeak() | direction == IT_TO_RU |
| "Tap to speak" | Текст | Центр | Нет | Нет | !voiceCompleted |
| Mic кнопка (72dp) | Кнопка | Центр | Icons.Default.Mic, 36dp | Клик -> onStartVoice() | !voiceCompleted |
| VoiceResultFeedback | Иконка | Центр | Check / Close | Нет | voiceCompleted |
| "Skip" | Кнопка | Низ, лево | Icons.Default.SkipNext | Клик -> skipVoice() / onFlip() | !isFlipped |
| "Flip" | Кнопка | Низ, право | Icons.Default.Flip | Клик -> onFlip() | !isFlipped |

##### CardBack (isFlipped == true)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| PosBadge + слово + TTS кнопка | Чип + Текст + Кнопка | Верх | PosBadge + Icons.Default.VolumeUp | Клик -> onSpeak() | Всегда |
| Перевод | Текст | Под словом | Нет | Нет | Всегда |
| Card "Forms" (m sg, f sg, m pl, f pl) | Контейнер | fillMaxWidth | Нет | Нет | forms.isNotEmpty() |
| "Collocations" | Текст | Под Forms | Нет | Нет | collocations.isNotEmpty() |
| Шаг мастерства ("Step X/9" / "Learned") | Текст | Низ | Нет | Нет | Всегда |
| "Again" (красный, "<1m") | Кнопка | Низ | Нет | Клик -> onAnswer(AGAIN) | isFlipped |
| "Hard" (оранжевый, "Xd") | Кнопка | Низ | Нет | Клик -> onAnswer(HARD) | isFlipped |
| "Good" (primary, "Xd") | Кнопка | Низ | Нет | Клик -> onAnswer(GOOD) | isFlipped |
| "Easy" (зелёный, "Xd") | Кнопка | Низ | Нет | Клик -> onAnswer(EASY) | isFlipped |

---

#### Sub-screen: CompletionScreen (VocabDrill)

**Условие перехода:** все карточки просмотрены -> кнопка "Exit" -> переход на [HOME], кнопка "Continue" -> возврат в SelectionScreen

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| "Perfect!" / "Done!" | Текст | Центр, 28sp Bold | Нет | Нет | Всегда |
| Card Stats (Correct X, Wrong Y, "N words reviewed") | Текст | Центр | Нет | Нет | showStats (через 800ms) |
| OutlinedButton "Exit" | Кнопка | Row, лево | Нет | Клик -> переход на [HOME] | showStats |
| Button "Continue" | Кнопка | Row, право | Нет | Клик -> переход в SelectionScreen | showStats |

---

## 8. LadderScreen

**AppScreen:** `LADDER`
**Источник:** SettingsSheet -> Show Ladder
**Файл:** GrammarMateApp.kt:2304

**Диаграмма состояний:** единственный экран без внутренних переходов. Кнопка Back -> возврат на предыдущий экран ([HOME] или [TRAINING]).

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Back | Кнопка | Верх, лево | Icons.Default.ArrowBack | Клик -> возврат на вызывающий экран ([HOME] или [TRAINING]) | Всегда |
| "Лестница интервалов" | Текст | Верх, центр-лево | Нет | Нет | Всегда |
| "Все уроки текущего пакета" | Текст | Верх, под заголовком | Нет | Нет | Всегда |
| "Нет данных по урокам." | Текст | Центр | Нет | Нет | ladderRows пуст |
| LadderHeaderRow (#, Урок, Карты, Дней, Интервал) | Текст | Под spacer | Нет | Нет | ladderRows не пуст |
| LadderRowCard для каждого урока (index, title, uniqueCardShows, daysSinceLastShow, intervalLabel) | Контейнер | LazyColumn | Нет | Нет | ladderRows не пуст. Фон: errorContainer если "Просрочка", иначе surfaceVariant |

---

## 9. Общие диалоги

Общие диалоги могут вызываться из нескольких экранов. Ни один из них не имеет собственного AppScreen.

---

### WelcomeDialog

**AppScreen:** нет (Dialog)
**Вызывается из:** GrammarMateApp (первый запуск)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Title "Welcome to GrammarMate!" | Текст | AlertDialog title | Нет | Нет | showWelcomeDialog (первый запуск) |
| "What's your name?" | Текст | Тело диалога | Нет | Нет | Всегда |
| OutlinedTextField (50 символов) | Поле ввода | Тело диалога | Нет | Ввод имени, Done -> onNameSet() | Всегда |
| "Continue" | Кнопка | confirmButton | Нет | Клик -> onNameSet(name) -> переход на [HOME] | Всегда |
| "Skip" | Кнопка | dismissButton | Нет | Клик -> onNameSet("GrammarMateUser") -> переход на [HOME] | Всегда |

### Streak Dialog

**AppScreen:** нет (Dialog)
**Вызывается из:** GrammarMateApp (после завершения сессии)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| "??" (emoji) | Emoji | AlertDialog icon | Нет | Нет | streakMessage != null |
| Title "Streak!" | Текст | AlertDialog title | Нет | Нет | streakMessage != null |
| streakMessage | Текст | AlertDialog text | Нет | Нет | streakMessage != null |
| "Longest streak: N days" | Текст | Текст | Нет | Нет | longestStreak > currentStreak |
| "Continue" | Кнопка | confirmButton | Нет | Клик -> dismissStreakMessage() | streakMessage != null |

### Boss Reward Dialog

**AppScreen:** нет (Dialog)
**Вызывается из:** GrammarMateApp (после boss-сессии)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Icon трофея | Иконка | AlertDialog icon | Icons.Default.EmojiEvents (BRONZE=#CD7F32, SILVER=#C0C0C0, GOLD=#FFD700) | Нет | bossRewardMessage != null && bossReward != null |
| Title "Boss Reward" | Текст | AlertDialog title | Нет | Нет | bossRewardMessage != null && bossReward != null |
| bossRewardMessage | Текст | AlertDialog text | Нет | Нет | bossRewardMessage != null && bossReward != null |
| "OK" | Кнопка | confirmButton | Нет | Клик -> clearBossRewardMessage() | bossRewardMessage != null && bossReward != null |

### Boss Error Dialog

**AppScreen:** нет (Dialog)
**Вызывается из:** GrammarMateApp (ошибка boss-сессии)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Title "Boss" | Текст | AlertDialog title | Нет | Нет | bossErrorMessage != null |
| bossErrorMessage | Текст | AlertDialog text | Нет | Нет | bossErrorMessage != null |
| "OK" | Кнопка | confirmButton | Нет | Клик -> clearBossError() | bossErrorMessage != null |

### Story Error Dialog

**AppScreen:** нет (Dialog)
**Вызывается из:** GrammarMateApp (ошибка story)

| Наименование | Тип | Расположение | Иконка | Поведение | Условие отображения |
|---|---|---|---|---|---|
| Title "Story" | Текст | AlertDialog title | Нет | Нет | storyErrorMessage != null |
| storyErrorMessage | Текст | AlertDialog text | Нет | Нет | storyErrorMessage != null |
| "OK" | Кнопка | confirmButton | Нет | Клик -> clearStoryError() | storyErrorMessage != null |

# Checklist покрытия legacy-функционала GrammarMate → эпики saga

**Документ:** карта покрытия функционала legacy-приложения (ветка `main`, 217 файлов `com.alexpo/grammermate/`) эпиками saga-плана Modified D' (E01-E13).
**Назначение:** coverage-gate перед AC-этапом. Каждая legacy-фича должна быть однозначно привязана к покрывающему эпику. GAP'ы подсвечиваются и закрываются до formalization.
**Источник аудита:** exhaustive read-through всех legacy-файлов на `main` (верифицировано чтением кода, не по именам файлов). См. saga activity 2026-07-07.
**Статус:** аудит завершён, 9 GAP'ов идентифицированы и распределены по эпикам ниже.

---

## A. Пользовательские экраны (14 маршрутов)

| Экран / маршрут | Источник (legacy) | Эпик | Статус |
|---|---|---|---|
| HOME | `ui/screens/HomeScreen.kt`, `GrammarMateApp.kt:373` | E13 | COVERED |
| LESSON (LessonRoadmap) | `GrammarMateApp.kt:543`, `ui/screens/LessonRoadmapScreen.kt` | E11 | COVERED |
| CHAPTER_LESSONS | `ui/screens/ChapterLessonsScreen.kt`, `GrammarMateApp.kt:1031` | E09 | COVERED |
| DAILY_PRACTICE | `ui/DailyPracticeScreen.kt`, `GrammarMateApp.kt:632` | E07 | COVERED |
| STORY (StoryQuiz) | `ui/screens/StoryQuizScreen.kt`, `GrammarMateApp.kt:637` | E09 | COVERED |
| TRAINING | `ui/screens/TrainingScreen.kt`, `ui/TrainingCardSession.kt`, `GrammarMateApp.kt:674` | E04 | COVERED |
| LADDER | `ui/screens/LadderScreen.kt`, `GrammarMateApp.kt:660` | E10 | COVERED |
| VERB_DRILL | `ui/VerbDrillScreen.kt`, `ui/VerbDrillViewModel.kt`, `GrammarMateApp.kt:816` | E05 | COVERED* |
| VOCAB_DRILL | `ui/VocabDrillScreen.kt`, `ui/VocabDrillViewModel.kt`, `GrammarMateApp.kt:850` | E06 | COVERED* |
| GRAMMAR_STORY_ROADMAP | `ui/screens/GrammarStoryRoadmapScreen.kt`, `GrammarMateApp.kt:887` | E09 | COVERED |
| STORY_READER | `ui/screens/StoryReaderScreen.kt` (+ inline `SimpleMarkdownParser:64`), `GrammarMateApp.kt:974` | E09 | COVERED |
| BACKGROUND_VOCAB | `ui/screens/BackgroundVocabScreen.kt`, `GrammarMateApp.kt:1069` | E08 | COVERED |
| SettingsSheet (overlay) | `ui/screens/SettingsScreen.kt` | E13 | COVERED |

*E05/E06 называют `VerbDrillRepository`/`VocabDrillRepository` — этих классов в legacy нет (логика в ViewModel + Store). Naming-mismatch, не функциональный gap — уточнить в SRS что «Repository» = Room-имплементация Store-логики.

## B. Доменные redirect-маршруты (back-compat)

| Маршрут | Источник | Эпик | Статус |
|---|---|---|---|
| ELITE → HOME redirect | `data/AppScreen.kt`, `GrammarMateApp.kt:623` | E13 | **GAP C3** → добавить в E13 (route back-compat) |
| VOCAB → HOME redirect | `data/AppScreen.kt`, `GrammarMateApp.kt:628` | E13 | **GAP C3** → добавить в E13 |

## C. Feature logic

| Компонент | Источник | Эпик | Статус |
|---|---|---|---|
| SessionRunner | `feature/training/SessionRunner.kt` | E04 | COVERED |
| CardSessionStateMachine | `feature/training/CardSessionStateMachine.kt` | E04 | COVERED |
| HintCalculator | `feature/training/HintCalculator.kt` | E04 | COVERED |
| WordBankGenerator | `feature/training/WordBankGenerator.kt` | E04 | COVERED |
| AnswerValidator | `feature/training/AnswerValidator.kt` | E04 | COVERED |
| CardProvider (SubLesson scheduling) | `feature/training/CardProvider.kt` | E04 | COVERED |
| StoryRunner / StoryResult | `feature/training/StoryRunner.kt`, `StoryResult.kt` | E09 | COVERED |
| SessionEvent | `feature/training/SessionEvent.kt` | E04 | COVERED |
| BossBattleRunner + BossOrchestrator + 3 BossType + tiers | `feature/boss/*.kt` | E11 | COVERED |
| DailyPracticeCoordinator + Composer + TENSE_LADDER | `feature/daily/*.kt` | E07 | COVERED |
| **TrainingStateAccess interface** | `feature/daily/DailySessionHelper.kt` | E01 | **GAP C2 — ✅ CLOSED (AC-18):** successor-порт определён в `domain/training/TrainingStateAccess.kt` (pure Kotlin), контракт зафиксирован в SRS-001 §5.11 |
| PomodoroHelper | `feature/pomodoro/PomodoroHelper.kt` | E12 | COVERED |
| VocabSprintRunner | `feature/vocab/VocabSprintRunner.kt` | E06 | COVERED |
| VocabPlaybackService + DeckPlayer + MediaSession | `feature/backgroundvocab/*.kt` | E08 | COVERED |
| BgVocabAudioResolver / StoryAudioResolver | `feature/backgroundvocab/*AudioResolver.kt` | E08 | COVERED (неявно через SoundPackManager) |
| ProgressTracker / ProgressRestorer | `feature/progress/*.kt` | E10 | COVERED |
| StreakManager | `feature/progress/StreakManager.kt` | E10 | COVERED |
| FlowerRefresher | `feature/progress/FlowerRefresher.kt` | E10 | COVERED |
| ChapterProgressCalculator / PackProgressCalculator | `feature/progress/*ProgressCalculator.kt` | E10 | COVERED |
| **BadSentenceHelper + BadSentenceStore + exportToTextFile** | `feature/progress/BadSentenceHelper.kt`, `data/BadSentenceStore.kt` | E04 | **GAP C1** → добавить в E04 (training card lifecycle: flagCard/hideCard там же) |

## D. Data stores (20)

| Store | Источник | Эпик | Статус |
|---|---|---|---|
| AppConfigStore (15 полей) | `data/AppConfigStore.kt` | E13 | COVERED (см. §J settings inventory) |
| LessonStore + LanguageManager + DrillFileManager | `data/*.kt` | E02 | COVERED |
| MasteryStore | `data/MasteryStore.kt` | E10 | COVERED |
| ProgressStore | `data/ProgressStore.kt` | E02/E10 | COVERED |
| StreakStore (per-language) | `data/StreakStore.kt` | E10 | COVERED |
| ProfileStore (userName) | `data/ProfileStore.kt` | E13/E10 | COVERED |
| PomodoroHistoryStore / PomodoroSettingsStore | `data/Pomodoro*Store.kt` | E12 | COVERED |
| VerbDrillStore (combo + last session) | `data/VerbDrillStore.kt` | E05 | COVERED |
| VocabProgressStore (sprint + SRS) | `data/VocabProgressStore.kt` | E06 | COVERED |
| WordMasteryStore (Anki) | `data/WordMasteryStore.kt` | E06 | COVERED |
| ChapterProgressStore | `data/ChapterProgressStore.kt` | E10 | COVERED |
| PackDailyCursorStore (per-pack) | `data/PackDailyCursorStore.kt` | E07 | COVERED |
| **PackLessonProgressStore** | `data/PackLessonProgressStore.kt` | E07 | **GAP C7** → добавить в E07 (pack-scoped progress isolation) |
| **DrillProgressStore** (per-lesson drill index) | `data/DrillProgressStore.kt` | E04 | **GAP C7** → добавить в E04 (training session resume) |
| HiddenCardStore | `data/HiddenCardStore.kt` | E04 | COVERED |
| **BadSentenceStore + exportToTextFile** | `data/BadSentenceStore.kt` | E04 | **GAP C1** → с BadSentenceHelper |
| GrammarChipStore (63 chips) | `data/GrammarChipStore.kt` | E13 | COVERED |
| BgVocabMarkStore (GREEN/RED) | `data/BgVocabMarkStore.kt` | E08 | COVERED |
| BgVocabPositionStore (last word) | `data/BgVocabPositionStore.kt` | E08 | COVERED |
| YamlListStore (helper) | `data/YamlListStore.kt` | E02 | COVERED (subsumed by Room migration) |

## E. Engines / Calculators / Parsers

| Компонент | Источник | Эпик | Статус |
|---|---|---|---|
| SpacedRepetitionConfig | `data/SpacedRepetitionConfig.kt` | E10 | COVERED |
| LessonLadderCalculator | `data/LessonLadderCalculator.kt` | E10 | COVERED |
| CefrCalculator | `data/CefrCalculator.kt` | E10 | COVERED |
| FlowerCalculator | `data/FlowerCalculator.kt` | E10 | COVERED |
| MixedReviewScheduler | `data/MixedReviewScheduler.kt` | E04 | COVERED |
| Normalizer | `data/Normalization.kt` | E01 | COVERED |
| CsvParser / CsvLineParser | `data/Csv*.kt` | E13/E02 | COVERED |
| VerbDrillCsvParser | `data/VerbDrillCsvParser.kt` | E05 | COVERED |
| VocabCsvParser / ItalianDrillVocabParser | `data/Vocab*.kt` | E06 | COVERED |
| BgVocabCsvParser | `data/BgVocabCsvParser.kt` | E08 | COVERED |
| MultilingualStoryParser | `data/MultilingualStoryParser.kt` | E09 (вызывает) / E01 FR-12 (перенос в :domain) | COVERED |
| StoryQuizParser | `data/StoryQuizParser.kt` | E09 | COVERED |
| PackImporter | `data/PackImporter.kt` | E02/E13 | COVERED |
| LessonPackManifest | `data/LessonPackManifest.kt` | E02 | COVERED |
| SimpleMarkdownParser (inline) | `ui/screens/StoryReaderScreen.kt:64` | E09 | COVERED |

## F. Audio / ASR / TTS

| Компонент | Источник | Эпик | Статус |
|---|---|---|---|
| TtsEngine (VITS-Piper + Kokoro + LRU 3) | `data/TtsEngine.kt` | E03 | COVERED |
| AsrEngine (Whisper + SileroVad) | `data/AsrEngine.kt` | E03 | COVERED |
| **HomophoneReplacerConfig** | `AsrEngine.kt:126,215` | E03 | **GAP C4** → добавить в E03 ASR-секцию (hook для homophone-замены) |
| AsrModelManager / AsrModelRegistry | `data/Asr*.kt` | E03 | COVERED |
| TtsModelManager / TtsModelRegistry | `data/Tts*.kt` | E03 | COVERED |
| TtsProvider (singleton holder) | `data/TtsProvider.kt` | E03 | COVERED |
| SoundPackManager / SoundPackRegistry | `data/SoundPack*.kt` | E08 | COVERED |
| SegmentPlayer | `shared/audio/SegmentPlayer.kt` | E03 | COVERED |
| BluetoothAudioRouter | `shared/audio/BluetoothAudioRouter.kt` | E03 | COVERED |
| AudioCoordinator (SoundPool SFX) | `shared/audio/AudioCoordinator.kt` | E03 | COVERED (через playSoundEffect) |
| MemoryChecker | `data/MemoryChecker.kt` | E03 | COVERED |
| **ModelLoadLogger** | `data/ModelLoadLogger.kt` | E03 | **GAP C5** → добавить в E03 рядом с MemoryChecker |

## G. Backup / Restore

| Компонент | Источник | Эпик | Статус |
|---|---|---|---|
| BackupManager + FileCollector + Restorer + RestoreNotifier | `data/Backup*.kt`, `data/RestoreNotifier.kt` | E02 (data) + E13 (UI) | COVERED |

## H. App shell / DI / infra

| Компонент | Источник | Эпик | Статус |
|---|---|---|---|
| MainActivity (crash handler → Downloads, locale, permissions) | `MainActivity.kt` | E13 | COVERED |
| GrammarMateApplication (AuditLogger.startSession) | `GrammarMateApplication.kt` | E13 | COVERED |
| **appVersion "1.7" magic string** | `GrammarMateApplication.onCreate` | E13 | **GAP C9** → wire to BuildConfig |
| AppContainer (manual DI) + StoreFactory | `AppContainer.kt`, `data/StoreFactory.kt` | E01/E02 | COVERED (Hilt DI заменяет) |
| AtomicFileWriter | `data/AtomicFileWriter.kt` | E02 | COVERED (atomic = Room transaction) |
| AppRoot restore-gate | `ui/AppRoot.kt` | E13 | COVERED |
| Theme (ThemeMode) | `ui/Theme.kt` | E13 | COVERED |
| **GrammarMateColors palette + CompositionLocal** | `ui/Theme.kt` | E13 | **GAP C8** → сохранить custom color tokens |
| AuditLogger (rotating file) | `shared/AuditLogger.kt` | E13 | COVERED |
| ScreenLogger (Logcat nav logger) | `shared/ScreenLogger.kt` | E13 | COVERED |
| SettingsActionHandler + applyLocale | `shared/SettingsActionHandler.kt` | E13 | COVERED |

## I. Content / Assets

| Asset | Эпик | Статус |
|---|---|---|
| 9 pack ZIPs + manifest v2 | E02/E13 | COVERED |
| 63 grammar chips JSON | E13 | COVERED |
| bg_vocab_12000.csv (16 cols) | E08 | COVERED |
| 4 story chapters .md | E09 | COVERED |
| tenses/{it,de}_tenses.yaml | E05 | COVERED (добавить de_tenses в scope — оба файла) |
| 7 Italian drill CSVs (verbs/adj/adv/nouns/numbers/pronouns/groups) | E05/E06 | COVERED |
| config.yaml (5 defaults) | E13 | COVERED |

## J. Settings inventory (35 строк, верифицировано SettingsScreen.kt)

| # | Setting | Persistent owner | Эпик |
|---|---|---|---|
| 1 | Interface language (system/en/ru) | AppConfigStore.uiLanguage | E13 |
| 2 | Test mode toggle | AppConfigStore.testMode | E13 |
| 3 | Show ladder (action) | — | E10/E13 |
| 4 | Theme mode (LIGHT/DARK/SYSTEM) | AppConfigStore.themeMode | E13 |
| 5 | Hint level (EASY/MEDIUM/HARD) | AppConfigStore.hintLevel | E04/E13 |
| 6 | Clickable word hints | AppConfigStore.clickableWordHints | E06/E13 |
| 7 | Vocab sprint limit (int) | AppConfigStore.vocabSprintLimit | E06/E13 |
| 8 | Session size (3..20) | AppConfigStore.sessionSize | E04/E13 |
| 9 | TTS speed (0.5..1.5) | AppConfigStore.ttsSpeed | E03/E13 |
| 10 | BG-vocab sentence pause (0.5..6s) | AppConfigStore.bgVocabSentencePauseMs | E08/E13 |
| 11 | Reset GREEN words (action) | BgVocabMarkStore.clearGreen | E08/E13 |
| 12 | Sound pack import/download/cancel | SoundPackManager | E08/E13 |
| 13 | TTS models IT/RU download | TtsModelManager | E03/E13 |
| 14 | Offline ASR toggle | AppConfigStore.useOfflineAsr | E03/E13 |
| 15 | Bluetooth microphone | AppConfigStore.useBluetoothMic | E03/E13 |
| 16 | Voice auto-start | AppConfigStore.voiceAutoStart | E03/E13 |
| 17 | Russian text size (1.0..2.0) | AppConfigStore.ruTextScale | E13 |
| 18 | Language/pack selection | LessonStore | E02/E13 |
| 19 | Add new language | LanguageManager | E13 |
| 20 | Import lesson pack (SAF ZIP) | PackImporter | E13 |
| 21 | Import lesson CSV (SAF) | CsvParser/PackImporter | E13 |
| 22 | Reset/reload (SAF) | PackImporter | E13 |
| 23 | Create empty lesson | LessonStore | E13 |
| 24 | Delete all lessons | LessonStore | E13 |
| 25 | Reset progress current language | SettingsActionHandler | E13 |
| 26 | Reset ALL progress | SettingsActionHandler | E13 |
| 27 | Parse-warning dialog (transient) | — | E13 |
| 28 | Installed packs list (delete per pack) | LessonStore | E13 |
| 29 | Profile user name | ProfileStore.userName | E10/E13 |
| 30 | Save progress now (manual backup) | BackupManager | E13 |
| 31 | Create backup | BackupManager | E13 |
| 32 | Restore from backup (SAF tree) | BackupManager | E13 |
| 33 | Available backups list (delete) | BackupManager | E13 |
| 34 | CSV format help (static) | — | E13 |
| 35 | Instructions help (static) | — | E13 |

**Note:** AppConfigStore содержит 15 persisted полей; остальные settings persist в auxiliary stores (BgVocabMark, Profile, Lesson, Backup) или transient. E13 должен явно перечислить всех владельцев персистентности (GAP C6).

## K. Cross-cutting concerns (coordination risk)

1. **TrainingStateAccess (GAP C2) — ✅ CLOSED (AC-18)** — самый cross-cutting контракт. Потребляется E04/E07/E08/E10/E11/E12. E01 определил successor-порт до старта Wave 1: `domain/training/TrainingStateAccess.kt` (pure Kotlin), контракт зафиксирован в SRS-001 §5.11. Wave 1 разблокирован. Риск расхождения закрыт NFR-4 (стабильность контракта портов).
2. **AuditLogger instrumentation** — разбросан по SettingsScreen (каждый toggle), GrammarMateApplication (session start), feature modules. E13 владеет, но каждый эпик, трогающий экран, должен переинструментировать. Риск: silent regression где новые экраны перестают логировать.
3. **ScreenLogger** — трогает каждый маршрут в GrammarMateApp. Nav refactor (E13) должен сохранить события SHOWN/NAV/TAP/OVERLAY.
4. **applyLocale** — AndroidX per-app language API; Activity recreation. Любой эпик, трогающий экран со строками, должен учитывать locale применяется на Activity level.
5. **Crash handler** → public Downloads. E13 владеет; E02/E03 не должны менять пути crash_log.
6. **AppContainer/StoreFactory DI** — singleton + pack-scoped cache. E01/E02 extraction должна сохранить семантику или возникнут data-loss races (тот самый баг, который StoreFactory исправлял).
7. **AtomicFileWriter** — все stores + BackupRestorer. E02 Room migration: решить, оставить ли для non-Room (BadSentence export, crash logs, model logs).
8. **Restore gate** (AppRoot + RestoreNotifier) — E02 (data) и E13 (UI) координируют новые gate-семантики.

## L. Итог аудита

**Вердикт:** 13-эпический план покрывает legacy-функционал с высокой точностью. **Целых незакрытых функциональных областей нет.** 9 GAP'ов ниже — explicitness/naming-проблемы, патчатся в существующие эпики:

| GAP | Что | Куда | Критичность |
|---|---|---|---|
| C1 | BadSentenceHelper + BadSentenceStore + exportToTextFile | E04 (training card lifecycle) | medium |
| **C2** | **TrainingStateAccess interface (cross-cutting порт) — ✅ CLOSED (AC-18)** | **E01 (domain ports)** | **HIGH — блокирует Wave 1 → закрыто в E01: `domain/training/TrainingStateAccess.kt`** |
| C3 | ELITE/VOCAB route redirects (back-compat) | E13 (AppShell/Nav) | low |
| C4 | HomophoneReplacerConfig (ASR hook) | E03 (audio ASR) | low |
| C5 | ModelLoadLogger (→ Downloads/model_load_log.txt) | E03 (audio infra) | low |
| C6 | AppConfigStore split: 15 persisted vs 35 UI settings (inventory владельцев) | E13 (settings) | medium |
| C7 | PackLessonProgressStore + DrillProgressStore | E07 + E04 соответственно | medium |
| C8 | GrammarMateColors palette + CompositionLocal | E13 (theme) | low |
| C9 | appVersion "1.7" magic string → BuildConfig | E13 (shell) | low |

**Coverage rate:** ~150 legacy features inventory → 141 COVERED явно + 9 GAP (патчатся) + ~10 PARTIAL (naming-mismatch, уточняется в SRS). После патча 9 GAP → 100% покрытие.

**GATE:** после патча 9 GAP в эпики безопасно переходить к AC-этапу.

### SRS-declared gaps (closure log)

Три gap'а объявлены в SRS-001 (FR-10/FR-12/FR-14) как незакрытые кейсы
доменного ядра и адресуются AC-14/AC-15/AC-16 соответственно:

| Gap | Что | AC | Статус |
|---|---|---|---|
| #1 | `MultilingualStoryParser` → `:domain` (pure-Kotlin парсер) | AC-15 | pending |
| #2 | `DailyTask` composers — сборка дневной нормы из блоков | AC-16 | pending |
| **#3** | **`LessonCompletionCalculator` — контракт завершения покрывает все legacy-кейсы** | **AC-14** | **CLOSED (task #442, 2026-07-08)** |

**gap #3 → CLOSED:** `LessonCompletionCalculator` (`domain/progress/`) покрывает
все legacy-кейсы завершения урока/под-урока:
1. полностью скрытые под-уроки авто-завершаются
   (`calculateCompletedSubLessons`, `deliverableCards.isEmpty() → completed++`);
2. порог завершения = `min(effective, 150)`
   (`isLessonComplete`, cap из `TrainingConfig.LESSON_COMPLETION_CARD_THRESHOLD=150`);
3. `activeSubLessonIndex` монотонно неубывающий — добавлена pure-функция
   `advanceActiveSubLessonIndex(current, actual, size)` (`maxOf(current, actual)`,
   clamping), никогда не движется назад (regression-anchor к AC-13/AC-5/AC-6).

Проверка: `gradlew.bat :domain:test --tests "*LessonCompletionCalculatorTest*"`
exit 0 (24 теста, 0 failures).

---

*Документ обновляется при выявлении новых GAP'ов или изменении покрытия. Последний аудит: 2026-07-07, верифицировано чтением кода legacy на ветке `main`.*

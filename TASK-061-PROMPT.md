# TASK-061: Training Consolidation — One Engine, One Screen

## Контекст проекта

GrammarMate — Android приложение для изучения грамматики (Kotlin, Jetpack Compose, Material 3).
Путь к проекту: D:\Разработка\BaseGrammy
Ветка: feature/training-consolidation
Build команда (Windows): `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug`
APK путь: app/build/outputs/apk/debug/grammermate.apk

## Что сделано

TrainingScreen — единый экран для ВСЕХ карточных сессий. 9 режимов: NORMAL, BOSS, BOSS_MEGA, DRILL, ELITE, MIX_CHALLENGE, VERB_DRILL, DAILY_TRANSLATE, DAILY_VERBS.

VerbDrillScreen — теперь только выбор (tense/group), потом навигирует на TrainingScreen(mode=VERB_DRILL).
DailyPracticeScreen — теперь координатор: блоки TRANSLATE и VERBS идут через TrainingScreen, VOCAB рендерится inline (Anki flip — другая парадигма).
DailyPracticeCoordinator переписан на block-config модель: DailySessionState.blocks: List<DailyBlock> вместо плоского List<DailyTask>.

## Что НЕ доделано (твоя задача)

### 1. SessionRunner рефакторинг — убрать mode-ветки

Сейчас в SessionRunner.kt есть отдельные методы:
- startVerbDrillSession()
- startDailyTranslateSession()
- startDailyVerbsSession()
- exitVerbDrillSession()
- exitDailySession()

И when(mode) ветки в:
- submitAnswer() — ветки для VERB_DRILL, DAILY_TRANSLATE, DAILY_VERBS
- updateWordBank() — ветка для VERB_DRILL / DAILY_VERBS
- startSession() — exclusion guards для VERB_DRILL, DAILY_TRANSLATE, DAILY_VERBS
- nextCardInternal() — completion guards

Всё это заменить на:
- Один метод startSession(cards: List<SessionCard>, mode: TrainingScreenMode)
- Один метод exitSession()
- submitAnswer() — единая логика без when(mode)
- updateWordBank() — единая генерация word bank без when(mode)

### 2. returnTo токен — навигация

TrainingScreen не знает откуда его вызвали. Получает returnTo: String — куда вернуться после завершения.
- Урок → returnTo = HOME
- Daily Practice → returnTo = DAILY_PRACTICE
- Verb Drill → returnTo = VERB_DRILL
- Mix Challenge → returnTo = HOME

GrammarMateApp.kt передаёт returnTo при навигации на TRAINING. Когда TrainingScreen завершает сессию → navigate(returnTo).

### 3. Word bank — починить генерацию

Word bank — это InputMode.WORD_BANK, доступен в любом режиме. Кнопка [📚] в UnifiedInputControlsBar.

Генерация word bank зависит от ТИПА КАРТЫ, а не от режима экрана:
- SentenceCard → generateForSentence() — дистракторы из карт урока
- VerbDrillCard → generateForVerb() — дистракторы из ответов текущей сессии

Сейчас в updateWordBank() ветка по screenMode (неправильно):
```kotlin
if (mode == VERB_DRILL || mode == DAILY_VERBS) { generateForVerb(...) }
else { generateForSentence(...) }
```

Надо: ветка по типу карты (правильно):
```kotlin
if (currentCard is VerbDrillCard) { generateForVerb(...) }
else { generateForSentence(...) }
```

Также проверить: updateWordBank() вызывается после каждого старта сессии (startVerbDrillSession, startDailyTranslateSession, startDailyVerbsSession) и после навигации между картами.

### 4. Проверить что Daily Practice блоки 1 и 3 реально идут через TrainingScreen

Проверить навигацию: DailyPracticeScreen → extract cards → navigate TRAINING → TrainingScreen рендерит → завершение → navigate DAILY_PRACTICE → coordinator.onBlockComplete() → следующий блок.

### 5. ИЗВЕСТНЫЙ БАГ: Daily Practice показывает только блоки 1 и 2, блок 3 (VERBS) не запускается

После завершения VOCAB блока (block 2), координатор должен продвинуться на block 3 (VERBS). Но VERBS блок не запускается — DailyPracticeScreen не переходит к нему, сессия завершается после VOCAB.

Возможные причины:
- onBlockComplete() для VOCAB не вызывает blockIndex++ или не триггерит startNextBlock
- DailyPracticeScreen не реагирует на изменение blockIndex после VOCAB
- LaunchedEffect ключ не меняется когда блок переключается с VOCAB на VERBS
- coordinator.getCurrentRenderVia() возвращает неправильно после VOCAB

Проверить:
- Coordinator: после VOCAB onBlockComplete() → blockIndex становится 2 → blocks[2].type == VERBS → blocks[2].renderVia == TRAINING_SCREEN
- DailyPracticeScreen: рекомпозиция с blockIndex=2 → currentBlock.type == VERBS → LaunchedEffect → onStartCardBlock → navigate TRAINING
- GrammarMateApp: onStartCardBlock(DailyBlockType.VERBS, cards) → vm.startDailyVerbsSession(cards) → navigate TRAINING

Этот баг ОБЯЗАТЕЛЬНО воспроизвести и починить. Добавить в верификацию отдельный пункт.

## ПРИНЦИПЫ (обязательно соблюдать)

1. **Mode — конфигурация рендеринга, НЕ логика.** Mode определяет ТОЛЬКО: subtitle текст, фон (белый/зелёный), verb chips, completion экран. Mode НЕ влияет на: submit, word bank, навигацию между картами, answer validation.

2. **TrainingScreen — единственный экземпляр.** Любой элемент на нём (word bank, input, navigation, result) принадлежит этому экрану. Переиспользование — через конфигурацию, без изменения логики.

3. **Прогрессы независимы.** Страйки, mastery, verb progress считаются одинаково независимо от режима. Система не знает откуда вызывалась тренировка.

4. **TENSE_LADDER не трогать.** Логика уровня → активные времена в DailySessionComposer не меняется.

5. **VOCAB блок — отдельная парадигма.** Flip-card + SRS rating. НЕ через TrainingScreen. Остаётся внутри DailyPracticeScreen.

## ЧЕКЛИСТ ВЕРИФИКАЦИИ — ОБЯЗАТЕЛЬНО ПРОВЕРИТЬ КАЖДЫЙ ПУНКТ

### A. Mode = конфиг, не логика
- [ ] SessionRunner.submitAnswer() — НЕТ when(mode) веток
- [ ] SessionRunner.updateWordBank() — НЕТ when(mode) веток, единая генерация
- [ ] SessionRunner.startSession() — ОДИН метод вместо startXxxSession()
- [ ] SessionRunner.nextCard() — единая логика для всех режимов
- [ ] Answer validation — единая логика

### B. Навигация returnTo
- [ ] TrainingScreen получает returnTo: String
- [ ] Урок → returnTo = HOME
- [ ] Daily Practice → returnTo = DAILY_PRACTICE
- [ ] Verb Drill → returnTo = VERB_DRILL
- [ ] Mix Challenge → returnTo = HOME
- [ ] Завершение → navigate(returnTo)

### C. TrainingScreen — 9 режимов
- [ ] NORMAL — lesson cards из LessonRoadmap
- [ ] BOSS — boss battle review
- [ ] BOSS_MEGA — mega boss battle
- [ ] DRILL — lesson-scoped drill
- [ ] ELITE — refresh session
- [ ] MIX_CHALLENGE — mixed challenge
- [ ] VERB_DRILL — после VerbDrillScreen выбора
- [ ] DAILY_TRANSLATE — Daily Practice block 1
- [ ] DAILY_VERBS — Daily Practice block 3

### D. Daily Practice block-config
- [ ] DailySessionState.blocks: List<DailyBlock> (не плоский список)
- [ ] DailyBlock: type, tasks, renderVia (TRAINING_SCREEN / INLINE), isComplete
- [ ] Единый onBlockComplete() → blockIndex++
- [ ] TRANSLATE → TrainingScreen → done → navigate(DAILY_PRACTICE) → onBlockComplete()
- [ ] VOCAB → inline → done → onBlockComplete()
- [ ] VERBS → TrainingScreen → done → navigate(DAILY_PRACTICE) → onBlockComplete()
- [ ] Все блоки готовы → HOME + страйк

### E. Word bank
- [ ] Работает в NORMAL режиме
- [ ] Работает в VERB_DRILL режиме
- [ ] Работает в DAILY_TRANSLATE режиме
- [ ] Работает в DAILY_VERBS режиме
- [ ] updateWordBank() ветка по ТИПУ КАРТЫ (VerbDrillCard vs SentenceCard), НЕ по mode
- [ ] updateWordBank() вызывается после каждого старта сессии
- [ ] updateWordBank() вызывается после навигации между картами (nextCard)

### F. Прогрессы
- [ ] recordDailyCardPracticed() — mastery + answered count для TRANSLATE
- [ ] persistDailyVerbProgress() — everShown + todayShown для VERBS
- [ ] rateVocabCard() — SRS step для VOCAB
- [ ] endSession() → страйк засчитан
- [ ] cancelDailySession() → cursor advancement
- [ ] НЕ зависят от режима/returnTo

### G. Экраны — роли
- [ ] TrainingScreen — единый рендерер, ВСЁ через один composable
- [ ] VerbDrillScreen — ТОЛЬКО выбор, НЕ рендерит карточки
- [ ] DailyPracticeScreen — координатор, НЕ рендерит TRANSLATE/VERBS
- [ ] VocabDrillScreen — отдельная парадигма (flip+SRS), НЕ через TrainingScreen
- [ ] HomeScreen — точка входа, страйки

### H. Anti-check (ЭТОГО НЕ ДОЛЖНО БЫТЬ)
- [ ] НЕТ рендера sentence/verb карточек вне TrainingScreen
- [ ] НЕТ отдельных TrainingCardSession для sentence/verb вне TrainingScreen
- [ ] НЕТ when(mode) в SessionRunner бизнес-логике
- [ ] НЕТ отдельных startXxxSession методов в SessionRunner
- [ ] НЕТ advanceToNextBlock / advanceDailyBlock сканирования
- [ ] НЕТ mode-специфичных token listeners в GrammarMateApp

### I. Build + APK
- [ ] Build проходит: assembleDebug
- [ ] APK устанавливается на устройство

### J. Верификация на устройстве
- [ ] Урок → TrainingScreen → пройти → HOME → страйк
- [ ] Verb Drill → выбор → TrainingScreen → пройти → HOME
- [ ] Daily Practice → TRANSLATE через TrainingScreen → sparkle → VOCAB inline → sparkle → VERBS через TrainingScreen → HOME → страйк
- [ ] БАГ: VERBS блок (блок 3) реально запускается после VOCAB, не пропадает
- [ ] Word bank виден и работает во всех режимах
- [ ] Все режимы TrainingScreen показывают "GrammarMate" заголовок
- [ ] Нет регрессий

## Ключевые файлы

| Файл | Что |
|------|-----|
| app/.../feature/training/SessionRunner.kt | Session lifecycle — рефакторить |
| app/.../ui/screens/TrainingScreen.kt | Единый экран рендеринга |
| app/.../ui/GrammarMateApp.kt | Навигация, returnTo, routing |
| app/.../ui/TrainingViewModel.kt | Связка SessionRunner + Coordinator |
| app/.../ui/DailyPracticeScreen.kt | Координатор daily блоков |
| app/.../feature/daily/DailyPracticeCoordinator.kt | Block-config оркестрация |
| app/.../feature/daily/DailySessionComposer.kt | Построение блоков (TENSE_LADDER) |
| app/.../data/Models.kt | DailySessionState, DailyBlock, TrainingScreenMode |
| app/.../ui/VerbDrillScreen.kt | Выбор только |
| app/.../ui/VocabDrillScreen.kt | Отдельная парадигма, НЕ трогать |
| TASK-061-DESIGN.md | Архитектурный документ |
| TASK-061-CHECKLIST.md | Этот чеклист |

## Порядок работы

1. Прочитать TASK-061-DESIGN.md и TASK-061-CHECKLIST.md
2. Прочитать ключевые файлы из таблицы выше
3. Сделать рефакторинг SessionRunner (убрать mode-ветки, единый startSession)
4. Добавить returnTo токен в навигацию
5. Починить word bank если сломан
6. Собрать APK (assembleDebug)
7. Пройтись по ВСЕМ пунктам чеклиста A-J
8. Закоммитить с Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

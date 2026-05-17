# TASK-062: Pause -> Input -> Check -> Play Cascade

## Контекст
Ветка: develop
Build: `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug`

## Описание
When a training session is PAUSED (regular pause or after hint shown), the input field remains visible and functional. The user can type an answer, press Check to validate it, and then press Play to resume the session.

## Текущее поведение
- When paused, input field is hidden (pomodoro pause) or non-functional
- Check button requires sessionState == ACTIVE
- submitAnswer() has a guard that returns early if sessionState != ACTIVE
- After hint shown, user must press Play to retry the same card -- cannot type and check during hint state

## Целевое поведение (Variant B -- cascade)

### Flow:
1. Session is PAUSED (manual pause, hint shown, or pomodoro pause)
2. Input field remains VISIBLE and EDITABLE
3. User types answer -> Check button becomes ENABLED
4. User presses Check -> answer is validated (submitAnswer() runs even in PAUSED state)
5. After Check -> Play button ACTIVATES (shows PlayArrow)
6. User presses Play -> session resumes ACTIVE on same card (no auto-advance)

### Key changes:
1. **TrainingScreen.kt**: Remove `if (!state.pomodoro.isPaused)` guard around input controls. Input always visible when card exists.
2. **UnifiedInputControlsBar.kt**: Check button enabled = `inputText.isNotBlank() && currentCard != null` (remove session state gate)
3. **SessionRunner.submitAnswer()**: Remove `sessionState != ACTIVE` early-return guard. Allow submission during PAUSED/HINT_SHOWN.
4. **SessionRunner.togglePause()**: Do NOT clear inputText during pause.
5. **UnifiedNavigationRow.kt**: After Check is pressed during pause, ensure Play button shows PlayArrow and is enabled.

## Принципы
1. Input is ALWAYS available when a card exists -- regardless of pause state
2. Check validates the answer regardless of session state
3. Play RESUMES the session -- it does NOT auto-advance
4. Next button (ArrowForward) is the ONLY way to advance to next card
5. This applies to ALL modes: NORMAL, DRILL, ELITE, VERB_DRILL, DAILY_TRANSLATE, DAILY_VERBS

## Чеклист

### A. Input visible during pause
- [x] Regular pause -> input field visible and editable
- [x] Hint shown (3 retries) -> input field visible and editable
- [x] Pomodoro pause -> input field visible and editable
- [x] Input text preserved across pause/resume

### B. Check button cascade
- [x] During ACTIVE: Check works as before
- [x] During PAUSED: Check enabled when input non-blank
- [x] During HINT_SHOWN: Check enabled when input non-blank
- [x] Check during pause -> answer validated -> result shown
- [x] Check during pause does NOT advance to next card

### C. Play button after Check
- [x] After Check during pause -> Play button activates
- [x] Pressing Play -> resumes ACTIVE on same card
- [x] Play does NOT auto-advance

### D. Cross-mode
- [x] NORMAL mode -- cascade works
- [x] VERB_DRILL mode -- cascade works
- [x] DAILY_TRANSLATE mode -- cascade works
- [x] DAILY_VERBS mode -- cascade works
- [x] DRILL mode -- cascade works

### E. Anti-check
- [x] NO auto-advance after Check during pause
- [x] NO input hiding during any pause type
- [x] submitAnswer() does NOT require sessionState == ACTIVE

## Ключевые файлы
| Файл | Что менять |
|------|-----------|
| SessionRunner.kt | submitAnswer() guard, togglePause() input preservation |
| TrainingScreen.kt | Remove isPaused guard on input controls |
| UnifiedInputControlsBar.kt | Check button enabled condition |
| UnifiedNavigationRow.kt | Play button state after Check during pause |

## Спецификации обновлены
- 12-training-card-session.md -- sections 12.3.1, 12.3.3, 12.4.6, 12.4.8, 12.8.1
- 22-use-case-registry.md -- UC-02, UC-03 (AC8-AC11), UC-63 (AC6-AC7)
- 23-screen-elements.md -- TS-10, TS-25, TS-30, TCS-07, TCS-20, VD-19, VD-29, DP-13, DP-17
- 19-screen-catalog.md -- Screen 4 business rules
- 08-training-viewmodel.md -- togglePause(), showAnswer(), submitAnswer()
- scenario-01-training-flow.md -- Step 4 guard relaxation
- scenario-05-input-modes.md -- TC-08, TC-09

## Порядок работы
1. Прочитать все ключевые файлы
2. Реализовать изменения в SessionRunner (убрать guard)
3. Реализовать изменения в UI компонентах
4. Собрать APK
5. Пройтись по чеклисту A-E
6. Закоммитить

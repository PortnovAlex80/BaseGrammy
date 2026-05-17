# TASK-061: Training Consolidation — One Engine, One Screen

## Principle
Every screen that renders sentence card sessions MUST use TrainingScreen.
No screen renders sentence/verb card sessions independently.

## Checklist

### TrainingScreen modes (the ONE screen)
- [ ] NORMAL — lesson cards from LessonRoadmap
- [ ] BOSS — boss battle review
- [ ] BOSS_MEGA — mega boss battle
- [ ] DRILL — lesson-scoped drill sub-mode
- [ ] ELITE — refresh session
- [ ] MIX_CHALLENGE — mixed challenge from HomeScreen
- [ ] VERB_DRILL — after VerbDrill selection
- [ ] DAILY_TRANSLATE — Daily Practice block 1
- [ ] DAILY_VERBS — Daily Practice block 3

### Delegation screens (selection/setup only, no card rendering)
- [ ] VerbDrillScreen — selection only, navigates to TrainingScreen(VERB_DRILL)
- [ ] DailyPracticeScreen — coordinator only, navigates to TrainingScreen for blocks 1 & 3
- [ ] DailyPracticeScreen keeps VOCAB block (Anki flip) internally — different paradigm

### Anti-checklist — these must NOT exist
- [ ] NO screen renders sentence cards inline outside TrainingScreen
- [ ] NO separate composable creates its own TrainingCardSession for sentence/verb cards
- [ ] NO DailyPracticeSessionProvider wrapping card sessions (blocks 1 & 3)

### Excluded by design
- VocabDrillScreen — flip-card + SRS rating paradigm, NOT a sentence card session
- StoryQuizScreen — quiz, not a card session
- HomeScreen, LessonRoadmapScreen, LadderScreen — no card rendering

### Verification
- [ ] Build passes
- [ ] APK installed and tested
- [ ] All TrainingScreen modes show "GrammarMate" header
- [ ] Daily Practice blocks 1 & 3 show "GrammarMate" header (not "Daily Practice")
- [ ] VerbDrill session shows "GrammarMate" header
- [ ] No regressions in existing modes (NORMAL, BOSS, DRILL, VERB_DRILL)

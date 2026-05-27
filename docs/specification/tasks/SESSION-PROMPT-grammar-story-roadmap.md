# SESSION PROMPT: Grammar Story Roadmap Implementation

🚀 **Copy this entire prompt into a new Claude session to continue implementation**

---

## CONTEXT

We are implementing **Grammar Story Roadmap** - a new layer of chapters above lessons for narrative grammar learning. This adds a "Grammar Story Roadmap" screen (like Duolingo's Story mode) for packs that have chapter structure, while maintaining backward compatibility for existing packs.

**Current Status:** Design phase complete, ready to start implementation

---

## QUICK START

1. **Read the task specification:**
   ```
   Read: docs/specification/tasks/TASK-grammar-story-roadmap.md
   ```

2. **Run the create-task skill:**
   ```
   /create-task
   ```
   
   This will:
   - Review the task specification
   - Update relevant specs (01-models-and-state.md, 02-data-stores.md)
   - Create implementation task prompts
   - Link bidirectionally between task and specs

3. **Follow the wave decomposition** (5 waves, TEAM mode):
   - Wave 1: Data Layer (Foundation)
   - Wave 2: Business Logic (Feature Layer)
   - Wave 3: UI Components (Screens)
   - Wave 4: ViewModel Integration (Glue)
   - Wave 5: End-to-End Testing & Polish

---

## CRITICAL REQUIREMENTS

### 🎯 Must-Have Acceptance Criteria

**Read full criteria:** `docs/specification/acceptance-criteria-grammar-roadmap.md`

**Summary:**
1. ✅ Manifest v2 with chapters parses correctly
2. ✅ Conditional UI: packs with chapters → Roadmap, without → Classic Home
3. ✅ Story Reader renders markdown content
4. ✅ Chapter progress persists (pack-scoped)
5. ✅ Backward compatibility: existing packs work unchanged

**Critical Path:**
- NO breaking changes to existing packs
- NO regression in verb drill, daily practice, flower states
- ALL existing tests must pass

---

## 📋 MANDATORY CHECKLIST

### Before Implementation (Pre-Wave 1)
- [ ] Read TASK-grammar-story-roadmap.md completely
- [ ] Read acceptance-criteria-grammar-roadmap.md
- [ ] Run `/create-task` to update specs and create task prompts
- [ ] Review existing test patterns: `app/src/test/java/com/alexpo/grammermate/ui/RegularLessonClickUiTest.kt`
- [ ] Review VerbDrillSessionCardRegressionTest pattern for session card tests

### Wave 1: Data Layer
- [ ] Add `Chapter` and `ChapterProgress` to `Models.kt`
- [ ] Create `ChapterProgressStore` with YAML persistence
- [ ] Update `LessonPackManifest` for schema v2
- [ ] Add `LessonStore.getChapters()` and `getChapterStory()`
- [ ] Unit tests: manifest v2 parsing
- [ ] Unit tests: chapter progress calculation
- [ ] Unit tests: backward compat (manifest v1 → empty chapters)
- [ ] Build passes, no existing tests broken

### Wave 2: Business Logic
- [ ] Create `ChapterProgressCalculator` in `feature/progress/`
- [ ] Implement `calculateChapterProgress()` logic
- [ ] Integrate with `MasteryStore` and `FlowerCalculator`
- [ ] Integration tests: lesson complete → chapter progress updates
- [ ] Integration tests: chapter independence
- [ ] Edge cases: empty chapter, single lesson
- [ ] All integration tests green

### Wave 3: UI Components
- [ ] Create `GrammarStoryRoadmapScreen` composable
- [ ] Create `StoryReaderScreen` with markdown rendering
- [ ] Add chapter list with progress bars
- [ ] Add chapter status indicators (LOCKED/ACTIVE/DONE)
- [ ] Add buttons: Read Story, Continue, Verb Practice, Flashcards, Daily Practice
- [ ] Implement conditional routing in `GrammarMateApp.kt`
- [ ] **MANDATORY:** Write UI click tests (see below)
- [ ] All UI tests green, no UI regressions

### Wave 4: ViewModel Integration
- [ ] Add chapter fields to `TrainingUiState`
- [ ] Create `ChapterProgressHelper` in `feature/chapters/`
- [ ] Add ViewModel methods: loadChapters(), getChapterProgress(), etc.
- [ ] Update `ProgressRestorer` and `FlowerRefresher`
- [ ] **MANDATORY:** Regression tests (see below)
- [ ] TrainingViewModel < 2000 lines
- [ ] All regression tests green

### Wave 5: E2E Testing & Polish
- [ ] Run full test suite
- [ ] Manual testing checklist (80%+)
- [ ] Test on real device with sample pack
- [ ] Test pack switching
- [ ] Test error handling
- [ ] Update documentation (CLAUDE.md, specs)
- [ ] Performance profiling (< 500ms load time)
- [ ] Ready for code review

---

## 🧪 MANDATORY CLICK TESTS

**Pattern:** Follow `RegularLessonClickUiTest.kt` and `VerbDrillSessionCardRegressionTest.kt`

**Must create:** `app/src/test/java/com/alexpo/grammermate/ui/GrammarStoryRoadmapClickTest.kt`

**Required test cases:**
1. ✅ `manifestV2_showsGrammarStoryRoadmap()` - Conditional UI routing
2. ✅ `manifestV1_showsClassicHomeScreen()` - Backward compat
3. ✅ `clickReadStory_opensStoryReaderScreen()` - Story navigation
4. ✅ `storyReader_rendersMarkdownContent()` - Markdown rendering
5. ✅ `clickContinue_startsLastActiveLesson()` - Continue button
6. ✅ `clickVerbPractice_opensVerbDrill()` - Verb practice integration
7. ✅ `clickFlashcards_opensVocabDrill()` - Flashcards integration
8. ✅ `clickDailyPractice_opensDailyPractice()` - Daily practice integration
9. ✅ `chapterProgressBar_showsCorrectPercentage()` - Progress display
10. ✅ `chapterStatus_showsCorrectState()` - Status indicators

**Reference implementation already written in:** `docs/specification/tasks/TASK-grammar-story-roadmap.md` (Part 4)

---

## 🔒 MANDATORY REGRESSION TESTS

**Must verify existing functionality:**

1. ✅ **Old Pack Still Works** (RT-1)
   - Existing pack without chapters loads correctly
   - All lessons accessible
   - Mastery progress preserved
   - No functionality broken

2. ✅ **Verb Drill Unaffected** (RT-2)
   - Verb drill works identically
   - Progress independent from chapters
   - Session card functionality preserved

3. ✅ **Daily Practice Unaffected** (RT-3)
   - 3 blocks work as before
   - Cursor state correct
   - Streak credited

4. ✅ **Flower State Unchanged** (RT-4)
   - Flowers grow correctly
   - `uniqueCardShows` counted correctly
   - Health decay works per Ebbinghaus

**Test files to update/add:**
- `GrammarStoryRoadmapRegressionTest.kt` - Full regression suite
- Add to existing test files if chapter logic affects them

---

## 📁 KEY FILES TO READ

### Specification
- `docs/specification/tasks/TASK-grammar-story-roadmap.md` - **READ THIS FIRST**
- `docs/specification/acceptance-criteria-grammar-roadmap.md` - Full acceptance criteria
- `docs/specification/01-models-and-state.md` - Data models reference
- `docs/specification/02-data-stores.md` - Stores reference
- `docs/specification/08-training-viewmodel.md` - ViewModel reference

### Existing Test Patterns
- `app/src/test/java/com/alexpo/grammermate/ui/RegularLessonClickUiTest.kt` - UI click test pattern
- `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt` - Regression test pattern
- `app/src/test/java/com/alexpo/grammermate/ui/VerbPracticeClickUiTest.kt` - Feature test pattern

### Code Files to Modify
- `app/src/main/java/com/alexpo/grammermate/data/Models.kt` - Add Chapter, ChapterProgress
- `app/src/main/java/com/alexpo/grammermate/data/LessonPackManifest.kt` - Schema v2 support
- `app/src/main/java/com/alexpo/grammermate/data/LessonStore.kt` - getChapters(), getChapterStory()
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt` - Add chapter fields
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` - Conditional routing
- `app/src/main/java/com/alexpo/grammermate/ui/screens/` - New screens

---

## 🚨 RISK MITIGATION

### Critical Risks
1. **TrainingViewModel bloat** - Extract chapter logic to `ChapterProgressHelper`
2. **Backward compatibility** - Comprehensive regression tests before Wave 1
3. **Manifest migration** - Support v1 and v2 in same parser (fallback)
4. **Performance** - Lazy load chapter progress, cache in memory

### Rollback Plan
If critical regression found:
1. Revert Waves 4-5 (ViewModel + UI integration)
2. Keep Waves 1-2 (data layer - backward compat)
3. Feature flag: disable Roadmap UI via config
4. Hotfix: address specific regression
5. Re-integrate Waves 4-5 with fix

---

## 📊 SUCCESS METRICS

### Code Quality
- [ ] All tests green (unit + integration + UI + regression)
- [ ] TrainingViewModel < 2000 lines
- [ ] No TODOs in production code
- [ ] AtomicFileWriter used for all persistence

### Functional
- [ ] Manifest v2 packs show Roadmap
- [ ] Manifest v1 packs show Classic Home
- [ ] Story Reader renders markdown
- [ ] Chapter progress persists correctly
- [ ] All practice modes work from Roadmap

### Performance
- [ ] Roadmap loads < 500ms for 10 chapters
- [ ] Story Reader renders < 300ms for 100KB markdown
- [ ] No UI jank on scroll

---

## 🎯 FIRST ACTIONS IN NEW SESSION

1. **Read this entire prompt** ✓ (you're here!)

2. **Run the command:**
   ```
   /create-task
   ```

3. **Follow the skill's guidance** to:
   - Review requirements
   - Update specs
   - Create implementation task prompts
   - Link everything bidirectionally

4. **Start Wave 1** when `/create-task` completes

---

## 📚 DOCUMENTATION STRUCTURE

```
docs/specification/
├── TASK-grammar-story-roadmap.md          ← READ THIS FIRST
├── acceptance-criteria-grammar-roadmap.md ← Full acceptance criteria
├── 01-models-and-state.md                  ← Will update with Chapter models
├── 02-data-stores.md                       ← Will update with ChapterProgressStore
└── 08-training-viewmodel.md                ← Will update with chapter integration

app/src/test/java/com/alexpo/grammermate/ui/
├── GrammarStoryRoadmapClickTest.kt         ← MUST CREATE (UI tests)
└── GrammarStoryRoadmapRegressionTest.kt    ← MUST CREATE (regression)
```

---

## ✅ DEFINITION OF DONE

Feature is complete when:
1. ✅ All Must Have (P0) acceptance criteria met
2. ✅ All Regression Tests (RT-1 to RT-4) pass
3. ✅ Manual Testing Checklist 80%+ complete
4. ✅ Code review approved
5. ✅ Documentation updated (specs, CLAUDE.md)
6. ✅ Backward compatibility verified on existing packs
7. ✅ Performance benchmarks met (< 500ms load time)

---

**Created:** 2026-05-25  
**Status:** Ready for implementation  
**First Command:** `/create-task`

---

🚀 **Copy everything above this line into a new Claude session to begin!**

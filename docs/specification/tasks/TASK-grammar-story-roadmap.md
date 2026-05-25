# TASK: Grammar Story Roadmap Implementation

**Status:** Draft  
**Priority:** High  
**Complexity:** Complex  
**Layers Affected:** Data, UI, Feature  
**Estimated Effort:** 3-5 days

---

## Acceptance Criteria

📋 **Full acceptance criteria:** [acceptance-criteria-grammar-roadmap.md](../acceptance-criteria-grammar-roadmap.md)

### Must Have (P0)
- Manifest v2 с chapters парсится корректно
- Conditional UI routing (Roadmap vs Classic Home)
- Story Reader markdown rendering
- Chapter progress persistence
- Backward compatibility для существующих packs

---

## Assessment Output

**VERDICT:** TEAM  
**AGENTS:** 3-4 per wave  
**COMPLEXITY:** Complex  
**LAYERS AFFECTED:**
- Data: Models, Stores (LessonStore, ChapterProgressStore)
- Feature: ChapterProgressCalculator
- UI: GrammarStoryRoadmapScreen, StoryReaderScreen, GrammarMateApp routing

**RISKS:**
- Breaking backward compatibility для существующих packs
- TrainingViewModel перегрузка (уже ~1500 lines)
- Manifest migration edge cases
- Performance на large packs

---

## Decomposition Plan

### Wave 1: Data Layer (Foundation)
**Goal:** Создать data models и stores для chapters

**Agents:** 2 parallel
- **DATA-AGENT:** Models + Stores
- **TEST-AGENT:** Unit tests для data layer

**Tasks:**
1. Add `Chapter` and `ChapterProgress` data models to `Models.kt`
2. Create `ChapterProgressStore` with pack-scoped YAML persistence
3. Update `LessonPackManifest` to support schema v2 with chapters
4. Add `LessonStore.getChapters()` and `getChapterStory()` methods
5. Write unit tests for manifest v2 parsing
6. Write unit tests for chapter progress calculation
7. Test backward compat: manifest v1 → empty chapters list

**Checkpoint:** Build passes, unit tests green, no existing tests broken

---

### Wave 2: Business Logic (Feature Layer)
**Goal:** Создать helper для chapter progress calculation

**Agents:** 2 parallel
- **FEATURE-AGENT:** ChapterProgressCalculator + integration
- **TEST-AGENT:** Integration tests

**Tasks:**
1. Create `ChapterProgressCalculator` in `feature/progress/`
2. Implement `calculateChapterProgress(chapter, masteryStates)` logic
3. Integrate with existing `MasteryStore` and `FlowerCalculator`
4. Add chapter progress updates to `ProgressTracker`
5. Write integration tests: lesson complete → chapter progress updates
6. Test chapter independence: progress isolated across chapters
7. Test edge cases: empty chapter, single lesson chapter

**Checkpoint:** Integration tests green, progress calculation correct

---

### Wave 3: UI Components (Screens)
**Goal:** Создать новые UI screens

**Agents:** 2 parallel
- **UI-AGENT:** GrammarStoryRoadmapScreen + StoryReaderScreen
- **TEST-AGENT:** UI click tests

**Tasks:**
1. Create `GrammarStoryRoadmapScreen` composable
2. Create `StoryReaderScreen` composable with markdown rendering
3. Add chapter list display with progress bars
4. Add chapter status indicators (LOCKED/ACTIVE/DONE)
5. Add buttons: Read Story, Continue, Verb Practice, Flashcards, Daily Practice
6. Implement conditional routing in `GrammarMateApp.kt`
7. Write UI click tests: all buttons work
8. Write UI click tests: backward compat (manifest v1 → Classic Home)
9. Write StoryReader markdown rendering test

**Checkpoint:** UI renders, click tests green, no UI regressions

---

### Wave 4: ViewModel Integration (Glue)
**Goal:** Интегрировать chapters в TrainingViewModel

**Agents:** 1-2
- **VIEWMODEL-AGENT:** TrainingViewModel updates + helpers
- **TEST-AGENT:** Regression tests

**Tasks:**
1. Add chapter fields to `TrainingUiState`:
   - `chapters: List<Chapter>`
   - `activeChapterId: String?`
   - `chapterProgresses: Map<String, ChapterProgress>`
   - `currentStoryContent: String?`
2. Create `ChapterProgressHelper` in `feature/chapters/`
3. Add ViewModel methods:
   - `loadChapters()`
   - `getChapterProgress(chapterId)`
   - `openStoryReader(chapterId)`
   - `continueChapter(chapterId)`
4. Update `ProgressRestorer` to load chapter progress
5. Update `FlowerRefresher` to refresh chapter progress
6. Add regression tests: old packs still work
7. Add regression tests: verb drill unaffected
8. Add regression tests: daily practice unaffected
9. Add regression tests: flower state unchanged

**Checkpoint:** All regression tests green, ViewModel < 2000 lines

---

### Wave 5: End-to-End Testing & Polish
**Goal:** Полное тестирование и финальная полировка

**Agents:** 2 parallel
- **E2E-AGENT:** Manual testing + bug fixes
- **DOCS-AGENT:** Documentation updates

**Tasks:**
1. Run full test suite: unit + integration + UI + regression
2. Manual testing checklist execution (80%+)
3. Test on real device/emulator with sample pack
4. Test pack switching scenarios
5. Test error handling: missing story files, corrupted manifests
6. Update CLAUDE.md with chapter architecture
7. Update 01-models-and-state.md with new models
8. Update 02-data-stores.md with ChapterProgressStore
9. Add sample manifest v2 to assets for testing
10. Performance profiling: load time < 500ms for 10 chapters

**Checkpoint:** Manual checklist 80%+, documentation updated, ready for review

---

## Implementation Notes

### Dependencies
- **Wave 1** → **Wave 2** (data models needed for calculation)
- **Wave 2** → **Wave 3** (calculation logic needed for UI display)
- **Wave 3** → **Wave 4** (UI components need ViewModel integration)
- **Wave 4** → **Wave 5** (full integration needed for E2E testing)

### Risk Mitigation
- **TrainingViewModel bloat:** Extract chapter-specific logic to `ChapterProgressHelper`
- **Backward compat:** Add comprehensive regression tests before Wave 1
- **Manifest migration:** Support both v1 and v2 in same parser (fallback)
- **Performance:** Lazy load chapter progress, cache in memory

### Testing Strategy
- **Unit Tests:** Wave 1-2 (data + business logic)
- **Integration Tests:** Wave 2 (progress calculation)
- **UI Click Tests:** Wave 3 (screen interactions)
- **Regression Tests:** Wave 4 (existing functionality)
- **Manual Testing:** Wave 5 (real-world scenarios)

---

## Success Metrics

### Code Quality
- [ ] All tests green (unit + integration + UI + regression)
- [ ] TrainingViewModel < 2000 lines (extract helpers if needed)
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

### Backward Compatibility
- [ ] Existing packs load without errors
- [ ] Mastery progress preserved
- [ ] Verb drill works identically
- [ ] Daily practice works identically
- [ ] Flower states calculate correctly

---

## Rollback Plan

If critical regression found:
1. Revert Waves 4-5 (ViewModel + UI integration)
2. Keep Waves 1-2 (data layer - backward compat)
3. Feature flag: disable Roadmap UI via config
4. Hotfix: address specific regression
5. Re-integrate Waves 4-5 with fix

---

## References

- **Spec:** [01-models-and-state.md](../01-models-and-state.md)
- **Stores:** [02-data-stores.md](../02-data-stores.md)
- **Training VM:** [08-training-viewmodel.md](../08-training-viewmodel.md)
- **Acceptance Criteria:** [acceptance-criteria-grammar-roadmap.md](../acceptance-criteria-grammar-roadmap.md)
- **Click Tests Pattern:** `app/src/test/java/com/alexpo/grammermate/ui/RegularLessonClickUiTest.kt`

---

**Created:** 2026-05-25  
**Last Updated:** 2026-05-25  
**Status:** Ready for Assessment → Wave execution

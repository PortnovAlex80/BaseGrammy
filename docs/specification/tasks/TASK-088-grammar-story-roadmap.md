# TASK-088: Grammar Story Roadmap Implementation

**Status:** OPEN
**Created:** 2026-05-25
**Branch:** feature/grammar-story-roadmap (from main)
**Spec:** 01-models-and-state.md#1.1, 02-data-stores.md#2.15, 22-use-case-registry.md#Domain 30
**UC:** UC-88, UC-89, UC-90, UC-91, UC-92, UC-93, UC-94
**Scenario:** acceptance-criteria-grammar-roadmap.md

---

## Problem

Текущая архитектура GrammarMate не поддерживает нарративное обучение грамматике через истории. Все уроки находятся на одном уровне (плоский список), нет возможности группировать уроки в главы с сюжетной линий. Это ограничивает педагогические возможности - невозможно создать сюжетное погружение (как в Duolingo Stories), где пользователь следит по истории, параллельно изучая грамматику.

**Root cause:** Модель данных не имеет слоя "Chapters" между Pack и Lessons. UI всегда показывает плоский список уроков (Classic Home), независимо от содержимого пака.

**Desired state:** Паки могут иметь главы (Chapters) с историями в markdown формате. Паки с главами показывают Grammar Story Roadmap, паки без глав — классический Home. Прогресс по главам pack-scoped, независимый между главами.

---

## Changes

### Fix 1: Add Chapter and ChapterProgress data models
**Discrepancy:** N/A | **UC:** UC-88 AC1-2, UC-89 AC1-2 | **Spec:** 01-models-and-state.md#1.1

Добавить новые data classes для поддержки глав в системе.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/data/Models.kt` — добавить data classes

**What to do:**
1. Add `Chapter` data class with fields: `chapterId`, `order`, `title`, `subtitle`, `storyFile`, `lessons` (List<String>)
2. Add `ChapterProgress` data class with fields: `chapterId`, `lessonsStarted`, `lessonsCompleted`, `lastAccessedMs`
3. Add invariants validation: `lessonsStarted >= lessonsCompleted`, `lessonsCompleted <= lessons.size`

**Verification:** Data classes compile successfully, field types match spec, invariants enforced in init blocks.

---

### Fix 2: Create ChapterProgressStore for pack-scored progress tracking
**Discrepancy:** N/A | **UC:** UC-92 AC1-3, UC-93 AC1-2 | **Spec:** 02-data-stores.md#2.15

Создать новый store для персистентности прогресса по главам.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/data/ChapterProgressStore.kt` — новый файл
- `app/src/main/java/com/alexpo/grammermate/data/AtomicFileWriter.kt` — использовать для записи

**What to do:**
1. Create `ChapterProgressStore` class with pack-scoped file path: `grammarmate/packs/{packId}/chapter_progress.yaml`
2. Implement methods: `loadAll()`, `saveAll()`, `getProgress()`, `upsertProgress()`, `clear()`
3. Use YAML schema version 1 with `data` map keyed by chapterId
4. Use `AtomicFileWriter` for all writes (temp -> fsync -> rename pattern)
5. No caching (read from disk on every call, like VerbDrillStore)

**Verification:** Store persists and loads chapter progress correctly, atomic writes prevent corruption on crash, pack scoping works (different packs have independent progress).

---

### Fix 3: Update LessonPackManifest for schema v2 with chapters
**Discrepancy:** N/A | **UC:** UC-88 AC1-2, UC-94 AC1-2 | **Spec:** 01-models-and-state.md#1.1.24

Обновить парсер манифеста для поддержки schema version 2 с полем `chapters`.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/data/LessonPackManifest.kt` — обновить парсер

**What to do:**
1. Update `LessonPackManifest` to accept `schemaVersion: 1` or `2`
2. Add `chapters: List<Chapter>` field (default emptyList for v1)
3. In `fromJson()`: if `schemaVersion == 2`, parse `chapters` array; if v1, set to emptyList
4. Update validation: v2 requires at least one chapter with non-empty lessons OR drill sections
5. Maintain backward compat: v1 manifests without `chapters` field parse successfully

**Verification:** Manifest v2 with chapters parses correctly, manifest v1 parses without errors (empty chapters list), validation rejects invalid manifests.

---

### Fix 4: Add LessonStore methods for chapter access
**Discrepancy:** N/A | **UC:** UC-88 AC1-2, UC-90 AC2, UC-94 AC2 | **Spec:** 02-data-stores.md#2.1

Добавить методы в LessonStore для получения глав и контента историй.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/data/LessonStore.kt` — добавить методы

**What to do:**
1. Add `getChapters(packId): List<Chapter>` — reads manifest, returns chapters list (empty for v1)
2. Add `getChapterStory(packId, storyFile): String?` — loads markdown file from pack's story directory
3. Add `hasChapters(packId): Boolean` — checks if pack has non-empty chapters list
4. Story files location: `grammarmate/packs/{packId}/stories/{storyFile}`

**Verification:** `getChapters()` returns correct list for v2 packs, empty list for v1. `getChapterStory()` loads markdown content or null if missing. `hasChapters()` correctly determines pack type.

---

### Fix 5: Create ChapterProgressCalculator for progress computation
**Discrepancy:** N/A | **UC:** UC-89 AC2-3, UC-92 AC1-5 | **Spec:** Feature layer (new helper)

Создать helper для вычисления прогресса по главам на основе mastery data.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/feature/progress/ChapterProgressCalculator.kt` — новый файл

**What to do:**
1. Create `ChapterProgressCalculator` object with `calculateChapterProgress()` method
2. Input: `chapter: Chapter`, `masteryStates: Map<String, LessonMasteryState>`
3. Logic: count lessons with `uniqueCardShows > 0` (started), count with `intervalStepIndex >= 3` (completed)
4. Return `ChapterProgress` with computed counts and `lastAccessedMs` (max from all lessons)
5. Edge cases: empty chapter returns zero progress, single lesson chapter works correctly

**Verification:** Calculator correctly computes progress for chapters with 0, 1, and multiple lessons. Progress updates when mastery changes. Independent across chapters (lesson in Chapter 1 doesn't affect Chapter 2 progress).

---

### Fix 6: Create GrammarStoryRoadmapScreen UI
**Discrepancy:** N/A | **UC:** UC-89 AC1-5, UC-91 AC1-5 | **Spec:** 22-use-case-registry.md#UC-89, UC-91

Создать новый UI экран для отображения карты глав.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/screens/GrammarStoryRoadmapScreen.kt` — новый файл
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt` — добавить chapter state fields

**What to do:**
1. Create `GrammarStoryRoadmapScreen` composable with chapter list display
2. Each chapter card shows: title, subtitle, progress bar (lessonsCompleted/total), status icon (LOCKED/ACTIVE/DONE)
3. Add buttons: "Read Story" (if storyFile != null), "Continue", "Verb Practice", "Flashcards", "Daily Practice"
4. Status logic: LOCKED (no prior chapter progress), ACTIVE (started but not completed), DONE (all lessons completed)
5. Connect to ViewModel for chapter data loading

**Verification:** Screen renders all chapters from manifest in order. Progress bars show correct percentages. Status icons display correctly (Chapter 0 DONE, Chapter 1 ACTIVE, Chapters 2+ LOCKED). All buttons navigate to correct screens.

---

### Fix 7: Create StoryReaderScreen with markdown rendering
**Discrepancy:** N/A | **UC:** UC-90 AC1-6 | **Spec:** 22-use-case-registry.md#UC-90

Создать экран для чтения историй с markdown рендерингом.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/screens/StoryReaderScreen.kt` — новый файл

**What to do:**
1. Create `StoryReaderScreen` composable accepting chapter title and markdown content
2. Use markdown rendering library (e.g., MarkdownText from accompanist or custom)
3. Support: headers, bold, italic, lists, code blocks
4. Add scrollable content for long stories
5. Add "Back to Roadmap" button that returns to `GrammarStoryRoadmapScreen`

**Verification:** Markdown renders with mobile-adapted layout. Long content scrolls smoothly. Back navigation preserves progress. Missing story files are handled gracefully (button hidden).

---

### Fix 8: Implement conditional routing in GrammarMateApp
**Discrepancy:** N/A | **UC:** UC-88 AC1-4 | **Spec:** 22-use-case-registry.md#UC-88

Обновить роутинг приложения для переключения между Roadmap и Classic Home.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` — обновить when() clause

**What to do:**
1. Add check in `GrammarMateApp` navigation logic: if `activePackId != null && lessonStore.hasChapters(activePackId)`, show `GrammarStoryRoadmapScreen`
2. Otherwise, show existing `ClassicHomeScreen` (current behavior)
3. Update routing to react to pack switches (re-evaluate when `activePackId` changes)

**Verification:** Packs with chapters (schema v2) show `GrammarStoryRoadmapScreen`. Packs without chapters (schema v1) show `ClassicHomeScreen`. Switching active pack updates routing immediately. No regression in existing navigation flows.

---

### Fix 9: Integrate chapter progress into TrainingViewModel
**Discrepancy:** N/A | **UC:** UC-92 AC1-5, UC-93 AC1-2 | **Spec:** 08-training-viewmodel.md

Добавить chapter progress в ViewModel и обновлять при завершении уроков.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt` — добавить chapter state fields
- `app/src/main/java/com/alexpo/grammermate/feature/progress/ProgressTracker.kt` — обновить логику

**What to do:**
1. Add to `TrainingUiState`: `chapters: List<Chapter>`, `chapterProgresses: Map<String, ChapterProgress>`, `activeChapterId: String?`
2. Add ViewModel methods: `loadChapters()`, `getChapterProgress(chapterId)`, `updateChapterProgress(lessonId)`
3. Call `ChapterProgressCalculator.calculateChapterProgress()` when lesson mastery updates
4. Persist via `ChapterProgressStore.upsertProgress()` atomically
5. Load chapter progress on app init (pack-scoped)

**Verification:** Chapter progress loads on app start. Lesson completion updates chapter progress atomically. Switching packs loads correct chapter progress. Progress bars update in real-time on roadmap.

---

## Verification Checklist

1. **Manifest v2 parsing:** Pack with `schemaVersion: 2` and `chapters` array parses correctly, chapters list populated
2. **Backward compatibility:** Manifest v1 packs load without errors, `getChapters()` returns empty list, Classic Home shows
3. **Conditional routing:** Packs with chapters show GrammarStoryRoadmapScreen, packs without show Classic HomeScreen
4. **Chapter progress tracking:** Completing lesson in chapter increments `lessonsCompleted`, progress bar updates
5. **Progress isolation:** Progress in Pack A Chapter 1 doesn't affect Pack B Chapter 1 (same chapterId allowed)
6. **Story Reader:** Markdown renders correctly, long content scrolls, back navigation works
7. **Practice modes integration:** "Continue", "Verb Practice", "Flashcards", "Daily Practice" buttons work from Roadmap
8. **Regression tests:** Verb drill, daily practice, flower states work identically for all packs (RT-2, RT-3, RT-4)
9. **Build passes:** `assembleDebug` completes without errors
10. **Tests pass:** `test` suite passes, no new failures

---

## Scope Boundaries

**Do NOT touch:**
- Existing lesson mastery calculation (MasteryStore, FlowerCalculator) — only read from it
- Verb drill, vocab drill, daily practice core logic — only navigation integration
- Existing UI screens except GrammarMateApp routing — VerbDrillScreen, VocabDrillScreen, DailyPracticeScreen unchanged
- Global state (mastery.yaml, progress.yaml) — chapter progress is pack-scoped only
- TrainingViewModel size limit enforcement — but extract chapter logic to helpers if approaching 2000 lines

**Out of scope:**
- Chapter unlock logic beyond sequential (e.g., mastery-based unlocking) — use simple sequential for now
- Story search, bookmarks, offline caching — nice-to-have features for future
- Chapter editing/creation UI — chapters are author-time only (in pack manifest)
- Migration tool for v1→v2 packs — manual pack update required

---

## Regression Plan

After all fixes are implemented, run:

1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:**
   - Import manifest v2 pack with chapters → verify GrammarStoryRoadmapScreen shows
   - Import manifest v1 pack → verify Classic Home shows
   - Complete lesson in Chapter 0 → verify progress bar updates to 100%, status DONE
   - Switch active pack → verify routing updates, progress preserved
   - Tap "Read Story" → verify StoryReaderScreen opens with markdown
   - Tap "Continue" → verify last active lesson loads
   - Tap "Verb Practice" → verify VerbDrillScreen opens (no regression)
   - Tap "Flashcards" → verify VocabDrillScreen opens (no regression)
   - Tap "Daily Practice" → verify DailyPracticeScreen opens (no regression)
4. **Cross-task regression:**
   - Verify existing packs load without errors
   - Verify mastery progress preserved for old packs
   - Verify verb drill session card functionality unchanged
   - Verify daily practice 3-block flow unchanged
   - Verify flower states calculate correctly (Ebbinghaus math unchanged)
5. **UC/AC spot-check:** Read UC-88 through UC-94 from `22-use-case-registry.md`, confirm all ACs hold
6. **Spec sync:** If code diverged from spec intentionally, update `01-models-and-state.md`, `02-data-stores.md`, acceptance criteria document

---

## Git

**Commit strategy:** One combined commit for all fixes (atomic feature addition).

**Commit message:**
```
feat: Add Grammar Story Roadmap with chapters and story reader

- Add Chapter and ChapterProgress data models
- Create ChapterProgressStore for pack-scored progress tracking
- Update LessonPackManifest to support schema v2 with chapters
- Add LessonStore methods for chapter and story access
- Create ChapterProgressCalculator for progress computation
- Implement GrammarStoryRoadmapScreen with chapter list and progress bars
- Implement StoryReaderScreen with markdown rendering
- Add conditional routing (Roadmap vs Classic Home) in GrammarMateApp
- Integrate chapter progress into TrainingViewModel

Backward compatible: manifest v1 packs show Classic Home unchanged.
All practice modes (Verb Drill, Vocab Drill, Daily Practice) work from Roadmap.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

## Completion Log

| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| 2026-05-25 | Fix 1: Data models | OPEN | Pending implementation |
| 2026-05-25 | Fix 2: ChapterProgressStore | OPEN | Pending implementation |
| 2026-05-25 | Fix 3: Manifest v2 | OPEN | Pending implementation |
| 2026-05-25 | Fix 4: LessonStore methods | OPEN | Pending implementation |
| 2026-05-25 | Fix 5: Progress calculator | OPEN | Pending implementation |
| 2026-05-25 | Fix 6: GrammarStoryRoadmapScreen | OPEN | Pending implementation |
| 2026-05-25 | Fix 7: StoryReaderScreen | OPEN | Pending implementation |
| 2026-05-25 | Fix 8: Conditional routing | OPEN | Pending implementation |
| 2026-05-25 | Fix 9: ViewModel integration | OPEN | Pending implementation |

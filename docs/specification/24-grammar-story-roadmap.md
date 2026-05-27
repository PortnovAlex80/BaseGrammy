# 24. Grammar Story Roadmap -- Specification

## Overview

Grammar Story Roadmap is a narrative layer that organizes lessons into chapters with allegorical stories. This feature provides a story-driven learning experience where users progress through chapters while learning grammar concepts.

**Implementation Status:** ✅ COMPLETED (feature/grammar-story-roadmap branch)

**Key Features:**
- Chapter-based learning with 8 chapters
- Story reader with markdown rendering
- Chapter progress tracking (lessonsStarted, lessonsCompleted, lastAccessedMs)
- Conditional UI routing (chapters vs classic home)
- Language-dependent story content (Russian/English)
- Settings icon in top bar
- Removed all status icons (locks, checkmarks)
- Conditional drill button visibility
- Back navigation to pack selection

---

## Architecture

### Data Layer

**Chapter Data Model:**
```kotlin
data class Chapter(
    val chapterId: String,
    val order: Int,
    val title: String,
    val subtitle: String? = null,
    val storyFile: String? = null,
    val lessons: List<String> = emptyList()
)
```

**ChapterProgress Data Model:**
```kotlin
data class ChapterProgress(
    val chapterId: String,
    val lessonsStarted: Int = 0,
    val lessonsCompleted: Int = 0,
    val lastAccessedMs: Long = 0L
)
```

**Stores:**
- `ChapterProgressStore`: Pack-scoped chapter progress storage
- `ChapterProgressCalculator`: Computes progress from mastery data
- `LessonStore`: Extended to support manifest v2 with chapters

### UI Layer

**Screens:**
- `GrammarStoryRoadmapScreen`: Main chapter navigation screen
- `StoryReaderScreen`: Markdown story viewer
- `ClassicHomeScreen`: Fallback for packs without chapters

**Navigation Flow:**
```
Pack Selection (no active pack)
    ↓ [select pack]
Grammar Story Roadmap (chapters pack)
    ↓ [continue lesson]
Training Screen (LESSON mode)
    ↓ [back]
Grammar Story Roadmap
    ↓ [back button]
Pack Selection
```

---

## Manifest v2 Schema

Packs with chapters use `schemaVersion: 2`:

```json
{
  "schemaVersion": 2,
  "languageId": "it",
  "languageName": "Italian",
  "chapters": [
    {
      "chapterId": "chapter_0",
      "order": 0,
      "title": "Before Language",
      "subtitle": "The silence before words",
      "storyFile": "chapter_00_original.md",
      "lessons": [
        "lesson_01_A01",
        "lesson_02_A02"
      ]
    },
    {
      "chapterId": "chapter_1",
      "order": 1,
      "title": "First Words",
      "subtitle": "Breaking the silence",
      "storyFile": "chapter_01_original.md",
      "lessons": [
        "lesson_03_A03",
        "lesson_04_A04"
      ]
    }
  ]
}
```

**Backward Compatibility:**
- `schemaVersion: 1` packs continue to work
- `getChapters()` returns `emptyList()` for v1 packs
- UI shows `ClassicHomeScreen` for v1 packs

---

## Chapter Status System

**Status States:**
- **ACTIVE**: Chapter in progress (green highlight)
  - `lessonsStarted > 0 && lessonsCompleted < totalLessonsInChapter`
- **DONE**: All lessons completed
  - `lessonsCompleted == totalLessonsInChapter`

**Removed States:**
- ~~LOCKED~~: Removed for cleaner UX
- No status icons (locks, checkmarks) in UI

**Progress Calculation:**
- `lessonsStarted`: Count of lessons with `mastery > 0`
- `lessonsCompleted`: Count of lessons with `intervalStepIndex >= 3`
- Progress %: `(lessonsCompleted / totalLessonsInChapter) * 100`

---

## Story Reader

**Story File Location:**
1. Primary: `<storyFile>` in pack root
2. Fallback: `stories/<language>/<storyFile>`

**Language Detection:**
- Stories are language-dependent
- Russian stories for RU interface
- English stories for EN interface
- Filename pattern: `<storyFile>` or `<language>/<storyFile>`

**Markdown Rendering:**
- Mobile-optimized layout
- Supports: headers, bold, italic, lists, links
- Scrollable content
- Back navigation to roadmap

**Error Handling:**
- Missing story file: Hide "Read Story" button
- Corrupted markdown: Show error message
- Empty story: Show placeholder

---

## UI Changes

### Top Bar
- **Settings Icon**: Moved to top bar (removed from home tiles)
- **Back Button**: Returns to pack selection (only on HOME route)

### Conditional Visibility
- **No Pack Selected**: Hide lesson tiles, daily practice
- **Drill Buttons**: Only show if `hasVerbDrill` or `hasVocabDrill`
- **Chapter Cards**: Show all chapters in order

### Removed Elements
- All status icons (locks, checkmarks)
- Chapter LOCKED state
- Settings tile from home screen

### Color Scheme
- **ACTIVE Chapter**: Green highlight (`MasteryGreen`)
- **DONE Chapter**: Default card color
- **Progress Bars**: Green gradient

---

## Progress Tracking

**Pack-Scoped Storage:**
- Each pack has independent chapter progress
- File path: `grammarmate/packs/{packId}/chapter_progress.yaml`
- Isolated between packs

**Atomic Writes:**
- Uses `AtomicFileWriter` pattern
- Temp → fsync → rename
- Prevents data corruption

**Progress Updates:**
- `lessonsStarted`: Increments when lesson mastery > 0
- `lessonsCompleted`: Increments when lesson mastery >= 3
- `lastAccessedMs`: Updates on any lesson activity

**Real-Time Updates:**
- Progress bars update immediately
- Chapter status recalculates dynamically
- UI reflects changes without restart

---

## Practice Modes Integration

### Continue Button
- Launches last active lesson in chapter
- Opens `TrainingScreen` in LESSON mode
- Cards loaded from chapter's lessons

### Verb Practice
- Opens `VerbDrillScreen`
- Independent progress tracking
- Only shown if `hasVerbDrill`

### Flashcards
- Opens `VocabDrillScreen`
- Independent progress tracking
- Only shown if `hasVocabDrill`

### Daily Practice
- Opens Daily Practice Screen
- 3 blocks: translate, vocab, verbs
- Only shown if pack selected

---

## Error Handling

**Missing Story File:**
- "Read Story" button hidden or disabled
- Rest of functionality works
- Warning logged

**Corrupted Manifest:**
- Fallback to manifest v1 parsing
- If fails, pack not imported
- Error message shown to user

**Empty Chapter:**
- Chapter shown with 0 lessons
- Progress bar shows 0%
- "Continue" button disabled

**Missing Chapter Progress:**
- Returns default `ChapterProgress.forChapter(chapterId)`
- All values zeroed
- No errors thrown

---

## Performance

**Chapter Loading:**
- Target: < 500ms for 10 chapters with 50 lessons
- UI not blocked during load
- Smooth progress bar animations

**Story Rendering:**
- Target: < 300ms for 100KB markdown
- Smooth scroll (60 FPS)
- Memory usage within limits

**Progress Updates:**
- Atomic file writes
- No blocking I/O on main thread
- Optimized for frequent updates

---

## Testing

**Unit Tests:**
- `ChapterProgressCalculatorTest`: Progress computation logic
- `TrainingViewModelChapterIntegrationTest`: Chapter integration
- `ExternalLessonLoaderTest`: Manifest v2 parsing

**Manual Testing:**
- Old packs still work (backward compatibility)
- Verb drill unaffected
- Daily practice unaffected
- Flower state unchanged
- Pack switching preserves progress

**Regression Tests:**
- Old pack → Classic Home
- New pack → Roadmap
- Story Reader → Back → Roadmap
- Progress updates correctly
- Pack switching works

---

## Internationalization

**Supported Languages:**
- Russian (RU): Primary interface language
- English (EN): Secondary interface language

**Story Language:**
- Stories match interface language
- Fallback to Russian if English missing
- Language detection based on user settings

**Chapter Metadata:**
- Titles and subtitles language-dependent
- Story files language-specific
- Lesson content language-independent

---

## Migration Guide

**For Existing Packs:**
1. Add `schemaVersion: 2` to manifest.json
2. Add `chapters` array with chapter definitions
3. Create story files in `stories/` directory
4. Update lesson IDs to match chapter lessons
5. Test with ChapterProgressStore

**For New Packs:**
1. Start with manifest v2 schema
2. Define chapters before lessons
3. Create allegorical stories for each chapter
4. Order lessons logically within chapters
5. Test chapter progress tracking

**Backward Compatibility:**
- No changes needed for existing packs
- v1 packs continue to work
- Progress preserved during migration
- UI adapts automatically

---

## Future Enhancements

**Planned Features:**
- Chapter bookmarks
- Story search functionality
- Offline story caching
- Chapter-specific achievements
- Story audio narration
- Interactive story elements

**Nice to Have:**
- Smooth progress bar animations
- Chapter completion celebrations
- Story sharing functionality
- Chapter notes and highlights
- Story discussion forums

---

## Related Documentation

- [Acceptance Criteria](acceptance-criteria-grammar-roadmap.md)
- [Task Specification](tasks/TASK-088-grammar-story-roadmap.md)
- [Models and State](01-models-and-state.md) (Chapter, ChapterProgress)
- [Data Stores](02-data-stores.md) (ChapterProgressStore)
- [App Router](07-app-router.md) (Navigation flow)

---

## Changelog

**v1.0.0 (2026-05-27)**
- Initial implementation of Grammar Story Roadmap
- 8 chapters with allegorical stories
- Chapter progress tracking
- Story reader with markdown rendering
- Conditional UI routing
- Settings icon in top bar
- Removed all status icons
- Back navigation to pack selection

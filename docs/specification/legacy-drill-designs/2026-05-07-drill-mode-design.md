---
date: 2026-05-07
status: approved
---

# Drill Mode Design

## Overview

Add a "Drill" mode for pure card training without mastery/flower progress. When a lesson has a `drillFile` in its manifest, the lesson roadmap shows a "Drill" tile (FitnessCenter icon) instead of Story tiles.

## Manifest Extension

`LessonPackLesson` gets optional `drillFile` field:
```json
{ "lessonId": "L01_WORD_DRILL", "file": "lesson_01.csv", "drillFile": "lesson_01_drill.csv" }
```

Only lessons with `drillFile` show the Drill tile. Others keep Story as usual.

## Data Layer

- `LessonPackLesson.drillFile: String?` — parsed from manifest JSON
- `Lesson.drillCards: List<SentenceCard>` — loaded from drill CSV
- `DrillProgressStore` — saves/loads drill group progress per lessonId (YAML)
- `TrainingUiState` — added: isDrillMode, drillGroupIndex, drillTotalGroups, drillShowStartDialog, drillHasProgress

## UI

- **DrillTile**: replaces StoryCheckIn/StoryCheckOut in LessonRoadmapScreen when drillCards exist. Icon: FitnessCenter.
- **DrillStartDialog**: "Start Fresh" / "Continue" (Continue only if saved progress exists)
- **TrainingScreen**: shows "N / Total" group counter when isDrillMode

## Behavior

- Cards grouped by 9 consecutive cards per group
- All 3 input modes: VOICE, KEYBOARD, WORD_BANK
- Mastery NOT recorded — pure training
- Progress saved per lesson: which group was last completed
- Two start options: fresh (from group 0) or continue (from saved group)

## Pack Structure

```
EN_WORD_ORDER_A1.zip
  manifest.json
  lesson_01.csv          (main lesson cards)
  lesson_01_drill.csv    (drill cards)
```

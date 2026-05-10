# Card Mixing Mechanism — Reference

**Date:** 2026-05-10
**Type:** Reference documentation (no code changes)

## Overview

GrammarMate uses `MixedReviewScheduler` to build a static schedule of sub-lessons with interleaved review cards from previously completed lessons.

## Sub-Lesson Types

| Type | Description |
|------|-------------|
| NEW_ONLY | Only fresh cards from current lesson. First N sub-lessons. |
| MIXED | ~50% new cards + ~50% review from previous lessons. After NEW_ONLY block. |

First lesson (index 0) gets NEW_ONLY only — no MIXED sub-lessons.

## Schedule Building (MixedReviewScheduler.build)

1. For each lesson, split cards: first half → NEW_ONLY queue, second half → MIXED queue
2. Build NEW_ONLY sub-lessons: chunk into groups of `subLessonSize` (default 10)
3. Build MIXED sub-lessons: current-lesson cards + review from previous lessons
4. Review cards drawn from due lessons using interval ladder
5. Priority: reserve pool (cards 150+) > main pool > fallback to current lesson

## Interval Ladder

`[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]` — used as structural spacing (sub-lesson count, not days). A lesson becomes "due" when `globalMixedIndex - reviewStartMixedIndex` matches a ladder value.

## Key Data Structures

- `ScheduledSubLesson(type: SubLessonType, cards: List<SentenceCard>)`
- `LessonSchedule(lessonId: String, subLessons: List<ScheduledSubLesson>)`
- `LessonMasteryState` — tracks uniqueCardShows, shownCardIds, intervalStepIndex
- `SubLessonType` — NEW_ONLY, MIXED (WARMUP was removed, never re-introduce)

## Review Card Selection

1. Up to 2 due lessons selected (most recent first)
2. Reserve pool cards preferred (prevent phrase memorization)
3. Round-robin across due lessons
4. Max 300 review cards per lesson (50/50 main/reserve split)

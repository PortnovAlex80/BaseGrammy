# TASK-030: Spec-vs-Code Screen Audit — Discrepancy Registry

**Status:** OPEN
**Created:** 2026-05-15
**Branch:** N/A
**Spec:** 23-screen-elements.md, 19-screen-catalog.md, 14-theme-and-ui-components.md

## Audit Scope

316 UI elements across 12 categories, 10 screens, 16 dialogs, 7 shared components.
Spec = source of truth.

## Discrepancy Summary

| ID | Element | Severity | Type | Status | Task |
|----|---------|----------|------|--------|------|
| D-01 | Dark theme palette (0/8 match) | CRITICAL | CODE | OPEN | TASK-031 |
| D-02 | HS-01 Avatar (bg, fallback, click) | HIGH | SPEC+CODE | OPEN | TASK-032 |
| D-03 | ProfileStatsPopup + InitialsAvatar not in spec | HIGH | SPEC | OPEN | TASK-033 |
| D-04 | HS-15 Legend text structure | MEDIUM | SPEC | OPEN | TASK-034 |
| D-05 | TS-09 TTS Warning icon for OOM | MEDIUM | SPEC | OPEN | TASK-035 |
| D-06 | TS-27 + TCS-21 duplicate TTS buttons | MEDIUM | SPEC+CODE | OPEN | TASK-036 |
| D-07 | DP-11 HintAnswerCard visibility (spec error) | MEDIUM | SPEC | OPEN | TASK-037 |
| D-08 | VD-14 stale CODE PENDING note | LOW | SPEC | OPEN | TASK-020 |
| D-09 | VD-23 voice button launches speech directly | MEDIUM | SPEC+CODE | OPEN | TASK-021 |
| D-10 | VOC-41 custom report sheet + missing Hide Card | HIGH | CODE | OPEN | TASK-022 |
| D-11 | SH-03 SharedInputModeBar not extracted | HIGH | CODE | OPEN | TASK-023 |
| D-12 | SH-05 TextScaleProvider not implemented | MEDIUM | CODE | OPEN | TASK-024 |
| D-13 | SH-02 VoiceAutoLauncher fixed vs variable delay | MEDIUM | CODE | OPEN | TASK-025 |
| D-14 | SS-48 FilterChips + settings ordering | MEDIUM | SPEC | OPEN | TASK-026 |
| D-15 | DG-14/15/16/17 dialog location references | LOW | SPEC | OPEN | TASK-027 |
| D-16 | Icon inventory update (8 extra + LocalFlorist) | MEDIUM | SPEC | OPEN | TASK-028 |
| D-17 | Minor spec corrections (TS-37, SS-12, SS-20, SS-31) | LOW | SPEC | OPEN | TASK-029 |

## Statistics

- Total elements audited: 316
- OK (match spec): ~290 (92%)
- MISMATCH: 17 distinct discrepancies
- MISSING from code: 2 (SH-03, SH-05)
- EXTRA in code (not in spec): 3 (ProfileStatsPopup, InitialsAvatar, 8 extra icons)
- Spec errors (code is correct): 5 (DP-11, VD-14, TS-37, SS-48, ordering)

## Screens Audited

- HomeScreen (HS-01..HS-22): 2 mismatches, 1 extra
- LessonRoadmapScreen (LR-01..LR-13): 0 mismatches
- LadderScreen (LS-01..LS-11): 0 mismatches
- StoryQuizScreen (SQ-01..SQ-13): 0 mismatches
- TrainingScreen (TS-01..TS-37): 3 mismatches
- TrainingCardSession (TCS-01..TCS-29): 2 mismatches
- DailyPracticeScreen (DP-01..DP-30): 1 mismatch (spec error)
- VerbDrillScreen (VD-01..VD-41): 2 mismatches
- VocabDrillScreen (VOC-01..VOC-48): 1 mismatch
- SettingsSheet (SS-01..SS-48): 3 mismatches
- Dialogs (DG-01..DG-17): 1 location mismatch
- Shared Components (SH-01..SH-07): 2 missing, 1 delay mismatch
- Theme (colors, icons, typography): 1 palette mismatch, 9 icon mismatches

## Verification Checklist

1. All 17 discrepancies have corresponding task files (TASK-020 through TASK-029, TASK-031 through TASK-037)
2. Each task has correct severity, type, and spec references
3. Statistics add up: 17 discrepancies across 316 elements
4. No duplicate discrepancies

## Scope

- IN: Master discrepancy registry, cross-reference to child tasks
- OUT: Individual fix implementations (see child tasks)

## Dependencies

None — this is a documentation/tracking task. Child tasks may depend on each other where they touch the same files.

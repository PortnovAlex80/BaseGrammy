# Seamless Drill Mode Redesign

Date: 2026-05-08
Supersedes: 2026-05-07-drill-mode-design.md (original group-based drill)

## Problem

Drill mode splits cards into groups of 9 with pauses between them, creating a fragmented experience. No visual distinction from regular lessons (same teal theme). User wants a continuous, immersive drill flow.

## Design

### Seamless Card Flow

- Remove group-based iteration entirely (`drillGroupIndex`, `drillTotalGroups`, `onDrillGroupComplete()`)
- All drill cards form a single continuous stream
- After correct answer on card N, immediately load card N+1
- No pauses, no intermediate screens between cards
- Sequential order (no shuffle)

### Green Theme

- When `isDrillMode = true`, override colors:
  - Background: soft mint green (`Color(0xFFE8F5E9)`)
  - Primary accents: green (`Color(0xFF4CAF50)`)
  - Cards/surfaces: lighter green tint
- Reverts to standard teal theme when exiting drill
- Implementation: pass `isDrillMode` flag to theme/color scope in TrainingScreen

### Circular Progress Indicator

- Ring/circle at top of TrainingScreen during drill
- Shows `cardIndex / totalCards` text inside the ring
- Ring fills proportionally as cards are completed
- Replaces the current `N / Total` text counter

### Progress Persistence

- `DrillProgressStore` saves `cardIndex` (not `groupIndex`) per lesson
- `hasProgress()` checks if `cardIndex > 0`
- `clearDrillProgress()` resets to allow fresh start

### DrillStartDialog

- **No saved progress**: shows single "Start" button
- **Has saved progress**: shows "Continue from card N" / "Start Fresh" / "Cancel"
- Start Fresh clears saved cardIndex and starts from 0

### Reset Progress Option

- Long-press on DrillTile in roadmap clears saved drill progress
- Confirmation toast/snackbar shown

### Regular Lessons: No Changes

- All drill logic is gated behind `state.isDrillMode`
- Regular lesson flow (MixedReviewScheduler, sub-lessons, mastery, flowers) untouched
- TrainingScreen behavior diverges only in drill-specific branches

## Files to Modify

- `TrainingViewModel.kt` — remove group logic, add continuous card index, simplify `startDrill()`
- `GrammarMateApp.kt` — circular progress indicator, green theme wrapper, DrillStartDialog update
- `DrillProgressStore.kt` — change from groupIndex to cardIndex
- `Theme.kt` — add drill color scheme (or inline in GrammarMateApp)
- `Models.kt` / `TrainingUiState` — remove group fields, add cardIndex/totalCards

## Out of Scope

- Shuffle mode for drill cards
- Spaced repetition in drill
- Different input mode restrictions for drill

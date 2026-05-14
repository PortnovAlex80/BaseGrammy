---
name: profile-stats-popup
created: 2026-05-15
status: approved
---

# Profile Stats Popup

## Overview

When user taps their avatar on HomeScreen, show a micro-popup with learning statistics and CEFR level estimate.

## UI Design

### InitialsAvatar
- Colored circle with first 1-2 letters of user name
- Color from MaterialTheme color scheme (primary container)
- Size: 40dp on HomeScreen, 56dp in popup
- Place on HomeScreen top bar, right side (next to settings gear)

### Profile Stats Popup
- Triggered by tap on InitialsAvatar
- Small card-style popup (Popup composable or Dialog with small max width)
- Contains:
  1. InitialsAvatar (56dp) + user name
  2. Stats grid:
     - Cards completed (sum of uniqueCardShows across all lessons)
     - Words learned (WordMasteryStore.getMasteredCount())
  3. CEFR Level badge (prominent, e.g. "B1")

### Layout
```
┌─────────────────────────┐
│  [AB]  User Name        │
│                         │
│  📊 1,234 cards         │
│  📝 56 words learned    │
│                         │
│  ═══════════════════    │
│     🏆 Level: B1        │
└─────────────────────────┘
```

## CEFR Level Calculation

### Algorithm: Rank Center of Gravity

1. Load all `WordMasteryState` where `isLearned == true`
2. For each, look up corresponding `VocabWord.rank` (frequency rank)
3. If learned words count < 10 → return "A1" (insufficient data)
4. Calculate median rank of learned words
5. Map median to CEFR level:

| Median Rank | Level |
|------------|-------|
| < 500      | A1    |
| < 1000     | A2    |
| < 2000     | B1    |
| < 4000     | B2    |
| < 8000     | C1    |
| >= 8000    | C2    |

### Edge cases
- No learned words → show "—" instead of level
- Only 1 pack active → calculate for active pack's words only
- Multiple packs → aggregate across all packs

## Data Sources

| Data Point | Source | Method |
|-----------|--------|--------|
| Cards completed | MasteryStore | loadAll() → sum uniqueCardShows |
| Words learned | WordMasteryStore | getMasteredCount() |
| Word ranks | VocabWord from CSV | ItalianDrillVocabParser results |
| CEFR level | Calculated | CefrCalculator (new) |

## Architecture

### New components
1. `CefrCalculator` — pure function in `data/CefrCalculator.kt`, takes list of learned word ranks → returns CEFR level string
2. `InitialsAvatar` — composable in `ui/components/InitialsAvatar.kt`
3. `ProfileStatsPopup` — composable in `ui/components/ProfileStatsPopup.kt`

### State
- Add `showProfileStats: Boolean = false` to `DialogState` in `GrammarMateApp.kt`
- Stats loaded on-demand when popup opens (not in TrainingUiState)

### Integration
- HomeScreen: add InitialsAvatar next to settings gear
- GrammarMateApp: add popup composable conditionally rendered
- No changes to TrainingViewModel or stores — all data accessed directly

## Scope Boundaries
- NO avatar upload/preset selection
- NO historical activity chart
- NO per-lesson breakdown in popup
- NO profile editing from popup (use Settings for that)

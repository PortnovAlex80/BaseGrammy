# Verb Drill Mode — Design Spec

**Date:** 2026-05-09
**Status:** Approved
**Approach:** B — Isolated (separate ViewModel + screen, no changes to TrainingViewModel)

---

## Overview

New "Verb Drill" mode for drilling Italian verb conjugations by group and tense. Accessed via a special tile on the roadmap. Independent from the standard lesson/sub-lesson system.

**Problem:** 16 verb conjugation groups × multiple tenses = too many for the 12-tile roadmap. Need a different UI pattern with dropdown filtering instead of tiles.

---

## Data Layer

### New Files

#### `data/VerbDrillCard.kt`

```kotlin
data class VerbDrillCard(
    val id: String,          // "regular_are_Presente_0"
    val promptRu: String,    // "я готов (быть готовым)"
    val answer: String,      // "Io sono pronto."
    val verb: String? = null,    // "essere"       (optional)
    val tense: String? = null,   // "Presente"     (optional)
    val group: String? = null    // "irregular_unique" (optional)
)
```

#### `data/VerbDrillProgress.kt`

```kotlin
data class VerbDrillComboProgress(
    val group: String,
    val tense: String,
    val totalCards: Int,
    val everShownCardIds: Set<String>,   // permanent progress
    val todayShownCardIds: Set<String>,  // daily pool (resets at 8am)
    val lastDate: String                 // "2026-05-09" for reset check
)
```

#### `data/VerbDrillStore.kt`

- Stores progress in `verb_drill_progress.yaml` via `AtomicFileWriter`
- On load: compares `lastDate` with current date. If changed → clears `todayShownCardIds`, updates `lastDate`
- Progress key: `"{group}|{tense}"` (e.g. `"regular_are|Presente"`, `"|Presente"`, `"|"`) for no-filter scenarios

#### `data/VerbDrillCsvParser.kt`

- Parses CSV with columns: `RU;IT;Verb;Tense;Group`
- `RU` and `IT` are required. `Verb`, `Tense`, `Group` are optional
- Determines available columns from header or data row inspection

### CSV Format

```
Verb Conjugation Drill B1 Italian
RU;IT;Verb;Tense;Group
я готов;Io sono pronto.;essere;Presente;irregular_unique
ты был готов;Tu eri pronto.;essere;Imperfetto;irregular_unique
```

### Data Access Path

- CSV ships inside the lesson pack ZIP (imported via existing `LessonStore` import flow)
- After import, extracted to `context.filesDir/grammarmate/verb_drill/{lessonId}.csv`
- `VerbDrillViewModel` reads CSV directly from this path (loaded once, kept in memory)
- Available tense/group values extracted from parsed cards to populate dropdowns

### Pack Manifest Extension

New `type` field in lesson entries:

```json
{
  "lessonId": "it_verb_drill",
  "type": "verb_drill",
  "file": "it_verb_drill.csv"
}
```

- `type` defaults to `"standard"` if absent
- `type: "verb_drill"` triggers the special tile + VerbDrillScreen

### Optional Filtering

| Available columns | Dropdowns shown | Filtering |
|---|---|---|
| Tense + Group | Both | Select pair → drill that subset |
| Only Tense | Tense only | Select tense → drill all groups |
| Only Group | Group only | Select group → drill all tenses |
| Neither | None | Drill entire pool, single global progress |

---

## UI Layer

### Roadmap Integration

- New tile state: `VERB_DRILL` in `LessonTileState`
- Always unlocked (independent of other lesson progress)
- Distinct icon to differentiate from standard lesson tiles
- Tap → navigates to `VerbDrillScreen` instead of training

### VerbDrillScreen

```
┌──────────────────────────────┐
│  ← Verb Drill                │
│                              │
│  Время:  [Presente      ▼]  │
│  Группа: [regular_are   ▼]  │
│                              │
│  Прогресс: 247 / 412        │
│  Сегодня: 30 / 100          │
│  ████████░░░░░░░░░ 60%      │
│                              │
│  [    Продолжить     ]      │
│       или                    │
│  [      Старт       ]       │
│                              │
└──────────────────────────────┘
```

- Dropdowns appear only if corresponding columns exist in CSV
- Progress bar shows `everShownCardIds.size / totalCards` for selected combo
- "Продолжить" if `todayShownCardIds` has entries for this combo, "Старт" otherwise

### Drill Session Screen

- Reuses existing training UI (input field, answer checking, TTS)
- No sub-lessons or tiles — just sequential cards
- After 10 cards: congratulations screen (reuse existing component)
- Buttons: "Дальше" / "Выход"
- "Дальше" → next 10 random from pool (excluding today shown)
- "Выход" → back to selection screen

### New ViewModel: `ui/VerbDrillViewModel.kt`

Separate from `TrainingViewModel`. Responsibilities:
- Load CSV, parse into `VerbDrillCard` list
- Filter by selected tense/group
- Manage session: pick 10 random cards, exclude `todayShownCardIds`
- Track progress: update `everShownCardIds` and `todayShownCardIds` per card
- Persist progress via `VerbDrillStore`

---

## Session Mechanics

### Session Flow

1. User selects filters → taps Start/Continue
2. ViewModel loads cards, filters by selected combo
3. Picks 10 random from filtered pool, excluding `todayShownCardIds`
4. If fewer than 10 unshown-today remain → take what's available (3 cards is fine)
5. If 0 unshown-today → dialog "На сегодня всё! Завтра новые карточки."
6. Each card shown as training input → on answer, added to both shown sets + persisted immediately
7. After 10 cards → congratulations → "Дальше" / "Выход"

### Daily Reset (8am)

- No alarm/timer/background process
- Check on screen open: compare `lastDate` with current date
- If date changed → `todayShownCardIds = emptySet()`, `lastDate = today`
- Date comparison uses local date string (e.g. `"2026-05-09"`)

### Progress Storage

```yaml
# verb_drill_progress.yaml
combo:
  regular_are|Presente:
    totalCards: 2472
    everShownCount: 480
    todayShownCount: 30
    lastDate: "2026-05-09"
  irregular_unique|Presente:
    totalCards: 204
    everShownCount: 0
    todayShownCount: 0
    lastDate: "2026-05-09"
```

Stores both IDs and counts. `todayShownCardIds` is needed to exclude shown cards from next session pick. `everShownCardIds` drives permanent progress. Card IDs are deterministic (`{group}_{tense}_{index}`), reconstructed from CSV on load — so only IDs need storage, not full card data.

---

## File Changes Summary

### New Files

| File | Layer | Purpose |
|---|---|---|
| `data/VerbDrillCard.kt` | data | Card model |
| `data/VerbDrillProgress.kt` | data | Progress model |
| `data/VerbDrillStore.kt` | data | YAML progress persistence |
| `data/VerbDrillCsvParser.kt` | data | 5-column CSV parser |
| `ui/VerbDrillViewModel.kt` | ui | Drill logic |
| `ui/VerbDrillScreen.kt` | ui | Compose screen (dropdowns + progress + session) |

### Modified Files

| File | Change |
|---|---|
| `data/LessonPackManifest.kt` | Add `type` field to lesson entry (default `"standard"`) |
| `data/LessonStore.kt` | Handle `verb_drill` type: extract CSV to `verb_drill/` dir |
| `data/Models.kt` | Add `VERB_DRILL` to `LessonTileState` equivalent |
| `ui/GrammarMateApp.kt` | Add `VERB_DRILL` tile rendering + navigation to `VerbDrillScreen` |

### Unchanged Files

- `TrainingViewModel.kt` — not touched
- Existing packs — work unchanged (`type` defaults to `"standard"`)

---

## Verb Group Reference

| Group ID | Name | Type | Endings | Count |
|---|---|---|---|---|
| regular_are | Правильные -are | regular | -o, -i, -a, -iamo, -ate, -ano | 412 |
| regular_ere | Правильные -ere | regular | -o, -i, -e, -iamo, -ete, -ono | 163 |
| regular_ire | Правильные -ire | regular | -o, -i, -e, -iamo, -ite, -ono | 32 |
| regular_isc | Правильные -isc (ire) | regular | -isco, -isci, -isce, -iamo, -ite, -iscono | 57 |
| regular_ciare_giare | -ciare/-giare (i→drop) | regular | -o, -i, -a... (no doubling) | 69 |
| regular_care_gare | -care/-gare (h-insert) | regular | -co→-chi, -go→-ghi | 64 |
| irregular_porre | Неправильные (porre) | irregular | pon- stem | 9 |
| irregular_tenere | Неправильные (tenere) | irregular | ten-/tin- stem | 9 |
| irregular_venire | Неправильные (venire) | irregular | ven-/venn- stem | 7 |
| irregular_cogliere | Неправильные (cogliere) | irregular | cogl-/colg- stem | 6 |
| irregular_dire | Неправильные (dire) | irregular | dic- stem | 5 |
| irregular_trarre | Неправильные (trarre) | irregular | tragg- stem | 4 |
| irregular_unique | Уникальные/мелкие группы | irregular | mixed | 34 |

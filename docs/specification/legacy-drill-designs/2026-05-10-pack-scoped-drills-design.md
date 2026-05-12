# Pack-Scoped Drills Design

## Problem

Verb drill and vocab drill (Anki) are currently independent of the active lesson pack. Verb drill loads CSVs from all installed packs for a language. Vocab drill loads from hardcoded asset files with zero pack awareness. Users see drill UI regardless of whether their active pack contains drill content.

## Decision

Bind both drill types to the active lesson pack. Each pack explicitly declares its drill content in the manifest. Drills are visible only when the active pack contains them. Progress is scoped per-pack.

## Manifest Changes

Add two optional top-level sections to `manifest.json`:

```json
{
  "packId": "IT_VERB_GROUPS_ALL",
  "schemaVersion": 1,
  "language": "it",
  "lessons": [...],
  "verbDrill": {
    "files": ["it_verb_groups_all.csv"]
  },
  "vocabDrill": {
    "files": ["drill_nouns.csv", "drill_verbs.csv", "drill_adjectives.csv"]
  }
}
```

**New data class:**

```kotlin
data class DrillFiles(val files: List<String>)

data class LessonPackManifest(
    // ... existing fields ...
    val verbDrill: DrillFiles? = null,
    val vocabDrill: DrillFiles? = null
)
```

Remove `type: "verb_drill"` from `LessonPackLesson`. Verb drill entries move from the `lessons` array into the dedicated `verbDrill` section.

Old packs without these fields: `verbDrill` and `vocabDrill` default to `null`. No drill UI shown.

## Import & Storage

**On pack import**, LessonStore:

1. Processes `lessons[]` as usual (no more `type: "verb_drill"` handling)
2. If `manifest.verbDrill != null` → copies listed files to `grammarmate/drills/{packId}/verb_drill/`
3. If `manifest.vocabDrill != null` → copies listed files to `grammarmate/drills/{packId}/vocab_drill/`

**On pack deletion** → recursively deletes `grammarmate/drills/{packId}/`.

**New LessonStore methods:**

```kotlin
fun getVerbDrillFiles(packId: String, languageId: String): List<File>
fun getVocabDrillFiles(packId: String, languageId: String): List<File>
fun hasVerbDrill(packId: String, languageId: String): Boolean
fun hasVocabDrill(packId: String, languageId: String): Boolean
```

**Asset cleanup:** Remove hardcoded `assets/grammarmate/vocab/it/drill_*.csv`. All vocab drill data ships inside packs.

**Old paths** (`grammarmate/verb_drill/{lang}_{lessonId}.csv`) are no longer used. No migration — clean break.

## UI Visibility

**HomeScreen** — separate checks per drill type:

```kotlin
val hasVerbDrill = lessonStore.hasVerbDrill(state.activePackId, state.selectedLanguageId)
val hasVocabDrill = lessonStore.hasVocabDrill(state.activePackId, state.selectedLanguageId)

if (hasVerbDrill) { VerbDrillEntryTile(...) }
if (hasVocabDrill) { VocabDrillEntryTile(...) }
```

Each drill tile is visible independently. Pack without either → no drill tiles.

**Pack switch reactivity:** `selectPack()` updates `activePackId` in `TrainingUiState` → HomeScreen recomposes → `hasVerbDrill`/`hasVocabDrill` recalculate from new `activePackId`.

## ViewModel Changes

**VerbDrillViewModel:**

- Add `reloadForPack(packId: String)` method
- `loadCards()` reads from `lessonStore.getVerbDrillFiles(packId, languageId)` instead of `getVerbDrillFiles(languageId)`
- GrammarMateApp calls `reloadForPack` on navigation with current `activePackId`

**VocabDrillViewModel:**

- Replace hardcoded asset loading with `lessonStore.getVocabDrillFiles(packId, languageId)`
- Accept `packId` parameter from `TrainingUiState.activePackId`
- Uses `ItalianDrillVocabParser` as before, just different file source

## Progress Scoping

| Drill | Progress file path |
|-------|-------------------|
| Verb drill | `grammarmate/drills/{packId}/verb_drill_progress.yaml` |
| Vocab drill (Anki) | `grammarmate/drills/{packId}/word_mastery.yaml` |

Progress keys unchanged — same format as before, just different file per pack.

Progress deletes with pack (entire `drills/{packId}/` directory).

No migration of old progress files.

## Pack Creation

The `IT_VERB_GROUPS_ALL.zip` must be rebuilt with:

1. Existing verb drill CSV in the root
2. Vocab drill CSVs (`drill_nouns.csv`, etc.) in the root
3. Updated `manifest.json` with `verbDrill` and `vocabDrill` sections
4. Remove `type: "verb_drill"` from lessons array

Other packs remain unchanged (they have no drill sections → no drill UI).

## User Journeys

### Pack with both drills

1. Open app → HomeScreen checks active pack → both `hasVerbDrill` and `hasVocabDrill` are true
2. See two tiles: Verb Drill and Vocab Drill
3. Tap Verb Drill → loads data from `drills/{packId}/verb_drill/`, progress to `drills/{packId}/verb_drill_progress.yaml`
4. Return, tap Vocab Drill → loads data from `drills/{packId}/vocab_drill/`, progress to `drills/{packId}/word_mastery.yaml`

### Pack switch

1. Go to Settings → select different pack
2. `activePackId` updates → HomeScreen recomposes
3. New pack has only vocab drill → Verb Drill tile gone, Vocab Drill stays
4. Vocab drill shows data from new pack, progress is from new pack's file
5. Switch back → both tiles visible, original progress intact

### Pack without drills

1. Switch to pack without `verbDrill` or `vocabDrill` in manifest
2. HomeScreen → no drill tiles
3. No entry point to drill screens

### Fresh install with new pack

1. Import ZIP with `verbDrill` + `vocabDrill` sections
2. LessonStore copies files to `drills/{packId}/verb_drill/` and `drills/{packId}/vocab_drill/`
3. Select pack → see both drills, clean progress

## Files Changed

| File | Change |
|------|--------|
| `data/LessonPackManifest.kt` | Add `DrillFiles`, `verbDrill`, `vocabDrill` fields |
| `data/LessonStore.kt` | New import paths, new query methods, remove old `verb_drill` type handling |
| `data/VerbDrillStore.kt` | Scope progress file path by `packId` |
| `data/WordMasteryStore.kt` | Scope progress file path by `packId` |
| `ui/VerbDrillViewModel.kt` | `reloadForPack()`, load from pack-scoped path |
| `ui/VocabDrillViewModel.kt` | Load from LessonStore instead of assets, accept `packId` |
| `ui/GrammarMateApp.kt` | Separate visibility checks, pass `packId` to ViewModels |
| `assets/grammarmate/packs/IT_VERB_GROUPS_ALL.zip` | Rebuild with new manifest + vocab CSVs |
| `assets/grammarmate/vocab/it/drill_*.csv` | Remove (moved into pack) |

## Implementation Order

1. Create new branch from main
2. Rebuild `IT_VERB_GROUPS_ALL.zip` with new manifest + vocab CSVs
3. Update `LessonPackManifest.kt` — new fields
4. Update `LessonStore.kt` — import/query methods, remove old verb_drill type
5. Update progress stores — pack-scoped file paths
6. Update `VerbDrillViewModel` — pack-scoped loading
7. Update `VocabDrillViewModel` — LessonStore-based loading
8. Update `GrammarMateApp.kt` — separate visibility checks, packId passthrough
9. Remove asset vocab CSVs
10. Test full user journey

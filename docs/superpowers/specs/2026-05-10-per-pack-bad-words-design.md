# Per-Pack Bad Words Lists — Design Spec

**Date:** 2026-05-10
**Status:** Approved

## Overview

Bad/flagged cards are currently stored in two flat YAML files (one for training, one for drill). Refactor to a single pack-keyed YAML so each pack has its own bad cards list. Add bad card support to VerbDrill.

## Current State

- `grammarmate/bad_sentences.yaml` — regular training, flat list
- `grammarmate/drill_bad_sentences.yaml` — drill mode, flat list
- `BadSentenceStore` takes a `drillMode` boolean to pick which file
- No pack scoping — all flagged cards in one list
- VerbDrillViewModel has no bad sentence support

## New Schema

Single file: `grammarmate/bad_sentences.yaml`
```yaml
schemaVersion: 2
packs:
  EN_WORD_ORDER_A1:
    items:
      - cardId: "en_word_order_a1_42"
        languageId: "en"
        sentence: "..."
        translation: "..."
        addedAtMs: 1234567890
  IT_VERB_GROUPS_ALL:
    items:
      - cardId: "_Presente_7"
        languageId: "it"
        sentence: "..."
        translation: "..."
        addedAtMs: 1234567890
```

## BadSentenceStore Changes

Remove `drillMode` parameter. Add pack-scoped methods:
- `addBadSentence(packId, entry)`
- `removeBadSentence(packId, cardId)`
- `getBadSentences(packId): List<BadSentenceEntry>`
- `isBadSentence(packId, cardId): Boolean`
- `exportToTextFile(packId): File` → writes to `Downloads/BaseGrammy/bad_sentences_{packId}.txt`

## Migration

One-time in TrainingViewModel.init:
1. If old schema (schemaVersion=1), read all items
2. Extract lessonId from cardId, resolve packId via LessonStore.getPackIdForLesson()
3. Group by packId, write new format (schemaVersion=2)
4. Delete old drill_bad_sentences.yaml

## VerbDrill Bad Cards

- Add `BadSentenceStore` to VerbDrillViewModel
- Build `packIdForCardId` map during loadCards() — for each verb drill file `{languageId}_{lessonId}.csv`, resolve packId
- Add flag/unflag/isBad/export methods
- Add `badSentenceCount` and `currentCardIsBad` to VerbDrillUiState
- UI: flag button on incorrect answer, bad count display, export option

## Files

| File | Change |
|------|--------|
| `data/BadSentenceStore.kt` | Refactor to pack-scoped, new schema |
| `ui/TrainingViewModel.kt` | Remove drillBadSentenceStore, use pack-scoped calls |
| `ui/VerbDrillViewModel.kt` | Add BadSentenceStore, packIdForCardId, flag methods |
| `ui/VerbDrillScreen.kt` | Add flag button, bad count |
| `ui/GrammarMateApp.kt` | Wire VerbDrill callbacks |

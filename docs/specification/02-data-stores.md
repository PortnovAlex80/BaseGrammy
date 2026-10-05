# 2. Data Stores — Specification

Updated for the architecture review of 2026-10-05. This document describes storage ownership and compatibility boundaries. Executed checks belong to the [Spec Harness evidence ledger](../../spec-doc-harness/regression-evidence.md); an invariant below is not itself a test result.

Most stores live under `context.filesDir/grammarmate/` and serialize YAML. Background vocabulary marks and position use JSON directly under `filesDir`. `AtomicFileWriter` protects individual replacements; it does not make a sequence of independent stores one transaction.

## Store Inventory

Paths below are relative to `filesDir/grammarmate/`, except where explicitly prefixed with `filesDir`.

| Owner | Disk path | Scope and cache |
|---|---|---|
| LessonStore / LessonCatalog / LanguageManager | `packs/`, `packs.yaml`, `lessons/`, story/vocab indexes | Content facade; parsed lessons, manifests and YAML indexes have caches |
| MasteryStore | `mastery.yaml` | Schema 2, `pack:{packId}` → lesson; in-memory cache and scheduled writer |
| ProgressStore | `progress.yaml` | Global selection metadata and shared fields; reads disk |
| PackLessonProgressStore | `lesson_progress_{packId}.yaml` | Pack → lesson checkpoint; cached |
| PackDailyCursorStore | `daily_cursor_{packId}.yaml` | Independent cursor per pack; cached |
| VerbDrillStore | `drills/{packId}/verb_drill_progress.yaml`, `verb_drill_last_session.yaml` | Combo progress, saved batch and parsed card caches |
| AuxDrillStore | `drills/{packId}/aux_drill_progress.yaml` | Auxiliary combo progress; cached |
| WordMasteryStore | `drills/{packId}/word_mastery.yaml` | Word SRS progress; cached |
| VocabProgressStore | `drills/{packId}/vocab_progress.yaml` | Language/lesson/entry state within pack; cached |
| ProfileStore | `profile.yaml` | Profile and welcome attempts; reads disk |
| StreakStore | `streak_{languageId}.yaml` | Language-level streak/fire activity |
| HiddenCardStore | `hidden_cards.yaml` | Hidden card IDs; cached |
| BadSentenceStore | `bad_sentences.yaml` | Schema 2, pack-scoped reports; cached |
| AppConfigStore | `config.yaml` | Preferences; cached and published as state |
| PomodoroHistoryStore | `pomodoro_history.yaml` | Completed session records; reads disk |
| PomodoroSettingsStore | `pomodoro_settings.yaml` | Preferences, separate from history |
| BgVocabMarkStore | `filesDir/bg_vocab_marks.json` | Word → GREEN/RED; live cached marks |
| BgVocabPositionStore | `filesDir/bg_vocab_position.json` | Last word identity; reads disk |

Nullable-pack stores retain readers for their historical global paths. These compatibility paths do not authorize new global catalog routes or cross-pack writes. Chapter progress is derived from lesson mastery; there is no current `ChapterProgressStore` file.

## 2.1 LessonStore

Source: [LessonStore.kt](../../app/src/main/java/com/alexpo/grammermate/data/LessonStore.kt). It delegates content discovery to `LessonCatalog`, manifests/defaults to `LanguageManager`, installation to `PackImporter`, chapter stories to `PackChapterStoryRepository` and drill/vocabulary files to `DrillFileManager`.

| Content | Path |
|---|---|
| Language registry | `lessons/languages.yaml` |
| Language compatibility index and CSV copies | `lessons/{languageId}_index.yaml`, `lessons/{languageId}/lesson_{id}.csv` |
| Installed package registry | `packs.yaml` |
| Authoritative extracted package | `packs/{packId}/manifest.json` and manifest-relative content |
| Story quiz index/content | `stories/stories.yaml`, `stories/{storyId}.json` |
| Vocabulary index/content | `vocab/vocab.yaml`, `vocab/{languageId}/vocab_{lessonId}.csv` |
| Pack drills | `drills/{packId}/{verb_drill,vocab_drill,bg_vocab}/` |

Product navigation resolves lessons from the selected pack. The legacy language index is not a second product catalog. CSV import and lesson creation must publish a normal user pack that `getPackIdForLesson` and the pack selector can discover. A deliberately empty created lesson is a draft, not a fabricated training card.

Installation validates identifiers, canonical containment, manifest references and required content before mutating the live installation. The rollback inventory covers derived language/lesson/story/vocab indexes, drill files and registry as well as the package directory. Registry publication is last. Content update preserves user progress and unrelated packs. A rejected update leaves the prior package readable through warmed and fresh store instances.

`YamlListStore`, lesson and manifest caches must be invalidated after successful mutation or rollback. Per-record index batching must not publish stale buffered changes after rollback. `PackInstallTransaction` records prior files, obsolete derived CSV removals and first-time audio directory moves in a journal; LessonStore recovers an unfinished journal before constructing cached readers. The transaction resolves `filesDir` once to its canonical root before deriving journal-relative paths; this keeps Android's exposed and canonical app-private paths aligned while retaining the containment check for every target. This recovery protocol is separate from backup restore's exception rollback and does not establish a transaction across unrelated stores. Exact boundaries are specified in [ADR 033](../architecture/decisions/033-review-remediation-boundaries.md) and checked by installation tests.

## 2.2 MasteryStore

Source: [MasteryStore.kt](../../app/src/main/java/com/alexpo/grammermate/data/MasteryStore.kt). Current writers use schema 2:

```yaml
schemaVersion: 2
data:
  "pack:EXAMPLE":
    lesson_1:
      uniqueCardShows: 1
      totalCardShows: 1
      shownCardIds: [lesson_1_0]
```

The serializer also retains completion, interval, self-produced and encounter fields. The public product API uses `getForPack`, `recordCardShowForPack`, `recordSelfProducedForPack`, `markCardsShownForProgressForPack`, `markLessonCompletedForPack` and `clearPack`. Historical language-keyed payloads remain readable; new pack mastery is isolated even when two packs share lesson IDs.

High-frequency updates change the locked cache and schedule a coalesced write. A completed scheduled task releases its slot so a later window can persist again. Explicit `flush` drains pending work; clear/restore invalidate obsolete pending snapshots. Serialization and file I/O have separate locking from cache reads. The shared training writer explicitly flushes mastery when saving progress.

Navigation and checkpoint hydration must not call grading operations. WORD_BANK does not earn mastery. Completion and mastery counters are distinct; do not infer all counters from `shownCardIds.size` alone.

## 2.3 ProgressStore

Source: [ProgressStore.kt](../../app/src/main/java/com/alexpo/grammermate/data/ProgressStore.kt). Schema 1 wraps a `data` map. Current persisted selection contains `languageId`, optional `lessonId`, `activePackId`, `mode` and `currentScreen`, plus shared reward/voice/hint/compatibility fields.

`lessonId` is selection metadata. Current lesson position and counts come from `PackLessonProgressStore`, not the obsolete global counters. `TrainingProgressValidator` accepts supported historical fields and supplies safe values for ordinary store loading. Backup validation is stricter: it must reject unrelated or malformed structures before replacing valid data.

Startup selects the saved lesson only if it belongs to the resolved pack; missing or unavailable content uses the content resolver's fallback. The root screen starts at HOME. Hydration may resume an existing active checkpoint but does not count a new answer, encounter or mastery event.

## 2.4 Retired DrillProgressStore

The former lesson-drill runtime is retired. Historical `drill_progress_{lessonId}.yaml` is a compatibility backup format, not a reason to recreate an unused factory accessor or product mode. Active verb and auxiliary drills have their own stores below.

## 2.5 VerbDrillStore

Source: [VerbDrillStore.kt](../../app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt). Combo progress is schema 1 under `data`; saved session is a separate flat map with schema version, ordered `sessionCardIds`, index and filter context. Both are scoped to the selected pack.

Check/show-answer completion records the displayed card identity. Navigation alone does not add shown IDs. Repeat reconstructs the entire saved ordered batch; Continue uses filters and excludes already completed/shown cards; Reset deletes last-session context while retaining learning progress. UI regression checks exercise these actions through controls, not direct completion helpers.

## 2.6 WordMasteryStore

Source: [WordMasteryStore.kt](../../app/src/main/java/com/alexpo/grammermate/data/WordMasteryStore.kt). Schema 1 `data` maps word IDs to SRS state. Pack-scoped instances are shared through `StoreFactory`; save updates cache and disk. Restore invalidates live references. The global file is supported for existing data.

## 2.7 ProfileStore

Source: [ProfileStore.kt](../../app/src/main/java/com/alexpo/grammermate/data/ProfileStore.kt). Flat YAML contains `userName` and `welcomeDialogAttempts`, without a schema wrapper. It is included in progress backup. Profile UI is not an assessment command.

## 2.8 StreakStore

Source: [StreakStore.kt](../../app/src/main/java/com/alexpo/grammermate/data/StreakStore.kt). Flat language-scoped YAML contains streak counts, dates and fire/activity fields. Local device date drives streak boundaries. Old `streak.yaml` is migrated using its language metadata when restoring historical backups. Changing a lesson preserves the language-level streak display; changing a language reloads its state.

## 2.9 HiddenCardStore

Source: [HiddenCardStore.kt](../../app/src/main/java/com/alexpo/grammermate/data/HiddenCardStore.kt). Schema 1 stores `hiddenCardIds`. New hidden entries use a key qualified by the source pack and lesson/domain, separate from persisted mastery card IDs. Catalog sentence cards carry their runtime origin through mixed review; scheduling compares the concrete card's scoped identity. Historical raw IDs are retained as unmapped compatibility data and are not applied as global wildcards to origin-bearing cards. Consequently some formerly hidden cards can reappear until hidden in their actual context. Existing mastery history is retained, not redistributed: the original lesson cannot be inferred reliably from an ambiguous historical row ID. Pack-level clearing removes only that pack's scoped entries; explicit global reset clears all. A restored file replaces the cached set; absence in a confirmed complete snapshot clears prior hidden cards. Legacy partial backups do not imply that omitted files should be deleted.

## 2.10 BadSentenceStore

Source: [BadSentenceStore.kt](../../app/src/main/java/com/alexpo/grammermate/data/BadSentenceStore.kt). Schema 2 uses `packs: {packId: {items: [...]}}`. Older schema 1 `items` remains a supported migration input. Reports from different packs stay separate. New sentence reports use source-qualified identity so two lessons with card_2 can both be reported and one can be unflagged independently. Historical raw report entries remain retained/exportable; they are not blindly assigned to a current lesson. Existing non-sentence drill report identity stays compatible with its own reader. Compatibility migration is retained to protect saved user reports; it is not dead runtime.

## 2.11 VocabProgressStore

Source: [VocabProgressStore.kt](../../app/src/main/java/com/alexpo/grammermate/data/VocabProgressStore.kt). Schema 1 `data` contains language → lesson → completed indices and entry SRS state. Factory instances are pack-scoped and cached. Vocabulary's interval ladder is intentionally distinct from lesson mastery. Backup/restore must retain both pack and historical global forms.

## 2.12 AppConfigStore

Source: [AppConfigStore.kt](../../app/src/main/java/com/alexpo/grammermate/data/AppConfigStore.kt). Cached preferences include hint level, test mode, session size, language/theme and audio settings. Session reset clears transient card state while preserving preferences. Configuration and `pomodoro_settings.yaml` are intentionally outside progress backup; history is included separately.

## 2.13 BackupManager

Sources: [BackupManager](../../app/src/main/java/com/alexpo/grammermate/data/BackupManager.kt), [BackupSnapshotFiles](../../app/src/main/java/com/alexpo/grammermate/data/BackupSnapshotFiles.kt), [BackupRestorer](../../app/src/main/java/com/alexpo/grammermate/data/BackupRestorer.kt). Current backup creation targets `filesDir/grammarmate/backups/backup_latest/` in app-private storage. Restore accepts a file path or a user-selected SAF directory, including historical external backups. Creation does not itself export to Downloads or provide an off-device backup; uninstalling/clearing app data also removes its private backup. It is a directory, not a ZIP of the whole application.

The shared managed inventory includes mastery, selection progress, profile, streak, hidden/bad cards, word/vocab/verb/aux progress, saved verb session, pack lesson checkpoints and daily cursors, Pomodoro history, background vocabulary marks and position. Config, installed content, downloaded audio and TTS/ASR models are excluded. The authoritative filename/alias whitelist is `BackupSnapshotFiles`; aliases for files outside `grammarmate/` are explicit, never arbitrary parent-relative paths.

Modern backup writes `snapshot_manifest.yaml` after all managed data files, with a complete inventory and SHA-256 digest of every managed text payload; human-readable metadata may follow. Missing or altered listed content is a failed snapshot. A confirmed complete empty snapshot is distinct from an arbitrary empty selected directory. Omitted managed files are cleared only when completeness is established. Valid older snapshots use a separate compatible, non-destructive merge path for omitted files; legacy flat names and old streak naming remain readable.

All managed text payloads are read and validated against supported per-file YAML/JSON structures before mutation. Manifest digests cover decoded text re-encoded as UTF-8, not a forensic copy of arbitrary source bytes. An unrelated YAML map is not valid mastery just because it parses. Failure returns false and does not report success for partial copying. Caught write failures attempt rollback; rollback I/O failure is reported separately. Do not claim whole-application crash atomicity from individual file replacement or exception handling.

Restore invalidates warmed stores and pending mastery snapshots before the UI reloads. ProgressRestorer resolves the restored pack/lesson, reads that pack's checkpoint, rebuilds schedules, clamps the saved sublesson/cursor to current content and updates visible state without grading. Background marks/position and Pomodoro history must also become visible without an app restart.

## 2.14 YamlListStore

Source: [YamlListStore.kt](../../app/src/main/java/com/alexpo/grammermate/data/YamlListStore.kt). Writes `{schemaVersion: 1, items: [...]}` via `AtomicFileWriter`; historical bare lists remain readable. Reads are cached. `write` invalidates prior cached data; external replacement requires `invalidateCache`. It is a storage primitive, not an owner of a multi-file installation transaction.

## 2.15 Chapter progress

There is no shipping `ChapterProgressStore`. Chapter cards and completion derive from pack-scoped lesson mastery. Clearing mastery and recomputing chapter state resets the derived display; installing a package must not replace a fictitious `chapter_progress.yaml` owner.

## 2.16 Reset Progress Behavior

Reset keeps its user-visible scope. Global reset, language reset, pack drill reset and Verb SessionCard Reset are different operations; they must not be conflated. Language reset clears lesson mastery/checkpoints for the selected language's installed packs. Pack drill reset clears that pack's verb, word, vocabulary and auxiliary learning records, plus the corresponding historical language-level verb/auxiliary files used before pack scoping; other current pack files remain intact. Verb SessionCard Reset clears saved context only.

`ProgressTracker` owns progress reset commands. `StoreFactory.evict(packId)` and `clearCache()` include auxiliary stores. The ViewModel rebuilds schedules, chapter display, selected lesson state and bindings after reset; reset cancels stale pending writes and save deduplication must not suppress the next real save. User preferences survive context changes.

## 2.17 Pack checkpoints and auxiliary progress

`PackLessonProgressStore` schema 1 stores `packId` and `lessonProgress`. Each lesson checkpoint includes card index, counts, elapsed time, session state and optional `activeSubLessonIndex`. Missing sublesson in an old checkpoint derives the first incomplete sublesson. Explicitly choosing a different sublesson starts that choice; it must not be overridden by a resume checkpoint.

Only the displayed selected-lesson NORMAL/LESSON session writes this checkpoint. Drill, review, mixed-deck sessions and the cleared state after exit do not overwrite the selected lesson's saved position. Global selection metadata has a separate owner.

`PackDailyCursorStore` stores pack-specific offsets, date and saved card IDs. A read of one pack must not make `loadAll` return a partial inventory. Both pack stores invalidate caches after restore.

`AuxDrillStore` schema 1 stores combo data (verb/tense and shown IDs). It participates in backup, scoped/full reset and cache invalidation alongside verb progress.

## 2.18 Background vocabulary and Pomodoro

`PomodoroHistoryStore` serializes `sessions: [...]`, including language, optional pack/lesson, timing, counts and speed. Background marks serialize `{word: "GREEN"|"RED"}`; position serializes `{"word": "..."}`. Empty JSON objects are valid empty state. Background marks are currently keyed by word, not a new pack namespace; backup retains the production paths and semantics.

## 2.19 Imported personal lessons and grammar chips

CSV import and empty-lesson creation publish ordinary USER_* packs, with explicit personal or draft lesson type. Personal cards retain the former `${lessonId}_${zeroBasedIndex}` IDs; an external pack whose name merely begins with USER_ keeps its parser IDs. Re-importing the same personal title replaces that personal course with stable pack/lesson identity.

The one-time compatibility adapter recognizes only former indexed UUID lessons with the exact lesson_UUID.csv filename, preserving lesson/card IDs. Already owned lessons are skipped; invalid originals are retained; failed installation is retried before writing personal_packs_v1.done. It does not rediscover arbitrary orphan CSV files or restore the deleted global runtime catalog.

GrammarChipStore resolves manifest grammarChip paths within the installed pack and refreshes already initialized caches after successful content mutation. Startup remains lazy. Import validates declared file existence; the grammar-chip loader owns JSON parsing.

## Cross-Store Dependencies

Training saves collect a state snapshot, serialize it through `TrainingProgressWriter` and flush mastery. Session navigation is coordinated by SessionRunner/ViewModel; ownership of a separate input flow does not permit preserving another lesson's draft. Pack installation and backup restore each have their own bounded operation with explicit rollback/invalidation.

A successful unit suite cannot prove arbitrary process death, hardware filesystem failure or all audio lifecycle behavior. Those limits and the checks actually executed are recorded in the [harness](../../spec-doc-harness/acceptance.md). New stores or fields must update backup inventory, reset scope, cache invalidation and their regression tests together.

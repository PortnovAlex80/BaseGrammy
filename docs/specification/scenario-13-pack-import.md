# Scenario 13: Lesson Pack Import & Content Lifecycle -- Verification Report

**Date:** 2026-05-12
**Branch:** feature/daily-cursors
**Spec:** docs/specification/15-lesson-content-and-packs.md
**Files traced:**
- app/src/main/java/com/alexpo/grammermate/data/LessonStore.kt
- app/src/main/java/com/alexpo/grammermate/data/LessonPackManifest.kt
- app/src/main/java/com/alexpo/grammermate/data/CsvParser.kt
- app/src/main/java/com/alexpo/grammermate/data/VerbDrillCsvParser.kt
- app/src/main/java/com/alexpo/grammermate/data/ItalianDrillVocabParser.kt
- app/src/main/java/com/alexpo/grammermate/data/VocabCsvParser.kt
- app/src/main/java/com/alexpo/grammermate/data/AtomicFileWriter.kt
- app/src/main/java/com/alexpo/grammermate/data/Models.kt
- tools/pack_validator/pack_validator.py

---

## Test Case 1: Import valid ZIP with manifest + lessons

**Code path:**
1. `importPackFromUri()` (LessonStore.kt:182) or `importPackFromAssets()` (LessonStore.kt:188)
2. Both delegate to `importPackFromStream()` (LessonStore.kt:198)
3. `extractZipToTemp()` (LessonStore.kt:276) extracts to `packs/tmp_{UUID}/`
4. `importPackFromTempDir()` (LessonStore.kt:301) takes over:
   - Reads `manifest.json` (line 302-307)
   - Parses via `LessonPackManifest.fromJson()` (LessonPackManifest.kt:23)
   - Calls `ensureLanguage()` (LessonStore.kt:309, impl at line 563)
   - Calls `removePacksForLanguage(packId, languageId)` (LessonStore.kt:312, impl at line 779)
   - Moves temp dir to `packs/{packId}/` via `copyRecursively` + `deleteRecursively` (lines 314-319)
   - Iterates `manifest.lessons` filtered by `type != "verb_drill"` sorted by order (lines 321-329)
   - Each lesson: verifies source CSV exists, calls `importLessonFromFile()` (line 578)
   - `importLessonFromFile()` copies CSV to `lessons/{languageId}/lesson_{lessonId}.csv`, parses via `CsvParser.parseLesson()`, prefixes card IDs with lessonId
   - `importPackDrills()` (line 332, impl at line 807) copies drill files
   - `importStoriesFromPack()` (line 334, impl at line 649) processes JSON story files
   - `importVocabFromPack()` (line 335, impl at line 686) processes vocab CSVs
   - Updates `packs.yaml` registry (lines 337-358)

**Expected (spec 15.3.2):** 11-step import process as described above.
**Actual:** Code follows all 11 steps. Each step matches the spec description.
**Discrepancy:** None.

---

## Test Case 2: Import ZIP without manifest

**Code path:**
1. `extractZipToTemp()` extracts the ZIP contents.
2. `importPackFromTempDir()` (LessonStore.kt:302-306):
   ```kotlin
   val manifestFile = File(tempDir, "manifest.json")
   if (!manifestFile.exists()) {
       tempDir.deleteRecursively()
       error("Manifest not found")
   }
   ```

**Expected (spec 15.3.3):** Temp directory deleted; `error("Manifest not found")` thrown.
**Actual:** Exactly matches. Temp directory is cleaned up before throwing.
**Discrepancy:** None.

---

## Test Case 3: Import ZIP with malformed manifest.json

**Code path:**
1. ZIP extracts successfully.
2. `manifest.json` exists but contains invalid JSON.
3. `LessonPackManifest.fromJson()` is called at LessonStore.kt:307.
4. Inside `fromJson()` (LessonPackManifest.kt:24): `JSONObject(text)` will throw `org.json.JSONException`.
5. This exception propagates up through `importPackFromTempDir()`.

**Expected (spec 15.3.3):** Parsing fails with an error.
**Actual:** JSON parsing throws `JSONException`, which is a RuntimeException. The temp directory is NOT cleaned up in this case -- the `tempDir.deleteRecursively()` at line 304 only runs for the missing-manifest case. For a malformed manifest, the exception escapes before any cleanup.

**Discrepancy:**
- **Partial import cleanup gap.** If `LessonPackManifest.fromJson()` throws (malformed JSON, wrong schemaVersion, missing fields), the temp directory at `packs/tmp_{UUID}/` is NOT deleted. The cleanup at line 304 only covers the missing-file case. This leaves orphan temp directories in `grammarmate/packs/`. Over time, failed imports accumulate temp directories. The risk is low (UUID-named dirs are small) but constitutes a resource leak.
- **Spec says:** "If parsing fails, temp directory is deleted and an error is thrown." Code only deletes on missing file, not on parse failure.

---

## Test Case 4: Import ZIP with invalid CSV (wrong columns)

**Code path:**
1. ZIP extracts. Manifest parses successfully.
2. Lessons are imported via `importLessonFromFile()` (LessonStore.kt:578).
3. `CsvParser.parseLesson()` (CsvParser.kt:8-55) processes the CSV:
   - Lines with fewer than 2 columns are silently skipped (line 25-27: `if (columns.size < 2) return@forEach`).
   - Lines where either column is blank after trimming are silently skipped (line 30-32).
4. Back in `importPackFromTempDir()`, the `sourceFile.exists()` check passes because the CSV file exists in the ZIP, even if its content is garbage.

**Expected (spec 15.3.3):** "Invalid CSV rows: Silently skipped."
**Actual:** Rows with wrong column count are silently skipped. If ALL rows are invalid, the lesson is imported with zero cards. The import succeeds but produces an empty lesson.
**Discrepancy:**
- No validation that a lesson has at least one card. A completely empty/malformed CSV produces a `Lesson` with `cards = emptyList()` that gets registered in the lesson index. This is consistent with the spec ("Invalid CSV rows silently skipped") but may be unexpected behavior -- the app would show an empty lesson with a flower that can never bloom.
- The Python validator (`pack_validator.py` line 85-86) DOES check for at least one valid row and raises an error. The app itself does not.

---

## Test Case 5: Import ZIP with verbDrill section

**Code path:**
1. Manifest parses with `verbDrill: { files: ["it_verb_groups_all.csv"] }`.
2. `importPackDrills()` (LessonStore.kt:807-815):
   ```kotlin
   manifest.verbDrill?.files?.forEach { fileName ->
       val source = File(packDir, fileName)
       if (!source.exists()) return@forEach
       val targetDir = File(baseDir, "drills/${manifest.packId}/verb_drill")
       targetDir.mkdirs()
       val target = File(targetDir, source.name)
       source.copyTo(target, overwrite = true)
   }
   ```
3. For IT_VERB_GROUPS_ALL pack: copies `it_verb_groups_all.csv` to `grammarmate/drills/IT_VERB_GROUPS_ALL/verb_drill/it_verb_groups_all.csv`.

**Expected (spec 15.4.3):** Files copied to `grammarmate/drills/{packId}/verb_drill/`.
**Actual:** Correctly copies to `drills/{packId}/verb_drill/{filename}`.
**Discrepancy:** None.

---

## Test Case 6: Import ZIP with vocabDrill section

**Code path:**
1. Same as test case 5 but for `vocabDrill` section.
2. `importPackDrills()` (LessonStore.kt:816-823):
   ```kotlin
   manifest.vocabDrill?.files?.forEach { fileName ->
       val source = File(packDir, fileName)
       if (!source.exists()) return@forEach
       val targetDir = File(baseDir, "drills/${manifest.packId}/vocab_drill")
       targetDir.mkdirs()
       val target = File(targetDir, source.name)
       source.copyTo(target, overwrite = true)
   }
   ```
3. For IT_VERB_GROUPS_ALL pack: copies 6 CSVs to `grammarmate/drills/IT_VERB_GROUPS_ALL/vocab_drill/`.

**Expected (spec 15.4.3):** Files copied to `grammarmate/drills/{packId}/vocab_drill/`.
**Actual:** Correctly copies to `drills/{packId}/vocab_drill/{filename}`.
**Discrepancy:** None.

---

## Test Case 7: Re-import same pack (same packId)

**Code path:**
1. `importPackFromTempDir()` at LessonStore.kt:312 calls `removePacksForLanguage(manifest.packId, languageId)`.
2. The overload at line 779 removes the pack entry from `packs.yaml` and deletes the pack directory.
3. Lines 314-317 delete the existing pack directory if present.
4. Lines 318-319 copy new content and clean up temp.
5. `importLessonFromFile()` (line 578) calls `replaceById()` (line 614-615) for lessons with explicit IDs, removing old lesson index entries and CSV files.
6. Stories: `importStoriesFromPack()` (line 649) uses `removeIf` to replace old stories with matching `storyId + lessonId + phase`.
7. Vocab: `importVocabFromPack()` (line 686) uses `removeIf` for matching `lessonId + languageId`.
8. Drill files use `copyTo(overwrite = true)`.
9. Packs.yaml: lines 337-358 rebuild the registry, removing the old entry for this `packId` and adding the new one.

**Expected (spec 15.6.2):** In-place replacement. Old data removed, new data installed.
**Actual:** Fully matches. Re-import replaces all content for the same packId.
**Discrepancy:** None. Progress files (`verb_drill_progress.yaml`, `word_mastery.yaml`) are NOT deleted during re-import since they live in `drills/{packId}/` and `importPackDrills` only writes CSV files, not progress files. This matches spec 15.6.2 ("What is NOT reset on re-import").

---

## Test Case 8: Import pack with duplicate packId as existing

**Code path:** Identical to test case 7. The pack with the same `packId` is treated as an update.

**Expected:** Old pack replaced, new pack installed.
**Actual:** Same as test case 7. No separate "duplicate detection" -- it's handled as a version update.
**Discrepancy:** None. This is the intended behavior per spec 15.1.3 and 15.6.2.

---

## Test Case 9: Default pack seeding on first launch

**Code path:**
1. `seedDefaultPacksIfNeeded()` (LessonStore.kt:53-67):
   ```kotlin
   fun seedDefaultPacksIfNeeded(): Boolean {
       ensureSeedData()
       if (seedMarker.exists()) return false
       if (hasLessonContent()) {
           AtomicFileWriter.writeText(seedMarker, "skip")
           return false
       }
       var seededAny = false
       defaultPacks.forEach { pack ->
           val seeded = runCatching { importPackFromAssetsInternal(pack.assetPath) }.isSuccess
           if (seeded) seededAny = true
       }
       AtomicFileWriter.writeText(seedMarker, if (seededAny) "ok" else "none")
       return seededAny
   }
   ```
2. `defaultPacks` list (line 30-33):
   ```kotlin
   private val defaultPacks = listOf(
       DefaultPack("EN_WORD_ORDER_A1", "grammarmate/packs/EN_WORD_ORDER_A1.zip"),
       DefaultPack("IT_VERB_GROUPS_ALL", "grammarmate/packs/IT_VERB_GROUPS_ALL.zip")
   )
   ```
3. Both packs are bundled in `assets/grammarmate/packs/` and confirmed present.

**Expected (spec 15.2.3):** Both packs seeded on first launch if seed marker absent and no lesson content exists.
**Actual:** Matches spec exactly. Seed marker written atomically. Three possible values: "ok" (some seeded), "none" (none seeded), "skip" (content already existed).
**Discrepancy:** None.

---

## Test Case 10: Default pack update check

**Code path:**
1. `updateDefaultPacksIfNeeded()` (LessonStore.kt:69-83):
   ```kotlin
   fun updateDefaultPacksIfNeeded(): Boolean {
       ensureSeedData()
       val installed = getInstalledPacks()
       var updatedAny = false
       defaultPacks.forEach { pack ->
           val manifest = runCatching { readPackManifestFromAssets(pack.assetPath) }.getOrNull() ?: return@forEach
           val existing = installed.firstOrNull { it.packId == manifest.packId }
           val shouldUpdate = existing == null || existing.packVersion != manifest.packVersion
           if (shouldUpdate) {
               val updated = runCatching { importPackFromAssetsInternal(pack.assetPath) }.isSuccess
               if (updated) updatedAny = true
           }
       }
       return updatedAny
   }
   ```
2. `readPackManifestFromAssets()` (line 204) extracts the ZIP from assets, reads the manifest, and cleans up the temp dir.

**Expected (spec 15.2.3):** Compares `packVersion` strings. Re-imports if missing or different.
**Actual:** Exactly matches. Uses `!=` for string comparison. If pack not installed at all (`existing == null`), it re-imports.
**Discrepancy:** None.

---

## Test Case 11: Language switch after import

**Code path:**
1. `getLessons(languageId)` (LessonStore.kt:412) loads lessons for a specific language ID.
2. `getInstalledPacks()` (LessonStore.kt:132) returns ALL packs regardless of language.
3. Packs are stored in `packs.yaml` with their `languageId` field.
4. `getLessons()` reads from `lessons/{languageId}_index.yaml` -- a per-language index.
5. Drills: `hasVerbDrill(packId, languageId)` and `hasVocabDrill(packId, languageId)` filter drill files by `{languageId}_` prefix.

**Expected:** Imported packs are available only for their declared language.
**Actual:** Each pack declares a single `language` in its manifest. Lessons are stored under `lessons/{languageId}/` and indexed in `{languageId}_index.yaml`. Switching language queries a different index.
**Discrepancy:**
- Packs for language "en" are NOT visible when the active language is "it" and vice versa, because `getLessons()` queries a language-specific index. This is correct behavior.
- However, `getInstalledPacks()` returns ALL packs regardless of language. The UI layer must filter packs by language. This is not a discrepancy but a design note: the data layer does not filter packs by language, so the UI is responsible for showing only relevant packs.

---

## Test Case 12: Pack removal

**Code path:**
1. `removeInstalledPackData(packId)` (LessonStore.kt:219-239):
   - Reads installed manifest to get lesson IDs and language ID.
   - For each lesson: `deleteLesson()` removes CSV file and index entry.
   - `removePacksForLanguage(packId, languageId)` removes pack entry from `packs.yaml` and deletes pack directory.
   - `deletePackDrills(packId)` deletes `drills/{packId}/` recursively.
   - Falls back to removing registry entry + directory if no manifest found.
2. `deleteLesson()` (LessonStore.kt:469-493):
   - Removes lesson CSV, drill CSV, and index entry.
   - Calls `removeVocabEntries(languageId, lessonId)`.
3. `deletePackDrills()` (LessonStore.kt:244-249):
   - Deletes the entire `drills/{packId}/` directory including progress files.

**Expected (spec 15.6.3):**
1. Read manifest to get lesson IDs.
2. Delete each lesson.
3. Remove pack entry from packs.yaml.
4. Delete pack directory.
5. Delete drill directory.

**Actual:** Matches spec. All 5 steps are followed.
**Discrepancy:**
- The `removePacksForLanguage(packId, languageId)` overload at line 779 deletes the pack directory AND removes the pack entry from `packs.yaml`. But the code at line 219-230 does this sequence: deleteLessons -> removePacksForLanguage -> deletePackDrills. The `removePacksForLanguage` at line 779 already deletes the pack directory (line 788), so `deletePackDrills` at line 229 runs after the pack dir is gone -- this is fine since drills are in a different directory tree (`drills/` vs `packs/`).
- The fallback path (lines 232-238) runs when no manifest is found. It calls `removePackEntry`, deletes the pack dir, and calls `deletePackDrills`. This handles the case where a pack was partially installed or its manifest was corrupted.

---

## Test Case 13: Import interruption (kill mid-import)

**Code path analysis:**

The import flow in `importPackFromTempDir()` (LessonStore.kt:301-359) performs these operations in sequence:
1. Parse manifest (line 307) -- no side effects yet.
2. `ensureLanguage()` (line 309) -- writes to `languages.yaml`.
3. `removePacksForLanguage()` (line 312) -- removes old pack data from `packs.yaml`.
4. Move temp to pack dir (lines 314-319) -- creates `packs/{packId}/`.
5. Import lessons (lines 321-329) -- writes CSVs to `lessons/{languageId}/` and updates index.
6. Import drills (line 332) -- copies to `drills/{packId}/`.
7. Import stories (line 334) -- writes JSONs to `stories/`.
8. Import vocab (line 335) -- writes to `vocab/{languageId}/`.
9. Update packs.yaml (lines 337-358) -- registers the pack.

**Interruption scenarios:**

| Kill after step | State on disk | Recovery |
|---|---|---|
| Step 2 | Language added but no pack data. | Benign -- language entry is harmless. |
| Step 3 | Old pack data removed, new not yet installed. | **Data loss** -- old pack lessons and index entries are gone, new ones not yet written. |
| Step 4 | Pack directory exists with all ZIP contents. No lessons imported, no registry entry. | Pack dir is orphaned. On next launch, `updateDefaultPacksIfNeeded()` would re-import default packs (if applicable). User-imported packs would be lost. |
| Step 5 (partial) | Some lessons imported, some not. Index partially updated. | **Corrupt state** -- some lessons exist, others missing. |
| Step 6-8 | Lessons imported but drills/stories/vocab partially done. | Partial content. |
| Before step 9 | All content imported but pack not registered in `packs.yaml`. | Pack exists on disk but is invisible to `getInstalledPacks()`. Can be detected by scanning `packs/` directory. |

**Expected (spec):** The spec does not address import interruption explicitly. CLAUDE.md says "All file writes must go through AtomicFileWriter -- temp -> fsync -> rename."
**Actual:**
- `importLessonFromFile()` uses raw `FileOutputStream` (line 591-593), NOT `AtomicFileWriter`. This violates the CLAUDE.md Level B constraint.
- `importPackDrills()` uses `copyTo()` (line 814), NOT `AtomicFileWriter`.
- Only `importStoriesFromPack()` and `importVocabFromPack()` use `AtomicFileWriter.writeText()` (lines 660, 701).
- The `packs.yaml` and lesson index updates use `YamlListStore.write()` which goes through atomic writes.
- There is no transaction mechanism -- no way to roll back a partial import.

**Discrepancies:**
1. **AtomicFileWriter violation in lesson import.** `importLessonFromFile()` uses raw `FileOutputStream` instead of `AtomicFileWriter`. A crash during lesson CSV copy could leave a truncated file. This violates CLAUDE.md Level B.
2. **No import transaction.** If the import is interrupted between steps 3-9, the app is left in an inconsistent state. There is no cleanup mechanism for partial imports on next launch.
3. **Temp directory cleanup only on explicit error.** If the app is killed (not an exception), the temp directory remains. There is no startup scan for `packs/tmp_*` directories.

---

## Test Case 14: ZIP with path traversal (../../etc/passwd)

**Code path:**
1. `extractZipToTemp()` (LessonStore.kt:276-299):
   ```kotlin
   val outFile = File(tempDir, entry.name)
   val canonicalParent = tempDir.canonicalPath + File.separator
   val canonicalTarget = outFile.canonicalPath
   if (!canonicalTarget.startsWith(canonicalParent)) {
       error("Invalid zip entry: ${entry.name}")
   }
   ```

**Expected (spec 15.3.3):** Path traversal blocked with `error("Invalid zip entry: {name}")`.
**Actual:** Canonical path comparison prevents `../` traversal. The check uses `canonicalPath` which resolves symlinks and normalizes `..` components.
**Discrepancy:** None. Security check is properly implemented.

**Edge case -- ZIP bomb (overlapping entries):** The code does not limit the number of entries or total extracted size. A ZIP with thousands of entries or a decompression bomb would be extracted in full before any validation occurs.

---

## Test Case 15: Very large ZIP (100MB+)

**Code path analysis:**

1. `extractZipToTemp()` reads the entire ZIP into temp files. Uses `zip.copyTo(out)` which is a streaming copy -- memory usage is proportional to the buffer size, not the ZIP content size. This is acceptable.
2. `tempDir.copyRecursively(packDir, overwrite = true)` at line 318 duplicates all extracted files. For a 100MB ZIP, this creates ~100MB of temp files plus ~100MB of permanent files. Total peak disk usage is ~200MB.
3. Lesson CSV parsing: `CsvParser.parseLesson()` reads the file into memory via `BufferedReader`. For a lesson with thousands of cards, this is fine -- a 100MB lesson would be unusual.
4. `packs.yaml` update: reads ALL installed packs, filters, adds new entry, writes back. For many installed packs, this is fine.
5. The manifest JSON is parsed into memory via `JSONObject(text)`. Manifests are small.

**Expected (spec):** No explicit performance requirements stated.
**Actual:**
- Memory: Streaming ZIP extraction keeps memory usage bounded. No `readBytes()` or `readText()` on large files during extraction.
- Disk: Peak usage is ~2x the ZIP size due to temp + permanent copy.
- Time: Linear in ZIP size. The bottleneck is file I/O (extract, copy, parse).
- No progress reporting or cancellation mechanism.

**Discrepancy:**
- `importLessonFromFile()` reads the CSV file twice: once with `FileOutputStream.copyTo()` to copy it (line 591-593), then again with `CsvParser.parseLesson(targetFile.inputStream())` (line 594). For a very large lesson CSV, this is a minor inefficiency but not a bug.
- No ZIP entry count limit or file size limit. A malicious ZIP with 100,000 entries would create 100,000 files on disk.

---

## Summary of Discrepancies

### Critical (violates spec or architecture rules)

| # | Issue | Location | Severity |
|---|---|---|---|
| D1 | **Temp directory not cleaned on manifest parse failure.** When `LessonPackManifest.fromJson()` throws (malformed JSON, wrong schemaVersion), the temp directory at `packs/tmp_{UUID}/` is not deleted. Only the "manifest file missing" case is handled. | LessonStore.kt:301-307 | Medium -- resource leak on repeated failed imports |
| D2 | **`importLessonFromFile()` uses raw `FileOutputStream` instead of `AtomicFileWriter`.** A crash during copy leaves a truncated lesson CSV file. Violates CLAUDE.md Level B. | LessonStore.kt:591-593 | High -- data integrity risk on crash |
| D3 | **`importPackDrills()` uses `copyTo()` instead of `AtomicFileWriter`.** Same issue as D2 for drill files. | LessonStore.kt:814, 822 | Medium -- drill files are less critical but still violate Level B |

### Moderate (behavior gaps vs spec)

| # | Issue | Location | Severity |
|---|---|---|---|
| D4 | **No validation that imported lessons have at least one card.** An empty or completely malformed CSV produces a lesson with `cards = emptyList()`. The Python validator catches this; the app does not. | CsvParser.kt / LessonStore.kt:578-624 | Low -- edge case, easily visible in UI |
| D5 | **No import transaction or rollback mechanism.** If the app is killed during import (steps 3-9 in test case 13), the system is left in an inconsistent state with partial data. | LessonStore.kt:301-359 | Medium -- unlikely but unrecoverable |
| D6 | **No startup cleanup of orphaned temp directories.** Failed or interrupted imports leave `packs/tmp_*` directories that are never cleaned up. | LessonStore.kt | Low -- cosmetic/resource issue |

### Minor (spec inconsistencies or omissions)

| # | Issue | Location | Severity |
|---|---|---|---|
| D7 | **Pack validator does not validate drill sections.** The `verbDrill` and `vocabDrill` manifest sections are completely ignored by `pack_validator.py`. The validator predates these features. | pack_validator.py | Low -- documented in spec 15.7.4 |
| D8 | **Pack validator requires non-empty lessons array, but app allows empty lessons with drill sections.** `LessonPackManifest.fromJson()` line 61-63 allows manifests with no standard lessons if drill sections exist, but the validator at line 49-50 requires `lessons` to be non-empty. | pack_validator.py:49 vs LessonPackManifest.kt:61-63 | Low -- validator is more strict than app |
| D9 | **Pack validator checks for exactly 2 columns in lesson CSV, but the app accepts 3 columns (optional tense).** The validator raises `cols != 2` error; the app parses the 3rd column as tense. | pack_validator.py:76-77 vs CsvParser.kt:39-43 | Medium -- valid packs with tense column fail validation |

---

## Spec-to-Code Alignment Summary

| Spec Section | Code Match | Notes |
|---|---|---|
| 15.1.1 ZIP Structure | Full match | All files at root, manifest.json required |
| 15.1.2 manifest.json Schema | Full match | All fields parsed correctly |
| 15.1.3 Versioning Strategy | Full match | String equality for packVersion |
| 15.1.4 Validation Rules | Partial match | D1 (temp cleanup), D4 (empty lessons) |
| 15.2.1 EN_WORD_ORDER_A1 | Full match | Confirmed in assets |
| 15.2.2 IT_VERB_GROUPS_ALL | Full match | Confirmed in assets with all drill files |
| 15.2.3 Seeding | Full match | Seed marker, update check, force reload all work |
| 15.3.2 Import Steps 1-11 | Partial match | D2 (non-atomic lesson writes), D3 (non-atomic drill writes) |
| 15.3.3 Error Handling | Partial match | D1 (temp cleanup on parse failure) |
| 15.3.4 Filesystem Layout | Full match | Verified directory paths match code |
| 15.4 Pack-Scoped Drills | Full match | Import, access, and cleanup all correct |
| 15.5 CSV Formats | Full match | All parsers match described formats |
| 15.6 Content Lifecycle | Full match | Add, update, remove all work as specified |
| 15.7 Pack Validator | Partial match | D7, D8, D9 (validator gaps) |

---

## Recommendations

1. **Wrap `importPackFromTempDir()` in a try-catch** that deletes the temp directory on any exception (addresses D1).
2. **Replace raw `FileOutputStream` in `importLessonFromFile()` with `AtomicFileWriter`** (addresses D2). The copy can be done by reading the source into a string and using `AtomicFileWriter.writeText()`.
3. **Add temp directory cleanup on app startup** -- scan `packs/` for `tmp_*` directories and delete them (addresses D6).
4. **Update `pack_validator.py`** to validate `verbDrill`/`vocabDrill` sections, accept the optional 3rd tense column in lesson CSVs, and handle manifests with drill-only content (addresses D7, D8, D9).

# TASK-092: Fix pack-manifest file mapping in the lazy lesson-loading path, and test it

**Status:** BUG (silent) + missing test coverage
**Branch:** create `feature/lazy-lesson-mapping` from the current branch. Never commit to `main`.

> **Revision note.** An earlier version of this file said the lazy-loading functions were
> unimplemented stubs and that nothing called them. Both statements were wrong. The API
> shipped in commit `bc50a524a` ("feat: lazy loading API + daily practice optimization",
> 2026-06-06) and daily practice depends on it. This file has been rewritten against the
> actual code. If anything below does not match what you see, trust the code and say so.

---

## Read this first

You are working in an Android app written in Kotlin. The app teaches languages using
"packs". A pack is a folder of CSV files; each CSV is one lesson; each CSV row is one
flashcard.

There are two ways the app loads lessons:

| Path | Function | Loads |
|---|---|---|
| Full | `getLessons(packId, languageId)` | every lesson of the pack, with all cards |
| Lazy | `getLesson(packId, languageId, lessonId)` | one lesson |

Both already exist and both work for the common case. **There is one bug:** the lazy path
guesses a lesson's CSV filename instead of reading it from the pack manifest. When a pack
declares a filename that differs from the lesson ID, the lazy path fails to find the file
while the full path finds it fine.

**Your job:** make the lazy path resolve filenames the same way the full path does, then
write tests proving the two paths agree.

This is a small change — roughly 20 lines of real code plus tests.

---

## ⚠️ This code is live. Do not break it.

These functions are called in production by `DailyPracticeCoordinator`:

```
app/src/main/java/com/alexpo/grammermate/feature/daily/DailyPracticeCoordinator.kt
  line 370  lessonStore.getLessonCount(...)
  line 376  lessonStore.getLessonIdAtIndex(...)
  line 913  lessonStore.getLessonCount(...)
  line 923  lessonStore.getLessonIdAtIndex(...)
  line 928  lessonStore.getCardsForLesson(...)
```

If you change their behaviour beyond the filename fix described here, daily practice
breaks. Change nothing else.

---

## Rules you must follow

1. Work in small steps. Do ONE numbered step at a time.
2. After EVERY step, run the build command and make sure it succeeds before the next step.
3. Never delete or rename an existing test.
4. Never change a test so that it passes. If a test fails, fix your code instead.
5. Do not add new libraries or dependencies.
6. Do not reformat, reorder, or "clean up" code you were not asked to change.

---

## Things you must NOT touch

**Do not touch the default methods in the `LessonStore` interface** (around lines 66–103
of `LessonStore.kt`). They look like unimplemented stubs:

```kotlin
fun getLessonCount(packId: String, languageId: String): Int {
    // STUB - will be implemented
    return 0
}
```

They are *interface defaults*, and the real implementations are `override`s in the class
`LessonStoreImpl` further down the same file (around lines 428–458). Existing tests in
`LessonStoreLazyLoadingTest` deliberately call the interface defaults and assert they
return `0`/`null`/empty. Leave both alone.

**Do not reassign card IDs.** Card IDs are produced once, by `CsvParser`, as
`"card_$lineNumber"` (`CsvParser.kt:84`). Neither `loadLessonsFromDisk` nor
`loadSingleLessonFromDisk` rewrites them — they pass the parser's cards straight through,
which is exactly why the two paths already agree. There is a `card.copy(id = ...)` pattern
elsewhere in the codebase (`PackImporter.kt:113` and `:369`) — **that is a different code
path and you must not copy it into `LessonStore.kt`.** Adding ID reassignment here would
break the parity test in step 4.

---

## Build and test commands

Use Git Bash, from the repository root.

Build only (fast — after every step):

```bash
./gw.sh . compileDebugKotlin
```

Run tests (at the end of each step):

```bash
./gw.sh . testDebugUnitTest
```

**Use `testDebugUnitTest`, NOT `test`.** The `test` task also runs `testReleaseUnitTest`,
which has 83 failures that existed before you started (Robolectric cannot resolve
`ComponentActivity` in the release manifest). Those are not your problem and you must not
try to fix them. `testDebugUnitTest` has 530 tests and all 530 must pass.

One test class only:

```bash
./gw.sh . testDebugUnitTest --tests "com.alexpo.grammermate.data.LessonStoreLazyLoadingTest"
```

---

## Background: how the manifest maps lesson IDs to filenames

A pack ships a `manifest.json`. There are two schema versions, and a lesson's filename is
**not** always `"<lessonId>.csv"`.

The full path already handles this. Read these two existing functions before you start —
they are your source of truth:

- `getManifestLessonFileNames(manifest)` — around line 1097. Note line 1102–1104:
  ```kotlin
  val lessonIdToFile = manifest.lessons.associate { it.lessonId to it.file }
  manifest.chapters.flatMap { it.lessons }.map { lessonId ->
      lessonIdToFile[lessonId] ?: "$lessonId.csv"
  }.toSet()
  ```
  So: **if the manifest lists an explicit `file` for a lesson ID, that wins; otherwise fall
  back to `"<lessonId>.csv"`.**

- `loadLessonsFromDisk(languageId, packId)` — around line 564. This is the full path.

The buggy code ignores all of that:

```kotlin
// loadSingleLessonFromDisk, line 1180
val csvFile = File(packDir, "$lessonId.csv")

// loadLessonMetadataFromDisk, line 1155
val csvFile = File(packDir, "$lessonId.csv")
```

**Consequence:** for a pack whose CSVs were renumbered or renamed, `getLesson()` returns
`null` and `getLessonMetadata()` silently skips the lesson (`continue`), while
`getLessons()` returns it correctly.

---

## Step 1 — Add one shared helper

In `LessonStoreImpl`, next to the other private helpers near the bottom of the file, add:

```kotlin
/**
 * Resolve the CSV filename for [lessonId] in [packId].
 *
 * Mirrors the rule in [getManifestLessonFileNames]: an explicit `file` in
 * manifest.lessons wins, otherwise the filename is "<lessonId>.csv". Works for both
 * schema v1 (lessons listed at the root) and v2 (lesson IDs listed in chapters).
 */
private fun lessonFileNameFor(packId: String, lessonId: String): String {
    val manifest = languageManager.readInstalledPackManifest(packId)
    val explicit = manifest?.lessons?.firstOrNull { it.lessonId == lessonId }?.file
    return explicit ?: "$lessonId.csv"
}
```

`readInstalledPackManifest` is already cached, so calling this is cheap.

Build. Then go to step 2.

---

## Step 2 — Use the helper in `loadSingleLessonFromDisk`

Find `loadSingleLessonFromDisk` (around line 1177). Change only this line:

```kotlin
val csvFile = File(packDir, "$lessonId.csv")
```

to:

```kotlin
val csvFile = File(packDir, lessonFileNameFor(packId, lessonId))
```

Change nothing else in that function. In particular leave the `cards = cards` line exactly
as it is — see the "do not reassign card IDs" rule above.

Build. Then go to step 3.

---

## Step 3 — Use the helper in `loadLessonMetadataFromDisk`

Find `loadLessonMetadataFromDisk` (around line 1145). Make the same one-line change:

```kotlin
val csvFile = File(packDir, "$lessonId.csv")
```

becomes:

```kotlin
val csvFile = File(packDir, lessonFileNameFor(packId, lessonId))
```

Leave the `if (!csvFile.exists()) continue` line as it is.

Build, then run the full test suite. All 530 tests must still pass. Then go to step 4.

---

## Step 4 — Write the tests

### Where

Add to the existing file:

```
app/src/test/java/com/alexpo/grammermate/data/LessonStoreLazyLoadingTest.kt
```

### How to set up a pack on disk

The tests currently in that file are pure data-class tests and stub-object tests — **they
are not a usable example for disk-backed tests.** Use this file instead as your template:

```
app/src/test/java/com/alexpo/grammermate/data/LessonStoreLessonOrderTest.kt
```

It shows the pattern you need: `@RunWith(RobolectricTestRunner::class)`, a `@Before` that
builds `File(context.filesDir, "grammarmate")`, a seeded `LessonStoreImpl(context)`, and
real pack directories on disk. Copy that structure.

### What to test

Write one test per fact:

1. `getLessonCount` returns the number of lessons the manifest declares.
2. `getLessonCount` returns `0` for a pack ID that does not exist.
3. `getLessonIdAtIndex(0)` returns the first lesson in course order.
4. `getLessonIdAtIndex` returns `null` for an index past the end.
5. `getLessonIdAtIndex` returns `null` for index `-1`.
6. `getLessonMetadata` returns one entry per lesson, in course order.
7. `getLesson` returns a lesson whose `cards` list is not empty.
8. `getLesson` returns `null` for a lesson ID that does not exist.
9. `getCardsForLesson` returns the same list as `getLesson(...)!!.cards`.

10. **The regression test for the bug you just fixed.** Build a pack whose manifest maps a
    lesson ID to a filename that is NOT `"<lessonId>.csv"` — for example lesson ID
    `lesson_01_A01` stored in a file named `renamed_01.csv`, declared in the manifest as
    `{"lessonId": "lesson_01_A01", "file": "renamed_01.csv"}`. Then assert:
    - `getLesson(packId, languageId, "lesson_01_A01")` is not `null`;
    - `getLessonMetadata(packId, languageId)` contains an entry for that lesson ID.

    Confirm this test **fails** if you temporarily revert step 2, then passes with the fix.
    If it passes either way, the test is not testing the bug — fix the test.

11. **The parity test — the most important one.** For the same pack and lesson, assert that

    ```
    getLesson(packId, languageId, lessonId)!!.cards.map { it.id }
    ```

    equals

    ```
    getLessons(packId, languageId).first { it.id.value == lessonId }.cards.map { it.id }
    ```

    Card IDs are what the progress system stores on disk, so this is what protects a user's
    saved progress. It must compare IDs in order, not just sizes.

Run the full test suite. All 530 existing tests plus your new ones must pass.

---

## If you find that the bug does not reproduce

It is possible the packs used in tests never exercise the explicit-filename mapping. If
your step-4.10 test passes even with step 2 reverted, stop and report that instead of
deleting the test or weakening it. Say exactly what you observed. A wrong "all green" is
worse than an honest "I could not reproduce it".

---

## When you are done

Report back with:

- which steps you completed;
- the exact output line showing test count and failures;
- whether test 4.10 failed before the fix and passed after (say so explicitly);
- anything you could not do and why.

Do not claim the work is finished unless you actually ran `./gw.sh . testDebugUnitTest`
and saw it succeed. Paste the result.

# GrammarMate — Architecture, Business Logic & UI/UX Review

**Date:** 2026-09-08
**Branch:** `dev` @ `2f861d6fa`
**Scope:** production runtime (`legacy` flavor, `app/legacy-src/java`, 156 files / 45 593 LOC)
**Method:** static read of the full navigation shell, `TrainingViewModel`, progress/mastery domain code, data stores and CI config. No build or test run was performed — every finding below is anchored to a `file:line` reference so it can be verified directly.

**Decision taken during this review:** the `v2` runtime is dropped. It is not a migration target. This report therefore treats `legacy` as the one and only runtime, and v2 removal as Phase 0 of the plan.

---

## 0. Executive summary

The app works because a lot of defensive glue holds it together, not because the layering is sound. Three structural problems produce most of the symptoms you reported:

| Symptom you described | Root cause |
|---|---|
| "тормозит, проблемы с производительностью" | Synchronous disk I/O + YAML/JSON parsing runs **inside Compose composition**, re-executed on every recomposition — and one monolithic state flow makes every keystroke recompose the whole app. |
| "баги в порядке отображения экранов" | Navigation decisions are split across **three competing sources of truth**, and screen transitions are triggered **during composition** instead of in effects. |
| "флаки баги" | Same as above — writing state and navigating during the composition phase is non-deterministic by definition. Compounded by a **dead BackHandler** shadowed by a later-registered one. |
| "протекает бизнес-логика и статусы экранов" | The same "chapter progress" number is computed from **two different sources**, one of which the code itself documents as stale. Progress percentage is mathematically wrong. |

There is also a process problem that let all of this land: **CI enforces tests on the runtime being deleted and enforces nothing on the shipping app** ([ci.yml:47](.github/workflows/ci.yml#L47)).

Severity legend: **S1** = user-visible defect or data correctness, **S2** = performance / stability, **S3** = maintainability.

---

## 1. Performance — main-thread I/O inside composition

This is the single largest cause of the sluggishness, and it is unambiguous.

### P1 (S2) — Uncached disk read + JSON parse on every recomposition

[`LanguageManager.readInstalledPackManifest`](app/legacy-src/java/com/alexpo/grammermate/data/LanguageManager.kt#L221-L225) does `File.exists()` → `readText()` → JSON parse on **every call, with no cache**:

```kotlin
fun readInstalledPackManifest(packId: String): LessonPackManifest? {
    val manifestFile = File(File(packsDir, packId), "manifest.json")
    if (!manifestFile.exists()) return null
    return runCatching { LessonPackManifest.fromJson(manifestFile.readText()) }.getOrNull()
}
```

Call chain: `readInstalledPackManifest` ← [`LessonStore.hasChapters`](app/legacy-src/java/com/alexpo/grammermate/data/LessonStore.kt#L779-L782) ← [`vm.hasPackChapters`](app/legacy-src/java/com/alexpo/grammermate/ui/TrainingViewModel.kt#L1080-L1082).

And `hasPackChapters` is called **from inside a `BackHandler(enabled = …)` argument** — an expression re-evaluated on every single recomposition of the app shell:

- [GrammarMateApp.kt:1219](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1219) — `BackHandler(enabled = … vm.hasPackChapters(…) …)`
- Also in composable bodies: [:388](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L388), [:616](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L616)

### P2 (S2) — Uncached YAML config read on every recomposition

[`AppConfigStoreImpl.load()`](app/legacy-src/java/com/alexpo/grammermate/data/AppConfigStore.kt#L76) reads and SnakeYAML-parses `config.yaml` with no cache. It is reached from two per-composition call sites:

- [`vm.currentUiLanguage`](app/legacy-src/java/com/alexpo/grammermate/ui/TrainingViewModel.kt#L422) — `get() = configStore.load().uiLanguage`, passed as a `SettingsSheet` argument at [GrammarMateApp.kt:375](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L375)
- [`SettingsActionHandler.getClickableWordHints()`](app/legacy-src/java/com/alexpo/grammermate/shared/SettingsActionHandler.kt#L108-L110) — called at [GrammarMateApp.kt:374](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L374) and again at [:1377](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1377)

### P3 (S2) — Other work performed in composition

| Call site | Work done |
|---|---|
| [GrammarMateApp.kt:549](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L549) `vm.getPackTiles()` | [O(packs × lessons)](app/legacy-src/java/com/alexpo/grammermate/ui/TrainingViewModel.kt#L152-L190) mastery lookups + flower recalculation |
| [GrammarMateApp.kt:1364](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1364) `vm.reports.getBadSentenceCount()` | [store lookup](app/legacy-src/java/com/alexpo/grammermate/feature/progress/BadSentenceHelper.kt#L121-L124) |
| [GrammarMateApp.kt:1337](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1337) `GrammarChipStore.getChipForLesson` | map lookup + potential file load |
| [GrammarMateApp.kt:548](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L548) `vm.getPomodoroHistoryForSelectedLanguage()` | store read |

`LessonStore.getLessons` *is* cached ([LessonStore.kt:400-407](app/legacy-src/java/com/alexpo/grammermate/data/LessonStore.kt#L400-L407)) — the manifest and config paths are the ones that are not.

### P4 (S2) — Monolithic state flow amplifies P1–P3

[`uiState`](app/legacy-src/java/com/alexpo/grammermate/ui/TrainingViewModel.kt#L372-L393) merges **seven** flows (core, audio, story, vocabSprint, daily, flower, boss) into one `TrainingUiState` via nested `combine`. Any emission from any of them — a TTS state change, a download progress percent, a Pomodoro second, a single keystroke — produces a new whole-app state object and recomposes `GrammarMateApp` top-to-bottom, re-running every item in P1–P3.

This is the multiplier: the individual reads are maybe 1–10 ms each, but they fire on every frame-level state change.

### P5 (S2) — Blocking lock around YAML on the main thread

[`MasteryStore`](app/legacy-src/java/com/alexpo/grammermate/data/MasteryStore.kt#L58) uses a `ReentrantLock` held across YAML read/write. Reached from the main thread via `getChapterCards`/`getProfileStats`/`getPackTiles`. Contending with a background writer blocks the UI thread — an ANR risk, not just jank.

### P6 (S3) — Unconditional debug logging in hot paths

[`LessonStore.getChapters`](app/legacy-src/java/com/alexpo/grammermate/data/LessonStore.kt#L709-L711) logs one line **per chapter, per call**; [`getChapterCards`](app/legacy-src/java/com/alexpo/grammermate/ui/TrainingViewModel.kt#L2422-L2423) logs per lesson. String interpolation happens even when logging is off.

### P7 (S2) — Duplicate `VerbDrillViewModel` instances

Two distinct instances exist with different `ViewModelStoreOwner`s:

- [GrammarMateApp.kt:182](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L182) `verbDrillVm` — Activity-scoped (declared outside `NavHost`)
- [GrammarMateApp.kt:688](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L688) `verbTenseInfoVm` — NavBackStackEntry-scoped (declared inside `composable(TRAINING)`)

Both run `reloadForPack` ([:184-190](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L184-L190), [:690-696](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L690-L696), [:837-843](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L837-L843)) — duplicated loading work and two divergent copies of drill state.

---

## 2. Navigation & flakiness — the screen-order bugs

### F1 (S1) — State mutation and navigation performed *during composition*

This is the direct cause of "флаки баги" and wrong screen order. Compose may run, skip, or re-run a composable body at will; performing side effects there is undefined behaviour.

**Token-based navigation** at [GrammarMateApp.kt:1559-1592](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1559-L1592) sits in the raw body of `NavDialogs`, with no `LaunchedEffect`:

```kotlin
if (state.cardSession.subLessonFinishedToken < lastFinishedToken.value) {
    lastFinishedToken.value = state.cardSession.subLessonFinishedToken   // write during composition
}
if (currentRoute == Routes.TRAINING && state.cardSession.subLessonFinishedToken != lastFinishedToken.value) {
    lastFinishedToken.value = state.cardSession.subLessonFinishedToken   // write during composition
    vm.onTrainingSessionCompleted()                                      // VM mutation during composition
    …
    vm.daily.onBlockComplete()                                           // VM mutation during composition
    onNavigate(returnTo)                                                 // navigation during composition
}
```

Same pattern:
- Boss token → [GrammarMateApp.kt:1711-1714](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1711-L1714)
- TTS auto-speak → [GrammarMateApp.kt:1520-1526](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1520-L1526) dismisses a dialog and **starts TTS playback** from composition, which can re-fire and double-speak.

### F2 (S1) — Exit confirmation is dead code for lesson training

Two `BackHandler`s are live on the TRAINING route at once:

- Outer, registered first at [GrammarMateApp.kt:1230](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1230): `enabled = currentRoute == TRAINING && !showSettings` → shows the exit dialog.
- Inner, registered later at [GrammarMateApp.kt:824](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L824): `enabled = !isVerbDrillLike && returnTo != DAILY_PRACTICE && !showSettings` → calls `vm.finishSession()` and navigates.

In Compose the **last registered enabled handler wins**. For ordinary lesson training both predicates are true, so the inner one always intercepts: **back silently ends the session with no confirmation dialog**, and the outer handler can never run.

### F3 (S1) — The back stack is flattened, making one documented flow unreachable

Every navigation goes through [`onNavigate`](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L258-L262), which always applies `popUpTo(Routes.HOME) { inclusive = false }`. The stack is therefore never deeper than `[HOME, X]`.

Consequently `navController.popBackStack()` in STORY_READER ([:1043](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1043), [:1299](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1299)) can only ever land on HOME. The comment at [:1288-1290](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1288-L1290) promising a return to `GRAMMAR_STORY_ROADMAP` describes behaviour that cannot happen.

### F4 (S3) — Three competing sources of navigation truth

1. `NavController` back stack
2. `previousRoute`, a manually maintained `var` ([:171](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L171), written at [:257](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L257), [:319](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L319), [:438](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L438), [:497](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L497), [:757](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L757), [:956](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L956))
3. `state.cardSession.returnTo`, held in the ViewModel

Routing decisions mix all three — e.g. `onSessionDone` at [:772-810](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L772-L810) branches on `returnTo` **and** re-derives `hasChapters` from disk. When these disagree, the user lands on the wrong screen.

### F5 (S2) — Screen tracking silently mislabels two screens

[`routeToScreen`](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1169-L1184) has no branch for `CHAPTER_LESSONS` or `AUX_DRILL`; both fall through to `else -> AppScreen.HOME`. This value feeds `vm.settings.onScreenChanged(...)` ([:196](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L196)), which drives screen restore and analytics — so restoring after a kill from either screen returns the user to HOME, and the audit log is wrong.

### F6 (S3) — `remember` misuse

- [:1509](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1509) `remember { vm.getProfileStats() }` — **no key**, so the profile popup shows stale numbers on every open after the first.
- Lambdas keyed on the whole `dialogs` object (`remember(dialogs) { … }` at [:289](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L289), [:436](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L436), [:452](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L452), [:495](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L495), [:509](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L509), …) are invalidated by *any* dialog toggle, defeating the memoization.
- `onTtsSpeak` ([:225](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L225)) is keyed on `state.cardSession.answerText` among others — a new lambda identity on every answer change, forcing `TrainingScreen` to recompose.

---

## 3. Business logic leaks

### B1 (S1) — Chapter progress percentage is mathematically wrong

[Models.kt:735-739](app/legacy-src/java/com/alexpo/grammermate/data/Models.kt#L735-L739):

```kotlin
val progress: Float
    get() = if (lessonsStarted == 0) 0f else lessonsCompleted.toFloat() / lessonsStarted

val totalLessons: Int
    get() = lessonsStarted // This is updated as lessons are discovered
```

Progress is measured against **started** lessons, not the chapter's total. A chapter with 10 lessons where the user started and completed exactly 1 reports **100 %**. `totalLessons` returning `lessonsStarted` is wrong by its own name. `CLAUDE.md` specifies `(lessonsCompleted / totalLessons) * 100` — spec and code disagree, and the code is what ships.

### B2 (S1) — A documented API that always lies

[Models.kt:745-749](app/legacy-src/java/com/alexpo/grammermate/data/Models.kt#L745-L749) — `isLessonCompleted()` is documented as "Check if a specific lesson is completed (mastery step >= 3)" and unconditionally `return false`. Any caller silently gets "not completed".

### B3 (S1) — The same number computed from two different sources

| Screen | Source |
|---|---|
| Grammar Story Roadmap | **live** recomputation — [`ChapterProgressCalculator.calculateChapterProgress`](app/legacy-src/java/com/alexpo/grammermate/ui/TrainingViewModel.kt#L2430) |
| Chapter Lessons | **store snapshot** — [`_coreState.chapterProgresses[chapterId]`](app/legacy-src/java/com/alexpo/grammermate/ui/TrainingViewModel.kt#L1004-L1006), consumed at [GrammarMateApp.kt:1086](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1086) |

The code documents the divergence itself at [TrainingViewModel.kt:2428-2429](app/legacy-src/java/com/alexpo/grammermate/ui/TrainingViewModel.kt#L2428-L2429): *"Calculate progress LIVE from mastery data instead of relying on chapterProgressStore which may be stale/empty on app start."* The roadmap was fixed; the chapter screen was left on the stale source. **The roadmap and the chapter screen can show different numbers for the same chapter.** This is the "статусы экранов протекают" symptom, exactly.

### B4 (S2) — Pack scoping depends on discipline, not on the key

[`getChapterProgress(packId, chapterId)`](app/legacy-src/java/com/alexpo/grammermate/ui/TrainingViewModel.kt#L1004-L1006) **ignores its `packId` parameter**. The backing map is keyed by `chapterId` alone ([Models.kt:634](app/legacy-src/java/com/alexpo/grammermate/data/Models.kt#L634)), and chapter IDs (`chapter_0`, …) repeat across packs. Correctness relies entirely on `loadChapters()` clearing the map on every pack switch ([:2539-2564](app/legacy-src/java/com/alexpo/grammermate/ui/TrainingViewModel.kt#L2539-L2564)). Any path that changes pack without calling it leaks the previous pack's progress. A duplicate overload also exists at [:2665](app/legacy-src/java/com/alexpo/grammermate/ui/TrainingViewModel.kt#L2665).

### B5 (S2) — Completion rule: docs say one thing, code does another

Real rule ([ProgressTracker.kt:113-134](app/legacy-src/java/com/alexpo/grammermate/feature/progress/ProgressTracker.kt#L113-L134)): a lesson is complete when `uniqueCardShows >= min(effectiveCardCount, 150)`, which stamps `completedAtMs`.

Documented rule ([Models.kt:720](app/legacy-src/java/com/alexpo/grammermate/data/Models.kt#L720) and `CLAUDE.md`): `intervalStepIndex >= 3`.

`intervalStepIndex` is **never** used in any completion check anywhere in the codebase. The documented threshold is fiction.

### B6 (S3) — The WORD_BANK rule is enforced in two independent places

- [ProgressTracker.kt:67](app/legacy-src/java/com/alexpo/grammermate/feature/progress/ProgressTracker.kt#L67) — mastery/flower path
- [TrainingViewModel.kt:1144](app/legacy-src/java/com/alexpo/grammermate/ui/TrainingViewModel.kt#L1144) — daily cursor path

No shared predicate. A future third counting path will silently omit the rule.

### B7 (S3) — Dead code acknowledged in comments rather than deleted

[TrainingViewModel.kt:1141-1142](app/legacy-src/java/com/alexpo/grammermate/ui/TrainingViewModel.kt#L1141-L1142): *"the DailyPracticeSessionProvider path with its onCardAdvanced callback is dead code, never instantiated"*.

---

## 4. Architecture & process

### A1 (S1) — Two runtimes, and CI guards the wrong one

| Runtime | Size | Flavor | Status |
|---|---|---|---|
| `legacy` | 156 files / 45 593 LOC | `legacy` (v1.7, minSdk 24) | **ships to users** |
| `v2` + `:domain` | 98 + 49 files / 22 323 LOC | `v2` (`.v2preview`) | preview — **being dropped** |

[ci.yml:41](.github/workflows/ci.yml#L41) runs the **v2** suite as a blocking gate. [ci.yml:43-48](.github/workflows/ci.yml#L43-L48) runs the **production** suite with `continue-on-error: true`. The shipping app has had no enforced test gate; the code slated for deletion has one. This is the process root cause behind the surviving flaky bugs.

Good news for the removal: the separation is clean — legacy has **0** imports of `com.alexpo.grammermate.v2` and **0** of `com.alexpo.grammermate.domain`; v2 has **0** imports of legacy packages. v2 can be excised without touching legacy code.

### A2 (S3) — God objects

- [`TrainingViewModel`](app/legacy-src/java/com/alexpo/grammermate/ui/TrainingViewModel.kt) — 2 695 LOC, ~10 injected collaborators, owns navigation state, chapter state, profile stats, Pomodoro, sound packs and audio routing.
- [`GrammarMateApp.kt`](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt) — 2 111 LOC mixing routing, dialog hosting, business decisions and logging.
- [`SessionRunner`](app/legacy-src/java/com/alexpo/grammermate/feature/training/SessionRunner.kt) — 1 425 LOC.

### A3 (S3) — Large-scale copy-paste in the shell

- The `onDailyPractice` handler is duplicated **three times** verbatim: [:452-478](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L452-L478), [:509-535](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L509-L535), [:985-1011](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L985-L1011).
- `GrammarStoryRoadmapScreen` is invoked twice with **divergent parameters** — the HOME branch passes `onAuxDrill` ([:450](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L450)), the route branch ([:941](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L941)) does not. Same screen, two behaviours.
- `onReadStory` and `onPlayChapterStory` duplicated between the two call sites.

---

# Refactoring Plan

Ordered so that each phase is independently shippable and leaves the app in a working state. Phases 1–3 are the ones that fix your reported symptoms; do not reorder them behind the cleanup phases.

**Ground rule for every phase:** no behavioural change without a test that pins the behaviour first. The legacy suite already has 62 test files including click-UI tests — that is the safety net; Phase 0 makes it binding.

---

## Phase 0 — Single runtime & a CI gate that means something

*Goal: delete v2, and make the production test suite blocking. Nothing else can be trusted until CI enforces the shipping app.*

- [ ] **0.1** Confirm no device/user data depends on the v2 Room database (`.v2preview` is a separate `applicationId`, so uninstalling it is isolated — verify no shared external storage paths).
- [ ] **0.2** Delete the v2 source sets: `app/src/main/java/com/alexpo/grammermate/v2/`, `app/src/testV2/`, `app/src/androidTestV2/`.
- [ ] **0.3** Delete the `:domain` module and remove `include(":domain")` from `settings.gradle.kts` plus `implementation(project(":domain"))` from `app/build.gradle.kts`.
- [ ] **0.4** Remove the `v2` product flavor from `app/build.gradle.kts`, including the `assembleV2Debug` copy task.
- [ ] **0.5** Collapse the flavor dimension entirely: promote `legacy` to the default source set (`app/src/main`), fold `AndroidManifest.shared.xml` back into a single `AndroidManifest.xml`, and drop `flavorDimensions`. Restores plain `assembleDebug` / `testDebugUnitTest` and removes the `legacy-src` indirection.
- [ ] **0.6** Drop now-unused dependencies: Room + `ksp(room-compiler)`, Hilt + `ksp(hilt-compiler)`, DataStore, `kotlinx-serialization`, `androidx.hilt.navigation.compose`, and the `room.schemaLocation` KSP args + `schemas/` asset wiring.
- [ ] **0.7** Rewrite `.github/workflows/ci.yml`: one build, one unit-test job, one instrumentation job — all pointing at the single runtime, **all blocking**. Remove `continue-on-error: true`.
- [ ] **0.8** Fix or explicitly quarantine every failing legacy test so CI is green *and* blocking. Quarantine must be a named `@Ignore` with a linked issue, not a silent skip.
- [ ] **0.9** Update `CLAUDE.md`: remove the v2/flavor build instructions and the stale `java -cp` flavor-specific commands.
- [ ] **0.10** Delete `docs/specification/migration-parity-audit.md` and other v2-migration docs that no longer describe anything real.

**Exit criteria:** `assembleDebug` and `testDebugUnitTest` succeed with no flavor qualifier; CI fails the build when a production test fails.

---

## Phase 1 — Get I/O out of composition (fixes the lag)

*Goal: no disk read, YAML parse, or JSON parse may occur during a Compose composition pass. Highest performance return of the whole plan.*

- [ ] **1.1** Add an in-memory cache to `LanguageManager.readInstalledPackManifest`, keyed by `packId`, invalidated on pack import/delete. Mirrors the existing `lessonsCache` pattern in `LessonStore`.
- [ ] **1.2** Add an in-memory cache to `AppConfigStoreImpl.load()`, invalidated on `save()`.
- [ ] **1.3** Hoist `hasPackChapters` out of composition: compute it once when the active pack changes and expose it as a field on `TrainingUiState` (e.g. `navigation.activePackHasChapters`). Replace all six call sites — `BackHandler(enabled=…)` at [:1219](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1219) is the critical one.
- [ ] **1.4** Replace the `vm.currentUiLanguage` and `vm.settings.getClickableWordHints()` property-getter calls with values carried in state.
- [ ] **1.5** Convert `getPackTiles()`, `getChapterCards()`, `getProfileStats()`, `getPomodoroHistoryForSelectedLanguage()`, `getBadSentenceCount()` from imperative per-composition calls into state fields updated on the events that actually change them.
- [ ] **1.6** Move `MasteryStore` I/O off the main thread; keep the `ReentrantLock` only around in-memory cache mutation, never around file I/O.
- [ ] **1.7** Guard hot-path logging behind `BuildConfig.DEBUG` (or drop it) in `LessonStore.getChapters` and `TrainingViewModel.getChapterCards`.
- [ ] **1.8** Enable **StrictMode** `detectDiskReads().detectDiskWrites().penaltyLog()` on the main thread in debug builds. This is the regression guard that keeps I/O from creeping back into composition.

**Exit criteria:** StrictMode logs zero main-thread disk reads while navigating HOME → chapter → training and typing an answer.

---

## Phase 2 — One source of truth for navigation (fixes screen order & flakiness)

*Goal: every screen transition is triggered from an effect, never from composition, and is decided by exactly one authority.*

- [ ] **2.1** Move all token-based navigation into `LaunchedEffect(token)`: the sub-lesson block at [:1559-1592](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1559-L1592), the boss block at [:1711-1714](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1711-L1714), and the TTS auto-speak at [:1520-1526](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1520-L1526). No `MutableState` write, VM call, or `onNavigate` may remain in a composable body.
- [ ] **2.2** Replace the token + `MutableState` mechanism with a one-shot event channel from the ViewModel (`Channel` → `Flow`, consumed in a single `LaunchedEffect`). Tokens compared against remembered values are the workaround; a consumable event stream is the fix.
- [ ] **2.3** Delete `previousRoute`. Where a real "return to" is needed, either rely on the back stack or carry it in the session state — not both.
- [ ] **2.4** Stop flattening the back stack: remove the unconditional `popUpTo(Routes.HOME)` from `onNavigate` ([:258-262](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L258-L262)). Apply `popUpTo` only where a specific flow requires it. This makes `popBackStack()` meaningful and fixes the STORY_READER return path (F3).
- [ ] **2.5** Resolve the duplicated TRAINING `BackHandler`s (F2): decide the intended behaviour — exit dialog or silent finish — and keep exactly one handler. Add a click-UI regression test asserting back on lesson training shows the confirmation dialog.
- [ ] **2.6** Audit every remaining `BackHandler` for overlapping `enabled` predicates; document the intended priority order in one place.
- [ ] **2.7** Complete `routeToScreen` with `CHAPTER_LESSONS` and `AUX_DRILL` (F5), or replace the enum mapping with the route string itself.
- [ ] **2.8** Make `GRAMMAR_STORY_ROADMAP` either a real route or purely a HOME sub-state — not both. Currently it is rendered inside HOME *and* reachable as a route with different parameters.
- [ ] **2.9** Fix the `remember` misuse: key the `getProfileStats()` call ([:1509](app/legacy-src/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L1509)), and stop keying callback lambdas on the whole `dialogs` object.

**Exit criteria:** a click-UI test walks HOME → roadmap → chapter → lesson → training → back, and every hop lands on the documented screen, repeatably.

---

## Phase 3 — One definition per business rule (fixes leaking statuses)

*Goal: each domain rule exists exactly once, and the specs match the code.*

- [ ] **3.1** Fix `ChapterProgress.progress` to divide by the chapter's real lesson count, and remove the `totalLessons get() = lessonsStarted` lie (B1). Requires passing the chapter's total into the model or computing it in the calculator. **This changes what users see** — verify against `CLAUDE.md`'s stated intent before shipping.
- [ ] **3.2** Delete `ChapterProgress.isLessonCompleted()` (B2) — a stub returning a constant is worse than no method.
- [ ] **3.3** Make the Chapter Lessons screen consume the same live calculation as the roadmap (B3). Either delete `chapterProgressStore` as a read source and always compute from mastery, or make the store the single source and keep it eagerly fresh — but pick one.
- [ ] **3.4** Either honour the `packId` parameter in `getChapterProgress` or key `chapterProgresses` by `(packId, chapterId)` (B4). Delete the duplicate overload at [:2665](app/legacy-src/java/com/alexpo/grammermate/ui/TrainingViewModel.kt#L2665).
- [ ] **3.5** Reconcile the completion rule (B5): the code's 150-card threshold is the real behaviour. Update `Models.kt:720` and `CLAUDE.md` to state it, or change the code — but eliminate the contradiction. Note `intervalStepIndex` is currently unused for completion.
- [ ] **3.6** Extract the WORD_BANK exclusion into one named predicate (e.g. `InputMode.countsTowardMastery`) and call it from both sites (B6).
- [ ] **3.7** Delete the `DailyPracticeSessionProvider` dead path (B7).
- [ ] **3.8** Add unit tests pinning each rule: progress percentage, completion threshold, WORD_BANK exclusion, pack isolation of chapter progress.

**Exit criteria:** the roadmap and the chapter screen report identical numbers for the same chapter, on a cold start, in a test.

---

## Phase 4 — Decompose the god objects

*Goal: make the remaining work safe to do. Only start once Phases 1–3 are green — decomposition without tests is how the current state was reached.*

- [ ] **4.1** Split `GrammarMateApp.kt`: extract each `composable(...)` block into its own file under `ui/navigation/`, leaving only the graph declaration.
- [ ] **4.2** Extract the triplicated `onDailyPractice` handler into one shared function (A3), and unify the two `GrammarStoryRoadmapScreen` call sites so they cannot diverge.
- [ ] **4.3** Move `NavDialogs` into a dedicated dialog host driven by a single sealed `DialogState`, not seven independent booleans.
- [ ] **4.4** Split `TrainingUiState` so screens subscribe only to the slice they render — the largest remaining recomposition win after Phase 1.
- [ ] **4.5** Move chapter/roadmap concerns out of `TrainingViewModel` into a `ChapterViewModel`; move profile/Pomodoro/sound-pack concerns out too.
- [ ] **4.6** Resolve the duplicate `VerbDrillViewModel` instances (P7) — one owner, one scope.
- [ ] **4.7** Re-run the review checklist; target `TrainingViewModel` under 800 LOC and no UI file over 500 LOC.

---

## Sequencing & risk

| Phase | Effort | Risk | Fixes |
|---|---|---|---|
| 0 — single runtime + CI | M | Low (clean excision, verified 0 cross-imports) | process root cause |
| 1 — I/O out of composition | M | Low (caching + hoisting, behaviour-preserving) | **lag** |
| 2 — navigation truth | L | **High** — touches every screen transition | **screen order, flaky bugs** |
| 3 — business rules | M | Medium — changes numbers users see | **leaking statuses** |
| 4 — decomposition | XL | Medium | maintainability |

Phase 2 is the risky one and depends on Phase 0's test gate being real. Do not start it before 0.8 is checked off.

---

## Verification note

This review is a static analysis. No build, test run, or on-device profiling was performed in producing it. Every claim is traceable to the cited `file:line`. Before acting on Phase 1, capture a baseline with StrictMode and a Compose recomposition count to confirm the predicted frequency — the mechanism is certain from the code, the magnitude is not.

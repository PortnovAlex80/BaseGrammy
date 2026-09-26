# GrammarMate Atomic Design — implementation handoff prompt

Copy the prompt below into a coding model with repository access. Attach the migration plan if the model cannot read repository documents. Assign one phase or slice at a time; the default first assignment is P0. Use the separate [review prompt](atomic-design-review.md) after implementation.

---

You are the senior Android UI engineer implementing an architected, incremental Atomic Design migration for GrammarMate (repository directory: BaseGrammy).

## Assignment

Assigned phase/slice: **P0 — establish the source and verification baseline**, unless the user explicitly assigns another phase from `docs/plans/2026-09-26-atomic-design-migration.md`.

Read the plan and `docs/architecture/decisions/007-incremental-atomic-design.md`. Treat them as the proposed architecture for this migration, subject to verified repository evidence and the user's latest instructions. Execute the assigned scope completely. For P0, produce the inventory, route map, baseline evidence and next-slice handoff; do not start runtime refactoring. For a later assigned implementation phase, make and verify the code changes rather than returning only another plan.

Do not repeat completed phases if their recorded evidence remains valid. Explain any evidence-backed deviation. Resolve routine reversible choices yourself; ask only for information that cannot be obtained from the repository or environment. If repository access is unavailable, provide an explicitly provisional assessment and identify the missing evidence; do not claim to have inspected, edited, built, or tested code.

## Product and technical context

GrammarMate is an existing Android language-learning application. It includes translation lessons, vocabulary and verb drills, auxiliary drills, daily practice, grammar stories, boss exercises, audio input/output, and flower-based learning progress. Packs, languages, session history, and progress have important scoping rules.

The baseline uses Kotlin 2.0.21, Jetpack Compose BOM 2024.10.00, Material 3, Android minSdk 24 / targetSdk 34 / compileSdk 35, YAML-based stores, and Sherpa-ONNX audio. Verify actual versions in `gradle/libs.versions.toml` and `app/build.gradle.kts`. Keep the current versions, runtime identity, stores, navigation and application module. ADR-006 removed the alternate v2 runtime; do not recreate it.

Inspect repository instructions, working-tree changes and current branch before editing. Preserve unrelated user changes. Work in a task branch rather than committing on `main`; do not push, publish, or deploy to a personal/production device unless separately requested. Installing the app and test APKs into an isolated task emulator is part of the verification scope when running instrumented tests. Prefer that emulator; target a personal device only when explicitly authorized. Use the project's applicable local workflows without inventing passing verification.

## Required source inspection

Under `app/src/main/java/com/alexpo/grammermate/`, inspect the relevant portions of:

- `ui/Theme.kt` and `ui/components/Glass.kt`.
- `ui/GrammarMateApp.kt`, `ui/screens/TrainingScreenContent.kt`, `ui/screens/TrainingScreen.kt`.
- `ui/components/SharedComponents.kt`, `UnifiedInputControlsBar.kt`, `UnifiedNavigationRow.kt`, `SessionProgressIndicator.kt`, `VoiceAutoLauncher.kt`.
- The screen/feature files and tests affected by your assigned slice.

Also read ADR-006, relevant specs, `CLAUDE.md`, `.github/workflows/ci.yml`, and current build configuration. Source and executable behavior take precedence over stale descriptions; document conflicts. In particular, the theme/component specification incorrectly describes some UI infrastructure as absent.

The verified baseline production path is `GrammarMateApp -> TrainingScreenContent -> TrainingScreen`. A source search found no production call to `TrainingCardSession` beyond its declaration. Recheck this before acting. Do not build a migration around that unused abstraction or silently connect it to the app.

## Architecture to implement

Use a hybrid Atomic Design system:

- **Foundations:** existing semantic theme colors, typography, shapes, and justified spacing/dimension/motion rules.
- **Atoms:** small product-specific controls.
- **Molecules:** small coherent combinations of controls.
- **Organisms:** feature-owned sections such as a training prompt or answer panel.
- **Templates:** layout-only slot compositions, introduced after real repeated layouts are established.
- **Screens/pages:** concrete compositions, connected to existing application behavior by route/integration adapters.

Proposed destinations are `ui/designsystem/{foundation,atoms,molecules}`, feature-owned presentation under `ui/features/<owner>/`, existing screen/route entrypoints under `ui/screens/`, and an optional `ui/patterns/` for genuinely shared feature presentation. These are demand-driven destinations, not scaffolding requirements. Do not create empty layer folders, move every file, or wrap every Material primitive. Existing business code under `feature/` must not acquire UI dependencies.

Enforce the boundaries:

1. Foundations must not import components, features, navigation, or stores. `Theme.kt` currently wraps `AppBackground`, which applies insets: preserve this behavior through a separate app-level facade when separating foundational declarations.
2. Generic atoms/molecules must not import project domain/data models, ViewModels, session contracts, services, navigation, or files. They receive display values and callbacks and may use UI resources and Compose/Material directly.
3. Feature presentation receives narrow read-only UI values. Existing route/mapping adapters may read business state and translate it. Avoid mirrored mutable state and universal interfaces that merely hide `TrainingUiState` or `CardSessionContract`.
4. Keep feature ownership explicit. Sharing requires compatible behavior, state meaning, semantics and expected evolution; similar markup or two callers alone is insufficient.
5. Templates arrange slots and layout. They must not own grading, scheduling, audio orchestration, or session progression. Direct use of a lower level is allowed; five layers are not mandatory.
6. Keep temporary compatibility wrappers only when they delegate to one implementation and have a documented removal condition.

Preserve current visual values and behavior during extraction. Tokenize repeated or meaningful design rules, not every literal. Reuse existing Material/theme policy. A visual redesign, new animations, changed accessibility geometry, dependency upgrade, or domain refactor requires its own explicit scope.

## First production pilot (P2)

Use `TrainingScreen.kt::CardPrompt`, not `TrainingCardSession`.

Extract a feature-owned `TrainingPromptCard` with display-ready label/prompt, optional situation context, text-scale policy, audio presentation and callback. Keep hint calculation, `SentenceCard` interpretation, card identity and audit logging at the integration boundary. Keep `contextRu` visible independently of hint grading. Preserve `card_prompt_text`, audio enablement and states, localized labels, callback order, and drill-specific prompt suppression. Integrate the extracted component into the existing production path.

Provide representative pure previews: light/dark, long prompt/context, increased font scale, audio states. This pilot proves presentation boundaries and parity; it does not prove speech lifecycle, answer persistence, completion or an entire training template. If the extraction only adds forwarding layers, simplify it before expanding.

## State, effects and behavior constraints

- Preserve `TrainingScreenContent`'s dedicated typed-input and Pomodoro flows. Do not fold them into the large combined state or introduce extra collectors at a broader level.
- Keep authoritative answers, selected cards, mastery and session state with their existing owners. Document changes to transient/saveable state ownership.
- Before P3/P4, inventory recognizer/permission launchers, Bluetooth setup/cleanup, auto-advance, voice auto-launch, timer, hint/cache lookup and completion effects. Record owners, triggers, keys, cancellation and callback behavior. Extract display beneath current adapters first.
- Do not change effect keys, state capture, callback ordering, pause/retry transitions or launch count as an incidental consequence of extraction. Do not add stability annotations or memoization without valid contracts.
- Preserve pack/language scoping, persistence, restore and progress rules. WORD_BANK does not grow mastery flowers.
- Preserve Verb Repeat's complete saved batch and order; Continue excludes shown/completed cards, not navigation-only visits; Reset clears saved session context while retaining learning progress.
- Preserve daily blocks, vocabulary rating/flip, boss behavior, report/hide actions, unattempted-exit semantics, and distinctions among input modes.
- Keep actual semantics and existing test tags reachable, including `card_prompt_text`, `input_field`, `check_button`, `show_answer_button`, navigation/exit tags, and Verb SessionCard's start/repeat/continue/reset tags.
- Preserve focus, IME behavior, content descriptions, effective touch targets, localized strings, current typography/app text scale, insets and supported API behavior. Record unrelated defects separately.

## Verification and evidence

Use existing tooling before adding dependencies. Preview and Compose test dependencies already exist. Use pure fixture data; previews must not require a ViewModel, filesystem, installed pack, network, or live speech engine. Previews are not a substitute for runtime behavior tests or observed visual parity.

Run meaningful focused existing tests for changed behavior, then the phase's required build/full test gates after the final relevant change. Relevant suites include `RegularLessonClickUiTest`, `VerbPracticeClickUiTest`, `VerbDrillSessionCardRegressionTest`, `VerbDrillScreenStartFreshResumeTest`, `PauseCascadeClickUiTest`, `DailyPracticeClickUiTest`, `BossBattleClickUiTest`, `PackSwitchClickUiTest`, `NavigationJourneyClickUiTest`, and Pomodoro/input-mode suites. Select by actual changed behavior and inspect their assertions. Do not add tests that only mirror implementation, disable existing tests, or replace UI clicks with direct business calls to manufacture a pass.

Current CI gates are:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest --no-parallel --max-workers=1
./gradlew :app:connectedDebugAndroidTest --max-workers=1
```

On Windows, inspect the current build guidance and verify a full JDK with `jlink`. The documented working setup uses `C:\Users\user\.jdks\corretto-21.0.8\bin\java.exe` with the classpath `gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar` and `org.gradle.wrapper.GradleWrapperMain`. Use PowerShell's call operator for a quoted executable. Older JBR advice in `java.txt` conflicts with later guidance. Do not assume another machine has these paths. Avoid simultaneous Gradle test runs in one workspace.

For affected visual surfaces, record comparisons for representative light/dark, compact/tablet, long localized text, large system font and app text scale, plus relevant disabled/loading/error/empty and keyboard states. Inspect appropriate older/newer Android paths when background or audio behavior changes. If a tool, emulator, device, screenshot baseline or preview renderer is unavailable, identify that verification gap accurately. Never label unexecuted checks as passed or claim performance gains without measurements.

## Required deliverables

For P0, create a durable inventory and baseline report with source symbols, actual callers, atomic level, owner, state/effects, semantics/tags, migration status, exact commands/results and unresolved questions. Do not change production code in P0.

For implementation phases, deliver the bounded production change, relevant previews and justified regression coverage, updated inventory/specification entries, and a concise handoff containing:

1. Assigned scope and baseline revision.
2. What changed and why, with file/symbol references and a real production caller.
3. Public API/dependency decisions and state/effect ownership changes.
4. Exact validation performed and its result; distinguish baseline failures, introduced failures, and unavailable checks.
5. Visual evidence and remaining uncertainty.
6. Compatibility wrappers and removal criteria, rollback boundary, and the next recommended slice.

Complete the assigned slice; do not expand it into an application rewrite. End by reporting achieved scope rather than declaring the entire migration complete prematurely.

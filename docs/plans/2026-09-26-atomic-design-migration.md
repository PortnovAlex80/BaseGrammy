# GrammarMate: incremental Atomic Design migration

Date: 2026-09-26. Source baseline: `c2717a075`.
Status: P0–P4 and one bounded P6 cleanup slice have been implemented; P5 and final P6 acceptance remain open. See the dated [inventory](../atomic-design/P0-baseline-inventory.md) for evidence.

Scope note (2026-09-29): this is the broader Atomic Design/UI migration backlog. The user's finite goal of modularity, isolation, clean layers and business correctness is governed by the separate [architecture closure contract](2026-09-29-architecture-closure-contract.md) and [ADR-008](../architecture/decisions/008-bounded-architecture-closure.md). The 24 P5 dispatch items are not automatically blockers for that goal.

Implementation handoff: [English implementation prompt](../prompts/atomic-design-implementation.md).
Independent review: [English review prompt](../prompts/atomic-design-review.md).
Rationale: [ADR-007](../architecture/decisions/007-incremental-atomic-design.md).

## 1. Objective and scope

Make the shipping Android UI easier to redesign, compose, preview, and maintain by establishing reusable visual foundations and well-bounded presentation components. Apply Atomic Design within Kotlin, Jetpack Compose, and Material 3. Preserve learning behavior and the observable interface during structural migration. A later visual redesign can deliberately change composition, typography, illustration, and motion in separately reviewable changes.

Success means that a repeated visual rule has one owner, production screens use the extracted components, and UI sections can be previewed without a ViewModel, installed lesson pack, filesystem, or speech engine. The number of folders, wrappers, or extracted functions is not a success metric.

Keep the existing application module, navigation, runtime identity, persistence formats, dependency versions, and session engines. ADR-006 retired the v2 runtime; this work must not recreate it. Domain/ViewModel decomposition, SRS changes, framework replacement, and a new Gradle design-system module are outside this migration.

## 2. Verified starting point

Paths below are relative to `app/src/main/java/com/alexpo/grammermate/` unless stated otherwise. Recheck callers against the implementation checkout; this document is a dated snapshot.

| Source | What exists | Migration implication |
|---|---|---|
| `ui/Theme.kt` | Material light/dark schemes, semantic colors, typography, shapes, compatibility accessors | Extend the existing system; do not create a competing palette. |
| `ui/components/Glass.kt` | Background, surfaces, outline/glow helpers; `AppBackground` also applies safe-drawing insets | Keep drawing/layout ownership explicit. A foundation must not depend upwards on a component. |
| `ui/components/SharedComponents.kt` | Navigation/audio buttons and download dialogs in one file | Split by responsibility when a slice needs them. |
| `ui/GrammarMateApp.kt` -> `ui/screens/TrainingScreenContent.kt` -> `ui/screens/TrainingScreen.kt` | Active training route and presentation | This is the pilot integration path. |
| `ui/screens/TrainingScreen.kt::CardPrompt` | Hint calculation, optional context, scaled text, TTS action, audit logging | Extract display content while preserving its existing integration boundary. |
| `ui/components/UnifiedInputControlsBar.kt` | Display mixed with contract calls, recognizer launcher, Bluetooth hooks, cache loading, hints | Characterize effect ownership before extracting its display. |
| `ui/components/UnifiedNavigationRow.kt` | Contract-derived enabled state, controls, exit confirmation | Preserve button behavior, confirmation ownership, and tags. |
| `ui/TrainingCardSession.kt` | A slot-based abstraction with session actions, local input, and filesystem access | No production invocation found in the baseline search. It is not the active template or pilot target. |
| `ui/screens/HomeScreen.kt`, `ui/VocabDrillScreen.kt`, `ui/screens/SettingsScreen.kt` | Large files with multiple UI responsibilities | Migrate coherent sections after the training pilot establishes useful boundaries. |
| `app/build.gradle.kts` | Preview/tooling and Compose test dependencies already present | Start with existing tools; no new catalog/screenshot dependency is required. |
| `.github/workflows/ci.yml` | Blocking APK, unit-test, and instrumented-test jobs | Preserve these gates. |

`docs/specification/14-theme-and-ui-components.md` contains stale claims about missing custom typography and component directories. Consult actual code and tests, record disagreements, and update the relevant specification as each area migrates. Historical architecture reports are not evidence that an old defect remains today.

## 3. Target architecture

Use a hybrid: shared visual foundations and generic controls, with feature-owned compositions. Atomic levels describe responsibility; they are not five mandatory function calls or global packages through which every element must pass.

| Level | Responsibility | GrammarMate example | Excluded responsibility |
|---|---|---|---|
| Foundations | Semantic colors, typography, shapes, useful spacing/dimension/motion rules | Existing emerald palettes and feedback colors | Screen layout, navigation, feature state |
| Atoms | Small product-specific visual controls | Styled icon action, primary action, status badge | Session commands, storage, speech lifecycle |
| Molecules | Small coherent combinations | Labeled action, audio action with status/tooltip | Owning a session or obtaining a ViewModel |
| Organisms | Meaningful feature sections | Training prompt, answer panel, session summary | Reimplementing grading, mastery, or scheduling |
| Templates | Layout and slots for proven repeated structures | A training layout with header, content, feedback, controls | Selecting cards, advancing sessions, starting audio |
| Screens/pages | Concrete composition; route adapters connect application behavior | Regular lesson, daily practice, vocabulary | Duplicating lower-level visual policy |

Foundations are an additional implementation concern beneath the five classic Atomic Design levels. Material primitives remain usable directly when a custom wrapper would add no product policy.

Possible destinations, created only when an actual extraction needs them:

```text
ui/
  Theme.kt                       # public app theme/composition facade during migration
  designsystem/
    foundation/                  # tokens and theme data, no component imports
    atoms/                       # generic product controls
    molecules/                   # generic display combinations
    preview/                     # preview configuration helpers
  features/
    training/
      model/                     # small presentation values
      mapping/                   # source state -> presentation; boundary only
      components/                # feature molecules and organisms
      templates/                 # only after repeated layout is demonstrated
    vocab/ ...                   # create only as migrated
  patterns/                      # optional cross-feature presentation, evidence required
  screens/                       # existing screens and route adapters retained incrementally
  components/                    # existing implementations/adapters until their callers migrate
```

Keep existing business helpers under `feature/` in their current role. Do not move all screens or rename public functions to make the tree look complete. Private feature compositions may remain beside their screen where that is clearer.

Dependency rules:

1. Foundation declarations depend on Kotlin/Compose types, not components, features, navigation, or stores. Separate the application background/inset wrapper from foundational token declarations if needed; preserve the public theme behavior.
2. Generic atoms and molecules depend only on foundations, lower generic UI, Material/Compose, and UI resources. They must not import project `data`, business `feature`, ViewModels, session contracts, services, files, or navigation.
3. Feature presentation components consume small read-only presentation values and callbacks. Mapping/application adapters may depend on existing business types. Keep mappings outside generic UI; do not duplicate authoritative state in another mutable store.
4. Feature components must not import another feature's UI implementation. Promote a pattern only when actual callers share behavior, state meaning, accessibility, and likely evolution. Two similar-looking callers alone are insufficient.
5. Business code must not depend on UI packages. Templates only arrange content. Screens may use any appropriate lower level directly.
6. Existing integration wrappers can remain temporarily. They must delegate to one rendering implementation, have an owner and removal condition, and must not create a second execution path.

## 4. Component API and state rules

- Prefer explicit values, small cohesive UI models, callbacks, and slots. Use `modifier: Modifier = Modifier` consistently and apply it to the documented root. Avoid a universal component configured by many unrelated booleans.
- A pure component may own transient visual state such as tooltip expansion. Durable answers, grading, progress, selected cards, and session state stay with their existing authoritative owners. Decide save/restore ownership explicitly before moving state.
- Keep recognizer/permission launchers, audio setup/cleanup, timers, navigation and file/cache access at an integration boundary. Do not merely rename an effectful component to `Molecule`.
- Preserve the separate `inputText` and Pomodoro collections in `TrainingScreenContent`; do not put high-frequency updates back into the large combined state for a tidier signature.
- Preserve callback ordering, effect keys, cancellation, and current-state capture. Do not move a `LaunchedEffect`, `remember`, or activity launcher across boundaries without a behavior check.
- Keep localized strings, content descriptions, state descriptions, focus, keyboard/IME behavior, and test tags. Preserve effective touch targets; explicitly record existing accessibility defects instead of silently changing geometry in a parity slice.
- Preserve computed typography, text-scale behavior, dimensions, animation timing, theme resolution, and insets initially. A token is justified by meaning or repetition, not merely by being a numeric literal. Feature-specific dimensions are allowed.
- Do not add `@Stable`/`@Immutable`, broad `remember`, or callback containers to silence performance concerns. Such claims must match actual mutability and capture behavior.

Example pilot contract, conceptual rather than a required API:

```text
TrainingPromptCard(
    label, displayPrompt, optionalSituationContext,
    textScale, audioPresentation, onAudioClick, modifier
)
```

The integration boundary performs the existing hint calculation, extracts `SentenceCard.contextRu`, maps TTS state, and preserves audit logging. The component renders these values. `contextRu` remains visible independently of the graded hint ladder. Audio presentation must preserve idle/initializing/speaking/error states, enabled behavior, error information, and accessibility labels.

## 5. Implementation phases

Execute one bounded phase or slice per handoff. P0 precedes P1; later work uses the evidence recorded by earlier phases. A shared file has one writer at a time.

| Phase | Work and source scope | Required output / exit evidence |
|---|---|---|
| P0 — Baseline | Trace active routes and callers; inspect theme, training components, tests, build setup | Component inventory; active/dead/uncertain classification; screenshot or explicitly unavailable visual baseline; effect/state ownership ledger; current build/test results. No structural implementation. |
| P1 — Foundations | `Theme.kt`, `Glass.kt`, values required by pilot | One source per migrated token; unchanged theme/background/inset behavior; no foundation-to-component cycle; light/dark preview fixtures. Add only useful tokens; preserve compatibility imports where needed. |
| P2 — Prompt pilot | Active `TrainingScreen.kt::CardPrompt`, audio visual control if extraction is useful | A production-used prompt organism with narrow inputs; integration retains hints/audit; tag, context, scale and audio state parity; no duplicate prompt in drill modes. Evaluate whether extraction actually improves ownership. |
| P3a — Feedback/progress | Training result display and appropriate portions of `SessionProgressIndicator.kt` | Previewable feedback/progress display; existing formulas/timer sources unchanged; focused tests pass. |
| P3b — Input/navigation | `AnswerBox`, `UnifiedInputControlsBar`, `UnifiedNavigationRow`, relevant word-bank/hint display | Effect ledger completed before movement; pure display beneath stable integration adapters; keyboard, voice, word-bank, pause, hint and exit behavior verified. Keep this separate from P3a. |
| P4 — Training compositions | Regular lesson, verb/aux drill integration, daily practice, vocabulary | Reuse components where semantics agree; independent slices per mode; layout template introduced only where repetition is proven; all mode-specific branches preserved. |
| P5 — Remaining UI | Home/pack tiles; chapter/lesson roadmaps and ladder; story reader/quiz; settings/dialogs; background vocabulary, profile and Pomodoro surfaces; root/loading/error views | A route coverage matrix accounts for every production screen, sheet, dialog and overlay. Each surface adopts the useful foundations/components or records why it remains feature-specific. |
| P6 — Consolidation | Migrated callers, obsolete adapters, catalog, UI specification | Verified obsolete wrappers removed; no abandoned duplicate implementations; dependency checks; final build/unit/instrumented results and visual review status; updated component ownership/docs. |

P2 proves presentation boundaries, token usage, semantics and previewability. It does **not** prove voice lifecycle, input preservation, completion, or an entire training template. Those remain explicit P3/P4 gates. If the pilot adds only forwarding layers, simplify to the existing folder structure before expanding.

### Initial P0 inventory

For each candidate record: source symbol, actual production callers, level, feature owner, authoritative state owner, effects, semantics/tags, proposed destination, validation, status. Start with:

- `NavIconButton`, `TtsSpeakerButton`, `InitialsAvatar`.
- `CardPrompt`, `HintAnswerCard`, `ClickableHintAnswerCard`.
- `SessionProgressIndicator`, `DifficultyRatingRow`.
- `UnifiedInputControlsBar`, `UnifiedNavigationRow`, `VoiceAutoLauncher`.
- `PackTileCard`, drill entry tiles, lesson tiles, SessionCard controls.
- Completion screens, report/info sheets, download dialogs, background/inset wrapper.

Track status as `unassessed`, `characterized`, `extracted`, `adopted`, `verified`, or `retired`. A catalog-only component is not adopted.

### Ownership ledger for P3/P4

For each voice launcher, Bluetooth setup/cleanup callback, automatic advance, hint action, input state, timer, popup/cache lookup and completion callback, record: current owner, event trigger, effect keys, lifecycle/cancellation, state read, proposed owner, and regression evidence. Detect overlapping paths before trying to consolidate them. Preserve behavior; file unrelated defects as separate work.

## 6. Behavioral invariants

Treat current implementation and relevant executable tests as evidence; investigate conflicts rather than replacing a business rule during extraction.

- Pack/language scoping, progress, persistence, restoration, and chapter completion remain unchanged.
- WORD_BANK does not grow mastery flowers; keyboard/voice retain their existing rules.
- Verb Repeat replays the saved full batch in order. Continue excludes completed/shown cards, not navigation-only visits. Reset clears saved session context while retaining learning progress.
- Check/hint completion and navigation-only visits keep their different semantics. Pausing, showing hints, retries, play/resume, next/previous, and automatic advance retain current transitions.
- Daily blocks, vocabulary rating/flip flow, boss behavior, and empty/unattempted session exits preserve their distinct semantics.
- Preserve existing tags including `card_prompt_text`, `input_field`, `check_button`, `show_answer_button`, `prev_button`, `next_button`, `pause_button`, `exit_button`, `exit_confirm_button`, `verb_start_button`, `session_card`, `repeat_button`, `continue_button`, and `reset_button`. Inspect exact visibility and merged semantics, not only string presence.
- Keep speech lifecycle and callback ordering, single intended audio action, report/hide behavior, offline behavior, safe insets, locale fallback, and supported SDK behavior.

## 7. Validation strategy

No application tests were run to author this plan. The implementation agent must establish a fresh baseline and distinguish pre-existing failures from introduced failures.

Use existing tests for behavior; add tests only for meaningful uncovered boundary risks. Click-path regressions must drive actual UI controls. Do not disable tests, weaken assertions, fabricate passing counts, or substitute direct ViewModel calls for a required UI action.

| Changed slice | Existing suites to inspect and select |
|---|---|
| Prompt / regular training | `RegularLessonClickUiTest`, relevant `VerbPracticeClickUiTest` and `BossBattleClickUiTest` cases |
| Input / navigation | `InputModeSwitchTest`, `WordBankToggleTest`, `PauseCascadeClickUiTest`, `SubmitAnswerTest`, `NavigationClickTest` |
| Verb selection / saved session | `VerbDrillSessionCardRegressionTest`, `VerbDrillScreenStartFreshResumeTest` |
| Daily / pack boundaries | `DailyPracticeClickUiTest`, `PackSwitchClickUiTest`, `PackScopedLessonIsolationTest` |
| Timer / full journey | `PomodoroBannerClickUiTest`, `PomodoroClickUiTest`, `NavigationJourneyClickUiTest`, `SmokeClickTest` |
| Final instrumented gate | Existing `ClassicAppLaunchSmokeTest` and `:app:connectedDebugAndroidTest` |

Run a focused relevant subset while iterating. At phase closure run the required build and full unit gate once after the last relevant change; broaden/repeat only for a new failure or concern. Run the instrumented gate for affected navigation/platform flows and final acceptance using an isolated task emulator, or an explicitly authorized test device. Installing app/test APKs into that emulator is part of verification; personal-device deployment is separate. An unavailable test environment is an unverified gate, not a pass.

Current CI commands:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest --no-parallel --max-workers=1
./gradlew :app:connectedDebugAndroidTest --max-workers=1
```

On this Windows workspace use a full JDK containing `jlink`. Verify the path before running; `java.txt` has contradictory older JBR advice. Current project guidance records Corretto 21.0.8 and the three-wrapper-JAR workaround:

```powershell
$atomicJava = 'C:\Users\user\.jdks\corretto-21.0.8\bin\java.exe'
$atomicClasspath = 'gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar'
& $atomicJava -cp $atomicClasspath org.gradle.wrapper.GradleWrapperMain :app:assembleDebug --no-parallel --max-workers=1
& $atomicJava -cp $atomicClasspath org.gradle.wrapper.GradleWrapperMain :app:testDebugUnitTest --no-parallel --max-workers=1
```

Append Gradle `--tests` filters for actual existing classes for focused runs. Check `app/build.gradle.kts`, `gradle/libs.versions.toml`, local JDK/SDK availability, and CI before changing commands. Do not upgrade dependencies to obtain APIs shown by newer online documentation.

Visual evidence should cover the affected states, not a combinatorial matrix of every screen: light/dark, a compact phone, representative tablet width, large system font, app text scale, long RU/EN/IT content, disabled/loading/error/empty states, and keyboard visibility where relevant. Inspect background/inset and audio behavior on appropriate API levels, including below API 31 if those paths change. Keep repeatable fixtures. Previews alone do not establish visual parity or runtime performance; report what was actually rendered or exercised.

## 8. Multi-model handoff and completion

Give an implementation model the master prompt, this plan, and one phase/slice. Defaults in the prompt select P0 only. After a slice, use the independent review prompt against its diff and evidence. Keep architecture/API changes under one integrator; parallel work is suitable only for disjoint files after shared contracts are settled. Do not run multiple Gradle builds against the same working directory concurrently.

Each handoff must record: baseline revision, completed scope, file/symbol changes, production caller, public API decisions, effects/state ownership changes, exact verification results, remaining gaps, rollback boundary, next slice. Update the inventory rather than re-auditing completed work on every model handoff.

Migration is complete only when the route coverage matrix is accounted for, adopted components have reachable production consumers and representative previews, lower layers obey dependency rules, temporary adapters are removed or explicitly justified, behavior gates pass, and visual evidence has been reviewed. Code compiling, a populated component catalog, or lower file line counts alone do not establish completion.

## References

The five-level vocabulary follows [Brad Frost's Atomic Design](https://atomicdesign.bradfrost.com/chapter-2/). Compose supports extending its existing [design system](https://developer.android.com/develop/ui/compose/designsystems/custom); the boundary guidance is consistent with [state hoisting](https://developer.android.com/develop/ui/compose/state-hoisting). Use [Compose previews](https://developer.android.com/develop/ui/compose/tooling/previews) for isolated states and preserve [accessibility semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics) during extraction. Check examples against the repository's installed Compose version.

# Migration parity audit

Updated: 2026-08-28

## Verdict

The refactoring plan is not complete. The v2 runtime passed its own implemented
scope but did not preserve the complete product surface. The complete runtime is
therefore restored as the `legacy` production variant; v2 is isolated under the
`com.alexpo.grammermate.v2preview` application ID.

## Product surface

| Journey/domain | Production (`legacy`) | v2 preview | Migration status |
|---|---|---|---|
| Lessons, sub-lessons, pause/resume | Present | Partial | parity not proven |
| Lesson/chapter roadmap | Present | Missing/partial | open |
| Boss battle and rewards | Present | Missing | open |
| Daily practice | Present | Present, different state model | parity not proven |
| Verb and vocabulary drills | Present | Present, reduced wiring | parity not proven |
| Profile, onboarding, reset | Present | Missing/partial | open |
| Backup and restore | Present | Missing | open |
| Background vocabulary service | Present | Missing | open |
| Story roadmap, reader, quiz, real voice | Present | Partial | open |
| TTS, ASR and Bluetooth routing | Present | runtime gaps remain | open |
| Settings and accessibility controls | Present | Partial | open |
| YAML to Room upgrade and rollback | YAML production store | Room preview store | data parity open |

The authoritative requirements remain in `22-use-case-registry.md`, the scenario
documents, `user-journey-models*.md`, and `legacy-archive/legacy-test-plan.md`.
The registry itself needs normalization: its declared totals do not match its
physical unique UC/AC rows and duplicate IDs are present. No requirement may be
dropped while that cleanup is performed.

## Evidence collected

- `:app:assembleLegacyDebug`: PASS; production ID `com.alexpo.grammermate`, version 1.7.
- `:app:assembleV2Debug`: PASS; preview ID `com.alexpo.grammermate.v2preview`.
- `:app:testV2DebugUnitTest`: PASS, 257 app tests (domain tests are separate).
- `:app:testLegacyDebugUnitTest`: 466 tests, 420 pass, 46 fail.
- `:app:connectedLegacyDebugAndroidTest`: PASS, 1/1 production launch smoke on API 36.
- Manual cold launch: `MainActivity` reaches onboarding without FATAL/ANR.
- Clean-install time to onboarding: approximately 100 seconds on the API 36 emulator.

## Open regression groups

| Group | Failures | Treatment |
|---|---:|---|
| Pause/lesson Compose journeys | 15 | rebase semantics and verify manually/device-side |
| Verb resume/start-fresh Compose journeys | 7 | reconcile fixture setup with current pack-scoped state |
| Daily/boss/pomodoro Compose journeys | 7 | distinguish stale assertions from product defects |
| Parser/normalizer expectations | 10 | reconcile tests with the documented import contract |
| Chapter progress | 6 | resolve `completedAtMs` versus derived-completion contract |
| Background deck next action | 1 | reproduce against real playback state |

## Release blockers

1. Move the optional bundled audio-bank installation off the first-screen critical
   path. The duplicate-copy fix is in place, but ZIP extraction still blocks for too long.
2. Reduce the 50 legacy failures to zero or document and replace each obsolete test
   with equivalent requirement-linked evidence.
3. Add black-box journeys for backup/restore, roadmap, boss, background playback,
   story quiz/audio, settings and upgrade-state integrity.
4. Normalize the UC/AC registry and generate a machine-checked trace ledger.
5. Prove YAML/Room data compatibility for any cohort that has used the v2 build.

TASK-073 may close only when every blocker above has linked, passing evidence.

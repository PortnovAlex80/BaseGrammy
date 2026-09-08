# ADR-006: Drop the v2 runtime, collapse to a single flavorless runtime

**Status:** ACCEPTED (2026-09-08)
**Supersedes:** [ADR-005](005-restore-product-runtime-with-build-variants.md)
**Source:** `docs/architecture/ARCHITECTURE_REVIEW_2026-09-08.md` (Phase 0)

## Context

ADR-005 restored the legacy runtime as the shipping product and kept the v2
rewrite as an installable `.v2preview` flavor. Between 2026-08-27 and
2026-09-08 the v2 line was audited twice: the parity work remaining to make
v2 a real replacement (audio body, pack-scoped hidden coverage, drill
journeys, FSRS gating) has no schedule, while every shipped defect traced by
the 2026-09-08 review lives in the legacy runtime — the one CI did not gate
(`ci.yml` ran the legacy suite with `continue-on-error: true` and gated v2
instead).

## Decision

1. The v2 runtime is **dropped**, not paused. Its source sets
   (`app/src/main/java/**/v2/`, `src/testV2/`, `src/androidTestV2/`), the
   `:domain` module, `app/schemas/` and the v2-only dependencies
   (Room, Hilt, DataStore, kotlinx-serialization, Material lib) are deleted.
2. The `runtime` flavor dimension is collapsed. The former `legacy` source
   set is promoted to the default source set; plain `assembleDebug` /
   `testDebugUnitTest` are the product tasks. Version identity stays with
   the shipping product: `applicationId com.alexpo.grammermate`,
   `versionCode 7`, `versionName 1.7`, `minSdk 24`, `targetSdk 34`
   (last shipped as tag `apk-12000`).
3. v2 user data is unaffected: the preview lived under its own
   `com.alexpo.grammermate.v2preview` sandbox (Room db
   `grammarmate_v2.db`, DataStore `grammarmate_app_config`); the only shared
   path is an append-only diagnostic log in public Downloads.
4. CI gates exactly one runtime and is fully blocking, including the unit
   suite. Inherited failing tests are quarantined as named `@Ignore`s
   tracked in `docs/specification/legacy-test-quarantine.md`; quarantine
   removal is required as Phases 2–3 of the 2026-09-08 plan repair them.

## Consequences

- The v2 parity program and its tracking docs are retired; ADRs 001–004
  remain as historical records of that program.
- The blocking gate now covers the shipping app; any new failure is a
  release blocker by definition.
- Subsequent refactoring phases (I/O out of composition, navigation truth,
  business-rule unification, god-object decomposition) proceed against the
  single runtime with that gate in place.

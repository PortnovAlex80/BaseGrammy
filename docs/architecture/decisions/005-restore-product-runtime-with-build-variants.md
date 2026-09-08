# 005. Restore the complete product runtime with build variants

- Status: superseded by [006-drop-v2-single-runtime](006-drop-v2-single-runtime.md) (2026-09-08)
- Date: 2026-08-28
- Decision owner: repository maintainers

## Context

The v2 runtime was made the only launchable application before it reached product
parity. Its green test suite covers the implemented v2 surface, but the shipped
legacy product still contains user journeys absent from v2: lesson roadmap, boss
battle, profile, backup/restore and onboarding, background vocabulary playback,
the complete story flow, and several audio and settings paths. Some active v2
paths also contain runtime `TODO()` implementations.

The source and regression tests of the complete product are preserved under
`app/legacy-src`. They are the exact source moved from `app/src` when v2 was
introduced, not an obsolete snapshot. Product functionality must therefore be
restored before further migration work can be called complete.

## Decision drivers

| Driver | Weight |
|---|---:|
| Restore the complete user journey immediately | 30 |
| Avoid destructive or competing state writes | 25 |
| Keep fixes and regression tests verifiable | 15 |
| Isolate the incomplete runtime from production | 15 |
| Make rollback and comparison cheap | 10 |
| Minimize build-system cost | 5 |

Scores use a 1-5 scale.

| Option | Parity | State safety | Verification | Isolation | Reversible | Cost | Weighted total |
|---|---:|---:|---:|---:|---:|---:|---:|
| Separate `:classic-app` module | 5 | 4 | 3 | 5 | 5 | 2 | 415 |
| Continue v2 route-by-route | 2 | 5 | 5 | 5 | 4 | 1 | 350 |
| `legacy`/`v2` variants in `:app` | 5 | 4 | 4 | 4 | 5 | 4 | 435 |

## Decision

Use a single Android application module with two product variants:

- `legacy` is the production runtime and owns `com.alexpo.grammermate`;
- `v2` is a canary runtime with a distinct application ID suffix;
- resources, assets, signing, SDK configuration and dependency versions remain
  shared;
- runtime source, manifests, unit tests and instrumented tests are variant-scoped;
- YAML and Room state are left intact. Neither variant may delete or silently
  overwrite the other store;
- v2 may become production only after a machine-checked requirement ledger and
  black-box journey suite demonstrate parity, including upgrade/data scenarios.

This restores the product; it does not declare the legacy architecture to be the
long-term target.

## Rejected alternatives

### Separate application module

This gives strong source isolation, but resources, assets, the Sherpa binary,
signing and release metadata would acquire two owners. Portable fixes would also
need a shared library or duplication. The extra configuration drift is not
justified while both variants are one product.

### Keep migrating only v2

This is the cleanest final architecture, but it knowingly leaves established
user journeys unavailable for an extended period. It does not satisfy the
immediate product-preservation requirement.

## Pre-mortem and mitigations

1. **Legacy no longer compiles on the upgraded toolchain.** Compile it before
   changing product defaults; make compatibility-only changes and retain its
   regression suite.
2. **CI tests the wrong runtime.** Use explicit variant task names and produce
   distinctly named APKs. The production gate must include `legacy` tasks.
3. **A user has progress only in Room.** Never downgrade or delete Room. Treat
   Room-to-production migration as a separate, tested data task before changing
   the runtime for such a cohort.
4. **Two implementations drift indefinitely.** Track each use case and
   acceptance criterion against both runtime and black-box test evidence. Remove
   legacy only when the parity gate is green.
5. **A v2 implementation writes legacy files unexpectedly.** Keep variant source
   sets disjoint and add upgrade/state-integrity tests before release.

## Red-team result

Red-team review rejected the initially preferred separate-module option because
it duplicated resource and release ownership without solving YAML/Room
compatibility. Product variants scored higher after this correction.

## Consequences

- The default generic `assembleDebug` command is intentionally replaced in
  documentation and CI by explicit variant tasks.
- Existing v2 tests remain valuable, but they no longer constitute proof of
  product parity.
- The requirements and regression documents remain authoritative and must not be
  archived or marked superseded until the parity gate is met.


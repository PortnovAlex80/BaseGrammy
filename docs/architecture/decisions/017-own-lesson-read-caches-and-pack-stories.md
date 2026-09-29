# 017. Separate lesson reads and pack stories from lesson-store mutation

- **Status:** Accepted
- **Date:** 2026-09-29
- **Decision-maker:** architecture review under ADR-009
- **Scope:** `LessonStoreImpl` read/caching responsibilities

## Context and drivers

`LessonStoreImpl` combined pack import, legacy index writes, lesson cache and CSV reads, plus chapter-story lookup. The existing `LessonStore` API must remain stable while the tracked 1212-line owner moves below the 800-line guard. Import/index operations contain nested batch writes, so moving them in the same slice would widen the persistence risk. The closure contract values business correctness and one authoritative cache owner over a mechanical file split.

| Driver | Weight |
|---|---:|
| Correctness of pack/lesson reads | 35% |
| Cohesive ownership and isolation | 30% |
| Direct testability | 15% |
| Progress toward 800 lines | 10% |
| Implementation cost and reversibility | 10% |

| Option | Correctness | Isolation | Testability | Size | Cost | Weighted / 5 |
|---|---:|---:|---:|---:|---:|---:|
| A — lesson catalog plus pack-story reader | 4 | 5 | 4 | 5 | 3 | 4.30 |
| B — lesson catalog plus legacy index writer | 3 | 4 | 4 | 4 | 3 | 3.55 |
| C — story, index and external-import fragments | 2 | 3 | 3 | 3 | 2 | 2.55 |

## Decision

Choose A. `LessonCatalog` owns both lesson and metadata caches, manifest ordering and mapping, and all full/lazy CSV read paths. `PackChapterStoryRepository` owns chapter metadata access, pack-scoped story files, language preference and asset fallback. `LessonStoreImpl` remains the public facade and retains seeding, import/delete coordination, legacy index batching and the drill-word cache invalidation call. The story reader uses `LanguageManager`'s manifest lookup and does not introduce a second manifest cache.

`invalidateLessonsCache(languageId)` now evicts every pack-scoped lesson key for that language as well as the legacy language key. Previously it left `packId:languageId` entries stale after a language-scoped mutation. A regression changes a pack CSV after a cached read and checks the next read after invalidation.

## Pre-mortem and challenge

Six months later, this choice could have failed if (1) a pack import changed manifests without evicting catalog reads, (2) the new catalog formed a second source of cache state, (3) schema-v1 file mapping or manifest order changed, or (4) story lookup read the global story-quiz directory instead of the pack directory. Mitigations: one `LessonCatalog.invalidate` is called through the existing `LessonStoreImpl` mutation boundary; the catalog owns both read caches; existing lazy/order tests exercise v1/v2 lookup; story tests exercise pack-scoped files and language preference. Asset fallback remains in the moved reader with its prior copy/null behavior; the cached copy now uses the project's atomic-file writer.

The red-team review's strongest objection was cache divergence after import/delete/reload. It also identified an existing language-only invalidation gap, which is characterized and repaired in this slice. The alternative index-owner path would touch nested batch writes and PackImporter callbacks without improving read ownership, so it is deferred.

## Consequences and decision journal

The store facade stays below the 800-line guard and retains one write path. This does not make legacy index persistence a separate owner; that may be considered only if a later bounded requirement needs it. Existing schema-v2 explicit filename differences between full and lazy reads are left for a separate characterized behavior change.

At the next review, a pack import/delete/reload should invalidate catalog and manifest reads together, `getLessons(packId, languageId)` should retain manifest order, and story reads should remain pack-scoped. Revisit if a focused cache or asset fallback regression fails, or if `LessonCatalog` gains import/write responsibilities.

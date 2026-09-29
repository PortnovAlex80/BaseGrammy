# 018. Keep the app route registry and isolate destination content

- **Status:** Accepted
- **Date:** 2026-09-29
- **Decision-maker:** architecture review under ADR-009
- **Scope:** `GrammarMateApp` composition root

## Context and drivers

`GrammarMateApp.kt` had 1075 physical lines. It mixed app-wide lifecycle and navigation with route-specific drill/story content and a Settings/Bluetooth permission overlay. The navigation graph and `NavBackHandlers` registration order are observable behavior: destination handlers, route handlers and NavController's own pop handler have different priorities. The shared `VerbDrillViewModel` must remain above the graph, while vocab/aux ViewModels must remain scoped to their destinations.

| Driver | Weight |
|---|---:|
| Navigation and lifecycle correctness | 35% |
| Cohesive ownership and isolation | 30% |
| Testability | 15% |
| Progress toward 800 lines | 10% |
| Implementation cost and reversibility | 10% |

| Option | Correctness | Isolation | Testability | Size | Cost | Weighted / 5 |
|---|---:|---:|---:|---:|---:|---:|
| A — move the entire NavHost to one route-graph file | 3 | 2 | 3 | 5 | 5 | 3.10 |
| B — split drill/story destination content and Settings overlay | 4 | 5 | 4 | 4 | 3 | 4.20 |
| C — move Settings overlay only | 4 | 4 | 3 | 1 | 4 | 3.55 |

## Decision

Choose B. `GrammarMateApp` retains `NavHost`, the route declarations and their order, app-wide state/event collectors, shared drill ViewModel, `NavBackHandlers` and `NavDialogs`. `AppDrillDestinations` owns verb, vocab and aux route content. `AppStoryDestinations` owns reader, chapter-lessons and background-vocabulary route content. `AppSettingsOverlay` owns SettingsSheet callbacks and Bluetooth permission-launcher lifecycle. Each destination receives explicit inputs; no aggregated context object or second navigation state is introduced.

The destination `viewModel<T>()`, `LaunchedEffect`, `remember` and `BackHandler` calls remain inside the same destination composition slots. The Settings overlay remains before `NavHost`; `NavBackHandlers` and `NavDialogs` remain after it. This preserves the existing callback registration order and route-level ViewModel scope.

## Pre-mortem and challenge

The change could fail if (1) a destination ViewModel becomes app-scoped, (2) BackHandler registration moves after the route handlers, (3) a remembered callback gets a new key or stale capture, (4) the permission launcher is recreated or invoked outside Settings, or (5) the split only satisfies a line count while route bodies remain mixed in one large file. The extracted destinations retain their original route slots and call sites; the shared drill ViewModel is still hoisted, and vocab/aux `viewModel<T>()` stay inside their own destination functions. The overlay remains at the same Box position and receives the same mutable dialog state. Navigation and drill click regressions cover the principal reachable paths.

The red-team review challenged the superficially simpler whole-graph option: it would create another roughly 700-line owner with every feature's route body and an overloaded parameter list. That is a route registry only by name and weakens the requested isolation. The targeted option costs more edits but keeps each feature's integration code in a named file and avoids moving the graph itself.

## Consequences and decision journal

The original file is below 800 lines; destination files remain smaller and own no business rules. Existing remembered-callback capture behavior is preserved in this move, including story-reader callbacks; changing it requires a separate behavior test. At the next review, route order, BackHandler precedence, shared drill session identity and Settings resume/permission behavior should remain stable. Revisit this choice if a navigation click test fails or a destination needs app-wide mutable state.

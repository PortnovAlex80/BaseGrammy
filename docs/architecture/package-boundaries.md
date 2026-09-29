# Production package boundaries

The shipping `:app` remains one Gradle module. `scripts/check_package_boundaries.py`
checks every production Kotlin import in CI. Its focused examples run immediately
before the scan. Preview files are excluded from the design-system rule.

| Source | Allowed direction | Reason |
|---|---|---|
| `data` | Data and platform APIs; no feature or UI imports | Persistence and models cannot depend on a caller. |
| `shared` | Data and platform APIs; no feature imports | Shared contracts and audio services remain usable by every feature. |
| `feature` | Data, shared, own package and listed feature edges; no UI, Compose or Navigation imports | Business code does not depend on its presentation. |
| `ui/designsystem/{foundation,atoms,molecules}` | UI primitives and resources; no business models, stores, ViewModels or navigation | Reusable display elements stay context-free. |
| `ui/features/<owner>` | Own UI feature and lower layers; no other feature's UI | A screen does not take a sibling's component ownership. |
| Other `ui` | Feature, data and shared integration | ViewModels and route hosts compose the application. |

The only allowed cross-feature imports are directed:

| From -> to | Why |
|---|---|
| `boss -> training` | Boss orchestration uses the active `SessionRunner` and card provider. |
| `daily -> progress` | Completed daily blocks update the shared streak. |
| `daily -> training` | Daily answers use the common answer validator. |
| `training -> progress` | Session card selection and completion use the progress tracker and streak. |
| `vocab -> training` | Vocabulary answers use the common answer validator. |

`TrainingStateAccess` lives in `shared`; the ViewModel supplies its one implementation.
`AudioCoordinator` accepts the `StoryClipResolver` port from `shared.audio`;
the background-vocabulary feature supplies the file-based implementation at app
composition. This keeps the shared audio layer independent of that feature.

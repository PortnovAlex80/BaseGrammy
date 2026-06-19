# Premium Dark / Emerald — UI Redesign

- **Date:** 2026-06-19
- **Branch:** `feature/premium-dark-ui` (created from `38b158c`, same HEAD as the APK currently installed on the test device)
- **Stakeholder goal:** a more stylish, modern graphical design, kept in a separate branch so it can be compared against the current interface side-by-side on the device.
- **Scope:** visual layer only. No behavior, data, or navigation-logic changes.

## 1. Direction (decided)

| Question | Decision |
|----------|----------|
| Visual direction | **Premium Dark** — deep glass cards, gradients, soft shadows, "expensive" look |
| Accent color | **Emerald** (`#34D6B4`) — preserves the green spirit of the flower metaphor, deeper & higher-contrast on dark |
| Coverage | **All screens** — full result in one branch |
| Depth | **Theme + component restyling** — structure stays, look changes |

## 2. Design Principles

1. **Glassmorphism cards** — translucent `surface` over a gradient background, soft outline, soft drop shadow, 20dp corner radius. Concretely: container color = `surface` at **alpha 0.72**, outline = `outline` at alpha 0.35, shadow = 12dp blur / 0.18 alpha / 4dp y-offset. Effect of "frosted glass" floating over depth.
2. **Depth via background** — app background is a subtle vertical gradient with a faint emerald radial glow at the top, so glass cards read as layered, not flat black.
3. **One accent** — emerald is the single brand color for primary actions, active states, and progress. Correct/incorrect/SRS keep their semantic hues but are retuned for dark.
4. **Generous rounding** — 20dp cards, 16dp buttons/tiles, 28dp sheets. Single `Shapes` object, no ad-hoc per-call radii.
5. **Soft motion** — `AnimatedVisibility` (fade + slight slide) on card/section entry; gentle transitions on flower-stage changes. No jarring pops.
6. **Behavior-preserving** — no callbacks, state, or logic change. Only `Modifier`/color/shape/typography. Existing UI tests (which assert semantics) must pass unchanged.

## 3. Color Tokens

All tokens live in `Theme.kt`. The dark scheme is primary; light scheme is refreshed but secondary.

### Dark scheme (primary)

| Token | Hex | Use |
|-------|-----|-----|
| `background` | `#0E1512` | app background base |
| `surface` | `#15201B` | cards (base layer) |
| `surfaceVariant` | `#1E2B25` | raised tiles, secondary cards |
| `surfaceElevated` *(new)* | `#243329` @ ~12% alpha + blur | glass elements |
| `primary` | `#34D6B4` | buttons, active, progress |
| `onPrimary` | `#042822` | text on primary |
| `primaryContainer` | `#0B4A3C` | highlighted blocks |
| `onPrimaryContainer` | `#7FEFD8` | text on container |
| `secondary` | `#5E8B7E` | secondary accents |
| `onSecondary` | `#04130F` | text on secondary |
| `outline` | `#2A3A33` | borders |
| `outlineVariant` | `#1C2924` | subtle borders |
| `onBackground` | `#E8EFEB` | primary text |
| `onSurface` | `#E8EFEB` | primary text on surface |
| `onSurfaceVariant` | `#9DB0A6` | secondary text |
| `error` | `#EF5350` | errors |
| `errorContainer` | `#3A1B1B` | error glass |
| `onErrorContainer` | `#FFB4AB` | error text |

### Light scheme (refreshed)

| Token | Hex | Use |
|-------|-----|-----|
| `background` | `#F4F1EC` | warm off-white |
| `surface` | `#FFFFFF` | cards |
| `surfaceVariant` | `#E8E4DD` | raised tiles |
| `primary` | `#0F7C66` | deeper emerald (readable on light) |
| `onPrimary` | `#FFFFFF` | text on primary |
| `primaryContainer` | `#CFEFE5` | highlighted blocks |
| `onPrimaryContainer` | `#00382B` | text on container |
| `secondary` | `#5E8B7E` | secondary |
| `outline` | `#C9C2B6` | borders |
| `onSurface` | `#1B2420` | primary text |
| `onSurfaceVariant` | `#5A6B63` | secondary text |

### `surfaceElevated` (custom glass token)

Standard M3 `ColorScheme` has no `surfaceElevated`. We add it as a **new field on `GrammarMateColors`** (the app's custom semantic palette, not on `ColorScheme`). `glassSurface()` reads it via `LocalGrammarMateColors.current.surfaceElevated`. This is the **one** structural addition to `GrammarMateColors`; all existing fields keep their names.

### Semantic `GrammarMateColors` (retuned for dark)

Existing token names are **unchanged** (so imports compile and tests pass); only values change. Dark variants get muted glass backgrounds + brighter foregrounds. One new field (`surfaceElevated`) is added. See §6 for the component mapping.

## 4. Shapes & Typography (new)

The app currently uses Material 3 defaults (no `Shapes`, no `Typography` defined). We add both.

### `GrammarMateShapes`

| Token | Radius | Used by |
|-------|--------|---------|
| `extraSmall` | 8dp | chips, small badges |
| `small` | 12dp | small buttons, inner elements |
| `medium` | 16dp | buttons, entry tiles |
| `large` | 20dp | cards |
| `extraLarge` | 28dp | modal sheets, dialogs |

### `GrammarMateTypography`

Built on Material 3 type scale, retuned:
- `headlineMedium/Small`, `titleLarge/Medium` → `FontWeight.Bold`, `letterSpacing = -0.5.sp` (tighter, more modern).
- `bodyLarge/Medium` → `FontWeight.Medium` (better legibility on dark).
- Numeric progress/mastery → `SemiBold`, accent-tinted at call site.

No custom font files (stays on Roboto to avoid asset/bundle churn). Pure weight/tracking changes.

## 5. Glass & Background Helpers (new)

New file `ui/components/Glass.kt`:

- `Modifier.glassSurface(...)` — composable modifier: semi-transparent container color + soft shadow + thin outline + optional `Modifier.blur` backdrop (guarded by API ≥ 31 = Android 12, where render-effect blur exists; older APIs fall back to a solid elevated surface so the look degrades gracefully).
- `Modifier.emeraldGlow()` — soft radial glow for active/primary elements (uses `Brush.radialGradient` drawn in a `drawBehind`).
- `AppBackground()` — full-screen composable painting the gradient + top radial glow, placed once per screen via a shared `Scaffold`/`Box`.

**API guard:** render-effect blur (`RenderEffect.createBlurEffect`) requires API 31+. Below that, `glassSurface` renders as an opaque elevated surface (still on-brand, just no blur). No crash on the device's API level.

## 6. Component Restyling

All restyling happens in shared components; screens inherit most changes via `MaterialTheme`.

| Component | File | Change |
|-----------|------|--------|
| `Button` / `OutlinedButton` | (call sites) | primary = emerald + faint inner glow, height 52dp; outlined = glass (translucent + outline). Applied via a small wrapper or consistent modifier at call sites. |
| `LessonTile` | `HomeScreen.kt` | glass card; flower emoji on a soft stage-tinted glow (seed = muted, sprout = soft emerald, bloom = bright). Current/active lesson gets an emerald glow border. |
| `PackTileCard` | `HomeScreen.kt` | glass + thin glow progress bar. |
| `VerbDrill/VocabDrill/Daily/BgVocab` entry tiles | `HomeScreen.kt` | unified glass style + leading icon in a round emerald badge. |
| `LinearProgressIndicator` | (call sites) | 6dp height, rounded caps, glow on filled portion (custom `drawWithCache` or `Brush.linearGradient`). |
| Streak 🔥 indicator | `HomeScreen.kt` | preserved; sits on dark with soft orange glow. |
| Home top bar | `HomeScreen.kt` | avatar + name + streak left; language/pomodoro/settings right — all on a glass row. |
| Sheets (`*Sheet`, `*Dialog`) | `ui/components/` | 28dp top corners, glass surface, handle bar restyled. |
| `TrainingScreen` | `TrainingScreen.kt` | sentence card → glass; answer feedback retuned; progress indicator glow. |
| `StoryRoadmap / ChapterLessons / StoryReader` | respective | glass chapter cards; reader uses warm-dark surface for reading comfort. |
| `VocabDrillScreen / VerbDrillScreen` | respective | SRS buttons on glass; correct/incorrect backgrounds → glass-tinted semantic. |
| `SettingsScreen / BackgroundVocabScreen` | respective | grouped glass sections; destructive red retuned. |

## 7. Screens in Scope (all)

HomeScreen (incl. ZeroStateLanguageSelector), TrainingScreen, GrammarStoryRoadmapScreen, LessonRoadmapScreen, ChapterLessonsScreen, StoryReaderScreen, VocabDrillScreen, VerbDrillScreen, SettingsScreen, BackgroundVocabScreen, DailyPracticeScreen, PomodoroSummary, and all shared components/sheets/dialogs.

## 8. Migration & Backward Compatibility

- **Semantic tokens unchanged:** `CorrectGreen`, `IncorrectRed`, `SrsAgainBackground`, `BossGold`, etc. keep their **names** and the top-level `val` accessors in `Theme.kt`. Only their **values** change (retuned for dark). Every screen imports them by name — zero import churn.
- **`GrammarMateColors` data class:** existing fields unchanged in name; new values in `DarkGrammarMateColors` / `LightGrammarMateColors`. One new field added: `surfaceElevated` (the glass token). All existing top-level accessor `val`s remain.
- **`GrammarMateTheme` signature:** unchanged (`themeMode` param stays). Dark is now the richer scheme.
- **Theme mode:** the app already supports LIGHT/DARK/SYSTEM via `ThemeMode`. We do not force dark — we make dark the polished default. SYSTEM/LIGHT still work; light is refreshed per §3.
- **No new dependencies:** uses only `androidx.compose.*` already in the project (foundation, material3, animation). No new libraries (avoiding `accompanist`-style blur libs to keep build simple on the Windows Gradle workaround).
- **Tests:** existing tests assert semantics (testTags, text) and color *names* where used — none assert raw hex of theme tokens. They must pass unchanged. The color-scheme migration tests (if any check specific semantic mappings) will be re-pointed to the new values.

## 9. Comparison Strategy (the original ask)

1. Branch `feature/premium-dark-ui` created from the same commit as the currently-installed APK.
2. Implement theme + components + screens (per the implementation plan).
3. Build debug APK (`assembleDebug` → `app/build/outputs/apk/debug/grammermate.apk`), install on the connected Samsung device via `adb install -r`.
4. User compares the current installed build vs the new one, directly on-device.
5. Engineer also captures before/after screenshots into `bg_vocab_scratch/` (gitignored) for reference.
6. Decision: merge, revise, or keep as experiment.

## 10. Out of Scope (YAGNI)

- Custom font files (stay Roboto).
- New screens or navigation changes.
- Logic/behavior/state changes.
- Re-illustrating the flower metaphor (emoji stays).
- Animated illustrations / Lottie.
- New iconography libraries (stay Material icons).

## 11. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Blur unavailable on older API → broken look | `glassSurface` falls back to opaque elevated surface below API 31; degrades gracefully. |
| Hard-to-read text on glass | enforce `onSurface`/`onSurfaceVariant` contrast; glass alpha tuned so text stays AA-contrast. |
| Restyle churn across many files → merge conflicts / breakage | centralize in `Glass.kt` + theme tokens; screens call helpers, not raw hex. Build checkpoint after each layer. |
| Existing tests assert old colors | verified tokens are accessed by name; will run full test suite as checkpoint. |
| Build on Windows Gradle workaround | use the 3-JAR classpath per `CLAUDE.md`; build via subagent per project delegation rules. |

## 12. Acceptance Criteria

1. App builds (`assembleDebug`) on the Windows Gradle workaround.
2. App installs and launches on the Samsung device without crash.
3. Dark mode shows the new glass/emerald design; light mode shows the refreshed scheme; SYSTEM follows device.
4. Every screen in §7 renders with the new theme (no leftover hardcoded light-pastel hex causing white-on-white).
5. Existing UI tests pass (run focused + full suite as checkpoint).
6. Before/after screenshots captured in `bg_vocab_scratch/` for comparison.

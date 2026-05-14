# TASK-010: Theme Mode Switching (Light/Dark/System)

**Status:** OPEN
**Created:** 2026-05-15
**Branch:** feature/theme-switch (from main)
**Spec:** 14-theme-and-ui-components.md#14.7
**UC:** UC-66

---

## Problem

The app has dark and light color schemes defined in Theme.kt but no way for the user to choose between them. The theme follows the system setting automatically, but users want manual control. Some users prefer dark mode always, others want light always. The setting needs to persist across restarts.

## Changes

### Fix 1: Add ThemeMode enum and persist in AppConfigStore
**UC:** UC-66.1, UC-66.5 | **Spec:** 14-theme-and-ui-components.md#14.7

1. Create ThemeMode enum: LIGHT, DARK, SYSTEM (default)
2. Add themeMode field to AppConfig data class with default SYSTEM
3. Update AppConfigStore to read/write themeMode
4. Update SettingsActionHandler with setThemeMode(mode) method

**Files:**
- `data/Models.kt` or new file -- ThemeMode enum
- `data/AppConfigStore.kt` -- add themeMode field
- `shared/SettingsActionHandler.kt` -- add setThemeMode()

### Fix 2: Update GrammarMateTheme to accept themeMode
**UC:** UC-66.2, UC-66.3, UC-66.4, UC-66.6 | **Spec:** 14-theme-and-ui-components.md#14.7

1. GrammarMateTheme accepts themeMode parameter
2. When SYSTEM -> use isSystemInDarkTheme()
3. When LIGHT -> always LightColors
4. When DARK -> always DarkColors
5. Pass themeMode from TrainingViewModel state -> GrammarMateApp -> GrammarMateTheme

**Files:**
- `ui/Theme.kt` -- accept and use themeMode
- `ui/GrammarMateApp.kt` -- pass themeMode to GrammarMateTheme
- `ui/TrainingViewModel.kt` -- expose themeMode in state

### Fix 3: Add theme toggle to Settings screen
**UC:** UC-66.1, UC-66.6 | **Spec:** 14-theme-and-ui-components.md#14.7

1. Add "Appearance" section in Settings
2. Add 3-option selector (Light / Dark / System) using segmented button or similar
3. On selection -> calls settingsHandler.setThemeMode(mode) -> state updates -> theme applies immediately

**Files:**
- `ui/screens/SettingsScreen.kt` -- add theme selector (SS-46, SS-47)

### Fix 4: Convert hardcoded semantic colors to theme-aware
**Spec:** 14-theme-and-ui-components.md#14.7.5

1. Replace hardcoded green colors (drill background, tense label, progress bar) with theme-aware alternatives
2. Replace hardcoded red (#B00020) for destructive buttons with MaterialTheme.colorScheme.error
3. Keep boss trophy colors and speed indicator colors as-is (thematic)

**Files:**
- `ui/screens/TrainingScreen.kt` -- drill background, tense label
- `ui/components/SharedComponents.kt` -- DrillProgressRow colors
- `ui/screens/SettingsScreen.kt` -- destructive button colors

## Verification Checklist
1. Settings shows Appearance section with Light/Dark/System options
2. Selecting Light -> app uses light theme immediately
3. Selecting Dark -> app uses dark theme immediately
4. Selecting System -> follows device dark/light setting
5. Theme preference persists after app restart
6. Build: assembleDebug passes

## Scope Boundaries
**Do NOT touch:**
- Learning content language selector (separate feature)
- String resources or localization
- Any screen layouts (only add settings row)

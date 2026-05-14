# TASK-011: Interface Language Switching (English/Russian)

**Status:** DONE
**Created:** 2026-05-15
**Completed:** 2026-05-15
**Branch:** feature/arch-feature-migration
**Spec:** 14-theme-and-ui-components.md#14.8
**UC:** UC-67

---

## Problem

The app has English and Russian string resources but no way for the user to switch the interface language. The UI language follows the system locale, but users learning Russian may want English UI, and Russian-speaking users may want Russian UI regardless of learning content language.

## Changes

### Fix 1: Add uiLanguage preference to AppConfigStore
**UC:** UC-67.2, UC-67.4 | **Spec:** 14-theme-and-ui-components.md#14.8

1. Add uiLanguage field to AppConfig (String, default "system")
2. Update AppConfigStore to read/write uiLanguage
3. Values: "system", "en", "ru"

**Files:**
- `data/AppConfigStore.kt` -- add uiLanguage field
- `shared/SettingsActionHandler.kt` -- add setUiLanguage()

### Fix 2: Implement locale switching with AppCompatDelegate
**UC:** UC-67.3, UC-67.5 | **Spec:** 14-theme-and-ui-components.md#14.8

1. Use AppCompatDelegate.setApplicationLocales() with LocaleListCompat
2. When "system" -> clear override, follow system locale
3. When "en" -> set locale to English
4. When "ru" -> set locale to Russian
5. Apply locale in SettingsActionHandler or at app startup
6. Store locale using AndroidX per-app language API (automatic persistence)

**Files:**
- `shared/SettingsActionHandler.kt` -- apply locale change
- `ui/MainActivity.kt` or `ui/AppRoot.kt` -- ensure locale is applied at startup

### Fix 3: Add language toggle to Settings screen
**UC:** UC-67.1, UC-67.4 | **Spec:** 14-theme-and-ui-components.md#14.8

1. Add "Interface language" option in Settings (distinct from learning content language)
2. Options: System / English / Русский
3. On selection -> calls settingsHandler.setUiLanguage(code) -> activity recreates with new locale

**Files:**
- `ui/screens/SettingsScreen.kt` -- add language selector (SS-48)

## Verification Checklist
1. Settings shows Interface language option (separate from learning language)
2. Selecting English -> UI switches to English strings immediately
3. Selecting Русский -> UI switches to Russian strings immediately
4. Selecting System -> follows device language setting
5. Language preference persists after app restart
6. Learning content language is NOT affected by UI language change
7. Build: assembleDebug passes

## Scope Boundaries
**Do NOT touch:**
- Learning content language system (LessonStore, languageId)
- Theme or dark mode settings
- Italian translations (values-it/) -- out of scope
- Any screen layouts (only add settings row)

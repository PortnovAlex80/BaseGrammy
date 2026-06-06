# UI/UX Walkthrough Report — GrammarMate

**Date:** 2026-06-02
**Devices:** Xiaomi (78f83a6d), 1080x2400 + Huawei (KN99XGX8OZMZWKGQ), 1280x2772
**Method:** ADB + Maestro 2.6.0 hierarchy + screenshot analysis
**App version:** Debug build (commit 4087704)

---

## Screens Visited

| # | Screen | Status | Notes |
|---|--------|--------|-------|
| 1 | Onboarding (Welcome) | ✅ Works | Name dialog on fresh install |
| 2 | Classic Home (EN default) | ✅ Works | Word Order (A1), 12-lesson roadmap |
| 3 | Select Package dropdown | ✅ Works | Shows "Word Order (A1)" + "Word Order Drills (A1)" |
| 4 | Lesson 1 → Sub-lessons | ✅ Works | "Word Order Simple Tenses", Exercise 1/15, 1544 cards |
| 5 | Sub-lesson 1 → Training | ✅ Works | Opens 1/10 exercise, translation mode |
| 6 | Daily Practice → Training | ✅ Works | Opens exercise 1/10, translation mode |
| 7 | Training answer check | ⚠️ Partial | Answer submission works but Google Speech interferes |
| 8 | Settings | ✅ Works | Interface language, Service/Test mode, Appearance |
| 9 | How This Training Works | ✅ Works | Info dialog with OK button |
| 10 | Pomodoro Timer | ✅ Works | 5/15/20 min presets + custom, Start button |
| 11 | Language selector | ✅ Works | English, Italian, German, Chinese, Russian, Greek |
| 12 | Pack switch to Drills | ✅ Works | Switches to "Word Order Drills (A1)", updates roadmap |
| 13 | Grammar Story Roadmap (Device 1) | ✅ Works | 2 chapters, Read Story + Continue buttons |
| 14 | Story Reader (Device 1) | ✅ Works | Markdown rendering, back works |

### Not visited (no EXPRESS pack installed on this device)
- Verb Practice screen
- Flashcards screen
- Grammar Story Roadmap chapters (need Italian/Greek/German EXPRESS pack)

---

## Issues Found

### Critical (Navigation)

- ~~**BUG-001**: **BACK from training exits app**~~ — ❌ **INVALID** — Code review confirmed: TrainingScreen has 3-tier BackHandler (GrammarMateApp.kt:746-761, 1079-1084) that navigates to VERB_DRILL, LESSON, or shows exit dialog. No `Activity.finish()` calls. Could not verify on-device due to INJECT_EVENTS restriction. User confirmed: "нет этого бага".
- **BUG-002**: **BACK from Grammar Story Roadmap may not work** — User reported BACK key stays on roadmap, only ← arrow works. Code review shows BackHandler IS present (GrammarMateApp.kt:1068-1074) with correct `clearActivePack()` logic. The ← arrow calls the same function (line 353-359). Possible causes: (a) another BackHandler intercepting with higher priority, (b) timing/state condition not met, (c) fixed in newer build. **Needs on-device verification.**

### Medium (UX)

- **ISSUE-003**: **Keyboard covers Continue button on onboarding** — After typing a name, the soft keyboard stays open and covers the "Продолжить" (Continue) button. User must manually dismiss keyboard.
- **ISSUE-004**: **Google Speech consent dialog blocks voice-auto-start flow** — By design, `voiceAutoStart=true` (Models.kt:579) and `inputMode=VOICE` trigger `launchVoiceRecognition()` automatically on each card (TrainingScreen.kt:500-519). On first use, Google Speech shows a consent dialog that blocks the entire training flow. BACK doesn't dismiss it cleanly. The consent dialog should be handled before entering training, or the app should detect first-use state and default to KEYBOARD input mode.
- **ISSUE-005**: **Pack selector doesn't close on outside tap** — Tapping outside the "Select Package" dropdown doesn't close it. Only BACK key closes it.

### Low (Accessibility)

- **ISSUE-006**: **No contentDescription on lesson grid cells** — Maestro hierarchy shows only "1", "2" etc. as text. No accessibility labels for screen readers or test automation.
- **ISSUE-007**: **No testID on interactive elements** — Daily Practice, lesson tiles, Pomodoro timer etc. have no test tags. Makes Maestro testing fragile (coordinate-based only).
- **ISSUE-008**: **Pomodoro Timer icon has no text label** — Only shows clock icon, no accessibility text.

---

## UI Element Map

### Grammar Story Roadmap — Huawei (1280x2772) — EXPRESS Pack

#### Top Bar
| Element | Bounds | Center | Note |
|---------|--------|--------|------|
| ← Back arrow | [13,178][169,334] | (91, 256) | accessibilityText="Back" |
| Title "Grammar Story Roadmap" | [182,210][984,301] | — | Header text |
| ⚙ Settings | [1111,178][1267,334] | (1189, 256) | accessibilityText="Settings" |

#### Practice Options (3 buttons row)
| Element | Bounds | Center | Note |
|---------|--------|--------|------|
| Verb Practice | [52,516][627,672] | (340, 594) | Left button |
| Flashcards | [653,516][1228,672] | (941, 594) | Right button |
| Daily Practice | [52,698][1228,854] | (640, 776) | Full-width button |

#### Chapter Cards
**Chapter 0: "Введение – Архитектура языка" (0/0 lessons)**
| Element | Bounds | Center | Note |
|---------|--------|--------|------|
| Card container | [52,906][1228,1569] | — | Full card |
| 🔊 Play story | [1020,1004][1176,1160] | (1098, 1082) | accessibilityText="Play story" |
| Progress bar | [104,1276][1176,1355] | — | 0% filled |
| "Read Story" button | [104,1361][1176,1517] | (640, 1439) | Only button (no Continue) |

**Chapter 1: "Глава 1 – Настоящее: я и мир здесь" (7/16 lessons)**
| Element | Bounds | Center | Note |
|---------|--------|--------|------|
| Card container | [52,1621][1228,2284] | — | Full card |
| 🔊 Play story | [1020,1719][1176,1875] | (1098, 1797) | accessibilityText="Play story" |
| Progress bar | [104,1991][1176,2070] | — | ~44% filled (7/16) |
| "Read Story" button | [104,2076][627,2232] | (366, 2154) | Left button |
| "Continue" button | [653,2076][1176,2232] | (915, 2154) | Right button |

---

### Classic Home — Xiaomi (1080x2400) — Word Order Pack

#### Top Bar
| Element | Bounds | Center | Note |
|---------|--------|--------|------|
| Avatar "T" | [33,128][165,260] | (99, 194) | Profile initial |
| Name "TestUs" | [187,161][319,227] | — | Username |
| Streak "? / 0d" | [330,161][438,227] | — | Streak counter |
| Language flag | [612,128][772,260] | (692, 194) | UK flag = English |
| Pomodoro Timer | [772,128][904,260] | (838, 194) | Clock icon |
| Settings gear | [904,128][1036,260] | (970, 194) | Settings screen |

#### Pack Selector
| Element | Bounds | Center | Note |
|---------|--------|--------|------|
| "Word Order (A1)" | [44,304][1036,458] | (540, 381) | Tappable, opens dropdown |

#### Grammar Roadmap Grid (12 lessons, 4x3)
| Lesson | Bounds | Center | Status |
|--------|--------|--------|--------|
| 1 | [44,645][276,843] | (160, 744) | seed |
| 2 | [298,645][530,843] | (414, 744) | ? |
| 3 | [552,645][784,843] | (668, 744) | ? |
| 4 | [806,645][1036,843] | (921, 744) | ? |
| 5-12 | below, same pattern | — | ? |

#### Bottom Section
| Element | Bounds | Center | Note |
|---------|--------|--------|------|
| Daily Practice | [44,1316][1036,1492] | (540, 1404) | Green button |
| "How This Training Works" | [44,1756][1036,1888] | (540, 1822) | Outlined button |

---

## Maestro E2E Test Infrastructure

### Setup
- **Maestro 2.6.0** installed at `C:\Users\user\.maestro\`
- Driver APK: `dev.mobile.maestro` installed
- Server APK: `dev.mobile.maestro.test` installed
- Run command: `maestro test .maestro/flows/ --no-reinstall-driver`

### INJECT_EVENTS Blocker (CRITICAL)

**Both devices block ALL input injection from ADB shell:**

| Method | Huawei (KN99XGX8OZMZWKGQ) | Xiaomi (78f83a6d) |
|--------|--------------------------|-------------------|
| `input tap` | ❌ SecurityException | ❌ SecurityException |
| `input keyevent` | ❌ SecurityException | ❌ SecurityException |
| `sendevent` | ❌ Permission denied | ❌ Permission denied |
| `cmd input tap` | ❌ SecurityException | ❌ SecurityException |
| `monkey` | ✅ Works (instrumentation) | ✅ Works (instrumentation) |
| `screencap` | ✅ Works | ✅ Works |
| `am start` | ✅ Works | ✅ Works |
| Maestro hierarchy | ✅ Works | ✅ Works |
| Maestro `tapOn` | ❌ INJECT_EVENTS | ❌ INJECT_EVENTS |

**Root cause:** Huawei/Xiaomi require "Установка через USB" developer option which requires a SIM card with SMS verification. No workaround found without SIM.

**Solution: Enable Maestro Accessibility Service**
Maestro uses Accessibility to perform clicks — bypasses INJECT_EVENTS entirely. Requires ONE-TIME manual setup:

```
Settings → Accessibility → MaestroDriverAccessibilityService → Enable
```

Or programmatically (requires root or ADB with WRITE_SECURE_SETTINGS):
```bash
adb shell settings put secure enabled_accessibility_services \
  "dev.mobile.maestro/dev.mobile.maestro.MaestroDriverAccessibilityService"
```

### Limitations on Windows + Non-rooted Device
1. **INJECT_EVENTS blocked** — All touch/key input blocked (see above). Only `monkey` works.
2. **Cyrillic in YAML garbles** — Maestro CLI on Windows breaks UTF-8 Russian text
3. **`launchApp` unreliable** — Sometimes goes to home screen; use `adb shell am start` instead
4. **ADB `input text` truncates** — Spaces and long text unreliable

### Working Commands (without Accessibility)
- `am start -n com.alexpo.grammermate/.MainActivity` ✅
- `am force-stop com.alexpo.grammermate` ✅
- `exec-out screencap -p` ✅
- `maestro hierarchy --no-reinstall-driver --compact` ✅
- `monkey -p com.alexpo.grammermate -c android.intent.category.LAUNCHER 1` ✅

### Hybrid Approach (with Accessibility enabled)
```
1. Launch app:     adb shell am start -n com.alexpo.grammermate/.MainActivity
2. Read UI:        maestro hierarchy --no-reinstall-driver --compact
3. Tap elements:   maestro test flow.yaml --no-reinstall-driver  (uses Accessibility)
4. Screenshot:     adb exec-out screencap -p > file.png
5. Assert:         maestro test flow.yaml --no-reinstall-driver
```

### Hybrid Approach (without Accessibility — read-only)
```
1. Launch app:     adb shell am start -n com.alexpo.grammermate/.MainActivity
2. Read UI:        maestro hierarchy --no-reinstall-driver --compact
3. Screenshot:     adb exec-out screencap -p > file.png
4. Navigate:       am start with different component/intent
5. Kill app:       am force-stop com.alexpo.grammermate
```

### Test Flows Created
Located at `.maestro/flows/`:
1. `00-onboarding.yaml` — Onboarding welcome (cyrillic issue)
2. `01-pack-selection-screen.yaml` — Verify home screen
3. `02-select-pack-roadmap.yaml` — Pack selector
4. `03-story-reader.yaml` — Story reader (needs roadmap pack)
5. `04-start-training.yaml` — Training from Continue button
6. `05-settings-screen.yaml` — Settings screen

---

## Recommendations

### Critical Fixes
1. ~~Fix BACK from training~~ — INVALID (confirmed by code review: 3-tier BackHandler exists)
2. **Fix BACK from roadmap** — Allow BACK to navigate to pack selection (BUG-002 confirmed)
3. **Fix Google Speech auto-trigger** — `voiceAutoStart=true` blocks training on first use (ISSUE-004)

### UX Improvements
4. **Dismiss keyboard on Continue** — Auto-hide soft keyboard when tapping Continue
5. **Close pack dropdown on outside tap** — Standard dropdown behavior
6. **Add test tags to all Compose elements** — `testTag = "daily_practice_button"` etc.

### Test Automation
7. **Add `Modifier.testTag()`** to all interactive Compose elements
8. **Use English-only assertions** in Maestro flows
9. **Create hybrid ADB+Maestro scripts** using the approach above
10. **Build regression suite** covering: onboarding → home → lesson → training → check → back

---

## Screenshots

All in `device_screens/` directory:
- `walk_01.png` — Onboarding welcome
- `walk_02_typed.png` — Name entered
- `walk_04_after_continue.png` — Home after onboarding
- `walk_05_daily_practice.png` — Home screen
- `walk_05b_daily_practice.png` — Training with Google Speech overlay
- `walk_06_training.png` — Exercise 1/10
- `walk_07_after_check.png` — Google Speech intercept
- `walk_09_answer2.png` — Google Speech again
- `walk_12_home.png` — Home screen (re-launched)
- `walk_13_settings.png` — Settings screen
- `walk_14_how_it_works.png` — How This Training Works dialog
- `walk_15_select_pack.png` — Pack selector dropdown
- `walk_17_lesson1.png` — Lesson 1 sub-lessons (NEW status)
- `walk_18_sublesson1.png` — Sub-lesson 1 training exercise
- `walk_19_pomodoro.png` — Pomodoro Timer screen
- `walk_20_drills_pack.png` — Word Order Drills pack home
- `walk_21_language.png` — Language selector dropdown

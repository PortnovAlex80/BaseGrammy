# TASK-008: Share Translation via QR Code

**Status:** OPEN
**Created:** 2026-05-15
**Branch:** feature/qr-share (from feature/perf-and-cursor-fixes)
**Spec:** 12-training-card-session.md#12.8.2, 23-screen-elements.md (SH-01, SH-07)
**UC:** UC-65
**Scenario:** N/A (new feature)

---

## Problem

Users sometimes doubt whether a translation is correct. Currently, the SharedReportSheet allows flagging bad sentences, but there's no quick way to check a translation externally. The user wants to verify translations on their phone by scanning a QR code with another device's camera and pasting the text into a translator app.

## Changes

### Fix 1: Add QR generation library dependency
**UC:** UC-65 | **Spec:** 12-training-card-session.md#12.8.2

Add a pure-Kotlin QR code generation library to the project. Recommended: `io.github.alexzhirkevich:qrose` (lightweight, no native dependencies, Compose-friendly) or similar.

**Files:**
- `app/build.gradle.kts` — add dependency

**Verification:** Project syncs and builds with new dependency.

### Fix 2: Create QrShareDialog composable
**UC:** UC-65.1, UC-65.2, UC-65.3, UC-65.4, UC-65.5 | **Spec:** 23-screen-elements.md (SH-07)

Create a new dialog composable that:
1. Shows translation pair text at the top: "RU: {prompt}" / "{LANG}: {answer}"
2. Shows a QR code encoding the same text
3. Shows an "Open in Google Translate" button that opens `https://translate.google.com/?sl=ru&tl={targetLang}&text={encodedPrompt}`
4. Has a close button to dismiss

**Files:**
- `ui/components/QrShareDialog.kt` — NEW file

**Pseudocode:**
```kotlin
@Composable
fun QrShareDialog(
    promptRu: String,
    answerText: String,
    targetLanguage: String,  // "it", "en", etc.
    onDismiss: () -> Unit
) {
    val qrText = "RU: $promptRu\n${targetLanguage.uppercase()}: $answerText"
    val translateUrl = "https://translate.google.com/?sl=ru&tl=$targetLanguage&text=${Uri.encode(promptRu)}"

    AlertDialog(
        onDismissRequest = onDismiss,
        // Title: translation pair text
        // Body: QR code rendering
        // Buttons: "Open in Google Translate" + "Close"
    )
}
```

**Verification:** Dialog shows text + QR + Google Translate button.

### Fix 3: Add 5th option to SharedReportSheet
**UC:** UC-65.1 | **Spec:** 12-training-card-session.md#12.8.2

Add the "Share translation" option to SharedReportSheet:
1. Add `shareText: String? = null` parameter (when null, option is hidden)
2. Add `onShareQr: () -> Unit` callback
3. Add 5th row with QrCode2 icon, "Share translation" text
4. On tap -> calls onShareQr()

Also update all callers of SharedReportSheet to pass the new parameters.

**Files:**
- `ui/components/SharedReportSheet.kt` — add 5th option
- `ui/screens/TrainingScreen.kt` — wire shareText + onShareQr
- `ui/screens/VerbDrillScreen.kt` — wire shareText + onShareQr
- `ui/screens/VocabDrillScreen.kt` — wire shareText + onShareQr
- `ui/components/DailyPracticeComponents.kt` — wire shareText + onShareQr (if report sheet exists there)

**Verification:** All 4 report sheets show 5th option. Tapping opens QrShareDialog.

### Fix 4: Wire QrShareDialog state in GrammarMateApp
**UC:** UC-65.1, UC-65.4 | **Spec:** 07-app-router.md

Add state for showing QrShareDialog in GrammarMateApp or at the screen level:
1. Add `showQrDialog: Boolean` + `qrDialogData: QrDialogData?` state
2. When onShareQr is called, set the state to show dialog with card data
3. Wire Google Translate Intent to open URL in browser

**Files:**
- `ui/screens/TrainingScreen.kt` — manage QR dialog state locally or delegate
- `ui/components/QrShareDialog.kt` — contains dialog

**Verification:** Full flow: report -> share translation -> see QR + text -> open Google Translate.

---

## Verification Checklist
1. Report sheet shows 5th option "Share translation" with QrCode2 icon
2. Tapping opens dialog with translation pair text at top
3. QR code is generated and scannable by phone camera
4. QR text format: "RU: ...\n{LANG}: ..."
5. "Open in Google Translate" button opens correct URL in browser
6. Close button dismisses dialog, returns to report sheet
7. Works on TrainingScreen, VerbDrillScreen, VocabDrillScreen, DailyPractice
8. Build: `assembleDebug` passes

## Scope Boundaries
**Do NOT touch:**
- Existing report sheet options 1-4 (flag, hide, export, copy)
- BadSentenceStore or any data layer
- TrainingViewModel business logic
- Card session logic or state machine

## Regression Plan
After all fixes, run:
1. **Build:** `assembleDebug` — must pass
2. **Per-fix verification:** check each item from Verification Checklist
3. **Cross-task regression:**
   - Report sheet options 1-4 still work (flag, hide, export, copy)
   - Training flow unaffected
   - Verb drill flow unaffected
   - Vocab drill flow unaffected
   - Daily practice flow unaffected
4. **UC/AC spot-check:** UC-65 ACs hold, existing UC-53 (report sheet) ACs still hold
5. **Spec sync:** verify code matches updated specs

## Dependencies
- QR generation library (must be added to build.gradle.kts first)

## Git
One commit. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---
## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: QR library dependency | | |
| | Fix 2: QrShareDialog composable | | |
| | Fix 3: 5th option in SharedReportSheet | | |
| | Fix 4: Wire state in screens | | |

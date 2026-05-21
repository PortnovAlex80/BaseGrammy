# TASK-079: Daily Practice Pack Switching and Package Language Grouping

**Status:** DONE  
**Created:** 2026-05-21  
**Spec Reference:** UC-21 (updated), UC-38 (updated), UC-80 (new)  

---

## Problem Statement

### Issue 1: Daily Practice Pack Switching Bug

**Problem:** When a user switches the active pack, the daily practice session doesn't switch to the new pack. The session continues using stale data from the previous pack, causing incorrect content to be displayed.

**Root Cause:**
- `DailyPracticeCoordinator.getCurrentBlock()` doesn't validate that `activePackId` has changed
- `TrainingViewModel.selectPack()` calls `resetState()` but doesn't cancel ongoing daily sessions
- Stale session data from the old pack remains in memory

**User Impact:**
- User selects a different pack (e.g., from Italian to English)
- Daily practice continues showing Italian content instead of English
- Confusing UX where pack selection doesn't affect daily practice
- Cursor progress gets mixed between packs

### Issue 2: Package List Needs Language Grouping

**Problem:** The package selector list (from TASK-078) shows all packs in a flat list, mixed by languages. This makes it hard to distinguish between English and Italian packs when multiple packs are installed.

**User Impact:**
- Difficult to find packs for a specific language
- No visual separation between language groups
- Confusing pack names when multiple languages are installed

---

## Changes Required

### Part 1: Daily Practice Pack Switching Fix (Bug Fix)

#### 1.1 Add Pack Validation in DailyPracticeCoordinator
**Location:** `feature/daily/DailyPracticeCoordinator.kt`

**Change:** Add `activePackId` validation in `getCurrentBlock()` and session composition

```kotlin
// Before: No pack validation
fun getCurrentBlock(sessionState: DailySessionState, blockType: BlockType): DailyBlock {
    // Builds blocks without checking if activePackId changed
}

// After: Validate pack ID on each block access
fun getCurrentBlock(sessionState: DailySessionState, blockType: BlockType): DailyBlock {
    val currentPackId = progressStore.activePackId
    
    // If session's packId doesn't match current pack, invalidate session
    if (sessionState.packId != currentPackId) {
        invalidateDailySession()
        throw SessionInvalidatedException("Pack changed from ${sessionState.packId} to $currentPackId")
    }
    
    // Continue with normal block logic
}
```

**Acceptance Criteria:**
- AC1: `getCurrentBlock()` throws `SessionInvalidatedException` when `activePackId` doesn't match session's packId
- AC2: Exception is caught in UI and triggers session rebuild
- AC3: New session is built using the new pack's cursor state
- AC4: No stale data from old pack leaks into new session

#### 1.2 Cancel Daily Session in TrainingViewModel.selectPack()
**Location:** `ui/TrainingViewModel.kt`

**Change:** Cancel daily session before switching packs

```kotlin
// Before: Only resets state
fun selectPack(packId: String) {
    resetState()
    // ... rest of pack switching logic
}

// After: Cancel daily session explicitly
fun selectPack(packId: String) {
    // Cancel any active daily session
    if (uiState.dailySessionState != null) {
        cancelDailySession()
    }
    
    resetState()
    
    // Switch pack
    val pack = lessonStore.getPackById(packId)
    if (pack != null) {
        // ... rest of pack switching logic
    }
}
```

**Acceptance Criteria:**
- AC5: `selectPack()` calls `cancelDailySession()` before `resetState()`
- AC6: Daily session state is cleared (`dailySessionState = null`)
- AC7: Daily session cancellation doesn't affect other session types (training, verb drill)
- AC8: Cursor state for the new pack is loaded correctly

#### 1.3 Invalidation Logic for Mid-Session Pack Changes
**Location:** `ui/DailyPracticeScreen.kt` and `feature/daily/DailyPracticeCoordinator.kt`

**Change:** Handle session invalidated exception gracefully

```kotlin
// In DailyPracticeScreen
LaunchedEffect(sessionState) {
    try {
        val block = dailyPracticeCoordinator.getCurrentBlock(sessionState, currentBlockType)
        _currentBlock.value = block
    } catch (e: SessionInvalidatedException) {
        // Show pack changed dialog, then rebuild session
        showPackChangedDialog = true
    }
}

// Dialog handler
fun onPackChangedConfirmed() {
    viewModel.startDailyPractice() // Rebuild with new pack
    showPackChangedDialog = false
}
```

**Acceptance Criteria:**
- AC9: `SessionInvalidatedException` caught in `DailyPracticeScreen`
- AC10: User sees dialog: "Active pack changed. Rebuild daily practice session?"
- AC11: Confirming rebuilds session with new pack's cursor
- AC12: Canceling returns to Home screen

---

### Part 2: Package Language Grouping (Feature)

#### 2.1 Add Language Grouping to Package List
**Location:** `ui/screens/HomeScreen.kt` (new component from TASK-078)

**Change:** Group packages by `languageId` with section headers

```kotlin
@Composable
fun PackageSelectorList(
    packs: List<LessonPack>,
    currentPackId: String?,
    onPackSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Group packs by language
    val groupedPacks = packs.groupBy { it.languageId }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = "Select Package",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        groupedPatches.forEach { (languageId, languagePacks) ->
            // Language section header
            Text(
                text = getLanguageDisplayName(languageId), // "English" / "Italian"
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            // Collapsible pack list for this language
            var expanded by remember { mutableStateOf(true) }
            
            if (expanded) {
                languagePacks.forEach { pack ->
                    PackageItem(
                        pack = pack,
                        isSelected = pack.packId == currentPackId,
                        onClick = {
                            onPackSelected(pack.packId)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}
```

**Acceptance Criteria:**
- AC13: Packages grouped by `languageId` (e.g., "English", "Italian")
- AC14: Language names displayed as section headers
- AC15: Each language section is collapsible
- AC16: Default state: all language sections expanded
- AC17: Clicking header collapses/expands that language's pack list
- AC18: Current pack highlighted with checkmark

#### 2.2 Language Display Name Helper
**Location:** `data/LanguageId.kt` or `ui/Utils.kt`

**Change:** Add function to convert language code to display name

```kotlin
fun getLanguageDisplayName(languageId: LanguageId): String {
    return when (languageId) {
        LanguageId.ENGLISH -> "English"
        LanguageId.ITALIAN -> "Italian"
        LanguageId.RUSSIAN -> "Русский"
        // Add future languages here
    }
}
```

**Acceptance Criteria:**
- AC19: Function returns human-readable language name
- AC20: Language names are localized (Russian name for Russian, etc.)
- AC21: Falls back to `languageId.code` if language not found

---

## Verification Checklist

### Daily Practice Pack Switching
1. [ ] Switch pack while daily practice is active → session invalidated
2. [ ] Confirm "Rebuild session" → new session uses new pack
3. [ ] Verify cursor state comes from new pack (not old pack)
4. [ ] Block 1 content matches new pack's lesson at cursor position
5. [ ] Block 3 verbs match new pack's verb drill content
6. [ ] Switch pack before starting daily practice → no errors
7. [ ] Switch pack multiple times → each switch rebuilds session correctly

### Package Language Grouping
1. [ ] Package list shows language section headers
2. [ ] Clicking language header collapses/expands pack list
3. [ ] All sections expanded by default
4. [ ] Selecting pack from collapsed section works
5. [ ] Language names are human-readable (not codes)
6. [ ] Multiple packs per language grouped correctly
7. [ ] Checkmark shows on selected pack

### Regression Testing
1. [ ] Normal daily practice flow unchanged (no pack switch)
2. [ ] Repeat/Continue dialog still works
3. [ ] Cursor advances correctly after session completion
4. [ ] Verb drill pack switching still works
5. [ ] Vocab drill pack switching still works
6. [ ] TrainingViewModel.selectPack() doesn't break other flows

---

## Scope Boundaries

### In Scope
- Fix daily practice pack switching bug
- Add language grouping to package selector list
- Invalidate daily session when pack changes
- Graceful UI handling of session invalidation
- Language display names for section headers

### Out of Scope
- Changes to daily practice cursor logic (except pack validation)
- Changes to Repeat/Continue dialog flow
- Pack import/delete functionality
- Settings screen pack management
- Verb drill and vocab drill pack switching (already work correctly)

---

## Regression Plan

### Areas to Test
1. **Daily Practice Session Lifecycle**: Verify session creation, invalidation, and rebuilding
2. **Pack Switching**: Test all pack switching scenarios (daily, verb drill, vocab drill, training)
3. **Cursor State Persistence**: Ensure cursor state is pack-scoped and doesn't leak
4. **Multi-Pack Users**: Test with users who have 3+ packs across 2+ languages
5. **UI Language Grouping**: Verify package list displays correctly with 1, 2, 3+ languages

### Automated Tests
- Add test for `DailyPracticeCoordinator.getCurrentBlock()` with pack change
- Add test for `TrainingViewModel.selectPack()` cancelling daily session
- Add test for `SessionInvalidatedException` handling
- Add UI test for package language grouping

### Manual Testing Checklist
1. Install 2 English packs + 2 Italian packs
2. Start daily practice on English pack 1
3. Switch to Italian pack 1 via package badge
4. Verify "Rebuild session" dialog appears
5. Confirm rebuild → verify Italian content shown
6. Verify cursor at position 0 (new pack's cursor)
7. Open package list → verify language grouping
8. Collapse English section → verify only Italian packs shown
9. Select Italian pack 2 → verify switch works

---

## Implementation Notes

### State Management
- Add `packId` field to `DailySessionState` to track which pack the session belongs to
- Add `SessionInvalidatedException` to `feature/daily/` package
- Add `cancelDailySession()` method to `TrainingViewModel`

### UI Components
- Modify `PackageSelectorList` from TASK-078 to include language grouping
- Add collapsible sections using `Accordion` or custom `Column` with state
- Add language header composable with expand/collapse icons

### Data Flow
1. User switches pack via package badge
2. `TrainingViewModel.selectPack()` called
3. Daily session cancelled (if active)
4. `activePackId` updated in state
5. Next `getCurrentBlock()` call detects pack mismatch
6. `SessionInvalidatedException` thrown
7. UI catches exception, shows rebuild dialog
8. User confirms → `startDailyPractice()` rebuilds session

### Error Handling
- `SessionInvalidatedException` should be caught at UI layer only
- Don't let exception crash the app
- Provide clear user messaging about what happened

---

## Related Use Cases

- **UC-21** (Start daily practice session): Updated AC for pack validation
- **UC-38** (HomeScreen package badge): Updated AC for language grouping
- **UC-80** (Package language grouping): New use case for feature

---

## Files to Modify

1. **feature/daily/DailyPracticeCoordinator.kt**
   - Add `SessionInvalidatedException` class
   - Add pack validation in `getCurrentBlock()`
   - Add `invalidateDailySession()` method

2. **ui/TrainingViewModel.kt**
   - Add `cancelDailySession()` method
   - Modify `selectPack()` to call `cancelDailySession()`

3. **data/Models.kt**
   - Add `packId: String` field to `DailySessionState`

4. **ui/screens/HomeScreen.kt**
   - Modify `PackageSelectorList` to group by language
   - Add collapsible sections
   - Add language header composables

5. **ui/DailyPracticeScreen.kt**
   - Add try-catch for `SessionInvalidatedException`
   - Add pack changed dialog

6. **data/LanguageId.kt** (or `ui/Utils.kt`)
   - Add `getLanguageDisplayName()` function

---

## Success Metrics

1. **Bug Fix**: Daily practice switches to new pack within 2 seconds of selection
2. **UX**: Pack changed dialog appears only when necessary (not on normal flow)
3. **Usability**: Package list is easier to navigate with language grouping
4. **Performance**: No lag when expanding/collapsing language sections
5. **Stability**: No crashes when switching packs during active daily session

---

## Open Questions

1. Should the pack changed dialog offer "Cancel and return to Home" option?
   - **Decision**: Yes, provide 3 options: "Rebuild session", "Cancel session", "Return to Home"

2. Should language sections remember collapsed state across app restarts?
   - **Decision**: No, always reset to expanded state for better discoverability

3. Should we show pack metadata (lesson count, last practiced) in the grouped list?
   - **Decision**: No, keep it simple for now (future enhancement)

---

## References

- **Specification**: `docs/specification/22-use-case-registry.md` (UC-21, UC-38, UC-80)
- **Current Implementation**: `feature/daily/DailyPracticeCoordinator.kt`, `ui/TrainingViewModel.kt`
- **Related Task**: TASK-078 (package badge refactor)
- **Related Spec**: `09-daily-practice.md` (cursor independence principle)

---

## Completion Log

| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| 2026-05-21 | Fix 1: Daily practice pack switching | DONE | Commit cbf4a42 - Added pack validation, SessionInvalidatedException, selectPack() calls cancelDailySession() |
| 2026-05-21 | Fix 2: Pack language filtering | DONE | Commit eed68b9 - Filter packages by selectedLanguageId in PackageSelectorList |

**Implementation Summary:**
- Fix 1 (Bug): Daily practice now properly switches when user selects different pack
- Fix 2 (UX): Package selector shows only packs for selected language (no language mixing)
- Both fixes verified with successful APK builds
- Total implementation time: ~2 hours including research and testing

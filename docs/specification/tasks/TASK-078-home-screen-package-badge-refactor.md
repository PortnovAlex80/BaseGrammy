# TASK-078: HomeScreen Package Badge Refactor

**Status:** OPEN  
**Created:** 2026-05-21  
**Spec Reference:** UC-38 (updated), UC-35, 07-app-router.md  

---

## Problem Statement

The current HomeScreen package badge displays both pack name AND lesson progress information (e.g., "EN_WORD_ORDER_A1 - Lesson 1/ Exercise 0/33"). This creates UI clutter and doesn't provide a clear hierarchy for users to understand pack vs lesson context.

**Current behavior:**
- Package badge (primaryLabel) shows pack name + lesson info combined
- Lesson info displayed as subtitle in the same clickable card
- No way to quickly switch between packs without going through Settings

**User impact:**
- Confusing what "pack" vs "lesson" means
- No easy way to see available packs
- Hidden pack selection behind Settings menu

---

## Changes Required

### 1. Package Badge Simplification (Primary Change)
**Location:** `ui/screens/HomeScreen.kt` (lines 232-251)

**Before:**
```kotlin
Card(
    modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onPrimaryAction)
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = primaryLabel,  // Shows "EN_WORD_ORDER_A1 - Lesson 1"
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        if (nextHint != null) {
            Text(
                text = nextHint,  // Shows "Exercise 0/33"
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
```

**After:**
```kotlin
Card(
    modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onShowPackageList)  // NEW: Shows package selector
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = activePackDisplayName ?: "Select Pack",  // Only pack name
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        // NO subtitle - lesson info moved elsewhere
    }
}
```

**Acceptance Criteria:**
- AC1: Package badge displays ONLY pack name (e.g., "EN_WORD_ORDER_A1" or "Italian Verb Groups")
- AC2: Badge shows "Select Pack" when no active pack is selected
- AC3: Click on badge opens inline package list (not bottom sheet or dialog)
- AC4: Package list shows all installed packs for current language
- AC5: Selecting a pack immediately switches active pack and updates badge

### 2. Lesson Info Relocation
**Location:** `ui/screens/HomeScreen.kt` (around line 254)

**Before:** Lesson info shown as subtitle in package card

**After:** Move lesson info to standalone text under "Grammar Roadmap" heading

```kotlin
Spacer(modifier = Modifier.height(16.dp))
Text(text = stringResource(R.string.home_grammar_roadmap), fontWeight = FontWeight.SemiBold)

// NEW: Lesson info moved here
if (currentLessonInfo != null) {
    Text(
        text = currentLessonInfo,  // "Lesson 1/ Exercise 0/33"
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp)
    )
}

Spacer(modifier = Modifier.height(8.dp))
```

**Acceptance Criteria:**
- AC6: Lesson progress info ("Lesson 1/ Exercise 0/33") displayed under "Grammar Roadmap" heading
- AC7: Lesson info logic unchanged (still shows current lesson + exercise count)
- AC8: Lesson info uses same color/style as before (grey, small text)
- AC9: Lesson info omitted when no lesson is selected (shows pack grid only)

### 3. Package Selection UX
**Location:** `ui/screens/HomeScreen.kt` (new component)

**Implementation:** Create inline package expansion list

```kotlin
@Composable
fun PackageSelectorList(
    packs: List<LessonPack>,
    currentPackId: String?,
    onPackSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Inline expansion (not modal/bottom sheet)
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
        packs.forEach { pack ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onPackSelected(pack.packId)
                        onDismiss()
                    }
                    .background(
                        if (pack.packId == currentPackId) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            Color.Transparent
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pack.displayName,
                    fontWeight = if (pack.packId == currentPackId) FontWeight.Bold else FontWeight.Normal
                )
                if (pack.packId == currentPackId) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text("✓", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
```

**Acceptance Criteria:**
- AC10: Package list expands inline below the badge (not modal overlay)
- AC11: List shows all installed packs for current language
- AC12: Current pack highlighted with checkmark
- AC13: Clicking outside list dismisses it
- AC14: Selecting a pack immediately switches activePackId and refreshes lesson grid
- AC15: Package list dismissed after selection

---

## Verification Checklist

1. [ ] Package badge shows ONLY pack name (no lesson info)
2. [ ] Click on badge expands inline package list
3. [ ] Package list shows all installed packs for current language
4. [ ] Selecting a pack immediately updates badge and lesson grid
5. [ ] Lesson info ("Lesson 1/ Exercise 0/33") displayed under "Grammar Roadmap" heading
6. [ ] Lesson progress calculation logic unchanged
7. [ ] Package list dismissed after selection or outside click
8. [ ] No regression in pack switching functionality
9. [ ] No regression in lesson progress display
10. [ ] UI responsive on phone and tablet layouts

---

## Scope Boundaries

### In Scope
- Refactor package badge to show pack name only
- Add inline package selection UX
- Move lesson info to under "Grammar Roadmap" heading
- Update HomeScreen state management for package list expansion

### Out of Scope
- Changes to pack import/delete functionality
- Changes to lesson progress calculation logic
- Changes to TrainingViewModel pack selection logic
- Changes to Settings screen pack management

---

## Regression Plan

### Areas to Test
1. **Pack Switching**: Verify that selecting a different pack updates the lesson grid correctly
2. **Lesson Progress**: Verify lesson info display still accurate after refactor
3. **Multi-Pack Users**: Test with users who have 3+ installed packs
4. **Single-Pack Users**: Test with users who have only 1 pack (list should still work)
5. **Language Switching**: Verify pack list updates correctly when switching languages
6. **Navigation**: Verify no navigation issues when package list is expanded/collapsed

### Automated Tests
- Add UI test for package badge click
- Add UI test for package selection
- Add UI test for lesson info display

### Manual Testing Checklist
1. Launch app with 2+ packs installed
2. Tap package badge → inline list expands
3. Select different pack → lesson grid updates
4. Verify lesson info shown under "Grammar Roadmap"
5. Switch language → verify pack list updates
6. Tap outside list → verify list dismisses

---

## Implementation Notes

### State Management
- Add `showPackageList: Boolean` to HomeScreen state
- Add `onPackageSelected: (String) -> Unit` callback to TrainingViewModel
- Reuse existing `selectPack(packId)` method in TrainingViewModel

### UI Components
- Create `PackageSelectorList` composable (inline expansion)
- Modify `primaryLabel` to show only pack name
- Add `currentLessonInfo` text component under "Grammar Roadmap"

### Data Flow
1. User taps package badge
2. HomeScreen sets `showPackageList = true`
3. PackageSelectorList rendered inline below badge
4. User selects pack → calls `onPackageSelected(packId)`
5. TrainingViewModel.selectPack(packId) triggered
6. State updates: activePackId, lessons, lesson grid
7. Package list dismissed, badge updated with new pack name

---

## Related Use Cases

- **UC-38** (Drill tile visibility by pack): Updated AC for pack badge behavior
- **UC-35** (Navigate Home to Training): Unchanged, but pack selection now easier
- **UC-39** (Import valid lesson pack): Unchanged
- **UC-42** (Pack removal): Unchanged

---

## Files to Modify

1. **ui/screens/HomeScreen.kt**
   - Modify package badge Card (lines 232-251)
   - Add lesson info display under "Grammar Roadmap" (line 254+)
   - Add `PackageSelectorList` composable
   - Add state for `showPackageList`

2. **ui/TrainingViewModel.kt** (if needed)
   - Verify `selectPack(packId)` handles UI state correctly
   - May need to add callback for pack selection completion

3. **data/Models.kt** (if needed)
   - No changes expected

---

## Success Metrics

1. **Usability**: Users can switch packs in 2 taps (vs 5+ through Settings)
2. **Clarity**: Package badge clearly shows current pack name
3. **Discoverability**: Users more aware of multiple installed packs
4. **Performance**: No lag when expanding package list (inline rendering)
5. **Accessibility**: Screen reader announces pack name and selection correctly

---

## Open Questions

1. Should package list show pack metadata (lesson count, last practiced)?
   - **Decision**: No, keep it simple for now (future enhancement)

2. Should package list be scrollable if 10+ packs installed?
   - **Decision**: Yes, add `verticalScroll()` modifier

3. Should there be a "Manage Packs" button in the list?
   - **Decision**: No, keep pack management in Settings (out of scope)

---

## References

- **Specification**: `docs/specification/22-use-case-registry.md` (UC-38)
- **Current Implementation**: `app/src/main/java/com/alexpo/grammermate/ui/screens/HomeScreen.kt`
- **Related Task**: TASK-057 (inline streak indicator - similar pattern)

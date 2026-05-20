# Remove Debug Dialog from VerbDrill

## Files to Modify

### 1. app/src/main/java/com/alexpo/grammermate/ui/VerbDrillScreen.kt
**Remove:**
- Lines 127-140: FloatingActionButton with bug icon (the entire Box modifier with FloatingActionButton)
- Lines 510-531: DebugInfoDialog composable function
- Lines 164-170: Conditional render for debug dialog (if state.showDebugInfo { DebugInfoDialog(...) })

**Remove imports:**
- Line 17: `import androidx.compose.material.icons.filled.BugReport`
- Line 13: `import androidx.compose.material3.FloatingActionButton` (if not used elsewhere)
- Line 14: `import androidx.compose.material3.Scaffold` (confirmed not used)

**DO NOT REMOVE:**
- Line 18: `import androidx.compose.material3.AlertDialog` (used in StartFreshResumeDialog at line 340)

### 2. app/src/main/java/com/alexpo/grammermate/data/VerbDrillCard.kt
**Remove from VerbDrillUiState data class (lines 72-73):**
- `val showDebugInfo: Boolean = false,`
- `val debugInfo: String = ""`

### 3. app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt
**Remove methods:**
- Lines 900-976: `fun showDebugDialog()` method (entire method with KDoc)
- Lines 975-980: `fun hideDebugDialog()` method (entire method with KDoc)

## Build Command
After changes, run:
```bash
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

## Return Format
1. List of files changed with exact line numbers
2. Summary of changes per file
3. Build result (PASS/FAIL)
4. Any blockers encountered

## Rules
- Use AtomicFileWriter for all file writes
- Read relevant spec files before coding
- Run build check and report result
- Commit with footer: Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
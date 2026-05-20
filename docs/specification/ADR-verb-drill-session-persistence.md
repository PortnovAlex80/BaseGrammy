# ADR: VerbDrill Session Persistence Strategy

**Date:** 2025-05-20
**Status:** Accepted (pending implementation)
**Context:** VerbDrill feature session resume functionality
**Related Issues:** VD-50 (session resume), VD-51 (inline SessionCard)

---

## Problem Statement

The VerbDrill feature has inconsistent `SessionCard` visibility on the selection screen. Users report:

1. **SessionCard sometimes shows** when there's a previous incomplete session
2. **SessionCard sometimes disappears** after completing a lesson
3. **SessionCard shows stale data** - card count or filters don't match the actual last session

### Root Cause

The `lastSessionContext` field in `VerbDrillUiState` is a **cached snapshot** that is set only once during `loadCards()` via `checkForLastSessionAndShowDialog()`. This cache is **never refreshed** after:

1. User starts a new session (which overwrites the YAML with new data)
2. User exits a session (which updates YAML with new `todayShownCardIds`)
3. User completes a session (which deletes YAML entirely)

The YAML file is the **single source of truth**, but the UI's cached copy becomes stale immediately after any session state change.

### Impact

| Scenario | Expected Behavior | Actual Behavior | User Impact |
|----------|-------------------|-----------------|-------------|
| Complete session, return to selection | SessionCard disappears | SessionCard shows with stale/null data | Confusion - appears to have a session to resume |
| Exit incomplete session, return | SessionCard shows with updated count | SessionCard shows old count | Misleading progress info |
| Click "Continue", then exit | SessionCard shows updated filters | SessionCard shows old filters | Filters don't match what user was doing |

---

## Decision

### Architecture: Reactive Session State with YAML as Source of Truth

We will implement a **reactive session state pattern** where:

1. **YAML file remains the single source of truth** for session persistence
2. **`lastSessionContext` is refreshed on every navigation** to VerbDrillScreen
3. **SessionCard visibility is derived from fresh YAML data**, not stale cache

### State Machine

```
┌──────────────────┐
│   NO_SESSION     │  ◄─── Initial: no YAML or YAML empty
└────────┬─────────┘
         │
         │  User selects filters + clicks "Start"
         ▼
┌─────────────────────────────────────────────────────────────────┐
│                      SESSION_ACTIVE                              │
│  - session: VerbDrillSessionState (non-null)                    │
│  - YAML: verb_drill_last_session.yaml exists                    │
│  - lastSessionContext: SET (from YAML)                          │
│  Entry: startSession(), onResumeSession()                       │
│  Side effect: saveLastSession() → YAML written                  │
└──────────────────────────┬──────────────────────────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         │                 │                 │
         ▼                 ▼                 ▼
  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
  │ All cards   │   │ User exits  │   │ Last card   │
  │ shown       │   │ mid-session │   │ completed   │
  └──────┬──────┘   └──────┬──────┘   └──────┬──────┘
         │                 │                 │
         ▼                 ▼                 ▼
  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
  │SESSION_DONE │   │SESSION_PAUSE│   │COMPLETE     │
  └──────┬──────┘   └──────┬──────┘   └──────┬──────┘
         │                 │                 │
         └─────────────────┴─────────────────┘
                           │
         ┌─────────────────┴─────────────────┐
         │                                   │
         ▼                                   ▼
  ┌─────────────┐                   ┌─────────────┐
  │ YAML        │                   │ YAML        │
  │ updated     │                   │ deleted     │
  └──────┬──────┘                   └──────┬──────┘
         │                                   │
         │  User navigates back              │
         ▼                                   ▼
  ┌─────────────────────────────────────────────────────┐
  │  REFRESH lastSessionContext from YAML (fix location)│
  └─────────────────────────────────────────────────────┘
         │                                   │
         ▼                                   ▼
  ┌─────────────┐                   ┌─────────────┐
  │ SessionCard │                   │ SessionCard │
  │ SHOWS       │                   │ HIDDEN      │
  └─────────────┘                   └─────────────┘
```

### Implementation Changes

#### 1. Add `refreshLastSessionContext()` to VerbDrillViewModel

```kotlin
/**
 * Refresh lastSessionContext from YAML.
 * Call this when user navigates back to VerbDrill selection screen
 * to ensure SessionCard shows accurate data.
 */
fun refreshLastSessionContext() {
    viewModelScope.launch {
        val lastSession = verbDrillStore.loadLastSession()
        _uiState.update { it.copy(lastSessionContext = lastSession) }
        Log.d(logTag, "refreshLastSessionContext: ${lastSession != null}")
    }
}
```

#### 2. Call refresh on VerbDrillScreen entry

In `VerbDrillScreen.kt`, add LaunchedEffect to refresh on first composition:

```kotlin
@Composable
fun VerbDrillScreen(
    viewModel: VerbDrillViewModel,
    onBack: () -> Unit,
    onStartSession: (List<VerbDrillCard>) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // VD-50: Refresh last session context on screen entry to fix stale cache bug
    LaunchedEffect(Unit) {
        viewModel.refreshLastSessionContext()
    }

    // ... rest of screen
}
```

#### 3. Clear lastSessionContext on session completion

In `exitSession()` (VerbDrillViewModel), explicitly clear when complete:

```kotlin
fun exitSession() {
    Log.d(logTag, "exitSession: saving last session...")
    val session = _uiState.value.session
    if (session != null && !session.isComplete) {
        saveLastSessionState(session)
    } else if (session != null && session.isComplete) {
        verbDrillStore.deleteLastSession()
        // FIX: Clear cached context when session completes
        _uiState.update { it.copy(lastSessionContext = null) }
    }
    verbDrillStore.flush()
    _uiState.update { it.copy(session = null, currentCardIsBad = false) }
}
```

---

## Consequences

### Positive

1. **SessionCard always shows accurate data** - refreshed on every navigation
2. **No more stale cache bugs** - context matches YAML source of truth
3. **Simple implementation** - one new function, one LaunchedEffect
4. **Minimal performance impact** - one YAML read per navigation (cached in store)

### Negative

1. **Additional YAML read** on every VerbDrillScreen navigation
   - Mitigation: Store has `lastSessionCache`, so disk read happens only once per process
2. **Race condition risk** if user navigates away immediately
   - Mitigation: LaunchedEffect with Unit key runs once, stable

### Neutral

1. **No data model changes** - VerbDrillLastSessionState unchanged
2. **No YAML format changes** - existing files remain compatible
3. **Behavior change** - SessionCard may appear/disappear more frequently (but correctly)

---

## Alternatives Considered

### Alternative A: File watcher / polling for YAML changes

**Description:** Watch `verb_drill_last_session.yaml` and emit StateFlow updates when changed.

**Rejected because:**
- Complex to implement (FileObserver on Android is non-trivial)
- Polling is inefficient and battery-draining
- Overkill for this use case

### Alternative B: Clear lastSessionContext on every session start

**Description:** Set `lastSessionContext = null` in `startSession()` to prevent showing stale data.

**Rejected because:**
- Breaks "Continue" workflow - SessionCard won't show for resumed sessions
- Loses resume capability mid-session
- Addresses symptom, not root cause

### Alternative C: Store session state in ViewModel only (no YAML)

**Description:** Keep session in memory, lose it on process death.

**Rejected because:**
- Regression from VD-50 - loses resume capability after app restart
- Poor UX - users lose progress on crashes/updates

---

## Testing Strategy

### Unit Tests

1. **Test `refreshLastSessionContext()` loads from store**
2. **Test `exitSession()` clears context when complete**
3. **Test `startSession()` preserves context until refresh**

### Integration Tests

1. **Start session → exit → navigate back → SessionCard shows**
2. **Complete session → navigate back → SessionCard hidden**
3. **Start → exit → change filters → navigate back → SessionCard shows new filters**

### E2E (Maestro)

1. **Flow: `11-verb-drill-session-resume.yaml`**
   - Start session, answer 2 cards, exit
   - Verify SessionCard shows "2 cards shown"
   - Click Continue, answer remaining, exit
   - Verify SessionCard hidden

---

## Migration Notes

### No data migration required

YAML format unchanged. Existing `verb_drill_last_session.yaml` files remain compatible.

### Code changes required

1. `VerbDrillViewModel.kt`: Add `refreshLastSessionContext()`
2. `VerbDrillViewModel.kt`: Update `exitSession()` to clear context on complete
3. `VerbDrillScreen.kt`: Add LaunchedEffect to call refresh

### Rollback plan

If bugs found, revert the three changes above. No data corruption possible since we only read YAML, not modify format.

---

## References

- Spec: `docs/specification/10-verb-drill.md`
- Scenario: `docs/specification/scenario/scenario-07-verb-drill.md`
- Code: `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt`
- Code: `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt`

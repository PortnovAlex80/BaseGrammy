---
name: verify-user-journey
description: Use BEFORE committing or delivering any feature that involves user interaction. Traces the complete user path end-to-end: data flow, button wiring, edge cases, state transitions. Catches "works in code but broken for user" bugs.
---

# Verify User Journey

Mandatory checklist before delivering ANY feature with UI interaction or data flow.

## When to use
- Before committing code that touches: UI screens, ViewModels, data parsing, navigation, button handlers
- When user reports "doesn't work" after code review said "looks good"

## Checklist

### 1. Data Flow Trace
For each piece of data the user sees:
- [ ] Where does it originate? (CSV, API, local storage, hardcoded)
- [ ] How does it get to the device? (assets, download, pack import)
- [ ] What parses/transforms it? (parser class, store)
- [ ] What model holds it? (data class)
- [ ] What ViewModel exposes it? (StateFlow, LiveData)
- [ ] What composable renders it?
- [ ] **Trace the FULL chain** — if any link breaks, the feature is broken

### 2. Button/Interaction Wiring
For every clickable element:
- [ ] onClick handler calls WHAT function?
- [ ] That function does WHAT to state?
- [ ] That state change causes WHAT UI update?
- [ ] Does the UI element have an `enabled` condition? Is it correct?
- [ ] **Press the button in your mind** — does the expected thing happen?

### 3. Edge Cases
Walk through these scenarios mentally:
- [ ] First item in list (index 0)
- [ ] Last item in list
- [ ] Empty list (no data)
- [ ] Single item list
- [ ] Navigation beyond bounds (prev at first, next at last)
- [ ] Action after completion (what happens when done?)
- [ ] Re-entry (open screen again after leaving)
- [ ] Data change (what if source data changes between sessions?)

### 4. State Machine
If the feature has multiple states:
- [ ] List all states explicitly
- [ ] List all transitions between states
- [ ] For each transition: what triggers it? What guards it?
- [ ] Is there a "stuck" state? (no transition possible, user trapped)
- [ ] Is there a "skip" state? (transition bypasses required step)

### 5. Integration Points
- [ ] Does the feature depend on imported data? Is that data present on device?
- [ ] Does it depend on another screen/feature? Does that feature call into this one correctly?
- [ ] Does it persist state? Is the persistence read back correctly?
- [ ] Does it handle configuration changes (rotation, theme)?

### 6. Cross-Reference with Existing Mechanics
- [ ] Does this feature copy logic from another part of the app?
- [ ] Is the copy complete? (check button wiring, state handling, edge cases)
- [ ] Are there differences? Are they intentional?

## Failure Protocol

If ANY checklist item fails:
1. STOP — do not commit
2. Document the failure: what's broken, where in the chain
3. Fix before proceeding
4. Re-run the full checklist after the fix

## Anti-Patterns to Watch For
- Method returns a value but caller ignores it (like showAnswer)
- State set but never read (dead state)
- Index advanced before UI shows feedback (off-by-one)
- ZIP/assets not rebuilt after source data changes
- Button enabled/disabled based on wrong condition
- Auto-advance/auto-next without terminal condition guard

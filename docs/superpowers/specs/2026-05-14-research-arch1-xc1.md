# Research Task: CRITICAL-ARCH-1 + CRITICAL-XC-1

**Type:** Architecture research + design decision (READ-ONLY, no code changes)
**Output:** Recommendation document for user approval before implementation

---

## RESEARCH TASK 1: CRITICAL-ARCH-1 — Extra ViewModels

### Background

CLAUDE.md Level B rule states: "Never create a second ViewModel." Yet two additional ViewModels exist:
- `VerbDrillViewModel.kt` (~608 lines) — handles verb conjugation drill screen
- `VocabDrillViewModel.kt` (~501 lines) — handles vocabulary flashcard drill screen

TrainingViewModel is the primary ViewModel. These two drill ViewModels have their own state flows, lifecycle, and direct AppContainer access.

### Question to answer

**Should we merge them into TrainingViewModel feature helpers, or document them as exceptions?**

### What to investigate

1. **Read these files completely:**
   - `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt`
   - `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillViewModel.kt`
   - `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt` (scan for any drill-related state/methods)

2. **Analyze:**
   - What state does each drill ViewModel own? Is it a subset of TrainingUiState or completely independent?
   - What stores do they access? Do any overlap with TrainingViewModel's stores?
   - How does GrammarMateApp create/destroy them? What's their lifecycle?
   - How does state sync back to TrainingViewModel? (e.g., `vm.refreshVocabMasteryCount()`)
   - What's the coupling: do TrainingViewModel methods call into drill ViewModels? Or is it one-directional?

3. **Evaluate Option A: Merge into feature helpers**

   Sketch what this looks like:
   - `VerbDrillRunner` in `feature/training/` (or `feature/drill/`) owned by TrainingViewModel
   - `VocabDrillRunner` in `feature/vocab/` owned by TrainingViewModel
   - State flows merged into TrainingViewModel's `combine()`
   - GrammarMateApp references everything through `vm.verbDrill` and `vm.vocabDrill`
   - Drill-specific screens call `vm.verbDrill.xxx()` instead of `verbDrillVm.xxx()`

   **Pros:** Single-ViewModel rule restored, state consistency guaranteed, no cross-VM sync hacks
   **Cons:** TrainingViewModel grows by ~200-300 lines (wiring), high-risk refactor touching GrammarMateApp navigation + screen files

4. **Evaluate Option B: Document as exceptions**

   Update CLAUDE.md to say: "VerbDrillViewModel and VocabDrillViewModel are documented exceptions to the single-ViewModel rule. They manage screens with completely independent state that does not overlap with TrainingViewModel's domain."

   **Pros:** Zero code risk, honest documentation
   **Cons:** Rule is weakened, future developers may create more ViewModels citing this precedent

5. **Evaluate Option C: Shared base with lifecycle delegation**

   Extract a `DrillViewModel` base that delegates to feature helpers, keeping separate ViewModels but sharing the pattern. Reduces duplication while respecting different lifecycles.

   **Pros:** Clean separation, shared pattern
   **Cons:** Still two ViewModels, added abstraction

### Output format

Return a recommendation with:
```
## VERDICT: Option A / B / C

## Reasoning
[3-5 sentences]

## Migration plan (if A or C)
[Step-by-step, estimated lines changed, risk assessment]

## Cost estimate
[Time + blast radius]
```

---

## RESEARCH TASK 2: CRITICAL-XC-1 — Backup Encryption

### Background

BackupManager creates backups as plaintext YAML files. BackupRestorer reads them back with zero validation or decryption. Files are stored in public Downloads/ directory. Any app with READ_EXTERNAL_STORAGE can read all user learning data (mastery, streaks, drill progress, preferences).

### What to investigate

1. **Read these files:**
   - `app/src/main/java/com/alexpo/grammermate/data/BackupManager.kt`
   - `app/src/main/java/com/alexpo/grammermate/data/BackupRestorer.kt`
   - `app/src/main/AndroidManifest.xml` (check allowBackup, backup rules)
   - `app/build.gradle.kts` (check dependencies — any crypto libs already?)

2. **Analyze current data at rest:**
   - What sensitive data is in backups? (list specific stores and what they contain)
   - Where are backups stored? (Downloads/BaseGrammy/, internal storage, both?)
   - What's the backup format? (single ZIP? individual YAML files? both?)

3. **Evaluate encryption approaches:**

   **Option A: Password-based encryption (PBKDF2 + AES-GCM)**
   - User sets a password in Settings
   - Backups encrypted with AES-256-GCM, key derived via PBKDF2
   - Pros: No server needed, standard Android crypto APIs
   - Cons: User must remember password, password recovery impossible

   **Option B: Android Keystore + key per device**
   - Generate a key in Android Keystore
   - Encrypt backups with this key
   - Pros: Transparent to user, no password
   - Cons: Can't restore on different device (key is hardware-bound)

   **Option C: Hybrid — device key for auto-backups, password for export**
   - Internal backups use Keystore (automatic)
   - Exported backups (Downloads/) use password (portable)
   - Pros: Best of both worlds
   - Cons: More complex implementation

   **Option D: No encryption, but restrict file access**
   - Move backups from Downloads/ to internal storage only
   - Use SAF (Storage Access Framework) for user-controlled export
   - Disable allowBackup in manifest
   - Pros: Simplest, reduces attack surface without crypto complexity
   - Cons: Doesn't protect against root/adb access

4. **Evaluate libraries:**
   - Can we use `javax.crypto` (built into Android) without external dependencies?
   - Or do we need Bouncy Castle / Tink / SQLCipher?
   - What's the minimum viable crypto for YAML text files?

5. **Check Android backup infrastructure:**
   - What does `android:allowBackup="true"` actually expose?
   - Should we add `android:fullBackupContent` rules?
   - Should we add `android:backupInclusionRules`?

### Output format

Return a recommendation with:
```
## VERDICT: Option A / B / C / D (or hybrid)

## Reasoning
[3-5 sentences]

## Implementation sketch
[Which files change, what crypto API, estimated effort]

## Trade-offs
[What we gain vs what we lose]

## Cost estimate
[Time + complexity]
```

---

## How to run this research

Spawn ONE agent (or two parallel agents) that do READ-ONLY analysis. No code changes. Return the recommendation documents. User approves before any implementation.

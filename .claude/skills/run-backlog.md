---
name: run-backlog
description: Orchestrate the task backlog. Use when user asks to run backlog, process pending tasks, execute task pool, or types /run-backlog. Analyzes all open TASK-* files, groups into parallel-safe batches by area cluster + file overlap, executes wave-by-wave with regression gates.
---

# Run Backlog — Task Pool Orchestrator

This skill manages the full lifecycle of the open task backlog: dependency analysis, batch planning, parallel execution, and regression verification.

## Activation

User invokes `/run-backlog` or asks "run backlog", "process tasks", "execute task pool", "handle open tasks". Optionally accepts area filter: `/run-backlog dark-mode` to limit to specific area.

## Execution Pipeline (MANDATORY — follow exactly)

### Phase 1: ANALYZE (1 Explore agent)

Spawn ONE Explore agent that:

1. Reads `docs/specification/tasks/README.md` to get the full task list
2. For every OPEN task (status not DONE, filename not starting with DONE-):
   a. Read the task file
   b. Extract: affected files (from task body), spec area (from Spec column in README), severity (from README table or task body)
3. Build a dependency matrix:
   - For each task pair, check if they share ANY file path
   - Tasks sharing files → SEQUENTIAL dependency (must be in same batch, ordered)
   - Tasks with zero file overlap → PARALLEL-safe (can run simultaneously)
4. Group tasks into area clusters based on their Spec reference:
   - `14-theme` → dark-mode/theme cluster
   - `23-screen-elements` → screen-audit cluster
   - `05-audio` → audio cluster
   - etc.
5. Output the analysis:

```
## Backlog Analysis

TOTAL OPEN TASKS: N
AREA CLUSTERS: [list with task count per cluster]

### Dependency Matrix
| Task | Area | Files Touched | Conflicts With |
|------|------|---------------|----------------|
| TASK-012 | 14-theme | Theme.kt, ... | TASK-013, TASK-031 |
| TASK-020 | 23-screen | VerbDrillScreen.kt | (none) |
| ... |

### Parallel Groups (zero file overlap)
[Groups of tasks that can run simultaneously]

### Sequential Chains (shared files)
[Ordered lists of tasks that MUST run one after another]
```

### Phase 2: PLAN (main context, no agent)

From the analysis, construct batches:

**Batch construction rules:**
1. One batch = one area cluster (keep related work together)
2. Within a batch: max 5 agents parallel for independent tasks
3. Tasks with file conflicts within same area → sequential sub-waves inside the batch
4. Order by severity: CRITICAL first, then HIGH, then MEDIUM, then LOW
5. If single area has > 5 tasks → split into multiple batches (complete first batch before starting second)

**Output plan for user approval:**

```
## Execution Plan

### Batch 1: dark-mode (branch: batch/001-dark-mode)
  Wave 1 (3 agents, parallel):
    - TASK-031: CRITICAL — Dark Theme Color Palette Mismatch
    - TASK-012: Theme.kt Color Constants — Dark-Mode Adaptation
    - TASK-033: ProfileStatsPopup + InitialsAvatar Missing from Spec
  Wave 2 (2 agents, parallel — shares files with Wave 1):
    - TASK-013: TrainingScreen Dark-Mode Fix
    - TASK-014: VocabDrillScreen + DailyPracticeScreen Dark-Mode Fix
  Checkpoint: build + regression-check

### Batch 2: screen-audit (branch: batch/002-screen-audit)
  Wave 1 (5 agents, parallel):
    - TASK-020, TASK-021, TASK-022, TASK-023, TASK-024
  Wave 2 (5 agents, parallel):
    - TASK-025, TASK-026, TASK-027, TASK-028, TASK-029
  Checkpoint: build + regression-check

### Batch 3: misc-fixes (branch: batch/003-misc-fixes)
  Wave 1 (3 agents, parallel):
    - TASK-034, TASK-035, TASK-036, TASK-037
  Checkpoint: build + regression-check

TOTAL: N tasks across 3 batches, estimated K waves
```

**WAIT for user to approve or adjust the plan before proceeding.**

### Phase 3: EXECUTE (loop over batches)

For each approved batch:

#### 3a. Branch setup
```
git checkout main
git checkout -b batch/NNN-description
```
- If dirty working tree → stash or ask user first
- NEVER reuse a branch from another batch

#### 3b. Execute waves
For each wave in the batch (max 5 agents simultaneously):

1. Spawn agents — one per task. Each agent prompt contains:
   - The FULL content of its task file (inline, not just a reference)
   - The specific files it owns (from the dependency analysis)
   - Files it MUST NOT touch (from other tasks in same batch)
   - Build command to verify
   - Instruction: "After changes, report: files modified, build result, any issues"

2. Collect ALL agent results

3. Per-task evaluation:
   - Agent succeeded (build passes, files match task scope) → mark OK
   - Agent failed (build fail, wrong files, incomplete) → REVERT that task's changes:
     ```
     git checkout -- <files-that-agent-modified>
     ```
     Mark task as RETRY in README.md. Continue with other tasks.

4. Batch checkpoint:
   - `git diff --name-only` — verify only expected files changed
   - Build: `assembleDebug`
   - Run `/regression-check` skill (spawn regression agent)
   - If regression fails → fix in main context or spawn fix agent. Do NOT proceed to next batch.

5. Commit batch:
   ```
   git add <all-changed-files-for-this-batch>
   git commit -m "Batch NNN: description

   Tasks: TASK-xxx, TASK-yyy (DONE)
   Retry: TASK-zzz (failed, returned to backlog)

   Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
   "
   ```

6. Post-batch housekeeping:
   - For each DONE task: `git mv TASK-xxx.md DONE-TASK-xxx.md`
   - Update README.md links
   - For RETRY tasks: set status back to OPEN in README.md, add retry note
   - Commit housekeeping

#### 3c. Next batch
- Report to user: "Batch NNN complete. X/Y tasks DONE. Z returned to backlog."
- Ask: "Continue with Batch NNN+1?" or proceed if user said "run all" initially

### Phase 4: FINALIZE (after all batches)

1. Final regression-check across ALL batches
2. Update CHANGELOG.md with all completed tasks
3. Final commit
4. Report summary:
   ```
   ## Backlog Execution Complete

   Batches: N
   Tasks completed: X / Y
   Tasks retrying: Z
   Branches created: batch/001-xxx, batch/002-xxx, ...
   All builds: PASS
   Regression: PASS

   Ready for testing on device.
   ```

## Rules

1. **One batch = one branch.** Never mix batches on the same branch. Never move a task from one batch's branch to another.
2. **Max 5 agents per wave.** Always. No exceptions.
3. **Failed task = isolate + continue.** Revert only that task's files. Other tasks in the batch proceed. Mark reverted task as RETRY.
4. **Regression between batches is mandatory.** Even if batch had failures. Never skip.
5. **DONE-prefix on completion.** Every completed task gets renamed to `DONE-TASK-xxx.md`. This is non-negotiable.
6. **Hotspot files — single owner per wave:** TrainingViewModel.kt, GrammarMateApp.kt, Models.kt. Only one agent per wave may touch each.
7. **User approval required for plan.** Phase 2 output must be approved before Phase 3 starts. User can adjust batch composition, reorder, or skip tasks.
8. **Area filter:** If user provides an area (e.g., `/run-backlog dark-mode`), only analyze and plan tasks matching that area's Spec reference.

## Failure Handling

| Situation | Action |
|-----------|--------|
| Build fails for one agent | Revert that agent's files. Mark task RETRY. Continue batch. |
| Build fails for all agents | Revert entire wave. Mark all tasks RETRY. Report to user. |
| Regression fails after batch | Fix in main context. If unfixable, report and wait for user. |
| Agent modifies wrong files | Revert those files. Mark task RETRY. Warn in report. |
| 3+ retries for same task | Escalate to user. Task may need re-scoping. |

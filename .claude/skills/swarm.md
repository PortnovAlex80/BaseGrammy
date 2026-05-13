---
name: swarm
description: Kick-start the agent decomposition workflow. Use when the user asks to execute a task through the swarm of agents, or types /swarm with a task description. Forces Assessment → Decomposition → Wave execution as defined in CLAUDE.md.
---

# Swarm — Agent Decomposition Kick-Starter

This skill forces the full decomposition pipeline defined in CLAUDE.md "EXECUTION MODE" section. It MUST NOT be skipped or shortcut.

## Activation

User invokes `/swarm` with an optional task description. If no task is provided, ask: "What task should I decompose and execute?"

## Execution Pipeline (MANDATORY — follow exactly)

### Phase 1: Assessment Agent

Spawn **one** Assessment subagent (read-only, Explore type). The agent:

1. Reads the task description
2. Reads relevant spec files (check CLAUDE.md "Document map by component" for which spec applies)
3. Reads relevant source files to understand current state
4. Returns the **Assessment output format** exactly:

```
VERDICT: SUBAGENTS | TEAM
AGENTS: N (min 2)
COMPLEXITY: simple | moderate | complex
LAYERS AFFECTED: [list]
UNKNOWNS: [list]
RISKS: [list]
REASONING: [brief explanation]

DECOMPOSITION PLAN:
Wave 1 (N agents, max 5):
  - Step 1.1: [atomic action — 1 file / 1 function / 1 concept]
  - Step 1.2: [atomic action]
  - Step 1.3: [atomic action]
Wave 2 (N agents, max 5):
  - Step 2.1: [depends on Wave 1 results]
  ...
DEPENDENCIES: [what Wave N+1 needs from Wave N]
```

**Atomic step criteria:** modifies ≤ 2 files, adds ≤ 1 new function or class, contains ≤ 50 lines of new code, has a single clear success check.

**Assessment MUST NOT spawn agents or modify files.** It only reads and analyzes.

### Phase 2: Announce to User

After Assessment returns, announce in main context:

```
ASSESSMENT COMPLETE
Verdict: [SUBAGENTS/TEAM]
Complexity: [level]
Agents needed: [N]

DECOMPOSITION PLAN:
Wave 1: [list steps]
Wave 2: [list steps]
Dependencies: [what connects waves]

Proceed? (User can modify the plan)
```

**WAIT for user confirmation before spawning any implementation agents.**

### Phase 3: Wave Execution

Execute waves sequentially. For each wave:

1. **Spawn** all wave agents simultaneously (max 5)
2. **WAIT** for ALL agents in the wave to complete
3. **Checkpoint:**
   - List results: ✓/✗ for each agent
   - If files were modified → spawn a build-check subagent
   - Report: "Wave N complete. N/N succeeded. [any failures]"
4. **Gate:** If any agent failed — FIX before next wave. Never start Wave N+1 with unresolved failures from Wave N.
5. **Proceed** to next wave only after checkpoint passes

### Phase 4: Final Report

After all waves complete:

1. Summarize what was done (bullet list per wave)
2. Report final state: files changed, lines added/removed
3. Note any follow-up items or tech debt discovered
4. Ask user if they want a build verification

## Decomposition Strategy: LAYERS + TEAM vs WAVES + SUBAGENTS

### The Core Insight

**Layer-based decomposition maps naturally to TEAM mode.** When work is split by architecture layers (data → helpers → UI), agents have sequential dependencies — the helpers agent needs to know what the data agent changed. TEAM mode gives agents `SendMessage` for real-time coordination, while SUBAGENTS mode leaves them blind to each other's work.

**Wave-based decomposition maps naturally to SUBAGENTS mode.** When tasks are truly independent (different files, no shared state), fire-and-forget SUBAGENTS work fine — no coordination needed.

### Strategy Matrix

| Decomposition | Agent Mode | Coordination | Best for |
|---------------|------------|--------------|----------|
| **LAYERS** (data → helpers → UI) | **TEAM** | SendMessage between layers | Cascading refactors, type migrations, interface changes |
| **WAVES** (parallel independent tasks) | **SUBAGENTS** | None (main collects) | Audits, new features with new files, independent bug fixes |
| **SINGLE** | One agent | N/A | < 3 files, < 30 lines, one-layer bug fix |

### Why LAYERS + TEAM works better than WAVES + SUBAGENTS for refactoring

**WAVES problem (learned the hard way):**
- Wave 2 agents each touch different files but share Models.kt → file conflicts
- Build checkpoints are fake — build fails until ALL waves complete
- Agents can't adapt to each other's changes — they work blind
- Main context becomes a bottleneck coordinating merges

**LAYERS + TEAM solution:**
- Data agent finishes Models.kt changes → sends summary to helpers agent via SendMessage
- Helpers agent reads the summary and adapts its approach (e.g., knows new types, uses `.value` correctly)
- Each agent sees the full picture for its layer and makes informed decisions
- Build checkpoints are REAL — data layer compiles alone, then data+helpers, then full build
- Zero file conflicts by design (each agent owns its layer)

### The LAYER-TEAM Pattern

```
Phase 1: Assessment → identifies layers affected
Phase 2: TeamCreate → spawns team with layer-based roles:
  - DATA-AGENT: owns data/ (Models.kt, stores, parsers)
  - HELPERS-AGENT: owns ui/helpers/ (all helpers)
  - UI-AGENT: owns ui/ (TrainingViewModel, GrammarMateApp, screens)
Phase 3: Sequential execution with SendMessage coordination:
  1. DATA-AGENT works → commits → sends summary to HELPERS-AGENT
  2. HELPERS-AGENT reads summary, adapts → commits → sends summary to UI-AGENT
  3. UI-AGENT reads summary, adapts → commits
  4. Build verification
Phase 4: TeamDelete
```

### The WAVE-SUBAGENTS Pattern (for independent work)

```
Phase 1: Assessment → confirms zero file overlap between tasks
Phase 2: Spawn 2-5 SUBAGENTS in parallel (no team, no SendMessage)
Phase 3: Main collects all results → commit → build check
Phase 4: Next wave if needed
```

### When to use which

| Scenario | Strategy | Why |
|----------|----------|-----|
| Type migration across 20+ files | **LAYERS + TEAM** | Data agent changes types → helpers agent adapts → UI agent adapts. Real coordination. |
| Refactoring shared interfaces | **LAYERS + TEAM** | Interface in data/ → implementors in helpers/ → consumers in ui/ |
| Architecture audit / review | **WAVES + SUBAGENTS** | Read-only, no conflicts, fully independent. |
| Adding new feature (all new files) | **WAVES + SUBAGENTS** | New files don't conflict. |
| Bug fix in one layer | **Single agent** | No decomposition needed. |
| Mixed: some layers change, some don't | **HYBRID** | TEAM for affected layers, skip unchanged layers. |

### Flexibility rule

The decomposition strategy is a SPECTRUM, not a binary choice:
- A task might need LAYERS for one part and WAVES for another
- A small refactor might only touch 2 layers (data + helpers) — use 2 TEAM agents, skip UI
- An audit + fix might start as SUBAGENTS (analysis) then switch to TEAM (implementation)
- **Assessment decides.** The Assessment agent explicitly recommends the strategy and mode.

### Hard-won lessons (DO NOT VIOLATE)

1. **Never split a single file across agents.** If Models.kt needs changes, ONE agent owns it. Multiple agents touching the same file = lost changes or conflicts.

2. **Worktree isolation is unreliable for large refactors.** Changes can be silently lost when worktrees auto-cleanup. For write operations, use direct file writes to main working directory.

3. **Build checkpoints between waves are meaningless when changes span layers.** If Wave 2-A changes Models.kt types and Wave 2-B changes helpers that use those types, the build fails until BOTH are done. This is NOT a real checkpoint.

4. **Layer-based execution gives real checkpoints:** After data layer agent finishes → build check (data layer compiles). After helpers agent → build check (data + helpers compile). After UI agent → full build.

5. **Trivial tasks (add 3 constants) don't benefit from decomposition.** The overhead of spawning, reading files, context setup exceeds the work itself. Combine trivial tasks into one agent.

6. **Max 3 agents for implementation work.** More than 3 creates coordination overhead in main context that exceeds the parallelism benefit. Exception: read-only analysis (up to 5).

7. **TEAM mode is for coordination, not just "quality through conflict."** The original TEAM design (architect/reviewer/implementer arguing) is one use. Layer-based sequential coordination is another, equally valid use. Don't force the role-conflict pattern when layer coordination is what's needed.

## Rules

- **Main context is coordination only** — no code editing, no file writing, no builds in main context
- **Max 3 implementation agents** (5 for read-only analysis)
- **Layer-based + TEAM preferred** for any task touching shared files across layers
- **Wave-based + SUBAGENTS** only when tasks have zero file overlap
- **Never assign the same file to two agents**
- **Heavy-output (builds, git log) always via subagent**
- **Assessment agent is always the first and only agent in Phase 1**
- **User confirms plan before any implementation agents spawn**
- **After each layer agent completes: commit, then build-check, then notify next layer agent**

## Decomposition Trigger Checklist

Before accepting the Assessment verdict, verify the task triggers decomposition:
- Touches ≥ 3 files? → decompose
- Adds ≥ 1 new class or ≥ 3 new functions? → decompose
- Requires reading spec AND modifying code? → decompose
- Involves TrainingViewModel? → decompose
- Implementation > 30 lines? → decompose
- Has sequential dependencies? → decompose

If NONE of these apply — the task is trivial. Tell the user and ask if they still want the swarm pipeline or prefer a simple single-agent fix.

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

## Rules

- **Main context is coordination only** — no code editing, no file writing, no builds in main context
- **Max 5 agents per wave** — split into more waves if needed
- **One tool call per main context action** — spawn or read, never both
- **Heavy-output (builds, git log) always via subagent**
- **Assessment agent is always the first and only agent in Phase 1**
- **User confirms plan before any implementation agents spawn**

## Decomposition Trigger Checklist

Before accepting the Assessment verdict, verify the task triggers decomposition:
- Touches ≥ 3 files? → decompose
- Adds ≥ 1 new class or ≥ 3 new functions? → decompose
- Requires reading spec AND modifying code? → decompose
- Involves TrainingViewModel? → decompose
- Implementation > 30 lines? → decompose
- Has sequential dependencies? → decompose

If NONE of these apply — the task is trivial. Tell the user and ask if they still want the swarm pipeline or prefer a simple single-agent fix.

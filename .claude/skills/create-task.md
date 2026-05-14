---
name: create-task
description: Full requirements pipeline — discuss requirements, update specs, create implementation task prompt, link bidirectionally. Use when user says "создай задачу", "оформи требования", "запиши таску", "create task", or after identifying a bug / discussing a feature that needs spec updates before implementation.
---

# Create Task — Requirements → Spec → Task Prompt

## Core Axiom

**Task prompt is a self-contained implementation brief.** An execution agent with no session context must be able to pick it up and implement the changes. Every fix references spec sections + UC IDs + AC numbers for full traceability.

## Reference Example

`docs/specification/tasks/TASK-001-daily-cursor-independence.md` — the first task created with this pipeline. All tasks follow this structure.

## Main Loop

```
DISCUSS → SPEC UPDATE → TASK CREATION → LINK & INDEX → CONFIRM & COMMIT
```

Announce on activation:
```
CREATE-TASK PIPELINE: [topic]
Phases: Discuss → Update Specs → Create Task → Link → Commit
```

---

## Phase 1: Requirements Discussion

**Goal:** Understand what needs to change and why.

1. Read the relevant spec file(s) for the component the user is discussing. Use the document map in CLAUDE.md to find the right spec.
2. Read the relevant scenario file(s) and UC entries from `22-use-case-registry.md`.
3. Ask the user clarifying questions to pin down:
   - What is the current (wrong) behavior?
   - What should the correct behavior be?
   - Are there edge cases or constraints?
   - What is in scope vs out of scope?
4. Present a concise requirements summary (2-5 bullet points) for user approval.
5. **Do NOT proceed to Phase 2 without user confirmation of requirements.**

**Anti-patterns:**
- Jumping to spec writing without understanding the "why"
- Assuming the user wants the same solution you'd pick
- Skipping edge cases because "it's simple"

---

## Phase 2: Spec Update

**Goal:** Write the agreed requirements into spec files.

Update these files as needed:

| File | When to update |
|------|---------------|
| Main spec (e.g., `09-daily-practice.md`) | Always — add/modify sections with new behavior |
| `22-use-case-registry.md` | When UCs or ACs are added/modified |
| Scenario files (`scenario-NN-*.md`) | When discrepancies are resolved or new paths traced |
| `23-screen-elements.md` | When UI elements change |
| `trace-index.md` | When new symbols are introduced (usually during implementation, not here) |

**Rules:**
- Every new behavior gets a UC entry with numbered ACs
- Every resolved discrepancy gets `SPEC RESOLVED, CODE PENDING` status
- Changes must be internally consistent: UC IDs referenced in spec sections, scenario discrepancy numbers match
- Present a summary of all changes before writing: "Will update 09#9.5.1 (tense source), UC-24 AC2 (reversed), scenario-06 discrepancy #2 (resolved)"
- **Wait for user approval before editing files.**

---

## Phase 3: Task Creation

**Goal:** Create a self-contained implementation prompt in `docs/specification/tasks/`.

1. Determine next task number: read `docs/specification/tasks/README.md`, find the highest TASK-NNN, increment.
2. Generate kebab-case slug from the task title.
3. Create `docs/specification/tasks/TASK-NNN-{slug}.md` using this template:

```markdown
# TASK-NNN: {Title}

**Status:** OPEN
**Created:** {YYYY-MM-DD}
**Branch:** feature/{slug} (from {current branch})
**Spec:** {spec file#section references, comma-separated}
**UC:** {UC-IDs with AC numbers, comma-separated}
**Scenario:** {scenario file references, comma-separated}

---

## Problem
{1-3 paragraphs: what's wrong or what needs to change, why, root cause if known}

## Changes

### Fix 1: {Title}
**Discrepancy:** {#N or N/A} | **UC:** {UC-XX ACn} | **Spec:** {file#section}

{Description of what the code change does and why}

**Files:** {exact file paths — `package/ClassName.kt` — method name}

**Verification:** {how to check this fix works}

### Fix 2: {Title}
{...same structure...}

---

## Verification Checklist
1. {testable assertion derived from spec}
2. {testable assertion}
3. {... 5-7 items}

## Scope Boundaries
**Do NOT touch:**
- {explicit list of things outside scope}

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work (list affected screens/flows)
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: ... | | |
| | Fix 2: ... | | |
```

**Rules:**
- Task file MUST be self-contained — no references to "this session" or "as discussed above"
- Every fix MUST reference: spec section + UC + AC (traceability)
- Verification checklist MUST be derived from spec, not invented
- Regression plan MUST include: build check, tests, per-task verification, cross-task regression, UC/AC spot-check, spec sync
- Scope boundaries MUST be explicit (prevents scope creep)
- Pseudocode snippets are optional but helpful for non-obvious logic

---

## Phase 4: Link & Index

**Goal:** Make task discoverable from both directions.

1. Add at the end of the relevant spec section:
   ```
   **Implementation task:** [TASK-NNN: Title](tasks/TASK-NNN-{slug}.md)
   ```

2. Update `docs/specification/tasks/README.md` — add row to the table:
   ```
   | [TASK-NNN](TASK-NNN-{slug}.md) | {Title} | OPEN | {spec ref} | {date} |
   ```

---

## Phase 5: Confirm & Commit

1. Show user a summary:
   - Task: TASK-NNN — {title}
   - Spec files updated: {list}
   - Task file: `docs/specification/tasks/TASK-NNN-{slug}.md`
2. Wait for user approval.
3. Stage and commit all changes together (spec updates + task file + index update).
4. Use commit message format:
   ```
   Update {component} spec: {brief summary}
   
   {Bullet list of key spec changes}
   - Add task TASK-NNN: {title}
   
   Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
   ```

---

## Task Status Lifecycle

| Status | Meaning |
|--------|---------|
| OPEN | Created, awaiting implementation |
| IN PROGRESS | Implementation started |
| SPEC RESOLVED, CODE PENDING | Spec updated, code fix not yet applied |
| DONE | All fixes implemented and verified |

Update task status in the task file header when implementation begins/compleases.

---

## Anti-Patterns (learned from TASK-001 session)

| Anti-pattern | Why it's bad | What to do instead |
|-------------|-------------|-------------------|
| Writing task without updating specs first | Task becomes disconnected from spec truth | Always Phase 2 before Phase 3 |
| Vague verification ("should work") | Can't confirm implementation is correct | Derive checklist from spec UC/AC |
| Missing scope boundaries | Implementation leaks into unrelated areas | Explicit "Do NOT touch" list |
| Task references session context ("as we discussed") | Future agent has no session context | Self-contained, no session refs |
| Creating task without UC updates | No acceptance criteria to verify against | Every new behavior gets a UC |

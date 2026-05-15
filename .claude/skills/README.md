# Project Skills

Claude Code auto-discovers skills here. Each skill = one subdirectory with a `SKILL.md`.

## Structure (required)

```
.claude/skills/
├── add-feature/
│   └── SKILL.md        # ← frontmatter: name + description, then markdown body
├── swarm/
│   └── SKILL.md
└── ...
```

## Rules

- File **must** be named `SKILL.md` (not `skill.md`, not `<name>.md`)
- Each skill lives in its own subdirectory
- YAML frontmatter with `name` and `description` is required
- No plugin.json needed - project skills are auto-discovered
- Optional: `references/`, `scripts/`, `examples/` subdirs inside each skill
- Skills appear immediately after file changes (no restart needed)

## Triggering

Skills are invoked by the Skill tool when user intent matches the `description` field.
Write descriptions in third person with specific trigger phrases.

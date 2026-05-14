# Task Tracker

Mini task tracker linked to specs. Each task is a self-contained file with status, spec references, and acceptance criteria.

| Task | Title | Status | Spec | Created |
|------|-------|--------|------|---------|
| [TASK-001](TASK-001-daily-cursor-independence.md) | Daily Practice Cursor Independence | OPEN | 09-daily-practice | 2026-05-14 |
| [TASK-002](TASK-002-performance-caching.md) | In-Memory Data Caching for Performance | OPEN | 20-NFR, 02-data-stores | 2026-05-14 |
| [TASK-003](TASK-003-tts-thread-safety-error-ux.md) | TTS Thread Safety and Error UX | OPEN | 05-audio-tts-asr | 2026-05-14 |
| [TASK-004](TASK-004-welcome-dialog-max-attempts.md) | WelcomeDialog Max 3 Attempts | OPEN | 13-app-entry | 2026-05-14 |
| [TASK-005](TASK-005-tts-error-icon-memory.md) | TTS Error Icon for Memory/Loading Failures | OPEN | 05-audio-tts-asr | 2026-05-14 |
| [TASK-006](TASK-006-verb-drill-play-button-fix.md) | Verb Drill Play Button Fix (resume + TTS icons) | OPEN | 10-verb-drill, 12-training-card-session | 2026-05-14 |

## Re-Audit CRITICAL Fix Wave (2026-05-14)

Source: [EXECUTION-re-audit-11-criticals.md](EXECUTION-re-audit-11-criticals.md) — 4-wave plan fixing 11 CRITICALs from re-audit (YELLOW verdict)

| Wave | CRITICALs | Agents | Status |
|------|-----------|--------|--------|
| Wave 1: Data mutex | DATA-1, DATA-2, W-DATA-1 | 3 parallel | pending |
| Wave 2: UI + Build | UI-1, UI-2, XC-2, TEST-1, XC-3 | 3 parallel | pending |
| Wave 3: Testability | TEST-3, TEST-2 | 2 parallel | pending |
| Wave 4: Research | ARCH-1, XC-1 | 2 parallel (read-only) | pending |

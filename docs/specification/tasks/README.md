# Task Tracker

Mini task tracker linked to specs. Each task is a self-contained file with status, spec references, and acceptance criteria.

| Task | Title | Status | Spec | Created |
|------|-------|--------|------|---------|
| [DONE-TASK-001](DONE-TASK-001-daily-cursor-independence.md) | Daily Practice Cursor Independence | DONE | 09-daily-practice | 2026-05-14 |
| [DONE-TASK-002](DONE-TASK-002-performance-caching.md) | In-Memory Data Caching for Performance | DONE | 20-NFR, 02-data-stores | 2026-05-14 |
| [DONE-TASK-003](DONE-TASK-003-tts-thread-safety-error-ux.md) | TTS Thread Safety and Error UX | DONE | 05-audio-tts-asr | 2026-05-14 |
| [DONE-TASK-004](DONE-TASK-004-welcome-dialog-max-attempts.md) | WelcomeDialog Max 3 Attempts | DONE | 13-app-entry | 2026-05-14 |
| [DONE-TASK-005](DONE-TASK-005-tts-error-icon-memory.md) | TTS Error Icon for Memory/Loading Failures | DONE | 05-audio-tts-asr | 2026-05-14 |
| [DONE-TASK-006](DONE-TASK-006-verb-drill-play-button-fix.md) | Verb Drill Play Button Fix (resume + TTS icons) | DONE | 10-verb-drill, 12-training-card-session | 2026-05-14 |
| [DONE-TASK-007](DONE-TASK-007-verb-drill-exit-navigation.md) | Verb Drill Exit Navigation to HOME | DONE | 10-verb-drill | 2026-05-14 |
| [DONE-TASK-008](DONE-TASK-008-qr-share-translation.md) | Share Translation via QR Code | DONE | 12-training-card-session | 2026-05-15 |
| [TASK-009](TASK-009-profile-stats-popup.md) | Profile Stats Popup with CEFR Level | OPEN | custom spec | 2026-05-15 |
| [TASK-010](TASK-010-theme-mode-switching.md) | Theme Mode Switching (Light/Dark/System) | OPEN | 14-theme-and-ui-components | 2026-05-15 |
| [TASK-011](TASK-011-interface-language-switching.md) | Interface Language Switching (English/Russian) | OPEN | 14-theme-and-ui-components | 2026-05-15 |

## Re-Audit CRITICAL Fix Wave (2026-05-14)

Source: [EXECUTION-re-audit-11-criticals.md](EXECUTION-re-audit-11-criticals.md) — 4-wave plan fixing 11 CRITICALs from re-audit (YELLOW verdict)

| Wave | CRITICALs | Agents | Status |
|------|-----------|--------|--------|
| Wave 1: Data mutex | DATA-1, DATA-2, W-DATA-1 | 3 parallel | pending |
| Wave 2: UI + Build | UI-1, UI-2, XC-2, TEST-1, XC-3 | 3 parallel | pending |
| Wave 3: Testability | TEST-3, TEST-2 | 2 parallel | pending |
| Wave 4: Research | ARCH-1, XC-1 | 2 parallel (read-only) | pending |

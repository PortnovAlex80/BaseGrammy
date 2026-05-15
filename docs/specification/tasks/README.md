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
| [DONE-TASK-010](DONE-TASK-010-theme-mode-switching.md) | Theme Mode Switching (Light/Dark/System) | DONE | 14-theme-and-ui-components | 2026-05-15 |
| [DONE-TASK-011](DONE-TASK-011-interface-language-switching.md) | Interface Language Switching (English/Russian) | DONE | 14-theme-and-ui-components | 2026-05-15 |
| [TASK-012](TASK-012-theme-color-constants-dark-mode.md) | Theme.kt Color Constants — Dark-Mode Adaptation | OPEN | 14-theme | 2026-05-15 |
| [TASK-013](TASK-013-training-screen-dark-mode.md) | TrainingScreen Dark-Mode Fix (Drill + Mix + Progress) | OPEN | 14-theme | 2026-05-15 |
| [TASK-014](TASK-014-vocab-daily-dark-mode.md) | VocabDrillScreen + DailyPracticeScreen Dark-Mode Fix | OPEN | 14-theme | 2026-05-15 |
| [TASK-020](TASK-020-spec-vs-code-screen-audit.md) | Spec-vs-Code Screen Audit — Discrepancy Registry | OPEN | 23-screen-elements, 19-screen-catalog, 14-theme-and-ui-components | 2026-05-15 |
| [TASK-021](TASK-021-dark-theme-palette-mismatch.md) | Dark Theme Color Palette Mismatch | OPEN | 14-theme-and-ui-components | 2026-05-15 |
| [TASK-022](TASK-022-avatar-discrepancies.md) | HS-01 Avatar Discrepancies (3 Sub-issues) | OPEN | 23-screen-elements | 2026-05-15 |
| [TASK-023](TASK-023-profile-stats-initials-avatar-spec.md) | ProfileStatsPopup + InitialsAvatar Missing from Spec | OPEN | 23-screen-elements, 19-screen-catalog | 2026-05-15 |
| [TASK-024](TASK-024-legend-text-structure.md) | HS-15 Legend Text Structure Mismatch | OPEN | 23-screen-elements | 2026-05-15 |
| [TASK-025](TASK-025-tts-warning-icon-oom.md) | TS-09 TTS Warning Icon for OOM Undocumented | OPEN | 23-screen-elements, 14-theme-and-ui-components | 2026-05-15 |
| [TASK-026](TASK-026-duplicate-tts-buttons.md) | TS-27 + TCS-21 Duplicate TTS Buttons in Result Area | OPEN | 23-screen-elements | 2026-05-15 |
| [TASK-027](TASK-027-hint-answer-card-visibility.md) | DP-11 HintAnswerCard Visibility Condition (Spec Error) | OPEN | 23-screen-elements, 09-daily-practice | 2026-05-15 |

## Spec-vs-Code Screen Audit (2026-05-15)

Source: [TASK-020](TASK-020-spec-vs-code-screen-audit.md) — 17 discrepancies found across 316 UI elements. Parent task with 8 immediate child tasks (TASK-021 through TASK-027). Remaining discrepancies (D-08 through D-17) to be filed as TASK-028 through TASK-037.

| Severity | Count | Tasks |
|----------|-------|-------|
| CRITICAL | 1 | TASK-021 (dark theme palette) |
| HIGH | 3 | TASK-022 (avatar), TASK-023 (missing spec), TASK-026 (duplicate TTS, pending) |
| MEDIUM | 3 | TASK-024 (legend), TASK-025 (TTS icon), TASK-027 (DP-11 spec error) |
| LOW | 1 | (to be filed: TASK-028+) |

## Re-Audit CRITICAL Fix Wave (2026-05-14)

Source: [EXECUTION-re-audit-11-criticals.md](EXECUTION-re-audit-11-criticals.md) — 4-wave plan fixing 11 CRITICALs from re-audit (YELLOW verdict)

| Wave | CRITICALs | Agents | Status |
|------|-----------|--------|--------|
| Wave 1: Data mutex | DATA-1, DATA-2, W-DATA-1 | 3 parallel | pending |
| Wave 2: UI + Build | UI-1, UI-2, XC-2, TEST-1, XC-3 | 3 parallel | pending |
| Wave 3: Testability | TEST-3, TEST-2 | 2 parallel | pending |
| Wave 4: Research | ARCH-1, XC-1 | 2 parallel (read-only) | pending |

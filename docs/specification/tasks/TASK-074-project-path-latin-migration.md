# TASK-074: Migrate Project to Latin-Only Path

**Status:** OPEN
**Created:** 2026-05-19
**Branch:** N/A (infrastructure)
**Spec:** N/A
**UC:** N/A

---

## Problem

All unit tests fail with `ClassNotFoundException` because the project lives at a Cyrillic path:

```
D:\Разработка\BaseGrammy
```

Gradle 8.9 on Windows URL-encodes the path (`%D0%A0%D0%B0%D0%B7%D1%80%D0%B0%D0%B1%D0%BE%D1%82%D0%BA%D0%B0`), which breaks the test worker classpath resolution. Every `testDebugUnitTest` run produces 100% `initializationError` for all test classes — including pure-Kotlin tests (FlowerCalculator, Normalizer) that don't use Robolectric.

The project already has `android.overridePathCheck=true` in `gradle.properties` which allows the build to succeed, but the **test runner** still fails because Gradle's worker process can't load classes from the encoded path.

### Impact

- Zero test execution possible on developer machine
- TASK-073 test suite (44+ new tests) compiles but cannot run
- No CI safety net — any regression goes undetected until manual testing
- Build (`assembleDebug`) works fine — only tests are affected

---

## Goal

Move the project to a Latin-only path that mirrors the current directory structure:

```
Current:  D:\Разработка\BaseGrammy
Target:   D:\Dev\BaseGrammy     (or D:\Development\BaseGrammy)
```

The new path must:
1. Contain no non-ASCII characters
2. Be at the same depth level (2 levels from drive root)
3. Preserve git history (`.git` directory)
4. Not break any hardcoded paths in configs, scripts, or IDE settings

---

## Investigation Items

### 1. Path References Audit

Search the entire project for hardcoded references to the current path:

```
grep -ri "Разработка" --include="*.kts" --include="*.properties" --include="*.xml" --include="*.gradle" .
grep -ri "D:\\\\Разработка" .
grep -ri "D:/Разработка" .
```

Check these specific files:
- `gradle.properties` — `android.overridePathCheck`
- `.idea/` workspace files — SDK paths, run configs
- `local.properties` — `sdk.dir`
- `app/build.gradle.kts` — any hardcoded paths
- `libs/` — any native library path references
- `.claude/` settings and memory — file path references

### 2. Git Integrity

Moving a git repo is straightforward (copy the folder), but verify:
- `.git` directory moves cleanly
- No git hooks reference absolute paths
- `.gitconfig` local config (if any) doesn't hardcode the path
- Worktrees in `.claude/worktrees/` — these contain absolute paths and must be cleaned up

### 3. IDE Configuration

Android Studio / IntelliJ stores absolute paths in:
- `.idea/workspace.xml` — run configs, recent files
- `.idea/*.xml` — module paths, SDK references
- `.idea/caches/` — can be deleted and regenerated

Determine: is it easier to delete `.idea/` and re-import, or edit XML files?

### 4. Claude Code Configuration

- `.claude/settings.json` — check for absolute path references
- `.claude/settings.local.json` — same
- `C:\Users\user\.claude\projects\d-------------BaseGrammy\` — this directory is derived from the project path; verify it auto-regenerates or needs manual migration
- Memory files at `C:\Users\user\.claude\projects\d-------------BaseGrammy\memory\` — these should persist regardless of project path change

### 5. Gradle Cache

- `~/.gradle/caches/` — contains cached build artifacts keyed by project path
- `~/.gradle/daemon/` — running daemons tied to old path
- Need `gradle --stop` + cache cleanup after move

### 6. Android SDK & NDK

- `local.properties` → `sdk.dir` — likely points to a fixed location, verify
- NDK paths in `build.gradle.kts` — should be relative or SDK-relative
- Sherpa-ONNX AAR in `libs/` — relative path, should be fine

### 7. Verification Plan

After move:
1. `gradle --stop`
2. Delete `.gradle/` cache in project root
3. Delete `app/build/`
4. Delete `.idea/` (let IDE regenerate)
5. Open project in Android Studio from new path
6. `assembleDebug` — must pass
7. `testDebugUnitTest` — must pass (this is the whole point)
8. Verify APK installs on device

---

## Scope

**In scope:**
- Audit all path references
- Document migration steps
- Execute the migration
- Verify build + tests pass

**Out of scope:**
- Changing any source code
- Changing project structure or package names
- CI/CD setup (no CI exists yet)

## Risk

| Risk | Mitigation |
|------|------------|
| Git history corruption | Copy (not move) first; verify `git log` works before deleting old |
| IDE loses settings | Delete `.idea/` and re-import; settings are mostly auto-detected |
| Worktree absolute paths | Delete `.claude/worktrees/` contents before move |
| Gradle daemon stale | `gradle --stop` before move |
| Memory files lost | Back up `~/.claude/projects/` before move; memory auto-creates on new path |

---

## Git

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Step | Status | Notes |
|------|------|--------|-------|
| | Path audit | | |
| | Migration steps documented | | |
| | Migration executed | | |
| | Build verified | | |
| | Tests verified | | |

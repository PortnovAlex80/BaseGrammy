"""Keep the APK's asset inputs reproducible from a clean Git checkout."""

from pathlib import Path
import re
import subprocess


ROOT = Path(__file__).resolve().parents[1]
ASSETS = Path("app/src/main/assets")
LESSON_STORE = Path("app/src/main/java/com/alexpo/grammermate/data/LessonStore.kt")
DEFAULT_PACK = re.compile(
    r'^\s*LanguageManager\.DefaultPack\("[^"\n]+",\s*"([^"\n]+)"\)',
    re.MULTILINE,
)


def required_packs(source: str) -> list[str]:
    """Read live declarations; commented-out language defaults remain disabled."""
    return DEFAULT_PACK.findall(source)


def check_assets(root: Path, defaults: list[str], tracked: set[str]) -> list[str]:
    problems = []
    for relative in defaults:
        path = ASSETS / relative
        if not (root / path).is_file():
            problems.append(f"Missing default pack: {path.as_posix()}")
    for path in sorted((root / ASSETS).rglob("*")):
        if path.is_file():
            relative = path.relative_to(root).as_posix()
            if relative not in tracked:
                problems.append(f"Untracked APK asset input: {relative}")
    return problems


def main() -> int:
    tracked = set(subprocess.check_output(
        ["git", "ls-files", "--", ASSETS.as_posix()], cwd=ROOT, text=True
    ).splitlines())
    defaults = required_packs((ROOT / LESSON_STORE).read_text(encoding="utf-8"))
    if not defaults:
        print("No default pack declarations found; update the asset check for the current catalog")
        return 1
    problems = check_assets(ROOT, defaults, tracked)
    if problems:
        print("\n".join(problems))
        return 1
    print(f"Bundled assets OK ({len(defaults)} default packs; all APK inputs tracked)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

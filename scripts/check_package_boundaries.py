"""Fail CI when production Kotlin imports cross the agreed package boundaries."""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/alexpo/grammermate"
APP = "com.alexpo.grammermate"
IMPORT = re.compile(r"^\s*import\s+([\w.*]+)", re.MULTILINE)

# These imports are directed collaborations, not ownership of another feature's state.
ALLOWED_FEATURE_EDGES = {
    ("boss", "training"),
    ("daily", "progress"),
    ("daily", "training"),
    ("training", "progress"),
    ("vocab", "training"),
}


def violation(parts: tuple[str, ...], imported: str) -> str | None:
    if not parts:
        return None
    layer = parts[0]
    is_ui = imported.startswith(f"{APP}.ui.")
    is_compose = imported.startswith("androidx.compose.")
    is_navigation = imported.startswith("androidx.navigation.")

    if layer in {"data", "feature", "shared"} and (is_ui or is_compose or is_navigation):
        return "data/feature/shared may not depend on UI, Compose or Navigation"
    if layer == "data" and imported.startswith((f"{APP}.feature.", f"{APP}.shared.")):
        return "data may not depend on a feature or shared service"
    if layer == "shared" and imported.startswith(f"{APP}.feature."):
        return "shared contracts and services may not depend on a business feature"

    if layer == "feature" and imported.startswith(f"{APP}.feature."):
        target = imported[len(f"{APP}.feature."):].split(".", 1)[0]
        edge = (parts[1], target)
        if target != parts[1] and edge not in ALLOWED_FEATURE_EDGES:
            return f"unlisted feature dependency {edge[0]} -> {edge[1]}"

    if len(parts) >= 3 and parts[:2] == ("ui", "designsystem") and parts[2] in {
        "foundation", "atoms", "molecules"
    }:
        if (imported.startswith(f"{APP}.data.") or
                imported.startswith(f"{APP}.feature.") or
                imported.startswith(f"{APP}.ui.navigation.") or
                is_navigation or imported.rsplit(".", 1)[-1].endswith(("ViewModel", "Store"))):
            return "design-system primitives may not depend on business or navigation"

    if len(parts) >= 3 and parts[:2] == ("ui", "features"):
        prefix = f"{APP}.ui.features."
        if imported.startswith(prefix):
            target = imported[len(prefix):].split(".", 1)[0]
            if target != parts[2]:
                return f"cross-feature UI dependency {parts[2]} -> {target}"
    return None


def check_source(relative: Path, source: str) -> list[str]:
    problems = []
    for imported in IMPORT.findall(source):
        reason = violation(relative.parts, imported)
        if reason:
            problems.append(f"{relative}: {imported}: {reason}")
    return problems


def main() -> int:
    files = sorted(SOURCE.rglob("*.kt"))
    problems = []
    for path in files:
        problems.extend(check_source(path.relative_to(SOURCE), path.read_text(encoding="utf-8")))
    if problems:
        print("\n".join(problems))
        return 1
    print(f"Package boundaries OK ({len(files)} production Kotlin files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

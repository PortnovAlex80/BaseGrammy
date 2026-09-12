# -*- coding: utf-8 -*-
"""Собрать ITALIAN_IMPERIAL_COURSE.zip из уроков Императорского курса.

Пак простой: manifest.json (schema v2, 19 глав-фаз лестницы) + 439 уроков
в корне зипа. Без stories / grammar_chips / дрилов / backgroundVocab.

Источники:
  - фазы и их диапазоны: docs/lesson-methodology/italian/ITALIAN_LADDER_v2.html (навигация)
  - уроки: D:/Development/Italianpacks/ITALIAN_IMPERIAL_COURSE/lesson_NNN_GYYY.csv

Выход: app/src/main/assets/grammarmate/packs/ITALIAN_IMPERIAL_COURSE.zip

ВНИМАНИЕ при обновлении урока/пака на телефоне: LanguageManager
.updateDefaultPacksIfNeeded переустанавливает встроенный пак только когда
packVersion в ассете отличается от установленной — поднимай PACK_VERSION.
"""
import json
import re
import sys
import zipfile
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parent.parent
LADDER = ROOT / "docs/lesson-methodology/italian/ITALIAN_LADDER_v2.html"
# Канонический источник уроков — git-каталог репозитория (провенанс аудита:
# пак должен пересобираться из одного репозитория).
LESSONS_DIR = ROOT / "docs/lesson-methodology/italian/imperial-lessons"
# Дриллы берём из Express-зипа в ассетах (тот же язык; репо-источник).
EXPRESS_ZIP = ROOT / "app/src/main/assets/grammarmate/packs/ITALIAN_EXPRESS_SHORT.zip"
VERB_DRILL_FILES = ["it_verb_groups_all.csv"]
VOCAB_DRILL_FILES = [
    "it_drill_nouns.csv", "it_drill_verbs.csv", "it_drill_adjectives.csv",
    "it_drill_adverbs.csv", "it_drill_numbers.csv", "it_drill_pronouns.csv",
]
OUT_ZIP = ROOT / "app/src/main/assets/grammarmate/packs/ITALIAN_IMPERIAL_COURSE.zip"

PACK_VERSION = "v4"
PACK_ID = "ITALIAN_IMPERIAL"

nav_html = LADDER.read_text(encoding="utf-8")
phases = re.findall(
    r'<a href="#phase-(\d+)">\s*<span>\d+</span>\s*<b>(.*?)</b>\s*'
    r'<small>L(\d+)[–-]L(\d+)</small>',
    nav_html,
    re.S,
)
phases = [(int(n), re.sub(r"\s+", " ", t).strip(), int(a), int(b)) for n, t, a, b in phases]
assert len(phases) == 19, f"expected 19 phases in ladder nav, got {len(phases)}"

lesson_files = sorted(LESSONS_DIR.glob("lesson_*.csv"))
assert len(lesson_files) == 439, f"expected 439 lessons, got {len(lesson_files)}"
stems = [f.stem for f in lesson_files]
nums = [int(re.match(r"lesson_(\d{3})_", s).group(1)) for s in stems]
assert nums == list(range(1, 440)), "lesson numbers not contiguous 1..439"

chapters = []
covered = []
for order, title, a, b in sorted(phases):
    chapter_stems = [s for s, n in zip(stems, nums) if a <= n <= b]
    assert chapter_stems, f"phase {order} ({title}) has no lessons"
    covered.extend(len(chapter_stems) * [order])
    chapters.append(
        {
            "chapterId": f"chapter_{order}",
            "order": order,
            "title": f"Фаза {order} — {title}",
            "subtitle": f"L{a:03d}–L{b:03d}",
            "lessons": chapter_stems,
        }
    )
assert sum(len(c["lessons"]) for c in chapters) == 439, "chapter coverage != 439"

manifest = {
    "schemaVersion": 2,
    "packId": PACK_ID,
    "packVersion": PACK_VERSION,
    "language": "it",
    "displayName": "Imperial Course Italian from Alex Po",
    "description": "Italian imperial course: grammar ladder v2, 19 phases, 439 lessons",
    "chapters": chapters,
    "verbDrill": {"files": VERB_DRILL_FILES},
    "vocabDrill": {"files": VOCAB_DRILL_FILES},
}

drill_files = VERB_DRILL_FILES + VOCAB_DRILL_FILES
with zipfile.ZipFile(EXPRESS_ZIP) as ze:
    drill_bytes = {name: ze.read(name) for name in drill_files}
    missing_drills = [n for n in drill_files if n not in ze.namelist()]
assert not missing_drills, f"drills not found in Express zip: {missing_drills}"

OUT_ZIP.parent.mkdir(parents=True, exist_ok=True)
tmp = OUT_ZIP.with_suffix(".zip.tmp")
with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=1))
    for f in lesson_files:
        z.write(f, f.name)
    for name, blob in drill_bytes.items():
        z.writestr(name, blob)
tmp.replace(OUT_ZIP)

# verify
with zipfile.ZipFile(OUT_ZIP) as z:
    names = z.namelist()
    m = json.loads(z.read("manifest.json").decode("utf-8"))
    ref_stems = [s for c in m["chapters"] for s in c["lessons"]]
    assert m["packId"] == PACK_ID and m["packVersion"] == PACK_VERSION
    assert len(names) == 440 + len(drill_files), len(names)
    assert sorted(ref_stems) == sorted(stems), "manifest refs != files"
    missing = [s for s in ref_stems if f"{s}.csv" not in names]
    assert not missing, f"missing files: {missing[:5]}"
    assert m["verbDrill"]["files"] == VERB_DRILL_FILES
    assert m["vocabDrill"]["files"] == VOCAB_DRILL_FILES
    for name in drill_files:
        assert z.read(name) == drill_bytes[name], f"drill corrupted: {name}"
    # первый урок: строка 1 = тема
    first = z.read("lesson_001_G001.csv").decode("utf-8").split("\r\n")[0]
    assert first.startswith("G001 - "), first
print(f"OK: {OUT_ZIP.name} — {len(names)} entries, 19 chapters, "
      f"{len(ref_stems)} lessons, {len(drill_files)} drill files, "
      f"packVersion {PACK_VERSION}, {OUT_ZIP.stat().st_size} bytes")
for c in chapters:
    print(f"  {c['chapterId']}: {c['subtitle']} — {len(c['lessons'])} уроков — {c['title']}")

"""
Build ITALIAN_FULL_COURSE.zip pack for GrammarMate.

Components:
- manifest.json (schema v2, 4 chapters, 63 lessons)
- 63 lesson CSVs (renamed from A01.csv -> lesson_01_A01.csv)
- 63 grammar chips (copied from ITALIAN_EXPRESS_SHORT)
- 7 drill files (copied from ITALIAN_EXPRESS)
- 4 story files (copied from ITALIAN_EXPRESS_SHORT)
"""

import zipfile
import json
import os
import sys

BASE = "D:/Development/BaseGrammy"
ASSETS = f"{BASE}/app/src/main/assets/grammarmate/packs"
LESSON_DIR = f"{BASE}/docs/lesson-methodology/italian/full-course-63"
OUTPUT = f"{ASSETS}/ITALIAN_FULL_COURSE.zip"

# Lesson mapping: (csv_filename, zip_filename, lesson_id)
LESSONS = []
# Block A: A01-A16 -> lesson_01_A01 .. lesson_16_A16
for i in range(1, 17):
    block_key = f"A{i:02d}"
    lesson_num = i
    LESSONS.append((f"{block_key}.csv", f"lesson_{lesson_num:02d}_{block_key}.csv", f"lesson_{lesson_num:02d}_{block_key}"))

# Block B: B01-B27 -> lesson_17_B01 .. lesson_43_B27
for i in range(1, 28):
    block_key = f"B{i:02d}"
    lesson_num = 16 + i
    LESSONS.append((f"{block_key}.csv", f"lesson_{lesson_num:02d}_{block_key}.csv", f"lesson_{lesson_num:02d}_{block_key}"))

# Block C: C01-C20 -> lesson_44_C01 .. lesson_63_C20
for i in range(1, 21):
    block_key = f"C{i:02d}"
    lesson_num = 43 + i
    LESSONS.append((f"{block_key}.csv", f"lesson_{lesson_num:02d}_{block_key}.csv", f"lesson_{lesson_num:02d}_{block_key}"))

# Manifest structure (4 chapters, same as ITALIAN_EXPRESS_SHORT)
manifest = {
    "schemaVersion": 2,
    "packId": "ITALIAN_FULL_COURSE",
    "packVersion": "v1",
    "language": "it",
    "displayName": "Full Course Italian from Alex Po",
    "description": "Italian full course: chapters 0-3, all 63 lessons A01-C20",
    "chapters": [
        {
            "chapterId": "chapter_0",
            "order": 0,
            "title": "Введение — Архитектура языка",
            "subtitle": "До языка",
            "storyFile": "chapter_00.md",
            "lessons": []
        },
        {
            "chapterId": "chapter_1",
            "order": 1,
            "title": "Глава 1 — Настоящее: я и мир вокруг",
            "subtitle": "Настоящее",
            "storyFile": "chapter_01.md",
            "lessons": [l[2] for l in LESSONS[0:16]]  # A01-A16
        },
        {
            "chapterId": "chapter_2",
            "order": 2,
            "title": "Глава 2 — Прошлое и возможное",
            "subtitle": "Прошлое и возможное",
            "storyFile": "chapter_02.md",
            "lessons": [l[2] for l in LESSONS[16:43]]  # B01-B27
        },
        {
            "chapterId": "chapter_3",
            "order": 3,
            "title": "Глава 3 — Глубина: субъективная реальность и голоса других",
            "subtitle": "Глубина",
            "storyFile": "chapter_03.md",
            "lessons": [l[2] for l in LESSONS[43:63]]  # C01-C20
        }
    ],
    "verbDrill": {
        "files": [
            "it_verb_groups_all.csv"
        ]
    },
    "vocabDrill": {
        "files": [
            "it_drill_nouns.csv",
            "it_drill_verbs.csv",
            "it_drill_adjectives.csv",
            "it_drill_adverbs.csv",
            "it_drill_numbers.csv",
            "it_drill_pronouns.csv"
        ]
    }
}

print(f"Building {OUTPUT}...")
print(f"  Lessons: {len(LESSONS)}")
print(f"  Chapters: {len(manifest['chapters'])}")

# Source zips
express_zip = zipfile.ZipFile(f"{ASSETS}/ITALIAN_EXPRESS.zip", "r")
short_zip = zipfile.ZipFile(f"{ASSETS}/ITALIAN_EXPRESS_SHORT.zip", "r")

with zipfile.ZipFile(OUTPUT, "w", zipfile.ZIP_DEFLATED) as zf:
    # 1. Write manifest
    manifest_bytes = json.dumps(manifest, indent=2, ensure_ascii=False).encode("utf-8")
    zf.writestr("manifest.json", manifest_bytes)
    print("  Written: manifest.json")

    # 2. Write lesson CSVs (from full-course-63 directory, renamed)
    for csv_name, zip_name, lesson_id in LESSONS:
        csv_path = os.path.join(LESSON_DIR, csv_name)
        if not os.path.exists(csv_path):
            print(f"  WARNING: Missing lesson CSV: {csv_path}")
            continue
        with open(csv_path, "rb") as f:
            data = f.read()
        zf.writestr(zip_name, data)
    print(f"  Written: {len(LESSONS)} lesson CSVs")

    # 3. Copy drill files from ITALIAN_EXPRESS
    drill_files = [
        "it_verb_groups_all.csv",
        "it_drill_nouns.csv",
        "it_drill_verbs.csv",
        "it_drill_adjectives.csv",
        "it_drill_adverbs.csv",
        "it_drill_numbers.csv",
        "it_drill_pronouns.csv"
    ]
    for df in drill_files:
        zf.writestr(df, express_zip.read(df))
    print(f"  Written: {len(drill_files)} drill files")

    # 4. Copy grammar chips from ITALIAN_EXPRESS_SHORT (all 63)
    chip_count = 0
    for name in short_zip.namelist():
        if name.startswith("grammar_chips/"):
            zf.writestr(name, short_zip.read(name))
            chip_count += 1
    print(f"  Written: {chip_count} grammar chips")

    # 5. Copy story files from ITALIAN_EXPRESS_SHORT
    story_count = 0
    for name in short_zip.namelist():
        if name.startswith("stories/"):
            zf.writestr(name, short_zip.read(name))
            story_count += 1
    print(f"  Written: {story_count} story files")

express_zip.close()
short_zip.close()

# Verify
print("\nVerifying...")
with zipfile.ZipFile(OUTPUT, "r") as zf:
    names = zf.namelist()
    print(f"  Total files in ZIP: {len(names)}")

    # Verify manifest
    m = json.loads(zf.read("manifest.json"))
    total_lessons_in_chapters = sum(len(ch["lessons"]) for ch in m["chapters"])
    print(f"  Pack ID: {m['packId']}")
    print(f"  Display name: {m['displayName']}")
    print(f"  Schema version: {m['schemaVersion']}")
    print(f"  Chapters: {len(m['chapters'])}")
    print(f"  Lessons in chapters: {total_lessons_in_chapters}")

    # Verify all lesson CSVs present
    missing = []
    for csv_name, zip_name, lesson_id in LESSONS:
        if zip_name not in names:
            missing.append(zip_name)
    if missing:
        print(f"  MISSING lesson files: {missing}")
    else:
        print(f"  All {len(LESSONS)} lesson files present")

    # Verify chips
    chip_files = [n for n in names if n.startswith("grammar_chips/")]
    print(f"  Grammar chips: {len(chip_files)}")

    # Verify stories
    story_files = [n for n in names if n.startswith("stories/")]
    print(f"  Story files: {len(story_files)}")

    # Verify drills
    drill_present = [df for df in drill_files if df in names]
    print(f"  Drill files: {len(drill_present)}/{len(drill_files)}")

print(f"\nDone: {OUTPUT}")

#!/usr/bin/env python3
"""
Create 3 ZIP packs from existing IT_EXPRESS.zip and story files.
Generates: ALLEGORY_PACK.zip, PORTAL_PACK.zip, POP_GRAMMAR_PACK.zip
"""

import json
import os
import shutil
import zipfile
from pathlib import Path
from typing import Dict, List, Any

# Configuration
BASE_DIR = Path(__file__).parent.parent
IT_EXPRESS_ZIP = BASE_DIR / "app" / "src" / "main" / "assets" / "grammarmate" / "packs" / "IT_EXPRESS.zip"
OUTPUT_DIR = BASE_DIR / "app" / "src" / "main" / "assets" / "grammarmate" / "packs"
TEMP_DIR = BASE_DIR / "tools" / "temp_pack_creation"

# Story directories
STORY_DIRS = {
    "ALLEGORY_PACK": BASE_DIR / "docs" / "lesson-methodology" / "books" / "book1-allegory" / "rus",
    "PORTAL_PACK": BASE_DIR / "docs" / "lesson-methodology" / "books" / "book2-portal-fantasy" / "rus",
    "POP_GRAMMAR_PACK": BASE_DIR / "docs" / "lesson-methodology" / "books" / "book3-pop-grammar" / "rus",
}

# Pack configurations
PACK_CONFIGS = {
    "ALLEGORY_PACK": {
        "packId": "allegory_story_roadmap",
        "displayName": "Garden of Grammar",
        "description": "Allegorical grammar learning through garden stories"
    },
    "PORTAL_PACK": {
        "packId": "portal_story_roadmap",
        "displayName": "Portal Fantasy",
        "description": "Portal fantasy grammar learning adventures"
    },
    "POP_GRAMMAR_PACK": {
        "packId": "pop_grammar_roadmap",
        "displayName": "Popular Grammar",
        "description": "Popular science approach to grammar learning"
    },
}

# Chapter subtitles from Russian story files (default fallbacks)
CHAPTER_SUBTITLES = {
    "chapter_0": "До языка",
    "chapter_1": "Настоящее",
    "chapter_2": "История и горизонт",
    "chapter_3": "Свёртки и ткань",
    "chapter_4": "Гипотеза о прошлом",
    "chapter_5": "Сборка всего",
    "chapter_6": "Та мысль",
}

# Lesson distribution across chapters
CHAPTER_LESSONS = {
    "chapter_0": [],  # Empty - intro chapter
    "chapter_1": [
        "lesson_01_A01", "lesson_02_A02", "lesson_03_A03", "lesson_04_A04",
        "lesson_05_A05", "lesson_06_A06", "lesson_07_A07", "lesson_08_A08",
        "lesson_09_A09", "lesson_10_A10", "lesson_11_A11", "lesson_12_A12",
        "lesson_13_A13", "lesson_14_A14", "lesson_15_A15", "lesson_16_A16"
    ],
    "chapter_2": [
        "lesson_17_B01", "lesson_18_B02", "lesson_19_B03", "lesson_20_B04",
        "lesson_21_B05", "lesson_22_B06", "lesson_23_B07", "lesson_24_B08",
        "lesson_25_B09", "lesson_26_B10"
    ],
    "chapter_3": [
        "lesson_27_B11", "lesson_28_B12", "lesson_29_B13", "lesson_30_B14",
        "lesson_31_B15", "lesson_32_B16", "lesson_33_B17", "lesson_34_B18",
        "lesson_35_B19", "lesson_36_B20", "lesson_37_B21", "lesson_38_B22",
        "lesson_39_B23", "lesson_40_B24", "lesson_41_B25", "lesson_42_B26",
        "lesson_43_B27"
    ],
    "chapter_4": [
        "lesson_44_C01", "lesson_45_C02", "lesson_46_C03"
    ],
    "chapter_5": [
        "lesson_47_C04", "lesson_48_C05", "lesson_49_C06", "lesson_50_C07",
        "lesson_51_C08", "lesson_52_C09", "lesson_53_C10", "lesson_54_C11",
        "lesson_55_C12", "lesson_56_C13", "lesson_57_C14", "lesson_58_C15",
        "lesson_59_C16", "lesson_60_C17", "lesson_61_C18"
    ],
    "chapter_6": [
        "lesson_62_C19", "lesson_63_C20"
    ],
}


def extract_title_from_story(story_path: Path) -> str:
    """Extract chapter title from Russian story file."""
    try:
        with open(story_path, 'r', encoding='utf-8') as f:
            first_line = f.readline().strip()
            # Format: "# Глава X — Title"
            if " — " in first_line:
                return first_line.split(" — ")[1]
            return CHAPTER_TITLES.get(story_path.stem, "Unknown")
    except Exception:
        return CHAPTER_TITLES.get(story_path.stem, "Unknown")


def create_manifest(pack_name: str, config: Dict[str, str], story_dir: Path) -> Dict[str, Any]:
    """Create manifest.json for a pack."""
    chapters = []

    for i in range(7):  # chapter_0 through chapter_6
        chapter_id = f"chapter_{i}"
        story_file = f"chapter_0{i}.md" if i < 10 else f"chapter_{i}.md"
        story_file = f"chapter_0{i}.md"  # All are 00-06

        # Extract title from story file
        story_path = story_dir / story_file
        title = extract_title_from_story(story_path)

        chapter = {
            "chapterId": chapter_id,
            "order": i,
            "title": "Intro" if i == 0 else f"Chapter {i} - {title}",
            "subtitle": CHAPTER_SUBTITLES.get(chapter_id, ""),
            "storyFile": story_file,
            "lessons": CHAPTER_LESSONS.get(chapter_id, [])
        }
        chapters.append(chapter)

    manifest = {
        "schemaVersion": 2,
        "packId": config["packId"],
        "packVersion": "v1",
        "language": "it",
        "displayName": config["displayName"],
        "description": config["description"],
        "chapters": chapters
    }

    return manifest


def setup_temp_directory() -> Path:
    """Create and return temporary directory for pack creation."""
    if TEMP_DIR.exists():
        shutil.rmtree(TEMP_DIR)
    TEMP_DIR.mkdir(parents=True, exist_ok=True)
    return TEMP_DIR


def extract_it_express(temp_dir: Path) -> None:
    """Extract IT_EXPRESS.zip to temporary directory."""
    print(f"Extracting {IT_EXPRESS_ZIP}...")
    with zipfile.ZipFile(IT_EXPRESS_ZIP, 'r') as zip_ref:
        zip_ref.extractall(temp_dir)
    print("Extraction complete.")


def create_pack(pack_name: str, temp_dir: Path) -> None:
    """Create a single pack ZIP."""
    config = PACK_CONFIGS[pack_name]
    story_dir = STORY_DIRS[pack_name]
    pack_temp_dir = temp_dir / pack_name
    pack_temp_dir.mkdir(parents=True, exist_ok=True)

    print(f"Creating {pack_name}...")

    # Create manifest
    manifest = create_manifest(pack_name, config, story_dir)
    manifest_path = pack_temp_dir / "manifest.json"
    with open(manifest_path, 'w', encoding='utf-8') as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)
    print(f"  Created manifest.json")

    # Copy lessons (all packs share the same lessons)
    it_extract_dir = temp_dir / "it_express"
    for lesson_file in it_extract_dir.glob("lesson_*.csv"):
        shutil.copy2(lesson_file, pack_temp_dir / lesson_file.name)
    print(f"  Copied 63 lesson files")

    # Copy grammar chips (all packs share the same chips)
    grammar_chips_dir = pack_temp_dir / "grammar_chips"
    grammar_chips_dir.mkdir(parents=True, exist_ok=True)
    src_chips_dir = it_extract_dir / "grammar_chips"
    for chip_file in src_chips_dir.glob("*.json"):
        shutil.copy2(chip_file, grammar_chips_dir / chip_file.name)
    print(f"  Copied 63 grammar chip files")

    # Copy story files (unique per pack)
    stories_dir = pack_temp_dir / "stories"
    stories_dir.mkdir(parents=True, exist_ok=True)
    for i in range(7):
        story_file = f"chapter_0{i}.md"
        src_story = story_dir / story_file
        if src_story.exists():
            shutil.copy2(src_story, stories_dir / story_file)
    print(f"  Copied 7 story files from {story_dir}")

    # Create ZIP
    output_zip = OUTPUT_DIR / f"{pack_name}.zip"
    with zipfile.ZipFile(output_zip, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for file_path in pack_temp_dir.rglob("*"):
            if file_path.is_file():
                arcname = file_path.relative_to(pack_temp_dir)
                zipf.write(file_path, arcname)

    print(f"  Created {output_zip}")
    print(f"  Package size: {output_zip.stat().st_size / 1024:.1f} KB")


def main():
    """Main function to create all 3 packs."""
    print("=" * 60)
    print("Creating 3 Grammar Story Roadmap Packs")
    print("=" * 60)

    # Setup
    temp_dir = setup_temp_directory()
    it_extract_dir = temp_dir / "it_express"
    it_extract_dir.mkdir(parents=True, exist_ok=True)

    # Extract IT_EXPRESS.zip
    with zipfile.ZipFile(IT_EXPRESS_ZIP, 'r') as zip_ref:
        zip_ref.extractall(it_extract_dir)

    # Create each pack
    for pack_name in ["ALLEGORY_PACK", "PORTAL_PACK", "POP_GRAMMAR_PACK"]:
        create_pack(pack_name, temp_dir)
        print()

    # Cleanup
    shutil.rmtree(temp_dir)
    print("Temporary files cleaned up.")

    print("=" * 60)
    print("All packs created successfully!")
    print(f"Output directory: {OUTPUT_DIR}")
    print("=" * 60)

    # List created files
    print("\nCreated files:")
    for pack_name in ["ALLEGORY_PACK", "PORTAL_PACK", "POP_GRAMMAR_PACK"]:
        zip_path = OUTPUT_DIR / f"{pack_name}.zip"
        if zip_path.exists():
            size_mb = zip_path.stat().st_size / (1024 * 1024)
            print(f"  {pack_name}.zip: {size_mb:.2f} MB")


if __name__ == "__main__":
    main()

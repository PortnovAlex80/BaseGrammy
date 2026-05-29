#!/usr/bin/env python3
"""
Create POP_EXPRESS pack based on IT_EXPRESS with book3-pop-grammar stories.
"""

import json
import zipfile
import shutil
from pathlib import Path

def main():
    source_pack = Path('app/src/main/assets/grammarmate/packs/IT_EXPRESS.zip')
    target_pack = Path('app/src/main/assets/grammarmate/packs/POP_EXPRESS.zip')
    stories_dir = Path('docs/lesson-methodology/books/book3-pop-grammar/rus')
    grammar_chips_dir = Path('docs/lesson-methodology/grammar_chips_json')
    temp_dir = Path('temp_pop_pack')

    print(f"Creating POP_EXPRESS pack from IT_EXPRESS...")
    print(f"Source: {source_pack}")
    print(f"Target: {target_pack}")
    print(f"Stories: {stories_dir}")
    print(f"Grammar chips: {grammar_chips_dir}")

    # Clean and create temp directory
    if temp_dir.exists():
        shutil.rmtree(temp_dir)
    temp_dir.mkdir()

    # Extract source pack
    print(f"\nExtracting IT_EXPRESS...")
    with zipfile.ZipFile(source_pack, 'r') as zf:
        zf.extractall(temp_dir)

    # Update manifest
    manifest_path = temp_dir / 'manifest.json'
    print(f"Reading manifest...")

    with open(manifest_path, 'r', encoding='utf-8') as f:
        manifest = json.load(f)

    # Update pack info
    manifest['packId'] = 'POP_EXPRESS'
    manifest['packVersion'] = 'v1'  # Reset version
    manifest['displayName'] = 'POP Express (Popular Grammar)'
    manifest['language'] = 'it'

    # Update chapters for POP grammar (7 chapters)
    pop_chapters = [
        {
            "chapterId": "POP_00",
            "order": 0,
            "title": "Intro",
            "subtitle": "Введение в популярную грамматику",
            "storyFile": "chapter_00.md",
            "lessons": ["lesson_01_A01", "lesson_02_A02"]
        },
        {
            "chapterId": "POP_01",
            "order": 1,
            "title": "Chapter 1 - Gender",
            "subtitle": "Род существительных",
            "storyFile": "chapter_01.md",
            "lessons": ["lesson_03_A03", "lesson_04_A04"]
        },
        {
            "chapterId": "POP_02",
            "order": 2,
            "title": "Chapter 2 - Articles",
            "subtitle": "Артикли",
            "storyFile": "chapter_02.md",
            "lessons": ["lesson_05_A05", "lesson_06_A06"]
        },
        {
            "chapterId": "POP_03",
            "order": 3,
            "title": "Chapter 3 - Prepositions",
            "subtitle": "Предлоги",
            "storyFile": "chapter_03.md",
            "lessons": ["lesson_07_A07", "lesson_08_A08"]
        },
        {
            "chapterId": "POP_04",
            "order": 4,
            "title": "Chapter 4 - Pronouns",
            "subtitle": "Местоимения",
            "storyFile": "chapter_04.md",
            "lessons": ["lesson_09_A09", "lesson_10_A10"]
        },
        {
            "chapterId": "POP_05",
            "order": 5,
            "title": "Chapter 5 - Adjectives",
            "subtitle": "Прилагательные",
            "storyFile": "chapter_05.md",
            "lessons": ["lesson_11_A11", "lesson_12_A12"]
        },
        {
            "chapterId": "POP_06",
            "order": 6,
            "title": "Chapter 6 - Verbs",
            "subtitle": "Глаголы",
            "storyFile": "chapter_06.md",
            "lessons": ["lesson_13_A13", "lesson_14_A14"]
        }
    ]

    manifest['chapters'] = pop_chapters

    print(f"Updated chapters: {len(pop_chapters)} chapters")

    # Write updated manifest
    with open(manifest_path, 'w', encoding='utf-8') as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    # Replace stories
    stories_target_dir = temp_dir / 'stories'
    if stories_target_dir.exists():
        shutil.rmtree(stories_target_dir)
    stories_target_dir.mkdir()

    print(f"\nCopying story files...")
    for story_file in stories_dir.glob('chapter_*.md'):
        target = stories_target_dir / story_file.name
        shutil.copy(story_file, target)
        print(f"  Copied {story_file.name}")

    print(f"Copied {len(list(stories_dir.glob('chapter_*.md')))} story files")

    # Replace grammar chips
    grammar_chips_target_dir = temp_dir / 'grammar_chips'
    if grammar_chips_target_dir.exists():
        shutil.rmtree(grammar_chips_target_dir)
    grammar_chips_target_dir.mkdir()

    print(f"\nCopying grammar chip files...")
    copied_chips = 0
    for chip_file in grammar_chips_dir.glob('grammar_chip_*.json'):
        target = grammar_chips_target_dir / chip_file.name
        shutil.copy(chip_file, target)
        copied_chips += 1
        if copied_chips <= 5:  # Show first 5
            print(f"  Copied {chip_file.name}")

    if copied_chips > 5:
        print(f"  ... and {copied_chips - 5} more")

    print(f"Copied {copied_chips} grammar chip files")

    # Create ZIP
    print(f"\nCreating POP_EXPRESS.zip...")
    with zipfile.ZipFile(target_pack, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for file_path in temp_dir.rglob('*'):
            if file_path.is_file():
                arcname = file_path.relative_to(temp_dir)
                zipf.write(file_path, arcname)

    # Clean up temp directory
    shutil.rmtree(temp_dir)
    print(f"Cleaned up temp directory")

    # Verify pack
    with zipfile.ZipFile(target_pack, 'r') as zf:
        files = zf.namelist()
        print(f"\nPack created successfully!")
        print(f"Pack: {target_pack}")
        print(f"Size: {target_pack.stat().st_size} bytes")
        print(f"Files in pack: {len(files)}")
        print(f"  - manifest.json: {'manifest.json' in files}")
        print(f"  - Story files: {len([f for f in files if f.startswith('stories/')])}")
        print(f"  - Grammar chips: {len([f for f in files if f.startswith('grammar_chips/')])}")
        print(f"  - Lesson files: {len([f for f in files if f.startswith('lesson_')])}")

    print(f"\nPOP_EXPRESS pack created successfully!")

if __name__ == '__main__':
    main()
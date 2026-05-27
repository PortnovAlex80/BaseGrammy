#!/usr/bin/env python3
"""
Update IT_EXPRESS manifest.json with grammar chip references.
"""

import json
from pathlib import Path

# Mapping from lesson ID to grammar chip file based on lesson analysis
LESSON_TO_CHIP = {
    "lesson_01_A01": "grammar_chip_01.json",
    "lesson_02_A02": "grammar_chip_02.json",
    "lesson_03_A03": "grammar_chip_03.json",
    "lesson_04_A04": "grammar_chip_04.json",
    "lesson_05_A05": "grammar_chip_05.json",
    "lesson_06_A06": "grammar_chip_06.json",
    "lesson_07_A07": "grammar_chip_07.json",
    "lesson_08_A08": "grammar_chip_08.json",
    "lesson_09_A09": "grammar_chip_09.json",
    "lesson_10_A10": "grammar_chip_10.json",
    "lesson_11_A11": "grammar_chip_11.json",
    "lesson_12_A12": "grammar_chip_12.json",
    "lesson_13_A13": "grammar_chip_13.json",
    "lesson_14_A14": "grammar_chip_14.json",
    "lesson_15_A15": "grammar_chip_15.json",
    "lesson_16_A16": "grammar_chip_16.json",
    "lesson_17_B01": "grammar_chip_17.json",
    "lesson_18_B02": "grammar_chip_18.json",
    "lesson_19_B03": "grammar_chip_19.json",
    "lesson_20_B04": "grammar_chip_20.json",
    "lesson_21_B05": "grammar_chip_21.json",
    "lesson_22_B06": "grammar_chip_22.json",
    "lesson_23_B07": "grammar_chip_23.json",
    "lesson_24_B08": "grammar_chip_24.json",
    "lesson_25_B09": "grammar_chip_25.json",
    "lesson_26_B10": "grammar_chip_26.json",
    "lesson_27_B11": "grammar_chip_27.json",
    "lesson_28_B12": "grammar_chip_28.json",
    "lesson_29_B13": "grammar_chip_29.json",
    "lesson_30_B14": "grammar_chip_30.json",
    "lesson_31_B15": "grammar_chip_31.json",
    "lesson_32_B16": "grammar_chip_32.json",
    "lesson_33_B17": "grammar_chip_33.json",
    "lesson_34_B18": "grammar_chip_34.json",
    "lesson_35_B19": "grammar_chip_35.json",
    "lesson_36_B20": "grammar_chip_36.json",
    "lesson_37_B21": "grammar_chip_37.json",
    "lesson_38_B22": "grammar_chip_38.json",
    "lesson_39_B23": "grammar_chip_39.json",
    "lesson_40_B24": "grammar_chip_40.json",
    "lesson_41_B25": "grammar_chip_41.json",
    "lesson_42_B26": "grammar_chip_42.json",
    "lesson_43_B27": "grammar_chip_43.json",
    "lesson_44_C01": "grammar_chip_44.json",
    "lesson_45_C02": "grammar_chip_45.json",
    "lesson_46_C03": "grammar_chip_46.json",
    "lesson_47_C04": "grammar_chip_47.json",
    "lesson_48_C05": "grammar_chip_48.json",
    "lesson_49_C06": "grammar_chip_49.json",
    "lesson_50_C07": "grammar_chip_50.json",
    "lesson_51_C08": "grammar_chip_51.json",
    "lesson_52_C09": "grammar_chip_52.json",
    "lesson_53_C10": "grammar_chip_53.json",
    "lesson_54_C11": "grammar_chip_54.json",
    "lesson_55_C12": "grammar_chip_55.json",
    "lesson_56_C13": "grammar_chip_56.json",
    "lesson_57_C14": "grammar_chip_57.json",
    "lesson_58_C15": "grammar_chip_58.json",
    "lesson_59_C16": "grammar_chip_59.json",
    "lesson_60_C17": "grammar_chip_60.json",
    "lesson_61_C18": "grammar_chip_61.json",
    "lesson_62_C19": "grammar_chip_62.json",
    "lesson_63_C20": "grammar_chip_63.json",
}

def main():
    manifest_path = Path('app/src/main/assets/grammarmate/packs/IT_EXPRESS/manifest.json')

    # Backup original manifest
    backup_path = manifest_path.with_suffix('.json.bak')
    import shutil
    shutil.copy(manifest_path, backup_path)
    print(f"Backed up manifest to {backup_path}")

    # Read manifest
    with open(manifest_path, 'r', encoding='utf-8') as f:
        manifest = json.load(f)

    # Update lessons with grammar chip references
    updated_count = 0
    for lesson in manifest.get('lessons', []):
        lesson_id = lesson.get('lessonId')
        if lesson_id in LESSON_TO_CHIP:
            lesson['grammarChip'] = LESSON_TO_CHIP[lesson_id]
            updated_count += 1
            print(f"Added grammar chip to {lesson_id}: {LESSON_TO_CHIP[lesson_id]}")

    # Write updated manifest
    with open(manifest_path, 'w', encoding='utf-8') as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    print(f"\nUpdated {updated_count} lessons with grammar chip references")
    print(f"Manifest saved to {manifest_path}")

if __name__ == '__main__':
    main()
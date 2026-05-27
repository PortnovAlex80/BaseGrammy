#!/usr/bin/env python3
"""
Test the chip key mapping logic to ensure it matches the actual chip keys.
"""

import json
from pathlib import Path

def get_chip_key_from_number(number):
    """Convert chip number to chip key (e.g., "01" -> "A01", "18" -> "B02")."""
    num = int(number)

    # Map lesson numbers to grammar chip keys based on the curriculum structure
    # A01-A16: lessons 1-16
    # B01-B27: lessons 17-43
    # C01-C20: lessons 44-63
    if num <= 16:
        return f"A{num:02d}"
    elif num <= 43:
        return f"B{num - 16:02d}"
    elif num <= 63:
        return f"C{num - 43:02d}"
    else:
        return str(number).upper()

def main():
    chips_dir = Path('docs/lesson-methodology/grammar_chips_json')

    print("Testing chip key mapping...")
    print("-" * 50)

    errors = []

    for chip_file in sorted(chips_dir.glob('grammar_chip_*.json')):
        # Extract chip number from filename
        chip_number = chip_file.stem.removeprefix('grammar_chip_')

        # Read the actual chip key from JSON
        with open(chip_file, 'r', encoding='utf-8') as f:
            chip_data = json.load(f)
            actual_key = chip_data['key']

        # Generate expected key from number
        expected_key = get_chip_key_from_number(chip_number)

        # Compare
        if actual_key != expected_key:
            errors.append((chip_file.name, chip_number, actual_key, expected_key))
            print(f"MISMATCH: {chip_file.name}")
            print(f"  Number: {chip_number}")
            print(f"  Actual key: {actual_key}")
            print(f"  Expected key: {expected_key}")
        else:
            print(f"OK: {chip_file.name} -> {actual_key}")

    print("-" * 50)
    if errors:
        print(f"\nFound {len(errors)} errors:")
        for error in errors:
            print(f"  {error}")
    else:
        print("\nAll chip keys match correctly!")

if __name__ == '__main__':
    main()
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Analyze lessons for Italian hints in Russian sentences."""

import re
import csv
import sys
from pathlib import Path
from collections import defaultdict

# Set UTF-8 encoding for Windows console
if sys.platform == 'win32':
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

def analyze_lesson(file_path):
    """Analyze a single lesson file for hints."""
    with open(file_path, 'r', encoding='utf-8') as f:
        reader = csv.reader(f, delimiter=';')
        rows = list(reader)

    total_lines = 0
    lines_with_hints = 0
    lines_without_hints = []
    lines_with_non_italian_hints = []

    for row in rows[1:]:  # Skip header
        if len(row) < 2:
            continue

        total_lines += 1
        russian = row[0]
        italian = row[1]

        # Check for hints in parentheses
        hint_pattern = r'\(([^)]+)\)'
        hints = re.findall(hint_pattern, russian)

        if hints:
            lines_with_hints += 1
            # Check if hints contain non-Italian text
            for hint in hints:
                # Check for non-Italian characters (Russian, English, etc.)
                if re.search(r'[а-яА-ЯёЁ]', hint) or re.search(r'[a-zA-Z]{3,}', hint):
                    lines_with_non_italian_hints.append((total_lines, russian, italian, hints))
        else:
            lines_without_hints.append((total_lines, russian, italian))

    percentage = (lines_with_hints / total_lines * 100) if total_lines > 0 else 0

    return {
        'total': total_lines,
        'with_hints': lines_with_hints,
        'without_hints': lines_without_hints,
        'bad_hints': lines_with_non_italian_hints,
        'percentage': percentage
    }

def main():
    lesson_dir = Path('D:/Development/BaseGrammy/docs/lesson-methodology/new_lessons')

    lessons_to_check = [
        'lesson_01_A01.csv',
        'lesson_02_A02.csv',
        'lesson_03_A03.csv',
        'lesson_04_A04.csv',
        'lesson_05_A05.csv',
        'lesson_06_A06.csv',
        'lesson_07_A07.csv'
    ]

    print("=" * 80)
    print("HINT ANALYSIS FOR LESSONS 01-07")
    print("=" * 80)
    print()

    for lesson_file in lessons_to_check:
        file_path = lesson_dir / lesson_file
        if not file_path.exists():
            print(f"[ERROR] {lesson_file}: file not found")
            continue

        result = analyze_lesson(file_path)

        print(f"[LESSON] {lesson_file}")
        print(f"   Total lines: {result['total']}")
        print(f"   With hints: {result['with_hints']} ({result['percentage']:.1f}%)")
        print(f"   Without hints: {len(result['without_hints'])}")

        if result['without_hints']:
            print(f"   First 3 lines without hints:")
            for line_num, russian, italian in result['without_hints'][:3]:
                print(f"     {line_num}. {russian[:50]}... -> {italian[:30]}...")

        if result['bad_hints']:
            print(f"   [WARNING] Lines with NON-Italian hints: {len(result['bad_hints'])}")
            for line_num, russian, italian, hints in result['bad_hints'][:3]:
                print(f"     {line_num}. Hints: {hints}")
                print(f"        {russian[:60]}...")

        print()

    print("=" * 80)
    print("SUMMARY STATISTICS")
    print("=" * 80)

    total_with_hints = 0
    total_lines = 0

    for lesson_file in lessons_to_check:
        file_path = lesson_dir / lesson_file
        if file_path.exists():
            result = analyze_lesson(file_path)
            total_with_hints += result['with_hints']
            total_lines += result['total']

            lesson_num = lesson_file.split('_')[1]
            print(f"Lesson {lesson_num}: {result['percentage']:.1f}% with hints ({result['with_hints']}/{result['total']})")

    print()
    if total_lines > 0:
        overall_percentage = (total_with_hints / total_lines) * 100
        print(f"OVERALL: {overall_percentage:.1f}% ({total_with_hints}/{total_lines} lines with hints)")

if __name__ == '__main__':
    main()

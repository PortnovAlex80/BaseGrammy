#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Analyze lessons for Italian hints in Russian sentences - DETAILED VERSION."""

import re
import sys
from pathlib import Path

# Set UTF-8 encoding for Windows console
if sys.platform == 'win32':
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

def analyze_lesson_detailed(file_path):
    """Analyze a single lesson file for hints with detailed output."""
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    total_lines = 0
    lines_with_hints = 0
    lines_without_hints = []
    hints_examples = []

    for i, line in enumerate(lines[1:], start=1):  # Skip header
        if ';' not in line:
            continue

        parts = line.strip().split(';')
        if len(parts) < 2:
            continue

        total_lines += 1
        russian = parts[0]
        italian = parts[1]

        # Check for hints in parentheses
        hint_pattern = r'\(([^)]+)\)'
        hints = re.findall(hint_pattern, russian)

        if hints:
            lines_with_hints += 1
            if len(hints_examples) < 5:  # Store first 5 examples
                hints_examples.append((i, russian, italian, hints))
        else:
            if len(lines_without_hints) < 5:  # Store first 5 examples
                lines_without_hints.append((i, russian, italian))

    percentage = (lines_with_hints / total_lines * 100) if total_lines > 0 else 0

    return {
        'total': total_lines,
        'with_hints': lines_with_hints,
        'without_hints': lines_without_hints,
        'hints_examples': hints_examples,
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
    print("DETAILED HINT ANALYSIS FOR LESSONS 01-07")
    print("=" * 80)
    print()

    for lesson_file in lessons_to_check:
        file_path = lesson_dir / lesson_file
        if not file_path.exists():
            print(f"[ERROR] {lesson_file}: file not found")
            continue

        result = analyze_lesson_detailed(file_path)

        print(f"[LESSON] {lesson_file}")
        print(f"   Total lines: {result['total']}")
        print(f"   With hints: {result['with_hints']} ({result['percentage']:.1f}%)")
        print(f"   Without hints: {len(result['without_hints'])}")

        if result['without_hints']:
            print(f"   Examples WITHOUT hints:")
            for line_num, russian, italian in result['without_hints'][:3]:
                russian_short = russian[:60] + "..." if len(russian) > 60 else russian
                italian_short = italian[:40] + "..." if len(italian) > 40 else italian
                print(f"     {line_num}. RU: {russian_short}")
                print(f"         IT: {italian_short}")

        if result['hints_examples']:
            print(f"   Examples WITH hints:")
            for line_num, russian, italian, hints in result['hints_examples'][:3]:
                russian_short = russian[:60] + "..." if len(russian) > 60 else russian
                italian_short = italian[:40] + "..." if len(italian) > 40 else italian
                print(f"     {line_num}. Hints: {hints}")
                print(f"         RU: {russian_short}")
                print(f"         IT: {italian_short}")

        print()

    print("=" * 80)
    print("SUMMARY STATISTICS")
    print("=" * 80)

    total_with_hints = 0
    total_lines = 0

    for lesson_file in lessons_to_check:
        file_path = lesson_dir / lesson_file
        if file_path.exists():
            result = analyze_lesson_detailed(file_path)
            total_with_hints += result['with_hints']
            total_lines += result['total']

            lesson_num = lesson_file.split('_')[1]
            status = "✓" if result['percentage'] > 0 else "✗"
            print(f"{status} Lesson {lesson_num}: {result['percentage']:.1f}% with hints ({result['with_hints']}/{result['total']})")

    print()
    if total_lines > 0:
        overall_percentage = (total_with_hints / total_lines) * 100
        print(f"OVERALL: {overall_percentage:.1f}% ({total_with_hints}/{total_lines} lines with hints)")
        print()

        if overall_percentage < 50:
            print("[ISSUE] Most lessons (6 out of 7) have NO hints at all!")
            print("[ISSUE] Only lesson 05 has hints (100% coverage)")
            print("[RECOMMENDATION] Add Italian hints to Russian sentences in lessons 01-04, 06-07")

if __name__ == '__main__':
    main()

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Analyze lessons for hints in Russian sentences."""

import re
import sys
from pathlib import Path
from typing import Dict, List, Tuple

# Set UTF-8 encoding for Windows console
if sys.platform == 'win32':
    import codecs
    sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer, 'strict')
    sys.stderr = codecs.getwriter('utf-8')(sys.stderr.buffer, 'strict')

def analyze_lesson(file_path: Path) -> Dict:
    """Analyze a lesson file for hints."""
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    total_lines = 0
    lines_with_hints = 0
    lines_without_hints = []
    lines_with_wrong_hints = []  # Non-Italian hints

    # Skip header line
    for line in lines[1:]:
        line = line.strip()
        if not line or line.startswith('#'):
            continue

        total_lines += 1

        # Split by semicolon to get Russian part
        if ';' in line:
            russian_part = line.split(';')[0].strip()

            # Check for hints in parentheses
            hint_pattern = r'\(([^)]+)\)'
            hints = re.findall(hint_pattern, russian_part)

            if hints:
                lines_with_hints += 1

                # Check if hints are Italian (basic check: contains Italian-like patterns)
                for hint in hints:
                    # Check if hint contains non-Italian text (basic heuristic)
                    # Italian typically doesn't contain Cyrillic or specific Russian patterns
                    if re.search(r'[а-яА-ЯёЁ]', hint):
                        lines_with_wrong_hints.append({
                            'line_num': total_lines,
                            'content': line.strip(),
                            'hint': hint,
                            'reason': 'Contains Cyrillic'
                        })
                    elif hint.startswith('- ') or ' - ' in hint:
                        # This looks like a verb hint (e.g., "(буду - essere)")
                        # Check if the Italian part is actually Italian
                        italian_part = hint.split('-')[-1].strip() if '-' in hint else hint
                        if re.search(r'[а-яА-ЯёЁ]', italian_part):
                            lines_with_wrong_hints.append({
                                'line_num': total_lines,
                                'content': line.strip(),
                                'hint': hint,
                                'reason': 'Italian part contains Cyrillic'
                            })
            else:
                lines_without_hints.append({
                    'line_num': total_lines,
                    'content': line.strip()
                })

    percentage = (lines_with_hints / total_lines * 100) if total_lines > 0 else 0

    return {
        'file': file_path.name,
        'total_lines': total_lines,
        'lines_with_hints': lines_with_hints,
        'lines_without_hints': lines_without_hints,
        'lines_with_wrong_hints': lines_with_wrong_hints,
        'percentage': percentage
    }

def main():
    # Get the directory where this script is located
    script_dir = Path(__file__).parent
    lessons_dir = script_dir.parent / 'new_lessons'

    lessons_to_check = [
        'lesson_22_B06.csv',
        'lesson_23_B07.csv',
        'lesson_24_B08.csv',
        'lesson_25_B09.csv',
        'lesson_26_B10.csv',
        'lesson_27_B11.csv',
        'lesson_28_B12.csv'
    ]

    print("=" * 80)
    print("АНАЛИЗ ПОДСКАЗОК В УРОКАХ 22-28")
    print("=" * 80)
    print()

    for lesson_file in lessons_to_check:
        lesson_path = lessons_dir / lesson_file
        if not lesson_path.exists():
            print(f"❌ Файл не найден: {lesson_file}")
            continue

        result = analyze_lesson(lesson_path)

        print(f"📚 {lesson_file}")
        print(f"   Процент с подсказками: {result['percentage']:.1f}%")
        print(f"   Всего строк: {result['total_lines']}")
        print(f"   С подсказками: {result['lines_with_hints']}")
        print(f"   БЕЗ подсказок: {len(result['lines_without_hints'])}")

        if result['lines_without_hints']:
            print(f"   Примеры строк БЕЗ подсказок (первые 5):")
            for item in result['lines_without_hints'][:5]:
                print(f"     {item['line_num']}. {item['content'][:80]}...")
            if len(result['lines_without_hints']) > 5:
                print(f"     ... и ещё {len(result['lines_without_hints']) - 5} строк")

        if result['lines_with_wrong_hints']:
            print(f"   ⚠️  Строки с НЕИТАЛЬЯНСКИМИ подсказками:")
            for item in result['lines_with_wrong_hints']:
                print(f"     {item['line_num']}. {item['hint']} ({item['reason']})")
                print(f"        {item['content'][:80]}...")

        print()

    print("=" * 80)
    print("СВОДНАЯ ТАБЛИЦА")
    print("=" * 80)
    print(f"{'Урок':<25} {'% с подсказками':<20} {'Без подсказок':<20}")
    print("-" * 80)

    for lesson_file in lessons_to_check:
        lesson_path = lessons_dir / lesson_file
        if lesson_path.exists():
            result = analyze_lesson(lesson_path)
            print(f"{lesson_file:<25} {result['percentage']:.1f}%{'':<15} {len(result['lines_without_hints'])} из {result['total_lines']}")

if __name__ == '__main__':
    main()

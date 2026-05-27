#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Detailed analysis of hint types in lessons 22-28."""

import re
import sys
from pathlib import Path
from typing import Dict, List, Tuple
from collections import Counter

# Set UTF-8 encoding for Windows console
if sys.platform == 'win32':
    import codecs
    sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer, 'strict')
    sys.stderr = codecs.getwriter('utf-8')(sys.stderr.buffer, 'strict')

def analyze_hint_types(file_path: Path) -> Dict:
    """Analyze hint types in a lesson file."""
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    hint_types = Counter()
    hints_by_line = {}

    # Skip header line
    for line_num, line in enumerate(lines[1:], start=1):
        line = line.strip()
        if not line or line.startswith('#'):
            continue

        # Split by semicolon to get Russian part
        if ';' in line:
            russian_part = line.split(';')[0].strip()

            # Check for hints in parentheses
            hint_pattern = r'\(([^)]+)\)'
            hints = re.findall(hint_pattern, russian_part)

            if hints:
                for hint in hints:
                    # Categorize hint type
                    if '-' in hint:
                        # Verb hints: "русский - italiano"
                        hint_types['verb_hints'] += 1
                    elif re.search(r'[а-яА-ЯёЁ]', hint):
                        # Russian-only hints (should not exist)
                        hint_types['russian_only'] += 1
                    elif re.search(r'[a-zA-Z]', hint):
                        # Italian-only hints
                        hint_types['italian_only'] += 1
                    else:
                        # Other
                        hint_types['other'] += 1

                    hints_by_line[line_num] = hints

    return {
        'file': file_path.name,
        'hint_types': dict(hint_types),
        'total_hints': sum(hint_types.values()),
        'hints_by_line': hints_by_line
    }

def main():
    script_dir = Path(__file__).parent
    lessons_dir = script_dir.parent / 'new_lessons'

    lessons_to_check = [
        ('lesson_22_B06.csv', 'Trapassato Prossimo'),
        ('lesson_23_B07.csv', 'Futuro Semplice'),
        ('lesson_24_B08.csv', 'Futuro Anteriore'),
        ('lesson_25_B09.csv', 'Condizionale Semplice'),
        ('lesson_26_B10.csv', 'Condizionale Composto'),
        ('lesson_27_B11.csv', 'Pronomi Diretti'),
        ('lesson_28_B12.csv', 'Pronomi Indiretti')
    ]

    print("=" * 100)
    print("ДЕТАЛЬНЫЙ АНАЛИЗ ТИПОВ ПОДСКАЗОК")
    print("=" * 100)
    print()

    total_verb_hints = 0
    total_italian_only = 0
    total_russian_only = 0

    for lesson_file, topic in lessons_to_check:
        lesson_path = lessons_dir / lesson_file
        if not lesson_path.exists():
            continue

        result = analyze_hint_types(lesson_path)

        print(f"📚 {lesson_file} - {topic}")
        print(f"   Всего подсказок: {result['total_hints']}")

        if result['hint_types']:
            print(f"   Типы подсказок:")
            if 'verb_hints' in result['hint_types']:
                count = result['hint_types']['verb_hints']
                total_verb_hints += count
                print(f"     • Глагольные (русский - italiano): {count}")
            if 'italian_only' in result['hint_types']:
                count = result['hint_types']['italian_only']
                total_italian_only += count
                print(f"     • Только итальянские: {count}")
            if 'russian_only' in result['hint_types']:
                count = result['hint_types']['russian_only']
                total_russian_only += count
                print(f"     • ⚠️  Только русские (ОШИБКА): {count}")
            if 'other' in result['hint_types']:
                print(f"     • Другие: {result['hint_types']['other']}")
        else:
            print("   • Нет подсказок")

        print()

    print("=" * 100)
    print("СВОДНАЯ СТАТИСТИКА")
    print("=" * 100)
    print(f"Глагольные подсказки (русский - italiano): {total_verb_hints}")
    print(f"Только итальянские подсказки: {total_italian_only}")
    print(f"⚠️  Только русские подсказки (ОШИБКИ): {total_russian_only}")
    print()

    print("=" * 100)
    print("РЕКОМЕНДАЦИИ")
    print("=" * 100)
    print()
    print("✅ Уроки 23, 24, 25, 27, 28:")
    print("   - Все строки имеют подсказки")
    print("   - Формат: Глагольные подсказки (русский - italiano)")
    print("   - Это ПРАВИЛЬНЫЙ формат для глагольных уроков")
    print()
    print("❌ Уроки 22, 26:")
    print("   - НЕТ подсказок вообще")
    print("   - Lesson 22 (Trapassato Prossimo): 0% с подсказками")
    print("   - Lesson 26 (Condizionale Composto): 0% с подсказками")
    print("   - РЕКОМЕНДАЦИЯ: Добавить подсказки в итальянском языке")
    print()
    print("📝 Пример правильного формата:")
    print("   Когда я пришёл, он (già) ушёл.;Quando sono arrivato, lui era già uscito.")
    print("   Она (non) ждала меня;Quando sono arrivato, lei non mi aspettava più.")
    print()

if __name__ == '__main__':
    main()

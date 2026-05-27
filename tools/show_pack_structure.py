#!/usr/bin/env python3
"""
Display IT_EXPRESS pack structure as a tree.
"""

import json
import zipfile
import sys

def main():
    # Извлекаем manifest.json из ZIP
    with zipfile.ZipFile('app/src/main/assets/grammarmate/packs/IT_EXPRESS.zip', 'r') as zip_ref:
        manifest_content = zip_ref.read('manifest.json').decode('utf-8')
        manifest = json.loads(manifest_content)

    # Показываем дерево структуры
    print('Дерево структуры IT_EXPRESS пакета:')
    print('=' * 60)

    for chapter in manifest['chapters']:
        print(f"\nГлава {chapter['order']}: {chapter['title']}")
        print(f"  Подзаголовок: {chapter.get('subtitle', 'N/A')}")
        print(f"  Файл истории: {chapter.get('storyFile', 'N/A')}")
        print(f"  Уроки ({len(chapter['lessons'])} шт.):")

        for lesson_id in chapter['lessons']:
            # Находим соответствующий урок в манифесте
            lesson = next((l for l in manifest['lessons'] if l['lessonId'] == lesson_id), None)
            if lesson:
                chip_info = f" [грам. чип: {lesson.get('grammarChip', 'N/A')}]" if lesson.get('grammarChip') else ""
                print(f"    {lesson['lessonId']}: {lesson['title']}{chip_info}")

    print(f"\n{'=' * 60}")
    print(f"Всего глав: {len(manifest['chapters'])}")
    print(f"Всего уроков: {len(manifest['lessons'])}")

    # Проверка соответствия уроков главам
    print(f"\nПроверка соответствия:")
    lessons_in_chapters = set()
    for chapter in manifest['chapters']:
        lessons_in_chapters.update(chapter['lessons'])

    defined_lessons = set(lesson['lessonId'] for lesson in manifest['lessons'])

    if lessons_in_chapters == defined_lessons:
        print("  OK: Все уроки в главах соответствуют определенным урокам")
    else:
        print("  WARNING: Несоответствие уроков!")
        if lessons_in_chapters - defined_lessons:
            print(f"    Уроки в главах, но не определены: {lessons_in_chapters - defined_lessons}")
        if defined_lessons - lessons_in_chapters:
            print(f"    Уроки определены, но не в главах: {defined_lessons - lessons_in_chapters}")

if __name__ == '__main__':
    main()
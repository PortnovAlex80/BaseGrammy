#!/usr/bin/env python3
"""
Display IT_EXPRESS pack structure as a readable tree.
"""

import json
import zipfile

def main():
    # Extract manifest from ZIP
    with zipfile.ZipFile('app/src/main/assets/grammarmate/packs/IT_EXPRESS.zip', 'r') as zip_ref:
        manifest_content = zip_ref.read('manifest.json').decode('utf-8')
        manifest = json.loads(manifest_content)

    # Display tree structure
    print("IT_EXPRESS Pack Structure Tree")
    print("=" * 70)

    for chapter in manifest['chapters']:
        print(f"\nChapter {chapter['order']}: {chapter['title']}")
        print(f"  Subtitle: {chapter.get('subtitle', 'N/A')}")
        print(f"  Story: {chapter.get('storyFile', 'N/A')}")
        print(f"  Lessons ({len(chapter['lessons'])}):")

        for lesson_id in chapter['lessons']:
            lesson = next((l for l in manifest['lessons'] if l['lessonId'] == lesson_id), None)
            if lesson:
                chip_info = f" [chip: {lesson.get('grammarChip', 'N/A')}]" if lesson.get('grammarChip') else ""
                print(f"    {lesson['lessonId']}: {lesson['title']}{chip_info}")

    print(f"\n{'=' * 70}")
    print(f"Total chapters: {len(manifest['chapters'])}")
    print(f"Total lessons: {len(manifest['lessons'])}")

    # Chapter summary
    print(f"\nChapter Summary:")
    print(f"-" * 40)
    for chapter in manifest['chapters']:
        print(f"Chapter {chapter['order']}: {len(chapter['lessons'])} lessons")

    # Verification
    lessons_in_chapters = set()
    for chapter in manifest['chapters']:
        lessons_in_chapters.update(chapter['lessons'])

    defined_lessons = set(lesson['lessonId'] for lesson in manifest['lessons'])

    print(f"\nVerification:")
    if lessons_in_chapters == defined_lessons:
        print(f"  OK: All {len(defined_lessons)} lessons properly assigned to chapters")
    else:
        print(f"  WARNING: Mismatch detected!")
        if lessons_in_chapters - defined_lessons:
            print(f"    Lessons in chapters but not defined: {lessons_in_chapters - defined_lessons}")
        if defined_lessons - lessons_in_chapters:
            print(f"    Lessons defined but not in chapters: {defined_lessons - lessons_in_chapters}")

if __name__ == '__main__':
    main()
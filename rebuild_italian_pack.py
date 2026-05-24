#!/usr/bin/env python3
"""
Пересобрать итальянский пакет с обновленным lesson_01.csv
"""

import zipfile
import shutil
from pathlib import Path

# Путь к пакету
pack_path = Path('./app/src/main/assets/grammarmate/packs/IT_VERB_GROUPS_ALL.zip')
temp_dir = Path('./temp_pack_rebuild')

# Создаем временную директорию
if temp_dir.exists():
    shutil.rmtree(temp_dir)
temp_dir.mkdir()

# Распаковываем существующий пакет
print(f"Распаковываем {pack_path.name}...")
with zipfile.ZipFile(pack_path, 'r') as zf:
    zf.extractall(temp_dir)

print(f"Распаковано {len(list(temp_dir.iterdir()))} файлов")

# Копируем ВСЕ обновленные уроки
lessons_dir = Path('./data/it/lessons')
for i in range(1, 7):
    source_lesson = lessons_dir / f'lesson_{i:02d}.csv'
    target_lesson = temp_dir / f'it_lesson_{i:02d}.csv'
    if source_lesson.exists():
        print(f"Копируем {source_lesson.name}...")
        shutil.copy(source_lesson, target_lesson)

# Проверяем размеры
original_size = len(source_lesson.read_text(encoding='utf-8'))
target_size = len(target_lesson.read_text(encoding='utf-8'))
print(f"Размер исходного файла: {original_size} байт")
print(f"Размер файла в пакете: {target_size} байт")

# Создаем новый ZIP
new_pack_path = pack_path  # Перезаписываем существующий
print(f"Создаем новый пакет {new_pack_path.name}...")

with zipfile.ZipFile(new_pack_path, 'w', zipfile.ZIP_DEFLATED) as zf:
    for file_path in temp_dir.iterdir():
        if file_path.is_file():
            arcname = file_path.name
            zf.write(file_path, arcname)
            print(f"  Добавлен: {arcname} ({file_path.stat().st_size} байт)")

# Очищаем временную директорию
shutil.rmtree(temp_dir)

print(f"\n[OK] Пакет {pack_path.name} обновлен!")
print(f"Размер пакета: {pack_path.stat().st_size} байт")

# Проверяем содержимое нового пакета
print(f"\nПроверка содержимого пакета:")
with zipfile.ZipFile(pack_path, 'r') as zf:
    file_list = zf.namelist()
    for name in sorted(file_list):
        info = zf.getinfo(name)
        print(f"  {name}: {info.file_size} байт")

    # Проверяем it_lesson_01.csv
    lesson_content = zf.read('it_lesson_01.csv').decode('utf-8')
    lines = lesson_content.strip().split('\n')
    print(f"\nСтрок в it_lesson_01.csv: {len(lines)} (включая заголовок)")
    print(f"Предложений в it_lesson_01.csv: {len(lines) - 1}")
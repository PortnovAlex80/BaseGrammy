# Инструкция: как подготовить пакет занятий (Lesson Pack)

Пакет занятий импортируется в приложение как zip-архив. В корне архива лежит `manifest.json` и файлы уроков CSV. Опционально можно добавить Story Quiz (JSON) и словарь (CSV).

## 1) Структура архива

```
lesson_pack.zip
  manifest.json
  lesson_01.csv
  lesson_02.csv
  ...
  vocab_L01_PRESENT_SIMPLE.csv        (опционально)
  story_L01_CHECK_IN.json             (опционально)
```

Обязательно:
- `manifest.json` в корне архива.
- Все файлы уроков, перечисленные в `manifest.json`.

Опционально:
- Story Quiz JSON-файлы (любое имя, кроме `manifest.json`).
- Vocabulary CSV-файлы с именем `vocab_<lessonId>.csv`.

## 2) manifest.json

Обязательные поля:
- `schemaVersion`: всегда `1`
- `packId`: строка, не пустая
- `packVersion`: строка, не пустая
- `language`: строка, не пустая (будет приведена к lower-case)
- `lessons`: массив уроков, минимум 1 элемент

Каждый урок:
- `lessonId`: строка, не пустая
- `file`: имя CSV файла
- `order`: число (опционально, но лучше указать)
- `title`: строка (опционально)

Пример:
```json
{
  "schemaVersion": 1,
  "packId": "EN_Core_A1",
  "packVersion": "v1",
  "language": "en",
  "lessons": [
    { "lessonId": "L01_PRESENT_SIMPLE", "order": 1, "title": "Lesson 1", "file": "lesson_01.csv" }
  ]
}
```

## 3) CSV урока

Формат CSV урока (по факту парсера):
- разделитель: `;`
- первая непустая строка = заголовок урока
- далее каждая строка = карточка
- строго 2 колонки: `ru;answers`
- `answers` может содержать несколько вариантов, разделенных `+`
- пустые строки игнорируются

Пример:
```
Lesson 1
Я работаю из дома.;I work from home+I work at home
Он часто опаздывает.;He is often late
```

Важно:
- если строка не содержит ровно 2 колонки, она будет пропущена;
- если в тексте нужен `;`, используйте кавычки `"..."` (CSV парсер поддерживает кавычки);
- заголовок очищается: берутся только буквы/цифры/пробелы, максимум 160 символов.

## 4) Story Quiz JSON (опционально)

Импортируются все `.json` файлы в пакете, кроме `manifest.json`. Формат:
- `storyId`, `lessonId`, `phase`, `text` — обязательные
- `phase`: `CHECK_IN` или `CHECK_OUT`
- `questions[]`: каждый вопрос должен иметь `qId`, `prompt`, `options[]`, `correctIndex`
- `explain` — опционально
 
Как задается правильный ответ:
- `correctIndex` — это индекс правильного варианта в массиве `options[]` (нумерация с нуля).
- Пример: если правильный вариант второй, то `correctIndex = 1`.

Пример:
```json
{
  "storyId": "S_L01_OUT_01",
  "lessonId": "L01_PRESENT_SIMPLE",
  "phase": "CHECK_OUT",
  "text": "Tom works in a small shop...",
  "questions": [
    {
      "qId": "Q1",
      "prompt": "Where does Tom work?",
      "options": ["In a shop", "In a hospital", "At school", "At home"],
      "correctIndex": 0
    }
  ]
}
```

## 5) Vocabulary CSV (опционально)

Имя файла: `vocab_<lessonId>.csv`

Формат:
- разделитель: `;`
- минимум 2 колонки: `native;target`
- 3-я колонка (опционально): `hard` или `1` или `true` → помечает слово как сложное

Пример:
```
дом;house
только что;just now;hard
```

## 6) Как выбирается Vocabulary Sprint и что такое lessonId

- `lessonId` — это строковый идентификатор урока из `manifest.json` (поле `lessons[].lessonId`).
- При импорте этот `lessonId` становится ID урока внутри приложения.
- Vocabulary Sprint для урока берется из файла `vocab_<lessonId>.csv`.
  Пример: если в манифесте `lessonId = "L01_PRESENT_SIMPLE"`, то файл словаря должен называться
  `vocab_L01_PRESENT_SIMPLE.csv`.

## 7) Проверка перед импортом

- В zip есть `manifest.json` и все указанные в нем CSV.
- `schemaVersion` = 1.
- `packId`, `packVersion`, `language`, `lessonId` не пустые.
- В CSV урока есть заголовок и хотя бы несколько карточек.

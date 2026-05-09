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

## 8) Verb Drill — тренировка глаголов (type: verb_drill)

Урок можно пометить как `verb_drill`, добавив поле `"type": "verb_drill"` в запись урока в `manifest.json`. Такие уроки попадают в отдельный режим Verb Drill на главном экране (вместо обычной дорожки уроков).

### manifest.json

Пример манифеста с verb_drill:

```json
{
  "schemaVersion": 1,
  "packId": "IT_VERB_GROUPS_ALL",
  "packVersion": "v1",
  "language": "it",
  "displayName": "Verb Drill - All Groups",
  "lessons": [
    {
      "lessonId": "it_verb_groups_all",
      "type": "verb_drill",
      "file": "it_verb_groups_all.csv",
      "order": 1,
      "title": "All Verb Groups"
    }
  ]
}
```

- `"type": "verb_drill"` — обязательно. Если поле отсутствует или равно `"standard"`, урок импортируется как обычный.
- `displayName` — опционально, отображается как название пакета.

### CSV формат для verb_drill

- Разделитель: `;`
- Первая непустая строка = заголовок урока (как в обычных CSV)
- **Вторая непустая строка = строка с именами колонок (header row)** — обязательна
- Далее строки с данными

**Колонки (header row):**

| Колонка | Обязательна | Описание |
|---------|-------------|----------|
| `RU`    | Да          | Подсказка на русском |
| `IT`    | Да          | Правильный ответ на итальянском |
| `Verb`  | Нет         | Инфинитив глагола (для фильтрации) |
| `Tense` | Нет         | Время глагола (для фильтрации) |
| `Group` | Нет         | Группа глаголов (для фильтрации) |

Минимум `RU` и `IT` обязательны. Остальные колонки опциональны — при их отсутствии фильтрация по соответствующему параметру недоступна.

### Пример CSV

```
Verb Conjugation Drill Italian
RU;IT;Verb;Tense;Group
я устал (essere stanco);Io sono stanco.;essere;Presente;irregular_unique
ты устал (essere stanco);Tu sei stanco.;essere;Presente;irregular_unique
он устал (essere stanco);Lui è stanco.;essere;Presente;irregular_unique
```

### Как работает фильтрация

При наличии колонок `Tense` и/или `Group` в CSV, на экране Verb Drill появляются выпадающие списки для фильтрации карточек по времени и группе. Пользователь выбирает нужные фильтры и запускает сессию из 10 случайных карточек.

### Куда попадают файлы

При импорте verb_drill CSV копируется в `verb_drill/{languageId}_{lessonId}.csv` внутри директории данных приложения. Оттуда он читается при открытии режима Verb Drill.

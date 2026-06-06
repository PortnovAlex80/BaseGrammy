# Генератор учебных программ GrammarMate

## Идея

Генератор создаёт **учебную программу** для нового языка — не просто ZIP-пакет, а полноценную программу обучения:
- Каждый язык получает **свою** структуру (не копию итальянского)
- Итальянский — референс по подходу (аллегорические истории, главы, дриллы)
- ZIP-пакет — артефакт, результат программы

## Запуск

```bash
cd tools

# Испанский (предопределённый профиль)
python create_language_program.py --language-code es --language-name Spanish

# Немецкий (3 рода, 4 падежа → своя структура глав)
python create_language_program.py --language-code de --language-name German

# Французский
python create_language_program.py --language-code fr --language-name French

# Произвольный язык
python create_language_program.py --language-code zh --language-name Chinese --pack-id CHINESE_EXPRESS

# Только патчи кода (без генерации файлов)
python create_language_program.py --language-code es --language-name Spanish --patches-only
```

## Что генерируется

### 1. Учебная программа
Каждый язык получает свою программу на основе грамматических особенностей:

| Язык | Род | Падежи | Особенности | Главы |
|------|-----|--------|-------------|-------|
| Итальянский | 2 (м/ж) | нет | Субжюнктив | 2 |
| Испанский | 2 (м/ж) | нет | Субжюнктив, два прошедших | 2 |
| Немецкий | 3 (м/ж/ср) | 4 | Падежи, порядок слов | 3 |
| Французский | 2 (м/ж) | нет | Субжюнктив, passé composé | 2 |
| Японский | нет | нет | 3 письменности | 3 |

### 2. Структура ZIP-пакета
```
{PACK_ID}.zip
├── manifest.json              # Схема v2: главы, уроки, дриллы
├── lesson_01_A01.csv          # Уроки RU→целевой язык
├── ...
├── grammar_chips/             # Грамматические чипы (MD)
├── grammar_chips_json/        # Грамматические чипы (JSON)
├── stories/                   # Аллегорические истории
│   ├── chapter_00.md          # Введение — "Запертая комната"
│   └── chapter_01.md          # Первая глава
├── {lang}_verb_groups_all.csv # Дрилл глаголов
├── {lang}_drill_nouns.csv     # Словарный дрилл
└── ...
```

### 3. Патчи кода приложения
Генератор автоматически подготавливает инструкции для:
- `LessonStore.kt` — регистрация пакета в defaultPacks
- `LanguageManager.kt` — добавление языка
- `MultilingualStoryParser.kt` — расширение regex для новых тегов `{lang}`
- `TtsModelRegistry.kt` — поиск и добавление TTS-модели
- `GrammarChipStore.kt` — исправление языкового ключа

### 4. TTS-модели
Генератор указывает, где искать голосовую модель:
- Sherpa-ONNX VITS-Piper модели: https://k2-fsa.github.io/sherpa/onnx/tts/all/
- Если модели нет → автоматически fallback на системный TTS Android

## Теги языка в историях

Истории используют теги `{lang}...{/lang}` для мультиязычного TTS:

```markdown
{es}*Si hubiera sabido que ibas a desaparecer*{/es} — если бы я знал, что ты исчезнешь
```

Для нового языка нужно обновить regex в `MultilingualStoryParser.kt`:
```kotlin
// ДО:
Regex("""\{(it|en|ru)\}(.+?)\{/\1\}""")
// ПОСЛЕ (добавлен "es"):
Regex("""\{(it|en|ru|es)\}(.+?)\{/\1\}""")
```

## Что нужно заполнить вручную

Генератор создаёт **каркас** — структуру программы. Содержание нужно заполнить:

1. **Уроки CSV** — фактические пары RU→целевой язык
2. **Истории** — развернуть аллегорические объяснения грамматики
3. **Дриллы** — таблицы спряжения, словарные списки
4. **Грамматические чипы** — правила и примеры

## Добавление нового предопределённого языка

Добавить профиль в `LANGUAGE_PROFILES` в `create_language_program.py`:

```python
"zh": LanguageProfile(
    code="zh", name="Chinese", name_ru="Китайский", name_ru_lower="китайский",
    pack_id="CHINESE_EXPRESS",
    has_genders=False, has_articles=False,
    has_verb_conjugation=False,  # Китайский не спрягает!
    tenses=["了 (le)", "过 (guò)", "会 (huì)", "在 (zài)"],
    writing_system="hanzi",
    num_chapters=3, lessons_per_chapter=12
),
```

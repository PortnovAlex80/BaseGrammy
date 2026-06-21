# Звуковой пакет фоновой озвучки (Sound Pack) — корпус и сборка

Эта документ описывает: **где лежит Opus-корпус озвучки**, как из него
собирается встроенный в APK пак (первые N рангов) и полный звуковой пак для
загрузчика (`SoundPackManager`), а также как пак попадает на устройство.

> См. также: [`docs/BUILD_INSTRUCTIONS.md`](../../BUILD_INSTRUCTIONS.md) —
> сборка самого APK; [`full-course-63/PACK_CREATION_REPORT.md`](full-course-63/PACK_CREATION_REPORT.md)
> — сборка **урок-паков** (CSV-контент, без аудио). Здесь — только **звук**.

---

## 1. Где лежит Opus-корпус

| Что | Путь | Примечание |
|-----|------|------------|
| **Корпус (распакованный)** | `docs/lesson-methodology/italian/work_opus/` | 116 973 `.opus`, ~615 МБ. **В `.gitignore`** — в репо не попадает, хранится локально. |
| Исходный архив | `docs/lesson-methodology/italian/work_opus.zip` | На самом деле это **TAR** (USTAR), несмотря на расширение `.zip`. В `.gitignore`. |
| Полный sound-pack ZIP (собранный) | `.bg_vocab_scratch/full_soundpack/ITALIAN_SHORT_soundpack_full.zip` | ~615 МБ, для загрузчика. В `.gitignore`. |
| Встроенный в APK пак | `app/src/main/assets/grammarmate/packs/ITALIAN_EXPRESS_SHORT.zip` | **В репо** (содержит первые 1000 рангов opus + весь урок-контент). |

### Структура файлов корпуса

Имя файла: `{lang}_r{rank}_f{fieldIndex}.opus`

- `lang` ∈ `{it, ru}` — итальянский / русский перевод.
- `rank` — **частотный ранг слова** (bare int, **БЕЗ нулевого добивания**):
  `it_r2_f0.opus`, `it_r1114_f0.opus`. Диапазон рангов в корпусе: **2..12501**.
- `fieldIndex` (`fN`) — какое поле карточки:
  - `f0` — слово
  - `f1` — коллокация
  - `f2` — пример 1 (s1)
  - `f3` — пример 2 (s2)
  - `f4` — пример 3 (s3)

→ **1 ранг = 1 слово = 1 карточка = до 10 клипов** (2 языка × 5 полей).
Не у всех рангов есть все 5 полей; покрытие частичное (~880 из первых 1000
рангов имеют полный набор). Отсутствующие клипы → корректный фоллбэк на TTS
в рантайме (`BgVocabAudioResolver.fileForRank ?: fileFor ?: Segment.Text`).

### Распаковка исходного архива

`work_opus.zip` — это TAR. На Windows GNU tar читает `d:` как remote host,
поэтому нужен `--force-local`:

```bash
cd docs/lesson-methodology/italian
tar --force-local -xf work_opus.zip     # распакует в ./work_opus/
ls work_opus | wc -l                     # ~116973
```

---

## 2. Соответствие ранг в файле ↔ ранг в колоде

Ранг в имени файла (`_r{rank}_`) = колонка `rank` в CSV колоды
(`bg_vocab_12000.csv`, ранги 2..12001). Резолвер
`BgVocabAudioResolver.fileForRank(packId, rank, slot)` ищет файл ровно по
этому рангу. **Ранг ≠ порядковый номер строки** — в ранней MVP-версии банка
был баг row-vs-rank (играл не тот клип); текущий банк ранговый, баг исправлен.

Слот → fieldIndex (см. `BgVocabAudioResolver.slotToFieldIndex`):
`WordIt/WordRu→f0, ColloIt/ColloRu→f1, SentenceIt(i)/SentenceRu(i)→f{i+2}`.

---

## 3. Сборка встроенного в APK пака (слайс первых N рангов)

Пак `ITALIAN_EXPRESS_SHORT.zip` = урок-контент + `bg_vocab/audio/` с opus
первых N рангов. Чтобы обновить/расширить слайс:

```bash
cd d:/Development/BaseGrammy
ZIP=app/src/main/assets/grammarmate/packs/ITALIAN_EXPRESS_SHORT.zip
STAGE=.bg_vocab_scratch/opus_build
CORPUS=docs/lesson-methodology/italian/work_opus

# 1. бэкап + распаковать текущий пак
cp "$ZIP" "$ZIP.v4bak"
rm -rf "$STAGE"; mkdir -p "$STAGE"
unzip -o -q "$ZIP" -d "$STAGE"

# 2. очистить старый audio
rm -rf "$STAGE/bg_vocab/audio"; mkdir -p "$STAGE/bg_vocab/audio"

# 3. скопировать opus рангов 2..N (N=1001 → первые 1000 рангов)
( cd "$CORPUS" && ls | grep -E '^(it|ru)_r([2-9]|[1-9][0-9]|[1-9][0-9][0-9]|100[01])_f[0-9]+\.opus$' \
    | xargs -d '\n' cp -t "$OLDPWD/$STAGE/bg_vocab/audio/" )

# 4. bump packVersion (v4 → v5) — заставит устройство реимпортнуть
sed -i 's/"packVersion": "v4"/"packVersion": "v5"/' "$STAGE/manifest.json"

# 5. пересобрать zip с FORWARD-SLASH (Python zipfile; .NET/PowerShell пишет backslash!)
python -c '
import os, zipfile, pathlib
STAGE = r".bg_vocab_scratch\opus_build"
ZIP   = r"app\src\main\assets\grammarmate\packs\ITALIAN_EXPRESS_SHORT.zip"
with zipfile.ZipFile(ZIP, "w", zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk(STAGE):
        for fn in sorted(files):
            full = os.path.join(root, fn)
            rel = pathlib.PurePath(os.path.relpath(full, STAGE)).as_posix()
            z.write(full, rel, compress_type=zipfile.ZIP_STORED if fn.endswith(".opus") else zipfile.ZIP_DEFLATED)
print("ok")
'

# 6. пересобрать APK (см. docs/BUILD_INSTRUCTIONS.md)
```

**Критично:** записи внутри zip должны быть с **forward-slash**
(`bg_vocab/audio/it_r2_f0.opus`), иначе Java `PackImporter`/`ZipInputStream` их
не найдёт — тихий откат на TTS. Python `zipfile` пишет forward-slash по
спеке; PowerShell `ZipFile` на Windows пишет backslash — **не использовать**.

После смены `packVersion` приложение при запуске реимпортит пак
(`LanguageManager.updateDefaultPacksIfNeeded`) и распакует новый audio в
`drills/ITALIAN_SHORT/bg_vocab/audio/`.

---

## 4. Сборка полного звукового пакета (для загрузчика)

Полный корпус отдаётся **вне APK** через загрузчик (`SoundPackManager`),
т.к. 615 МБ в APK не лезут.

```bash
cd d:/Development/BaseGrammy
python -c '
import os, zipfile
SRC = r"docs\lesson-methodology\italian\work_opus"
OUT = r".bg_vocab_scratch\full_soundpack\ITALIAN_SHORT_soundpack_full.zip"
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with zipfile.ZipFile(OUT, "w", zipfile.ZIP_STORED) as z:
    for fn in sorted(os.listdir(SRC)):
        if fn.endswith(".opus"):
            z.write(os.path.join(SRC, fn), "bg_vocab/audio/" + fn, compress_type=zipfile.ZIP_STORED)
print("ok", os.path.getsize(OUT))
'
```

На выходе — один ZIP (~615 МБ), все записи под `bg_vocab/audio/`, forward-slash,
stored (opus уже сжат). Этот файл → хостинг (см. §6).

---

## 5. Загрузчик (SoundPackManager)

Код: `app/src/main/java/com/alexpo/grammermate/data/SoundPackManager.kt`,
реестр: `SoundPackRegistry.kt`. Зеркалирует `TtsModelManager`:

- `downloadAndInstall(packId)` — скачивает ZIP по URL из `SoundPackRegistry`
  (3 ретрая, storage-проверка `StatFs`, стриминг с прогрессом) → стриминг-распаковка.
- `installFromUri(uri, contentResolver, packId)` — локальный импорт ZIP
  через SAF-пикер (без фазы скачивания).
- Стриминг-экстрактор `ZipInputStream` → `drills/{packId}/bg_vocab/audio/`,
  entry-by-entry (8192 buffer, path-traversal guard, atomic temp→rename,
  идемпотентный overwrite, O(1) RAM). path-фикс: записи с `bg_vocab/audio/`.

UI: Настройки → **«Звуковой пакет фоновой озвучки»**:
- «Импортировать из файла» — SAF-пикер (`ACTION_OPEN_DOCUMENT` `*/zip`).
- «Скачать с сервера» — `downloadAndInstall`.
- Прогресс: `when(DownloadState)` (Downloading/Extracting/Done/Error).

**Важно (почему прогресс ранее не рисовался):** `soundPackDownloadState` должен
писаться в `audioCoordinator._audioState` (НЕ в `_coreState`) — т.к.
`uiState = combine(_coreState, audioCoordinator.audioState) { core, audio, ... ->
core.copy(audio = audio) }` затирает audio состоянием координатора. См.
`AudioCoordinator.updateSoundPackDownloadState`.

---

## 6. Хостинг полного пака (GitHub Release)

gh CLI не установлен — поэтому URL в `SoundPackRegistry` пока placeholder:
`https://github.com/PortnovAlex80/BaseGrammy/releases/download/soundpack-v1/ITALIAN_SHORT_soundpack_full.zip`

Чтобы выложить (когда будет `gh` или через веб):
1. Создать release с тегом `soundpack-v1`.
2. Загрузить `ITALIAN_SHORT_soundpack_full.zip` (615 МБ; лимит GitHub — 2 ГБ/файл).
3. Прописать реальный download URL в `SoundPackRegistry.specFor("ITALIAN_SHORT").downloadUrl`.
4. Для приватного репо — заполнить `authToken` (Bearer), загрузчик сам добавит заголовок.

Для теста без хостинга — `adb push` ZIP в `/sdcard/Download/` и импорт через
SAF-пикер (`adb push ... /sdcard/Download/`, MSYS_NO_PATHCONV=1).

---

## 7. Чеклист «где что»

- Корпус (локально, gitignored): `docs/lesson-methodology/italian/work_opus/`
- Исходник-тар: `docs/lesson-methodology/italian/work_opus.zip`
- Полный sound-pack ZIP (gitignored): `.bg_vocab_scratch/full_soundpack/ITALIAN_SHORT_soundpack_full.zip`
- APK-пак (в репо): `app/src/main/assets/grammarmate/packs/ITALIAN_EXPRESS_SHORT.zip`
- Резолвер: `app/src/main/java/com/alexpo/grammermate/feature/backgroundvocab/BgVocabAudioResolver.kt`
- Загрузчик: `app/src/main/java/com/alexpo/grammermate/data/SoundPackManager.kt`
- Реестр URL: `app/src/main/java/com/alexpo/grammermate/data/SoundPackRegistry.kt`
- UI: `app/src/main/java/com/alexpo/grammermate/ui/screens/SettingsScreen.kt` (`SoundPackSection`)
- Плеер/фоллбэк: `app/src/main/java/com/alexpo/grammermate/feature/backgroundvocab/DeckPlayer.kt`

# -*- coding: utf-8 -*-
"""
Пересобрать итальянские паки, подставив уроки из docs/lesson-methodology/italian.

Все прочие записи (8800 opus-файлов, главы, дриллы, манифест) копируются как
есть, с сохранением исходного compress_type и порядка — аудио не пережимается.

Заодно это лечит расхождение zip и исходников: в поставляемом
ITALIAN_FULL_COURSE.zip 57 строк были испорчены автозаменой (китайское 故事,
"finishingали", "courage", а в B11 — русский род вместо итальянского), тогда
как исходники чистые.
"""
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets/grammarmate/packs"
SRC = ROOT / "docs/lesson-methodology/italian"

PACKS = [
    ("ITALIAN_EXPRESS_SHORT.zip", SRC / "short-engine-lessons"),
    ("ITALIAN_FULL_COURSE.zip", SRC / "full-course-63"),
]

# LanguageManager.updateDefaultPacksIfNeeded переустанавливает встроенный пак
# ТОЛЬКО когда packVersion в ассете отличается от уже импортированного. Без
# подъёма версии телефон продолжит работать со старой копией, и исправленный
# APK ничего не изменит. Прогресс при переимпорте не страдает: PackImporter
# переписывает только каталог пака, а id карточки = номер строки, и число
# строк в уроках сохранено.
PACK_VERSIONS = {
    "ITALIAN_EXPRESS_SHORT.zip": "v7",   # было v6
    "ITALIAN_FULL_COURSE.zip": "v2",     # было v1
}


def lesson_key(name: str) -> str | None:
    """lesson_27_B11.csv -> B11"""
    stem = Path(name).stem
    if not stem.startswith("lesson_"):
        return None
    return stem.split("_")[-1]


def rebuild(zip_name: str, lesson_dir: Path) -> int:
    src_zip = ASSETS / zip_name
    tmp = src_zip.with_suffix(".zip.tmp")
    replaced, missing = 0, []

    with zipfile.ZipFile(src_zip) as zin, zipfile.ZipFile(tmp, "w") as zout:
        for info in zin.infolist():
            key = lesson_key(info.filename)
            if key:
                srcfile = lesson_dir / f"{key}.csv"
                if not srcfile.exists():
                    missing.append(key)
                    zout.writestr(info, zin.read(info.filename))
                    continue
                # писать байты как есть: файлы уже в UTF-8 с нужными переводами строк
                zout.writestr(info, srcfile.read_bytes())
                replaced += 1
            elif info.filename == "manifest.json" and zip_name in PACK_VERSIONS:
                want = PACK_VERSIONS[zip_name]
                text = zin.read(info.filename).decode("utf-8")
                text, n = re.subn(r'("packVersion"\s*:\s*")[^"]*(")',
                                  rf"\g<1>{want}\g<2>", text, count=1)
                if not n:
                    raise RuntimeError(f"{zip_name}: в манифесте нет packVersion")
                zout.writestr(info, text.encode("utf-8"))
                print(f"  {zip_name}: packVersion -> {want}")
            else:
                zout.writestr(info, zin.read(info.filename))

    tmp.replace(src_zip)
    if missing:
        print(f"  ВНИМАНИЕ, нет исходника для: {missing}")
    print(f"  {zip_name}: подставлено уроков {replaced}")
    return replaced


def verify(zip_name: str, lesson_dir: Path) -> bool:
    ok = True
    with zipfile.ZipFile(ASSETS / zip_name) as z:
        for info in z.infolist():
            key = lesson_key(info.filename)
            if not key:
                continue
            want = (lesson_dir / f"{key}.csv").read_bytes()
            if z.read(info.filename) != want:
                print(f"  РАСХОЖДЕНИЕ: {zip_name}:{info.filename}")
                ok = False
    return ok


def main() -> int:
    rc = 0
    for zip_name, lesson_dir in PACKS:
        rebuild(zip_name, lesson_dir)
        if verify(zip_name, lesson_dir):
            print(f"  {zip_name}: сверка с исходниками пройдена")
        else:
            rc = 1
    return rc


if __name__ == "__main__":
    sys.exit(main())

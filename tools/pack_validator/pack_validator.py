import csv
import json
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path


@dataclass
class ValidationError(Exception):
    message: str

    def __str__(self) -> str:
        return self.message


class PackValidator:
    def __init__(self, zip_path: Path) -> None:
        self.zip_path = zip_path

    def validate(self) -> None:
        if not self.zip_path.exists():
            raise ValidationError(f"zip not found: {self.zip_path}")
        if not self.zip_path.is_file():
            raise ValidationError(f"not a file: {self.zip_path}")
        with zipfile.ZipFile(self.zip_path, "r") as zf:
            self._validate_manifest(zf)
            self._validate_stories(zf)
            self._validate_vocab(zf)

    def _validate_manifest(self, zf: zipfile.ZipFile) -> None:
        try:
            manifest_bytes = zf.read("manifest.json")
        except KeyError as exc:
            raise ValidationError("manifest.json not found") from exc
        try:
            manifest = json.loads(self._decode_text(manifest_bytes, "manifest.json"))
        except Exception as exc:
            raise ValidationError("manifest.json invalid json") from exc
        schema_version = manifest.get("schemaVersion", None)
        if schema_version != 1:
            raise ValidationError("manifest.json schemaVersion != 1")
        pack_id = str(manifest.get("packId", "")).strip()
        pack_version = str(manifest.get("packVersion", "")).strip()
        language = str(manifest.get("language", "")).strip()
        if not pack_id or not pack_version or not language:
            raise ValidationError("manifest.json missing packId/packVersion/language")
        lessons = manifest.get("lessons", [])
        if not isinstance(lessons, list) or not lessons:
            raise ValidationError("manifest.json lessons empty")
        for index, entry in enumerate(lessons):
            if not isinstance(entry, dict):
                raise ValidationError(f"manifest.json lessons[{index}] invalid")
            lesson_id = str(entry.get("lessonId", "")).strip()
            file_name = str(entry.get("file", "")).strip()
            if not lesson_id or not file_name:
                raise ValidationError(f"manifest.json lessons[{index}] missing lessonId/file")
            if file_name not in zf.namelist():
                raise ValidationError(f"missing lesson file: {file_name}")
            self._validate_lesson_csv(zf, file_name)

    def _validate_lesson_csv(self, zf: zipfile.ZipFile, file_name: str) -> None:
        try:
            raw = self._decode_text(zf.read(file_name), file_name)
        except Exception as exc:
            raise ValidationError(f"{file_name}: unreadable") from exc
        reader = csv.reader(raw.splitlines(), delimiter=";")
        title_seen = False
        valid_rows = 0
        for line_num, row in enumerate(reader, start=1):
            if not row or all(not cell.strip() for cell in row):
                continue
            if not title_seen:
                title_seen = True
                continue
            if len(row) != 2:
                raise ValidationError(f"{file_name}: line {line_num} cols != 2")
            ru = row[0].strip().strip('"')
            answers = row[1].strip().strip('"')
            if not ru or not answers:
                raise ValidationError(f"{file_name}: line {line_num} empty cell")
            valid_rows += 1
        if not title_seen:
            raise ValidationError(f"{file_name}: missing title")
        if valid_rows == 0:
            raise ValidationError(f"{file_name}: no valid rows")

    def _validate_stories(self, zf: zipfile.ZipFile) -> None:
        for name in zf.namelist():
            if not name.lower().endswith(".json"):
                continue
            if Path(name).name.lower() == "manifest.json":
                continue
            try:
                raw = self._decode_text(zf.read(name), name)
            except Exception as exc:
                raise ValidationError(f"{name}: unreadable") from exc
            try:
                data = json.loads(raw)
            except Exception as exc:
                raise ValidationError(f"{name}: invalid json") from exc
            story_id = str(data.get("storyId", "")).strip()
            lesson_id = str(data.get("lessonId", "")).strip()
            phase = str(data.get("phase", "")).strip()
            text = str(data.get("text", "")).strip()
            if not story_id or not lesson_id or not phase or not text:
                raise ValidationError(f"{name}: missing story fields")
            questions = data.get("questions", [])
            if not isinstance(questions, list):
                raise ValidationError(f"{name}: questions invalid")
            for q_index, q in enumerate(questions):
                if not isinstance(q, dict):
                    raise ValidationError(f"{name}: question {q_index} invalid")
                q_id = str(q.get("qId", "")).strip()
                prompt = str(q.get("prompt", "")).strip()
                options = q.get("options", [])
                correct_index = q.get("correctIndex", None)
                if not q_id or not prompt:
                    raise ValidationError(f"{name}: question {q_index} missing qId/prompt")
                if not isinstance(options, list) or not options:
                    raise ValidationError(f"{name}: question {q_index} options empty")
                if not isinstance(correct_index, int) or correct_index < 0 or correct_index >= len(options):
                    raise ValidationError(f"{name}: question {q_index} correctIndex invalid")

    def _validate_vocab(self, zf: zipfile.ZipFile) -> None:
        for name in zf.namelist():
            if not name.lower().endswith(".csv"):
                continue
            if not Path(name).name.lower().startswith("vocab_"):
                continue
            try:
                raw = self._decode_text(zf.read(name), name)
            except Exception as exc:
                raise ValidationError(f"{name}: unreadable") from exc
            reader = csv.reader(raw.splitlines(), delimiter=";")
            for line_num, row in enumerate(reader, start=1):
                if not row or all(not cell.strip() for cell in row):
                    continue
                if len(row) < 2:
                    raise ValidationError(f"{name}: line {line_num} cols < 2")
                native_text = row[0].strip().strip('"')
                target_text = row[1].strip().strip('"')
                if not native_text or not target_text:
                    raise ValidationError(f"{name}: line {line_num} empty cell")

    def _decode_text(self, data: bytes, label: str) -> str:
        for encoding in ("utf-8-sig", "utf-16", "utf-16-le", "utf-16-be", "cp1251"):
            try:
                return data.decode(encoding)
            except UnicodeDecodeError:
                continue
        raise ValidationError(f"{label}: unsupported encoding")


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: python tools/pack_validator/pack_validator.py <lesson_pack.zip>")
        return 2
    zip_path = Path(sys.argv[1])
    try:
        PackValidator(zip_path).validate()
    except ValidationError as exc:
        print(f"ERROR: {exc}")
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

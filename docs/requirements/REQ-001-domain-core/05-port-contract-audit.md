# 05 — Domain Port Contract Audit (AC-10 baseline lock)

**Эпизод:** E01 (epic_id=85), проект GrammarMate (project_id=25)
**AC:** AC-10 (`04-acceptance-criteria.md#AC-10`) — 9 доменных портов стабильны
**Канон:** SRS-001 §5 (`02-srs.md`), `docs/architecture/TARGET_ARCHITECTURE.md`
**Дата аудита:** 2026-07-08
**Baseline commit (dev):** ed4e3ccea
**Verdict:** PASS — все 9 интерфейсов присутствуют, сигнатуры совпадают с SRS-001 §5.1–§5.9.

---

## 1. Назначение документа

AC-10 фиксирует контракт 9 доменных портов как **baseline** (E01 = точка заморозки).
Любое изменение сигнатуры порта после старта Wave 1 = **drift** и должно идти через
отдельную drift-задачу с `trace_add(link_type:'derived_from', target:SRS-001)`, а не
молчаливым edit'ом (NFR-4, shared-mutation-risk).

Этот документ — записанный результат аудита baseline'а. CI/ревью-гейт «diff портов
после Wave 1 = пуст» (`git diff origin/main -- domain/.../repository/ domain/.../audio/`)
будет сравнивать именно с этим зафиксированным состоянием.

> Регресс-замок: данный документ сам по себе не меняет код портов. Порты уже
> существуют на ветке `dev` (созданы каркасными задачами E01); задача AC-10 —
> **аудит соответствия SRS §5 + фиксация baseline**, а не генерация интерфейсов.

## 2. Что проверено

- 9 интерфейсов в `domain/repository/` (7) и `domain/audio/` (2);
- сигнатуры каждого метода vs SRS-001 §5.1–§5.9:
  - mutating-методы = `suspend`;
  - реактивные источники событий = `Flow<...>`;
  - ID-параметры/возврат = value classes (`PackId`, `LessonId`, `CardId`,
    `LanguageId`, `ChapterId`, `SessionId`);
- 6 value-class ID + 6 companion-фабрик `SessionId` (`forLesson`, `forVerbDrill`,
  `forDailyTranslate`, `forDailyVerbs`, `forAuxDrill`, `forPomodoro`);
- отсутствие активных Android/sherpa-зависимостей в сигнатурах портов;
- `git diff dev -- domain/.../repository/ domain/.../audio/` = пуст (baseline = dev).

## 3. Audit-таблица: 9 портов × SRS §5

Все 9 интерфейсов совпадают с SRS-001 §5.1–§5.9 по числу и форме методов
(suspend/Flow/value-class). Подсчёт методов выполнен по фактическому коду на ветке
`dev` (commit ed4e3ccea).

| # | Порт | Пакет | SRS § | suspend | Flow | Совпадение сигнатур |
|---|------|-------|-------|---------|------|---------------------|
| 1 | `ContentRepository`    | `domain.repository` | §5.1 | 9  | 1  | ✓ (10 методов; единственный Flow — `observeLessons`) |
| 2 | `SessionRepository`    | `domain.repository` | §5.2 | 8  | 0  | ✓ (★ card_15: `setCurrentCard` по PK, атомарные `loadSession`/`saveSession`) |
| 3 | `MasteryRepository`    | `domain.repository` | §5.3 | 5  | 1  | ✓ (`observeMastery` Flow; `getDueCards`/`updateSrsState` SRS) |
| 4 | `ProgressRepository`   | `domain.repository` | §5.4 | 8  | 1  | ✓ (`observeStreak` Flow; streak/chapter/drill/daily-cursor) |
| 5 | `UserContentRepository`| `domain.repository` | §5.5 | 14 | 1  | ✓ (`observeHiddenCards` Flow; hidden/bad/bg-vocab/profile/pomodoro) |
| 6 | `VocabDrillRepository` | `domain.repository` | §5.6 | 16 | 1  | ✓ (`observeDueWords` Flow; vocab-SRS/verb/aux/boss-rewards) |
| 7 | `SettingsRepository`   | `domain.repository` | §5.7 | 4  | 1  | ✓ (`observeAppConfig` Flow; RMW `updateAppConfig`; migration flags) |
| 8 | `AudioRepository`      | `domain.audio`      | §5.8 | 4  | 2  | ✓ (`speak`/`recognizeSpeech` Flow; остальное suspend) |
| 9 | `AudioModelRepository` | `domain.audio`      | §5.9 | 3  | 3  | ✓ (`downloadTtsModel`/`downloadAsrModel`/`observeBluetoothMic` Flow) |
| — | **Итого** | — | — | **71** | **11** | **9/9 PASS** |

### Сводка правил (Then.2 AC-10)

- **mutating = suspend:** все 71 mutating-метод объявлены `suspend`. Ни один
  изменяющий состояние метод не блокирует поток синхронно.
- **реактивные = Flow:** все 11 реактивных источников — `Flow<...>`
  (события/прогресс/снимки для UI). Возвращаемое значение `Flow` всегда
  параметризовано доменным типом (`List<Lesson>`, `LessonMastery?`, `AudioEvent`,
  `DownloadProgress`, …).
- **ID = value classes:** каждый идентификатор в сигнатурах — один из
  `PackId`/`LessonId`/`CardId`/`LanguageId`/`ChapterId`/`SessionId` (все
  `@JvmInline value class(val value: String)`, файл `domain/model/Pack.kt`).
  Чистые строковые ключи (`comboKey`, `wordId`, `tense`, `key` миграции) — по
  контракту SRS §5 остаются `String` (не business-identity, а составные/свободные).

## 4. Value-class ID + SessionId-фабрики

Файл `domain/src/main/java/com/alexpo/grammermate/domain/model/Pack.kt`:

```kotlin
@JvmInline value class PackId(val value: String)
@JvmInline value class LessonId(val value: String)
@JvmInline value class CardId(val value: String)
@JvmInline value class LanguageId(val value: String)
@JvmInline value class ChapterId(val value: String)
@JvmInline value class SessionId(val value: String) {
    companion object {
        fun forLesson(packId, lessonId): SessionId
        fun forVerbDrill(packId): SessionId
        fun forDailyTranslate(packId): SessionId
        fun forDailyVerbs(packId): SessionId
        fun forAuxDrill(packId): SessionId
        fun forPomodoro(packId): SessionId
    }
}
```

Все 6 value classes и все 6 companion-фабрик присутствуют и совпадают с SRS §5
(вступительная часть секции: «`SessionId` имеет companion-фабрики …»).

## 5. Соответствие архитектуре

`docs/architecture/TARGET_ARCHITECTURE.md` (строка 73): направление зависимости
строго внутрь — `:app → :domain`; `:domain` не зависит ни от `:app`, ни от Android,
ни от Room/Hilt/Sherpa. Порты `domain/repository/*` и `domain/audio/*` — это
чистые Kotlin-интерфейсы, чьи реализации (Room E02, Sherpa E03) подключаются через
DI в data-слое. Контракт портов тем самым **не знает** о реализациях —
соответствует принципу инверсии зависимости.

> Примечание для AC-11: в `domain/audio/AudioRepository.kt` встречается текст
> `com.k2fsa.sherpa.onnx` — но **только внутри KDoc-комментария** (пояснение,
> почему порт скрывает Sherpa), не как `import` и не в сигнатуре. AC-11
> (grep `sherpa|com.k2fsa` → 0) адресует это отдельно; для AC-10 это не drift
> сигнатуры. Любое реальное изменение `import`/сигнатуры, тащащее Sherpa в порт,
> будет поймано AC-2/AC-11 как violation.

## 6. Drift-gate (forward-looking)

AC-10.Then.1: после старта Wave 1 `git diff origin/main -- domain/repository/
domain/audio/` должен быть пуст вне явно размеченных drift-задач.

**Правило для всех последующих задач (E04+):**
1. Если задаче нужно **добавить метод** в порт → это drift: создать отдельную задачу
   с `trace_add(link_type:'derived_from', target:SRS-001)`, обновить SRS §5 и этот
   документ (раздел «Drift log» ниже), затем менять интерфейс.
2. **Молчаливый edit сигнатуры порта** (переименование/удаление/смена типа
   параметра) в обычной feature-задаче = NFR-4 violation, возвращается на ревью.
3. Добавление реализации порта (новый `class … : ContentRepository` в data-слое)
   drift'ом **не** является — порт-контракт стабилен, реализации плодятся свободно.

### Drift log

| Дата | Задача | Порт | Изменение | trace → SRS |
|------|--------|------|-----------|-------------|
| — | — | — | (пусто: baseline зафиксирован, drift'ов нет) | — |

---

## 7. Итог

- **9/9 интерфейсов** присутствуют в `domain/repository/` (7) + `domain/audio/` (2);
- **сигнатуры** всех методов совпадают с SRS-001 §5.1–§5.9 (suspend/Flow/value-class);
- **baseline** зафиксирован на ветке `dev` (commit ed4e3ccea);
- **drift-gate** задокументирован; будущие изменения портов — только через
  drift-задачу с трассировкой на SRS-001.

**AC-10: PASS** — 9 доменных портов стабильны, контракт заморожен после E01.

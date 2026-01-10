# GrammarMate / Deep Grammar Trainer — TODO / чек‑лист для агента разработки (поэтапно, с гейтами)
## Hotfix 2026-01-10
- [x] Fix mastery persistence on card display and refresh flowers on exit.
- [x] Align flower decay with ladder interval rules.
- [x] Run `./gradlew test`.
- [x] Commit fixes.

Версия: 1.0  
Дата: 2026-01-08

## Пример структуры отчёта агента по этапу (как отвечать после каждого stage)
**Stage:** 00-bootstrap  
**Branch:** `stage/00-bootstrap` (от `main`)  
**Сделано:**  
- ✅ Создан Android-проект (Compose), экран-заглушка Home  
- ✅ CI/скрипты сборки `./gradlew assembleDebug`  
- ✅ Сборка Debug APK приложена

**Артефакты:**  
- `app-debug.apk`  
- `CHANGELOG_stage_00.md` (кратко: что сделано/что не сделано/риски)

**Команды для воспроизведения:**  
- `./gradlew clean assembleDebug`  
- `./gradlew test`

**Проверка (smoke):**  
- Установка APK на устройство → приложение открывается → не падает на старте

**Gate:**  
- Ожидаю команду пользователя: `GO STAGE 01`

---

## 0) Базовые ограничения (не нарушать)
1) **Нет сервера и нет базы данных** (ни SQL, ни NoSQL).  
2) Персистентность: **файлы** (JSON/YAML/CSV) + **атомарная запись** (temp → fsync → rename).  
3) Контент загружается пакетами **Lesson Pack** через **Settings**.  
4) UI: **stateless renderer** (Compose-экраны рисуют state; вычисления/бизнес‑правила — в домене/UseCases).  
5) Все форматы файлов — с `schemaVersion` + миграции.

Источник требований и терминов: `GrammarMate_project_idea_v1_5.md` (см. репозиторий/артефакты проекта).  

---

## 1) Договорённости по репозиторию
### 1.1 Ветки по этапам
- `main` — только стабильные точки (каждый stage merge-ится сюда после приемки).
- Каждая итерация делается в новой ветке:
  - `stage/00-bootstrap`
  - `stage/01-architecture`
  - `stage/02-file-storage`
  - `stage/03-lesson-pack-import`
  - `stage/04-home-skill-matrix`
  - `stage/05-training-mvp`
  - `stage/06-story-quiz`
  - `stage/07-settings-mgmt`
  - `stage/08-voice-tts`
  - `stage/09-polish-tests-release`

### 1.2 Теги релиза
- `rc-0.0.1-stage00`, `rc-0.0.2-stage01`, …  
- Финальный MVP: `mvp-1.0.0`

### 1.3 Минимальный набор артефактов на каждом этапе
- `app-debug.apk` (или AAB, если договоритесь, но для быстрых проверок — APK)
- `CHANGELOG_stage_XX.md`
- Скриншоты/видео (по возможности, но не блокирующее)
- Отчёт о тестах: `./gradlew test` (лог/скрин)

---

## 2) Test Content (готовится рано и живёт в репо)
Нужны три вида тестовых данных:
1) **Урок (CSV)** — RU→EN предложения.  
2) **Story Check-in / Check-out (JSON)** — история + квиз.  
3) **Vocabulary (CSV/JSON)** — слова RU→EN с флагом сложность.

Цель: чтобы с Stage 03+ можно было гонять реальный импорт и прогонять сценарии без ожидания методолога.

---

## 3) Поэтапный TODO‑план (с гейтами и критериями приемки)

### Stage 00 — Bootstrap Android проекта
**Branch:** `stage/00-bootstrap`  
**Цель:** убедиться, что всё собирается и запускается.

**TODO**
- [ ] Создать Android-проект (Kotlin, Gradle, Compose).
- [ ] Минимальная навигация (пока можно 1 экран-заглушка Home).
- [ ] Настроить buildTypes: debug/release (release пока без подписи).
- [ ] Добавить базовый `README.md` (как собрать).
- [ ] Добавить простейший crash-safe логгер (Logcat).

**Команды**
- `./gradlew clean assembleDebug`

**Acceptance (обязательно)**
- [ ] Debug APK ставится и запускается на реальном устройстве.
- [ ] Первый запуск без краша.

**Gate**
- Остановиться и ждать команды пользователя: `GO STAGE 01`.

---

### Stage 01 — Каркас архитектуры (без «фич»)
**Branch:** `stage/01-architecture`  
**Цель:** заложить слои, чтобы дальше не переделывать.

**TODO**
- [ ] Изучи документы по архитектуре проекта GrammarMate_project_idea.md и проведи моделирование домена
- [ ] Модули/пакеты: `app`, `domain`, `data` (можно в одном модуле, но с чёткими пакетами).
- [ ] Типы домена (минимум): `Profile`, `Settings`, `LessonPack`, `Lesson`, `SentenceItem`, `StoryQuiz`.
- [ ] UseCase-стиль: функции, которые принимают state + input и возвращают новый state/commands.
- [ ] In-memory репозитории (временные) для UI протяжки.

**Acceptance**
- [ ] Проект собирается.
- [ ] Есть unit‑test «скелет» (1–2 теста на доменные модели/UseCase).

**Gate**
- Debug APK + команда пользователя `GO STAGE 02`.

---

### Stage 02 — Файловая персистентность (атомарная запись) + миграции
**Branch:** `stage/02-file-storage`

**TODO**
- [ ] `FileStore`: чтение/запись JSON (profile/settings).
- [ ] Атомарная запись: write temp → flush/fsync → rename.
- [ ] Версионирование: `schemaVersion` и простая миграция v1→v2 (заглушка, но механизм должен быть).
- [ ] Путь хранения: `context.filesDir/grammarmate/…`
- [ ] Логи ошибок чтения/валидации.

**Acceptance**
- [ ] После убийства процесса данные не повреждаются (проверка: несколько раз сохранить/перезапустить).
- [ ] Пустой профиль создаётся автоматически на первом запуске.

**Gate**
- Debug APK + `GO STAGE 03`.

---

### Stage 03 — Импорт Lesson Pack (Settings) + тестовый пакет
**Branch:** `stage/03-lesson-pack-import`

**TODO**
- [ ] Экран Settings (минимальный) с кнопкой **Import Lesson Pack**.
- [ ] Импорт из zip (Document Picker) → распаковать во внутреннее хранилище.
- [ ] Валидация `manifest.json` (schemaVersion, packId, packVersion, lessons[]).
- [ ] Парс CSV урока (строгий разделитель `;`, UTF‑8).
- [ ] Индексация пакетов: список установленных пакетов, версии, дата импорта.
- [ ] Ошибки импорта показывать пользователю (toast/dialog) + лог.

**Acceptance**
- [ ] Можно импортировать тестовый pack → он отображается в Settings как установленный.
- [ ] После перезапуска приложения pack остаётся доступным.

**Gate**
- Debug APK + приложить тестовый pack zip + `GO STAGE 04`.

---

### Stage 04 — Home Screen + Grammar Skill Matrix (12 плиток)
**Branch:** `stage/04-home-skill-matrix`

**TODO**
- [ ] Home: Header (профиль, язык, ⚙).
- [ ] Primary action: Start/Continue (пока stub).
- [ ] Матрица 4×3 (12 уроков) со статусами: SEED / SPROUT / FLOWER / LOCKED.
- [ ] Overlay (паутинка) если FLOWER + низкая freshness (можно заглушку логикой).
- [ ] Легенда и кнопка «How This Training Works» (пока модалка с текстом).

**Acceptance**
- [ ] Матрица рендерится стабильно на разных экранах.
- [ ] Тап по tile открывает экран детали (можно заглушку) или сразу Training (по решению).

**Gate**
- Debug APK + `GO STAGE 05`.

---

### Stage 05 — Training MVP (RU→EN ввод текстом) + Warm‑up + Hint rate
**Branch:** `stage/05-training-mvp`

**TODO**
- [ ] Экран Training: RU prompt, поле ввода, кнопки: Check/Next, иконка “Показать ответ” (tooltip).
- [ ] Нормализация ответа (trim, lower, схлоп пробелов, пунктуация базово).
- [ ] Поддержка `alt_en` для сравнения (в UI не показывать).
- [ ] Сессия: список items на подурок (пока фиксированно 10), прогресс в %.
- [ ] Warm‑up: 3 лёгких предложения перед подуроком (не влияет на метрики).
- [ ] Подсказки: считать `hintUsageRate` на подурок.
- [ ] Показ ответа доступен всегда, останавливает авто‑STT и ставит таймер на паузу.
- [ ] Сохранение результатов сессии в Profile (файл).

**Acceptance**
- [ ] Можно пройти подурок, выйти, вернуться — прогресс сохранён.
- [ ] Hint rate считается корректно.

**Gate**
- Debug APK + `GO STAGE 06`.

---

### Stage 06 — Story Quiz (Check‑in / Check‑out)
**Branch:** `stage/06-story-quiz`

**TODO**
- [ ] Экран Story Quiz: текст истории + вопросы (MCQ/TF).
- [ ] Импорт/чтение story JSON из lesson pack.
- [ ] Сохранение метрик: accuracy, time-to-answer, hint rate (если добавите подсказки).
- [ ] Привязка к Lesson N: доступ до и после (можно через меню урока).

**Acceptance**
- [ ] Check-in и Check-out запускаются и завершаются без краша.
- [ ] Метрики сохраняются и отображаются «до/после» (минимально).

**Gate**
- Debug APK + `GO STAGE 07`.

---

### Stage 07 — Settings: управление контентом + профиль (экспорт/импорт)
**Branch:** `stage/07-settings-mgmt`

**TODO**
- [ ] Список пакетов: удалить pack.
- [ ] Delete all lessons (опасное действие → confirm).
- [ ] Reset progress (confirm).
- [ ] Export profile → zip (profile + settings).
- [ ] Import profile → восстановление (с миграциями).

**Acceptance**
- [ ] Экспорт/импорт работает на чистой установке.
- [ ] После reset прогресс реально сбрасывается.

**Gate**
- Debug APK + `GO STAGE 08`.

---

### Stage 08 — Голос (STT) + озвучка (TTS) — MVP
**Branch:** `stage/08-voice-tts`

**TODO**
- [ ] Voice input (Google STT): разрешения, кнопка микрофона, обработка ошибок.
- [ ] Auto‑submit после STT (без обязательного подтверждения кнопкой).
- [ ] TTS: озвучить правильный ответ на экране подсказки.
- [ ] Кнопка «STT ошибся» (опционально) — фиксировать флаг в статистике.
- [ ] Настройки звука/голоса в Settings.

**Acceptance**
- [ ] На устройстве можно продиктовать ответ и пройти карточку.
- [ ] TTS воспроизводит правильный ответ.

**Gate**
- Debug APK + `GO STAGE 09`.

---

### Stage 09 — Тесты, полировка UX, release pipeline
**Branch:** `stage/09-polish-tests-release`

**TODO**
- [ ] Unit‑tests на нормализацию, импорт CSV/manifest, подсчёт hint rate.
- [ ] Инструментальные smoke tests (минимум: старт приложения).
- [ ] Crash-free прогон основных flows: import pack → start training → finish → story quiz.
- [ ] Release build: подпись (если есть keystore), `assembleRelease`.
- [ ] Версионирование: `versionCode`, `versionName`.
- [ ] Чек‑лист регрессии перед релизом (см. раздел 4).

**Acceptance**
- [ ] `./gradlew test` проходит.
- [ ] `./gradlew assembleRelease` проходит.
- [ ] Релизный APK/AAB устанавливается и работает (smoke).

**Gate**
- Release candidate: `rc-1.0.0` + финальная приемка пользователем.

---

## 4) Чек‑лист проверки перед релизом (RC / MVP)
### 4.1 Установка/обновление
- [ ] Clean install (с нуля): старт без краша.
- [ ] Update install (поверх предыдущего RC): профиль и пакеты не ломаются.
- [ ] Права (микрофон): запрос только когда нужно.

### 4.2 Контент
- [ ] Import Lesson Pack zip → успех.
- [ ] Некорректный zip/manifest/csv → понятная ошибка, приложение не падает.
- [ ] Delete pack / delete all → реально удаляет, UI обновляется.

### 4.3 Home / Matrix
- [ ] 12 плиток, статусы корректно отображаются.
- [ ] Primary action ведёт в ожидаемый следующий шаг.

### 4.4 Training
- [ ] Warm‑up есть, но не влияет на прогресс.
- [ ] Ответ отображается, hint rate считается.
- [ ] Нормализация не ломает апострофы (don’t ≠ dont).
- [ ] Выход/возврат в сессию не ломает состояние.

### 4.5 Story Quiz
- [ ] Текст прокручивается, вопросы отвечаются, результат сохраняется.
- [ ] Сравнение «до/после» (хотя бы численно).

### 4.6 Персистентность
- [ ] После force close данные сохраняются.
- [ ] Нет повреждения файлов при частых сохранениях.

### 4.7 Производительность/стабильность
- [ ] Нет ANR на импорте (если нужно — фон/корутины).
- [ ] Импорт больших пакетов не вызывает OOM (stream unzip/parse).

---

## 5) Минимальный набор файлов/форматов (ориентир)
**Lesson Pack root (zip):**
- `manifest.json` (строго по спецификации: schemaVersion, packId, packVersion, language, lessons[])
- `lesson_01.csv` (и т.д.) — `ru;en;alt_en;comment`
- `*.json` (опционально) — Story Quiz файлы. Рекомендуемый подход для MVP: **сканировать все json в паке** и подхватывать те, у которых есть поля `lessonId` и `phase` (CHECK_IN/CHECK_OUT).
- `*.csv` (опционально) — Vocabulary файлы. В MVP можно держать тот же заголовок `ru;en;alt_en;comment` и читать «как словарь».

---

## 6) Примечания по методике (важные продуктовые флажки)
- Первые **3–4 подурока** каждой темы — строго **New-only**; смешивание включать позже.
- Уроки/пулы Active/Reserve и правила миксования — отдельный документ (в MVP можно stub, но интерфейсы закладывать).

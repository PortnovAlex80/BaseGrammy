# PRD-003 — Аудио-адаптер: завершение SherpaAudioRepository

**Эпизод:** E03 (epic_id=87), проект GrammarMate (project_id=25)
**Волна:** 0 — Фундамент
**Стадия:** Formalization (PRD)
**Дата:** 2026-07-07
**Status:** Accepted

**Артефакт-источник:** Discovery brief (artifact_id=473, `00-discovery-brief.md`, decision=go).
**Upstream-контракт:** SRS-001 (E01, artifact_id=450, `02-srs.md`, status=accepted) — доменные порты `AudioRepository` / `AudioModelRepository` и доменные типы событий зафиксированы в §5.8/§5.9 и FR-7.
**Repository:** BaseGrammy (repository_id=7), ветка `rewrite/v2-clean-architecture`.
**Код:** PRD-003

> PRD фиксирует продуктовые границы (зачем, для кого, что в scope). Системный контракт
> (порты, сигнатуры, JNI-специфика) — забота SRS-003 (saga-architect). Все сигнатуры
> доменных портов, упомянутые здесь, — regression-locked в SRS-001 (E01); PRD на них
> **ссылается, не дублирует** и не меняет.

---

## 1. Резюме эпизода

E03 — это инфраструктурный аудио-эпизод Wave 0, в котором data-слойный адаптер
`SherpaAudioRepository` (сегодня — архитектурный каркас с TODO) доводится до
**реального** TTS-синтеза и ASR-распознавания речи через нативную библиотеку
Sherpa-ONNX, а `SherpaAudioModelRepository` — до реальной доставки моделей на
диск. Доменный порт `AudioRepository` (pure-Kotlin, без Android/Sherpa) уже
зафиксирован в SRS-001 (E01) и **не меняется**; E03 реализует его.

Сам по себе эпизод не приносит пользователю новых экранов — его ценность
косвенная и заключается в снятии жёсткого блокера: **без работающего аудио
невозможны голосовой режим ввода, озвучка карточек, фоновый словарь и
story-narration** в любом из продуктовых эпиков Wave 1 (E04–E09). E03 — это
«голосовая опора» всей продуктовой программы GrammarMate.

Эпизод переносит рабочую логику из legacy-движка (`AudioCoordinator.kt` 939 строк +
`TtsEngine` / `AsrEngine` / `*ModelManager` / `*ModelRegistry` /
`BluetoothAudioRouter` / `SegmentPlayer` / `MemoryChecker`) в clean-архитектуру v2
как набор helper-классов за доменным интерфейсом. Sherpa-ONNX AAR уже подключён
к проекту (saga note id=2), доменные порты готовы (SRS-001 E01) — поэтому путь
однозначный (decision=go, развилки нет).

## 2. Бизнес-контекст

GrammarMate — приложение для тренировки иностранных языков с упором на
**разговорную практику**. Голос — первичный канал взаимодействия: учащийся
произносит ответ вслух, приложение распознаёт речь; приложение озвучивает
карточки, слова, истории. В legacy (ветка `main`) весь этот стек работал поверх
одного God Object'а `AudioCoordinator`, который тянул Sherpa-ONNX в каждый
модуль. В v2 (ветка `rewrite/v2-clean-architecture`) домен изолирован от
нативного аудио через чистый порт `AudioRepository`; остался лишь
data-слойный skeleton с TODO.

**Почему критично именно сейчас.** E03 — прямой блокер для всей голосовой
программы:

- **E04 Training, E05 Verb, E06 Vocab, E07 Daily (Wave 1, drill-эпики):** все
  четыре используют `speak()` для озвучки карточек/слов/предложений и
  `recognizeSpeech()` для голосового ввода ответа (`InputMode.VOICE`). Без
  реального TTS/ASR голосовой режим drill'ов остаётся заглушкой — флагманская
  фича продукта неработоспособна.
- **E08 BgVocab (фоновый словарь):** фоновое прослушивание словаря во время
  другой активности через `SegmentPlayer` (Mutex-serialized, pause/resume).
  Без аудио-адаптера фоновой сессии не существует.
- **E09 Story narration:** озвучка многоязычных историй по сегментам —
  переиспользует тот же `SegmentPlayer`.

Без E03 **voice mode отсутствует целиком** — приложение остаётся «немым»
тренажёром только с клавиатурным/word-bank вводом. Это снижает продуктовую
ценность до уровня обычного flashcard-приложения и обесценивает investment в
Sherpa-ONNX native lib.

**Решение спонсора (saga note id=28):** v2 — это база, достраиваем пробелы, а не
переписываем готовое. Аудио-адаптер — именно такой пробел: контракт готов,
реализация — нет. Decision=go, coverage=1.0.

## 3. User stories

PRD фиксирует ценности с точки зрения конечного пользователя GrammarMate. E03
разблокирует их **через downstream-эпики**; сам по себе он не добавляет UI, но
без него эти истории невозможно реализовать.

- **US-1 Голосовой ввод ответа (через E04–E07, ASR).** Как учащийся, я хочу
  произносить ответ вслух и видеть распознанный текст вместо ручного ввода,
  чтобы тренироваться в активном говорении, а не только в письме.
  *Зависит от:* `recognizeSpeech() → Flow<RecognitionEvent>` с реальным
  Whisper + Silero VAD.

- **US-2 Озвучка карточки (через E04–E07, TTS).** Как учащийся, я хочу слышать,
  как произносится слово/предложение/форма глагола, чтобы ставить
  произношение и тренироваться на слух.
  *Зависит от:* `speak() → Flow<AudioEvent>` с реальным OfflineTts.

- **US-3 Фоновый словарь (через E08, SegmentPlayer).** Как учащийся, я хочу,
  чтобы словарь играл фоном во время другой активности приложения, с
  возможностью поставить на паузу и возобновить, чтобы слушать слова
  пассивно, не отрываясь от основной тренировки.
  *Зависит от:* `SegmentPlayer` (Mutex-serialized, pause/resume re-speaks
  current segment).

- **US-4 Story narration (через E09, SegmentPlayer).** Как учащийся, я хочу
  слушать озвучку истории по сегментам (текст + паузы + аудио-вставки), чтобы
  воспринимать язык на слух в связном контексте.
  *Зависит от:* `SegmentPlayer` (shared с US-3).

- **US-5 Bluetooth-микрофон (ASR).** Как учащийся, я хочу использовать
  беспроводную гарнитуру для голосового ввода, чтобы тренироваться в
  движении и без проводов.
  *Зависит от:* `BluetoothAudioRouter` (SCO API<31 / `setCommunicationDevice`
  API≥31) + `AudioModelRepository.setBluetoothMic`.

- **US-6 Download моделей из Settings (через E13).** Как учащийся, я хочу
  скачать нужные языковые TTS/ASR-модели из настроек до тренировки, чтобы
  аудио работало офлайн (поезд, самолёт) без зависимости от сети.
  *Зависит от:* `AudioModelRepository.downloadTtsModel/downloadAsrModel`
  (HTTP с retry). Сам UI кнопки download — в E13 Settings; менеджер загрузки
  и распаковки — в E03.

## 4. Scope IN

E03 завершает аудио-адаптер. В scope входят:

### 4.1. TTS-синтез (`speak`)
- Реальный синтез речи через Sherpa-ONNX `OfflineTts` для всех поддерживаемых
  языков (VITS-Piper для большинства, Kokoro где применимо).
- **Резидентный LRU-кэш TTS-моделей (maxSize=3, ~450 MB native heap):**
  модели держатся загруженными в памяти для **мгновенного переключения языков
  без реинициализации** (latency-risk mitigation). Вытеснение по LRU.
- **RU text-scale** (тюнинг скорости/высоты для русского языка).
- **Fallback на Android `TextToSpeech`** если Sherpa-модель недоступна (нет
  файлов, 32-bit ARM не поддерживается и т.п.) — синтез не падает молча.
- Эмит реальной последовательности событий:
  `AudioEvent.Started → [Progress(fraction)] → Completed | Failed(error)`
  (события — доменные типы из SRS-001 FR-7).

### 4.2. ASR-распознавание (`recognizeSpeech`)
- Реальное распознавание через Sherpa-ONNX `OfflineRecognizer` (Whisper Small,
  ~375 MB, multilingual — одна модель на все языки).
- **Silero VAD** для детекции конца фразы (endpoint detection по паузе).
- Захват звука через Android `AudioRecord` (VOICE_RECOGNITION source, 16 кГц).
- Эмит реальной последовательности:
  `RecognitionEvent.ListeningStarted → [Partial(text)] → EndpointDetected → Final(text) | Failed`.

### 4.3. Доступность моделей (`isTtsAvailable` / `isAsrAvailable`)
- Реальная проверка по файловому манифесту (`requiredFiles` / `requiredDirs`
  из legacy-реестров): TTS — per-language (имена каталогов варьируются:
  `vits-piper-en_US-amy-low`, и т.д.), ASR — единый манифест
  (`asr/whisper-small/` + `asr/vad/silero_vad.onnx`, уже реализовано — сохранить).

### 4.4. Безопасность памяти (`MemoryChecker`)
- **Pre-check перед загрузкой любой модели в native heap** — блокировка load,
  если свободной памяти недостаточно (порог порядка ~800 MB для ASR / ~150 MB
  для TTS). Без этого TTS LRU (~450 MB) + ASR (~375 MB) → OOM.

### 4.5. Маршрутизация аудио (`BluetoothAudioRouter`)
- Переключение микрофона на Bluetooth SCO (API<31) / через
  `setCommunicationDevice` (API≥31) — перенос из legacy.

### 4.6. Воспроизведение сегментов (`SegmentPlayer`)
- **Mutex-serialized** последовательное воспроизведение сегментов озвучки;
  `pause` / `resume` переозвучивают текущий сегмент (не продолжают с середины
  — дизайн legacy сохраняется). **Shared с E08 (bg-vocab) и E09 (story)** —
  поэтому интерфейс `SegmentPlayer` должен быть зафиксирован в SRS-003
  (shared-mutation-risk, см. §7, §8).

### 4.7. Доставка моделей (`AsrModelManager`, `TtsModelManager` + Registry)
- Сетевая загрузка + распаковка `.tar.bz2` (TTS per-language, ASR Whisper+VAD)
  с **3-retry HTTP**. Прогресс эмитится через `DownloadProgress`
  (`Downloading(%) → Extracting → Initializing → Done | Failed`).
- Реестры моделей (per-language manifest) — перенос из legacy
  `TtsModelRegistry` / `AsrModelRegistry`.

### 4.8. Звуковые эффекты (`playSoundEffect`)
- SoundPool с готовыми mp3 из `res/raw` (`CORRECT_ANSWER`, `WRONG_ANSWER`).
  **Уже реализовано в skeleton** — сохранить как есть. `LESSON_COMPLETE`
  пока переиспользует клип корректного ответа (отдельный ресурс — minor gap).

### 4.9. Остановка (`stop`)
- Неблокирующая остановка текущего TTS-воспроизведения и активной ASR-записи
  (через флаг + cancel job, перенос из legacy).

## 5. Scope OUT

Явные non-goals эпизода (что **не** входит, чтобы избежать scope-creep):

- **Sound packs (pre-rendered Ogg-Opus ZIP, ITALIAN_SHORT ~615 MB)** — это
  отдельный механизм воспроизведения готовых аудио, отличающийся от TTS-синтеза.
  Переносится в **E08 (bg-vocab)**, где он и нужен (OQ-1, answered).
- **Доменный порт `AudioRepository` / `AudioModelRepository`** — уже зафиксирован
  в SRS-001 (E01). E03 его **не меняет** (NFR-4 drift-запрет). Любое изменение
  сигнатуры — отдельная drift-задача.
- **UI кнопок download / экран настроек аудио** — в **E13 (Settings)**. E03
  предоставляет только менеджер загрузки (port-реализацию).
- **Сама нативная библиотека Sherpa-ONNX** — уже подключена как AAR
  (`libs/sherpa-onnx-static-link-onnxruntime-1.12.40.aar`, saga note id=2, H3).
  E03 её использует, не встраивает.
- **Доменные sealed-типы событий** (`AudioEvent`, `RecognitionEvent`,
  `DownloadProgress`, `ModelStatus`, `SoundEffect`) — зафиксированы в SRS-001
  (FR-7); E03 их эмитит, не определяет.
- **Тестирование нативного синтеза на CI без эмулятора** — нативный движок
  требует устройства; unit-тесты домена используют fake `AudioRepository`
  (это уровень E01/E02). E03 покрывается инструментed-тестами на эмуляторе.

## 6. Success metrics (измеримые)

Каждая метрика — наблюдаемый исход, проверяемый на реальном устройстве.

- **SM-1 (TTS реальный синтез):** `SherpaAudioRepository.speak(text, lang, speed)`
  → `Flow<AudioEvent>`, в котором на непустом `text` при готовой модели
  наблюдается `Started` и затем `Completed` (реальный audible-вывод через
  AudioTrack, не заглушка). На неготовой модели — корректный `Failed` либо
  fallback Android TTS.
- **SM-2 (ASR реальное распознавание):** `recognizeSpeech(lang)` →
  `Flow<RecognitionEvent>`, в котором при готовой модели наблюдается
  `ListeningStarted`, затем `Final(recognizedText)` после произнесённой фразы
  (VAD детектирует конец). На неготовой модели — `Failed`.
- **SM-3 (LRU переключение языков):** переключение языка TTS при активном
  резидентном LRU-кэше — **без реинициализации** модели (мгновенный ответ),
  пока кэш не вытеснен (≤3 моделей). Метрика: повторный `speak` на уже
  загруженном языке не инициирует native-load.
- **SM-4 (zero Sherpa leakage в домене):** доменный порт остаётся pure —
  `grep "^import.*sherpa"` (case-insensitive `sherpa`) в
  `domain/.../audio/*` = **0** совпадений; `grep "^import android"` = **0**
  (наследуется из NFR-1 SRS-001). Sherpa-импорты живут только в
  `app/.../v2/core/data/audio/*`.
- **SM-5 (MemoryChecker блокирует load):** при нехватке свободной памяти ниже
  порога загрузка модели блокируется (load не вызывается, пользователь/вызывающий
  код получает предсказуемый сигнал), без OOM-crash.
- **SM-6 (Доставка моделей):** `downloadTtsModel(lang)` /
  `downloadAsrModel()` → `Flow<DownloadProgress>` с реальной сетевой загрузкой
  и 3-retry; после `Done` соответствующий `isTtsAvailable(lang)` /
  `isAsrAvailable()` возвращает `true` по файловому манифесту.
- **SM-7 (Bluetooth-микрофон):** при включённом флаге ASR-запись
  маршрутизируется на bluetooth-гарнитуру (SCO/communicationDevice); при
  выключении — на встроенный микрофон.
- **SM-8 (SegmentPlayer):** последовательное воспроизведение сегментов без
  наложений (Mutex); `pause` останавливает, `resume` переозвучивает текущий
  сегмент с начала.
- **SM-9 (playSoundEffect сохранён):** `CORRECT_ANSWER` / `WRONG_ANSWER`
  играют мгновенно через SoundPool (уже реализовано — regression-не-сломать).

## 7. Зависимости и интеграция

### 7.1. Зависимости E03 (depends on)
- **E01 (SRS-001, artifact_id=450):** доменные порты `AudioRepository` /
  `AudioModelRepository` и доменные типы событий (`AudioEvent`,
  `RecognitionEvent`, `DownloadProgress`, `ModelStatus`, `SoundEffect`)
  зафиксированы. **Precondition выполнен** (status=accepted).

### 7.2. Что блокирует E03 (dependents / downstream)
- **E04 Training, E05 Verb, E06 Vocab, E07 Daily:** используют `speak()` и
  `recognizeSpeech()` для голосового режима drill'ов.
- **E08 BgVocab:** использует `SegmentPlayer` для фонового словаря + sound packs
  (sound packs — отдельный механизм в самом E08, не в E03).
- **E09 Story narration:** использует `SegmentPlayer` для озвучки историй.

### 7.3. Shared-mutation-risk
`SegmentPlayer` — общая зависимость E08 и E09. Его **интерфейс** (контракт)
должен быть зафиксирован в SRS-003 до старта E08/E09, чтобы оба downstream-эпика
не меняли его «на ходу» (drift). Контракт доменных портов `AudioRepository` /
`AudioModelRepository` уже стабилен (NFR-4 SRS-001).

## 8. Риски

- **R1 (native-memory-risk, высокий):** резидентный LRU TTS-кэш (~450 MB
  native heap) + ASR Whisper (~375 MB) → суммарно близко к лимитам heap'а
  Android-процесса → риск OOM, особенно на devices с ограниченной памятью.
  *Митигация:* `MemoryChecker` pre-check перед каждым load (SM-5);
  LRU-вытеснение (maxSize=3); ASR-модель грузится по требованию, не держится
  резидентно вместе со всеми TTS.
- **R2 (latency-risk, высокий):** инициализация нативной модели (TTS/ASR) —
  секунды; если каждый `speak` реинициализирует модель, UX разрушен
  (флагманская фича ощущается «зависшей»).
  *Митигация:* резидентный LRU-кэш (SM-3) — модель грузится один раз и держится;
  `Started` эмитится только после готовности модели.
- **R3 (JNI-stability-risk, средний):** Sherpa-ONNX — JNI-мост к C++; некорректное
  освобождение native-ресурсов (модель, AudioTrack, AudioRecord) → утечки или
  crash. Отмена корутины-коллектора (`Flow`) должна корректно вызывать
  native-stop.
  *Митигация:* детерминированный release в `stop()` и при отмене; перенос
  проверенных паттернов из legacy (рабочий референс).
- **R4 (download-reliability-risk, средний):** модели ~375–615 MB по сети —
  обрывы, metered-сети, частичные файлы.
  *Митигация:* 3-retry HTTP; удаление частичного файла при отмене;
  `ModelStatus.ERROR` для битых файлов. Metered-проверка — забота UI (E13),
  не домена.
- **R5 (shared-mutation-risk, средний):** `SegmentPlayer` общий для E08/E09 —
  изменение его интерфейса после старта этих эпиков = drift.
  *Митигация:* зафиксировать интерфейс `SegmentPlayer` в SRS-003 (§7.3).

## 9. Допущения

Перенесены из brief (§4):

- **H1:** Доменные порты `AudioRepository` (`speak → Flow<AudioEvent>`,
  `recognizeSpeech → Flow<RecognitionEvent>`) и `AudioModelRepository` уже
  зафиксированы в SRS-001 (E01, verified по фактическому коду 2026-07-07).
  Adapter пишет имплементацию под **известный контракт**, не проектирует его.
- **H2:** Legacy `TtsEngine`, `AsrEngine`, `AsrModelManager`/`Registry`,
  `TtsModelManager`/`Registry`, `SegmentPlayer`, `BluetoothAudioRouter`,
  `MemoryChecker` — рабочий референс; логика переносится в v2 clean arch как
  `SherpaAudioRepository` + helper-классы. Source = direct sponsor + product
  discovery.
- **H3:** Sherpa-ONNX local AAR уже подключён к проекту
  (`libs/sherpa-onnx-static-link-onnxruntime-1.12.40.aar`, saga note id=2).
  E03 использует его, не встраивает.

## 10. Open questions

- **OQ-1 (resolved):** Sound packs (Ogg-Opus ZIP) — в E03 или E08?
  **Ответ:** в E08 (для bg-vocab). E03 — только TTS/ASR/SoundEffect.
- **OQ-2 (resolved):** Входят ли `AsrModelManager`/`TtsModelManager` (download
  с retry) в E03?
  **Ответ:** да. Менеджер загрузки и распаковки моделей — E03 (port-реализация
  `AudioModelRepository.download*`). UI кнопок download — E13 (Settings).

---

## 11. Traceability

| Артефакт | Тип | Source (derived_from) | Примечание |
|---|---|---|---|
| **PRD-003** | PRD | BRIEF-003 (artifact_id=473) | продуктывая формализация brief |
| Контракт портов | — | SRS-001 (artifact_id=450), §5.8/§5.9, FR-7 | upstream, regression-locked, НЕ меняется |

FR/NFR-коды (для SRS-003), UC/AC (saga-analyst) и dev-задачи (saga-planner)
будут `derived_from` PRD-003. Доменные порты трассируются к SRS-001 FR-6.8/FR-6.9/FR-7
(upstream), имплементации адаптеров — к PRD-003 §4.

---

*PRD фиксирует продуктовый intent (зачем, для кого, границы scope), не системный
контракт. Техническая специфика (JNI, AudioTrack/AudioRecord, нативные ID моделей,
LRU-структура) — забота SRS-003 (saga-architect). Доменные порты взяты как
fact из SRS-001 (E01); сигнатуры verified по фактическому коду
`v2/core/domain/audio/*` и `v2/core/data/audio/*` на 2026-07-07.*

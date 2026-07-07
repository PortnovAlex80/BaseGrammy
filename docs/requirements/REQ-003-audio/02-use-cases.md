# UC-003 — Use Cases: Аудио-адаптер SherpaAudioRepository

**Эпизод:** E03 (epic_id=87), проект GrammarMate (project_id=25)
**Волна:** 0 — Фундамент
**Стадия:** Formalization (UC)
**Дата:** 2026-07-07
**Status:** Accepted

**Артефакт-источник (derived_from):** PRD-003 (artifact_id=474, `01-prd.md`, status=accepted).
**Upstream-контракт:** SRS-001 (E01, artifact_id=450) — доменные порты `AudioRepository` / `AudioModelRepository` и доменные типы событий (`AudioEvent`, `RecognitionEvent`, `DownloadProgress`, `ModelStatus`, `SoundEffect`) зафиксированы в §5.8/§5.9/FR-7. E03 их **реализует**, не меняет.
**Repository:** BaseGrammy (repository_id=7), ветка `rewrite/v2-clean-architecture`.
**Код:** UC-003

> E03 — инфраструктурный эпизод. Прямого UI нет: «пользователи» адаптера — это
> downstream drill-эпики Wave 1 (E04 Training, E05 Verb, E06 Vocab, E07 Daily),
> E08 BgVocab, E09 Story, плюс конечный юзер GrammarMate (через voice/TTS/UI E13).
> Use cases здесь фиксируют **контракты использования** аудио-адаптера со стороны
> этих потребителей и системные сценарии (fallback, memory guard, bluetooth routing).
>
> Covers к FR помечены `[FR TBD]`: SRS-003 (saga-architect) создаёт FR параллельно;
> `trace_add(link_type:'covers')` UC→FR будет проставлен после регистрации FR.
> Базовая связь UC→PRD-003 зарегистрирована как `derived_from`.

---

## 0. Актёры и interested parties

| Актёр | Кто это | Роль в сценариях |
|---|---|---|
| **A1. Downstream drill-эпик** | E04 Training / E05 Verb / E06 Vocab / E07 Daily (Wave 1) | Потребитель `speak()`, `recognizeSpeech()`, `playSoundEffect()` для голосового режима карточек/drill'ов. |
| **A2. Downstream фоновой аудиопрограммы** | E08 BgVocab, E09 Story | Потребитель `SegmentPlayer` для фонового прослушивания словаря/историй. |
| **A3. End-user (учащийся)** | Конечный пользователь GrammarMate | Произносит ответ, слушает озвучку, носит BT-гарнитуру, запускает download моделей. |
| **A4. Downstream Settings (E13)** | Эпизод настроек | Пользователь `AudioModelRepository.download*` для UI кнопок download моделей. |
| **A5. Система** | `SherpaAudioRepository` + helper-классы (`MemoryChecker`, `BluetoothAudioRouter`) | Автоматические системные сценарии: memory guard, TTS fallback, audio routing. |

---

## UC-1. Озвучить карточку (TTS speak)

**Код:** UC-1
**Actor:** A1 Downstream drill-эпик (E04 Training / E05 Verb / E06 Vocab / E07 Daily).
**Цель:** получить audible-озвучку текста карточки/слова/формы глагола на заданном языке для тренировки произношения и аудирования.

**Precondition:**
- Доменный порт `AudioRepository.speak(text, lang, speed)` доступен (контракт SRS-001 FR-7).
- TTS-модель для языка `lang` либо (а) уже резидентна в LRU-кэше, либо (б) доступна на диске (`isTtsAvailable(lang) == true`), либо (в) недоступна, но срабатывает fallback (см. UC-5).
- Свободной native-памяти достаточно для load (иначе см. UC-6).

**Основной поток:**
1. Drill-эпик вызывает `speak(text, lang, speed)` с непустым `text`.
2. `SherpaAudioRepository` определяет, есть ли в LRU-кэше модель для `lang`.
3. Если кэш-miss и `isTtsAvailable(lang) == true` → загрузить модель в native heap (с MemoryChecker pre-check) и поместить в LRU.
4. Синтез речи через Sherpa-ONNX `OfflineTts` (VITS-Piper для большинства языков, Kokoro где применимо); для русского применяется RU text-scale.
5. Воспроизведение через Android `AudioTrack`.
6. Эмит доменной последовательности событий в `Flow<AudioEvent>`:
   `AudioEvent.Started → [Progress(fraction)] → AudioEvent.Completed`.

**Альтернативные потоки:**
- **2a. Кэш-hit:** модель для `lang` уже резидентна → шаги 3 пропускаются (см. UC-4), синтез начинается мгновенно.
- **3a. Кэш-full (LRU eviction):** в кэше уже `maxSize=3` моделей → вытеснить LRU-модель с детерминированным native-release, затем загрузить новую.
- **3b. Недоступность модели:** `isTtsAvailable(lang) == false` → fallback UC-5 (Android `TextToSpeech`).
- **3c. Нехватка памяти:** MemoryChecker блокирует load → эмит `AudioEvent.Failed(memory)` (см. UC-6).
- **5a. Ошибка синтеза/JNI:** эмит `AudioEvent.Failed(error)` с детерминированным release native-ресурсов.
- **0a. `stop()` во время воспроизведения:** неблокирующая остановка (флаг + cancel job, native-stop корректно вызывается).

**Postcondition:**
- Пользователь услышал озвучку `text` на языке `lang` (либо получил `Failed`/fallback), либо поток отменён через `stop()`.
- Native-ресурсы освобождены детерминированно (R3 mitigation).
- Если модель загружалась — она резидентна в LRU для последующих `speak`.

**Business rules:**
- BR-1.1: `text` пустой/blank → короткое замыкание без аудио (no audible output), эмит корректного терминального события.
- BR-1.2: `Started` эмитится **только после готовности модели** (R2 latency-risk mitigation) — drill-эпик не должен «видеть» задержку init как зависание.
- BR-1.3: LRU `maxSize=3` (~450 MB native heap) — hard limit.
- BR-1.4: RU text-scale применяется только к `lang=RU`.

**covers:** [FR TBD — TTS-синтез / `speak` / `AudioEvent`-эмит / LRU-кэш] (FR будут зарегистрированы в SRS-003).
**AC preview:**
- AC-1.1: Given готовая модель RU и непустой `text`, When `speak("привет","ru",1.0)`, Then Flow эмитит `Started` и затем `Completed` (audible через AudioTrack, проверяется инструментed-тестом на эмуляторе).
- AC-1.2: Given пустой `text`, When `speak("","ru",1.0)`, Then нет audible-вывода и поток корректно завершается.

---

## UC-2. Голосовой ввод ответа (ASR recognizeSpeech)

**Код:** UC-2
**Actor:** A1 Downstream drill-эпик (E04–E07) + A3 End-user (произносит ответ).
**Цель:** распознать произнесённую фразу и вернуть текст вместо ручного ввода (`InputMode.VOICE`).

**Precondition:**
- Доменный порт `AudioRepository.recognizeSpeech(lang)` доступен (SRS-001 FR-7).
- ASR-модель (Whisper Small, multilingual) доступна на диске: `isAsrAvailable() == true` (манифест `asr/whisper-small/` + `asr/vad/silero_vad.onnx`).
- Микрофон доступен (или BT-микрофон — см. UC-7).

**Основной поток:**
1. Drill-эпик переключает `InputMode` в `VOICE` и вызывает `recognizeSpeech(lang)`.
2. `SherpaAudioRepository` загружает `OfflineRecognizer` (по требованию, не резидентно — R1 mitigation).
3. Захват звука через Android `AudioRecord` (source `VOICE_RECOGNITION`, 16 кГц).
4. Запуск Silero VAD для детекции конца фразы (endpoint detection по паузе).
5. Эмит в `Flow<RecognitionEvent>`:
   `RecognitionEvent.ListeningStarted → [Partial(text)] → RecognitionEvent.EndpointDetected → RecognitionEvent.Final(recognizedText)`.

**Альтернативные потоки:**
- **2a. Нехватка памяти:** MemoryChecker блокирует load → эмит `RecognitionEvent.Failed(memory)` (UC-6).
- **2b. Модель недоступна:** `isAsrAvailable() == false` → эмит `RecognitionEvent.Failed(model_missing)`; drill-эпик откатывается на клавиатурный/word-bank ввод.
- **3a. BT-микрофон включён:** маршрутизация через `BluetoothAudioRouter` (UC-7).
- **5a. VAD не сработал (долгая пауза/шум):** таймаут → `Final("")` или `Failed(timeout)`.
- **0a. `stop()` во время записи:** корректная отмена (`AudioRecord.release` + native-stop), без crash.

**Postcondition:**
- Drill-эпик получил `Final(text)` (распознанный ответ) либо `Failed` и откатился на альтернативный ввод.
- `AudioRecord` и native-recognizer освобождены детерминированно.

**Business rules:**
- BR-2.1: ASR-модель грузится **по требованию**, не держится резидентно вместе со всем TTS LRU (R1 mitigation: суммарно ~825 MB невозможно держать одновременно).
- BR-2.2: Одна Whisper Small multilingual-модель обслуживает все языки (per PRD §4.2).
- BR-2.3: `stop()` обязан вызвать native-stop в JNI (R3 mitigation).

**covers:** [FR TBD — ASR-распознавание / `recognizeSpeech` / `RecognitionEvent` / Silero VAD].
**AC preview:**
- AC-2.1: Given готовая ASR-модель и микрофон, When юзер произносит «cat» в `InputMode.VOICE`, Then Flow эмитит `ListeningStarted` и в итоге `Final(text)` с распознанным текстом.
- AC-2.2: Given `isAsrAvailable() == false`, When `recognizeSpeech(lang)`, Then Flow эмитит `Failed(model_missing)` без crash.

---

## UC-3. Фоновое прослушивание сегментов (SegmentPlayer)

**Код:** UC-3
**Actor:** A2 Downstream фоновой аудиопрограммы (E08 BgVocab / E09 Story).
**Цель:** последовательное воспроизведение набора сегментов озвучки (слово → пауза → перевод → пауза, либо story-сегмент) в фоне, с возможностью паузы/возобновления/остановки.

**Precondition:**
- Доменный интерфейс `SegmentPlayer` зафиксирован в SRS-003 (R5 shared-mutation mitigation) — общий для E08 и E09.
- TTS-движок резидентен (минимум для первого сегмента языка).
- Поставлена очередь сегментов (E08: словарь; E09: история).

**Основной поток:**
1. Downstream-эпик стартует `SegmentPlayer.play(segments)` с непустой очередью.
2. `SegmentPlayer` воспроизводит сегменты **последовательно через Mutex** (без наложений).
3. После завершения текущего сегмента переходит к следующему.
4. По окончании очереди эмитит терминальное событие завершения.

**Альтернативные потоки:**
- **A1. `pause()`:** текущий сегмент останавливается (видимая пауза).
- **A2. `resume()`:** **переозвучивает текущий сегмент с начала**, а не продолжает с середины (legacy-дизайн сохраняется по решению PRD §4.6).
- **A3. `stop()`:** немедленная остановка, очередь очищается, Mutex освобождается.
- **A4. Очередь пуста:** немедленный возврат, без ошибок.
- **A5. Несколько одновременных запросов:** Mutex serialize'ит их — одновременный `play` ждёт освобождения (либо отклоняется — по контракту SRS-003).

**Postcondition:**
- Все сегменты воспроизведены последовательно, либо сессия остановлена через `pause`/`stop`.
- Mutex освобождён, доменные ресурсы — освобождены.

**Business rules:**
- BR-3.1: **Mutex-serialized** — одновременное воспроизведение сегментов запрещено (без наложений).
- BR-3.2: `resume` переозвучивает **с начала текущего сегмента** (legacy-дизайн; не «продолжает с середины»).
- BR-3.3: Интерфейс `SegmentPlayer` shared с E08/E09 — drift-locked контрактом SRS-003.

**covers:** [FR TBD — SegmentPlayer / последовательное воспроизведение / pause-resume-restart].
**AC preview:**
- AC-3.1: Given очередь из 3 сегментов, When `play(segments)`, Then сегменты звучат последовательно без наложений.
- AC-3.2: Given активное воспроизведение сегмента N, When `pause()` затем `resume()`, Then сегмент N звучит **с начала** (не с середины).

---

## UC-4. Переключение языка TTS без реинициализации (LRU cache hit)

**Код:** UC-4
**Actor:** A1 Downstream drill-эпик (сценарий it↔ru alternating — карточка на итальянском, перевод на русском и т.п.).
**Цель:** мгновенно переключать язык TTS между несколькими языками в одной тренировке без секундной задержки на нативную реинициализацию (R2 latency-risk mitigation).

**Precondition:**
- Ранее уже загружались ≥1 TTS-модели и они резидентны в LRU-кэше (`maxSize=3`).
- Кэш не вытеснил нужный язык (размер ≤3).

**Основной поток:**
1. Drill-эпик вызывает `speak(textIt, "it", speed)`.
2. В LRU-кэше модель `it` резидентна → **cache hit** (LRU-order обновляется).
3. Сразу синтез без native-load.
4. Следующий вызов `speak(textRu, "ru", speed)` → также cache hit → мгновенный синтез.

**Альтернативные потоки:**
- **A1. Cache miss (язык не в LRU):** загрузка модели с MemoryChecker pre-check и LRU-eviction (см. UC-1 шаг 3).
- **A2. Кэш вытеснил язык (size>3):** повторный `speak` на вытесненном языке инициирует native-load (это норма — метрика SM-3 говорит «пока кэш не вытеснен»).

**Postcondition:**
- Переключение языка состоялось без реинициализации, если язык оставался в кэше.
- LRU-порядок обновлён (последний использованный язык не вытесняется раньше).

**Business rules:**
- BR-4.1: LRU `maxSize=3` — hard cap, дальнейшие языки вытесняют LRU.
- BR-4.2: Метрика SM-3 (PRD): повторный `speak` на уже загруженном языке **не инициирует native-load** (наблюдаемо: нет init-latency, нет повторной аллокации модели).
- BR-4.3: LRU-eviction обязан детерминированно освобождать native-ресурсы (R3 mitigation).

**covers:** [FR TBD — LRU-кэш TTS-моделей / мгновенное переключение языков].
**AC preview:**
- AC-4.1: Given в LRU резидентны `it` и `ru`, When `speak("ciao","it")` затем `speak("привет","ru")`, Then ни один из вызовов не инициирует native-load (мгновенный ответ, наблюдаемо через отсутствие init-latency).

---

## UC-5. Fallback на Android TextToSpeech при отсутствии Sherpa-модели

**Код:** UC-5
**Actor:** A5 Система (автоматический fallback `SherpaAudioRepository`).
**Цель:** не уронить озвучку молча, если Sherpa-модель недоступна (нет файлов модели, 32-bit ARM не поддерживается и т.п.).

**Precondition:**
- Вызван `speak(text, lang, speed)`, и для `lang` выполнено одно из:
  - `isTtsAvailable(lang) == false` (нет файлов манифеста), или
  - Sherpa-модель не поддерживается платформой.

**Основной поток:**
1. `speak` определяет, что Sherpa-модель для `lang` недоступна.
2. `SherpaAudioRepository` делегирует синтез на системный Android `TextToSpeech`.
3. Эмит доменной последовательности `Flow<AudioEvent>` как в UC-1 (`Started → [Progress] → Completed`), но источником аудио является Android TTS (транзакт прозрачен для downstream-эпика).
4. (Опционально) downstream-эпик может получить сигнал о fallback-режиме через метаданные события.

**Альтернативные потоки:**
- **A1. Android TTS для языка `lang` тоже недоступен:** эмит `AudioEvent.Failed(tts_unavailable)` — корректный сигнал, а не молчание.
- **A2. `text` пустой:** короткое замыкание (BR-1.1).

**Postcondition:**
- Пользователь услышал озвучку через Android TTS, либо получил `Failed` с конкретной причиной.
- Sherpa-модель не аллоцировалась (отсутствует).

**Business rules:**
- BR-5.1: Fallback **автоматический и прозрачный** для downstream-эпика (тот же контракт `Flow<AudioEvent>`).
- BR-5.2: Fallback не должен маскировать **отсутствие модели** как успешный Sherpa-синтез — downstream имеет право знать источник аудио (quality/latency различаются).
- BR-5.3: Молчание (silent failure) — запрещено (PRD §4.1 «синтез не падает молча»).

**covers:** [FR TBD — TTS fallback / `AudioEvent.Failed` при отсутствии TTS].
**AC preview:**
- AC-5.1: Given `isTtsAvailable("fr") == false` и Android TTS для fr доступен, When `speak("bonjour","fr",1.0)`, Then Flow эмитит `Started` и `Completed` (озвучка через Android TTS).
- AC-5.2: Given ни Sherpa, ни Android TTS для `lang` недоступны, When `speak(text, lang, speed)`, Then Flow эмитит `Failed(tts_unavailable)` (не молчание).

---

## UC-6. MemoryChecker блокирует load модели при нехватке памяти

**Код:** UC-6
**Actor:** A5 Система (`MemoryChecker`).
**Цель:** предотвратить OOM-crash процесса при загрузке TTS/ASR-моделей в native heap (R1 native-memory-risk mitigation).

**Precondition:**
- Инициирован load TTS- или ASR-модели (через `speak`/`recognizeSpeech`/download).
- Порог свободной памяти: ~150 MB для TTS, ~800 MB для ASR (per PRD §4.4).

**Основной поток:**
1. Перед вызовом native-load `MemoryChecker.checkFree(threshold)` опрашивает доступную native-память.
2. Если свободной памяти ≥ порога → load разрешён.
3. Если свободной памяти < порога → load **блокируется**:
   - native-load не вызывается,
   - вызывающий код (`speak`/`recognizeSpeech`) получает предсказуемый сигнал `Failed(memory)` (или эквивалентный доменный код ошибки),
   - пользователь не видит OOM-crash.

**Альтернативные потоки:**
- **A1. После LRU-eviction:** если TTS LRU вытеснил модель и память освободилась → повторная попытка load может пройти успешно (downstream retry политика — забота вызывающего кода).
- **A2. ASR-модель слишком велика при активном TTS LRU:** load ASR блокируется → downstream откатывается на клавиатурный ввод (UC-2 A2b).

**Postcondition:**
- При недостатке памяти load не состоялся, процесс жив (без OOM).
- Вызывающий код получил предсказуемый сигнал `Failed(memory)`.

**Business rules:**
- BR-6.1: Pre-check выполняется **перед каждым** load (TTS и ASR) — без исключений.
- BR-6.2: Пороги зафиксированы в PRD §4.4 (~150 MB TTS / ~800 MB ASR) и не регулируются runtime-флагом.
- BR-6.3: Блокировка — единственный способ избежать OOM при ~825 MB суммарной аллокации (TTS LRU + ASR); ASR намеренно не резидентен вместе со всем TTS LRU (BR-2.1).

**covers:** [FR TBD — MemoryChecker / pre-load memory guard / SM-5].
**AC preview:**
- AC-6.1: Given свободной native-памяти < порога TTS, When `speak(...)` инициирует load новой модели, Then native-load **не вызывается**, и Flow эмитит `Failed(memory)` без OOM-crash процесса.

---

## UC-7. Bluetooth-микрофон routing (SCO / communicationDevice)

**Код:** UC-7
**Actor:** A3 End-user (с BT-гарнитурой) + A1 Downstream drill-эпик (ASR).
**Цель:** маршрутизировать запись ASR на Bluetooth-гарнитуру (тренировка в движении, без проводов).

**Precondition:**
- BT-гарнитура подключена и поддерживает SCO-профиль.
- Пользователь включил флаг использования BT-микрофона (через E13 Settings или UI drill-эпика).

**Основной поток:**
1. Пользователь включает BT-микрофон.
2. `BluetoothAudioRouter` активирует SCO-соединение (API<31) или `setCommunicationDevice` (API≥31).
3. `AudioModelRepository.setBluetoothMic(true)` фиксирует выбор.
4. Последующие вызовы `recognizeSpeech(lang)` захватывают звук через BT-микрофон.

**Альтернативные потоки:**
- **A1. Пользователь выключает BT-микрофон:** SCO-соединение разрывается, запись возвращается на встроенный микрофон.
- **A2. BT-гарнитура отключилась во время записи:** graceful fallback на встроенный микрофон (или `Failed`, по контракту SRS-003).
- **A3. API<31 (старые устройства):** используется SCO API (`startBluetoothSco`).
- **A4. API≥31:** используется `setCommunicationDevice` (новый рекомендованный путь).

**Postcondition:**
- ASR-запись маршрутизирована на BT-гарнитуру (или обратно на встроенный микрофон при выключении/отключении).

**Business rules:**
- BR-7.1: Поддержка обеих API-веток: SCO (API<31) и `setCommunicationDevice` (API≥31).
- BR-7.2: Routing — настройка пользователя, не угадывается автоматически.
- BR-7.3: При disconnect BT во время записи — graceful fallback, не crash.

**covers:** [FR TBD — BluetoothAudioRouter / SCO + setCommunicationDevice / SM-7].
**AC preview:**
- AC-7.1: Given подключена BT-гарнитура и включён флаг BT-микрофона, When `recognizeSpeech(lang)` вызывается, Then запись идёт через BT-микрофон (SCO/communicationDevice).
- AC-7.2: Given флаг BT-микрофона выключен, When `recognizeSpeech(lang)`, Then запись идёт через встроенный микрофон.

---

## UC-8. Download моделей (AsrModelManager / TtsModelManager, 3-retry HTTP)

**Код:** UC-8
**Actor:** A3 End-user (через A4 downstream E13 Settings).
**Цель:** скачать недостающие языковые TTS/ASR-модели до тренировки, чтобы аудио работало офлайн (поезд, самолёт).

**Precondition:**
- Эпизод E13 Settings предоставляет UI кнопку download.
- Сетевое соединение есть (metered-проверка — забота UI E13, не домена).

**Основной поток:**
1. Пользователь из E13 Settings нажимает «Download TTS для языка X» / «Download ASR».
2. E13 вызывает `AudioModelRepository.downloadTtsModel(lang)` / `downloadAsrModel()`.
3. `TtsModelManager` / `AsrModelManager` инициируют HTTP-загрузку `.tar.bz2` (TTS per-language, ASR = Whisper+VAD) с **3-retry политикой** при сбоях.
4. Эмит прогресса через `Flow<DownloadProgress>`:
   `Downloading(%) → Extracting → Initializing → DownloadProgress.Done`.
5. По `Done` модель распакована и доступна: `isTtsAvailable(lang)` / `isAsrAvailable()` возвращает `true` по файловому манифесту.

**Альтернативные потоки:**
- **A1. Обрыв сети / таймаут:** повторная попытка (до 3 retry), затем `DownloadProgress.Failed`.
- **A2. Отмена пользователем:** удаление частичного файла, эмит терминального события отмены.
- **A3. Битый файл (после download):** `ModelStatus.ERROR` для файла, эмит `Failed(corrupted)`.
- **A4. Metered-сеть:** блокировка/предупреждение — забота E13 (UI), домен получает только корректный запрос на download.

**Postcondition:**
- Модель на диске и `is*Available()` возвращает `true`, либо пользователь увидел `Failed` с конкретной причиной.
- Частичные файлы при отмене/сбои удалены (R4 download-reliability mitigation).

**Business rules:**
- BR-8.1: **3-retry HTTP** — повторная попытка до 3 раз перед `Failed`.
- BR-8.2: Частичный файл удаляется при отмене/сбое (без мусора на диске).
- BR-8.3: Битые файлы помечаются `ModelStatus.ERROR`, не маскируются как готовые.
- BR-8.4: Менеджер загрузки/распаковки — в E03; **UI кнопок — в E13** (разделение по PRD §5 Scope OUT).

**covers:** [FR TBD — AudioModelRepository.download* / 3-retry HTTP / DownloadProgress / SM-6].
**AC preview:**
- AC-8.1: Given сеть доступна и модель отсутствует, When `downloadTtsModel("de")`, Then Flow эмитит `Downloading(%)` и в итоге `Done`, после чего `isTtsAvailable("de") == true`.
- AC-8.2: Given первый HTTP-запрос упал с сетевой ошибкой, Then выполняется retry (до 3 попыток) перед эмитом `Failed`.

---

## UC-9. Звуковые эффекты (SoundPool, correct/wrong sounds)

**Код:** UC-9
**Actor:** A1 Downstream drill-эпик (E04 Training и др.).
**Цель:** мгновенная звуковая обратная связь на правильный/неправильный ответ (CORRECT_ANSWER / WRONG_ANSWER).

**Precondition:**
- SoundPool инициализирован, mp3-клипы загружены из `res/raw` (`CORRECT_ANSWER`, `WRONG_ANSWER`).
- (Уже реализовано в skeleton — regression-не-сломать, PRD §4.8.)

**Основной поток:**
1. Drill-эпик получает результат проверки ответа (correct/wrong).
2. Вызывает `playSoundEffect(SoundEffect.CORRECT_ANSWER)` или `WRONG_ANSWER`.
3. SoundPool мгновенно воспроизводит соответствующий клип.
4. Эмит короткого терминального события (без прогресс-фазы).

**Альтернативные потоки:**
- **A1. `LESSON_COMPLETE`:** пока переиспользует клип CORRECT_ANSWER (отдельный ресурс — minor gap, PRD §4.8).
- **A2. SoundPool не инициализирован:** no-op без crash (best-effort).

**Postcondition:**
- Звуковой эффект проигран мгновенно (latency минимальна, в отличие от TTS-синтеза).

**Business rules:**
- BR-9.1: SoundPool с pre-loaded клипами (не сетевая загрузка, не синтез) — мгновенная latency.
- BR-9.2: Regression-locked — уже реализованный skeleton не должен сломаться (SM-9).
- BR-9.3: `LESSON_COMPLETE` временно использует CORRECT_ANSWER-клип (minor gap, в PRD).

**covers:** [FR TBD — playSoundEffect / SoundEffect / SoundPool / SM-9].
**AC preview:**
- AC-9.1: Given инициализированный SoundPool, When `playSoundEffect(SoundEffect.CORRECT_ANSWER)`, Then мгновенно проигрывается клип CORRECT_ANSWER из `res/raw` (минимальная latency).

---

## Traceability matrix

| UC | Actor | covers (FR TBD) | Source (derived_from) | AC preview |
|---|---|---|---|---|
| UC-1 Озвучить карточку (TTS speak) | A1 drill-эпик | FR[TBD]: TTS-синтез / `speak` / `AudioEvent` / LRU | PRD-003 §4.1, US-2 | AC-1.1, AC-1.2 |
| UC-2 Голосовой ввод (ASR) | A1 drill-эпик + A3 user | FR[TBD]: ASR / `recognizeSpeech` / VAD | PRD-003 §4.2, US-1 | AC-2.1, AC-2.2 |
| UC-3 Фоновое прослушивание (SegmentPlayer) | A2 E08/E09 | FR[TBD]: SegmentPlayer / Mutex / pause-resume-restart | PRD-003 §4.6, US-3/US-4 | AC-3.1, AC-3.2 |
| UC-4 Переключение языка без реинициализации (LRU hit) | A1 drill-эпик | FR[TBD]: LRU-кэш / мгновенное переключение | PRD-003 §4.1, SM-3 | AC-4.1 |
| UC-5 Fallback на Android TextToSpeech | A5 система | FR[TBD]: TTS fallback / `Failed` без молчания | PRD-003 §4.1 | AC-5.1, AC-5.2 |
| UC-6 MemoryChecker блокирует load | A5 система | FR[TBD]: MemoryChecker / pre-load guard / SM-5 | PRD-003 §4.4 | AC-6.1 |
| UC-7 Bluetooth-микрофон routing | A3 user + A1 drill | FR[TBD]: BluetoothAudioRouter / SCO+communicationDevice / SM-7 | PRD-003 §4.5, US-5 | AC-7.1, AC-7.2 |
| UC-8 Download моделей (3-retry HTTP) | A3 user via E13 | FR[TBD]: download* / 3-retry / `DownloadProgress` / SM-6 | PRD-003 §4.7, US-6 | AC-8.1, AC-8.2 |
| UC-9 Звуковые эффекты (SoundPool) | A1 drill-эпик | FR[TBD]: playSoundEffect / `SoundEffect` / SM-9 | PRD-003 §4.8 | AC-9.1 |

**Итого:** 9 UC, 9 parent-derived_from PRD-003, covers→FR помечены `[FR TBD]` (FR регистрируются в SRS-003 saga-architect; traces covers будут добавлены после регистрации FR).

---

*UC-003 фиксирует контракты использования аудио-адаптера со стороны downstream drill/фоновой программ и системные сценарии (fallback, memory guard, BT routing). Доменные порты и sealed-типы событий взяты как fact из SRS-001 (E01, NFR-4 drift-запрет); FR/NFR-коды — в SRS-003 (saga-architect); AC будут формализованы saga-analyst на этапе AC.*

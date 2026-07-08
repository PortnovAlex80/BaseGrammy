# AC-003 — Acceptance Criteria: Аудио-адаптер SherpaAudioRepository

**Эпизод:** E03 (epic_id=87), проект GrammarMate (project_id=25)
**Волна:** 0 — Фундамент
**Стадия:** Formalization (AC)
**Дата:** 2026-07-07
**Status:** Draft

**Артефакт-источник (derived_from):** UC-003 (artifact_id=476, `02-use-cases.md`, status=accepted).
**Upstream-контракт:** SRS-003 (artifact_id=487, `02-srs.md`, status=accepted) + 17 FR (artifact_id=488–498, 508–513) + 5 NFR (artifact_id=514–518). Доменные порты и sealed-типы событий — факт из SRS-001 (E01, NFR-4 drift-запрет), в E03 не меняются.
**Repository:** BaseGrammy (repository_id=7), ветка `rewrite/v2-clean-architecture`.
**Coverage-gate:** `legacy-coverage-checklist.md` §F (audio) — COVERED, плюс GAP C4 (HomophoneReplacerConfig, `AsrEngine.kt:126,215`) и C5 (ModelLoadLogger → `Downloads/BaseGrammy/model_load_log.txt`) закрываются отдельными AC.

> Каждый AC написан в форме **Given / When / Then** с наблюдаемым исходом и
> конкретной проверкой (unit / integration / instrumented / grep / benchmark).
> «Работает корректно» — не AC. Каждый AC трассируется `derived_from` к UC-003
> (artifact_id=476) и `derived_from` к FR/NFR (488–518).
>
> Категории верификации:
> - **UNIT** — JVM unit test (`app/src/test/...`), без эмулятора. Fake/mock Sherpa.
> - **INSTR** — instrumented test (`app/src/androidTest/...`), эмулятор/устройство.
> - **GREP** — статическая проверка grep/regex по дереву исходников.
> - **BENCH** — latency-benchmark (timeout-assertion или лог-timestamp).
> - **DI** — Hilt graph validation (`@Binds` присутствует, graph компилируется).
> - **MANUAL** — ручная проверка на устройстве (для audible/sco-сценариев).

---

## 1. AC — Интеграция и DI

### AC-1 SherpaAudioRepository — DI-bound Singleton, реализует AudioRepository

**Given:** модуль `v2/core/data/audio/di/RepositoryModule.kt` (Hilt) и интерфейс `AudioRepository` (SRS-001 FR-7).
**When:** Hilt graph собирается и `@Inject audioRepository: AudioRepository` запрашивается в любом ViewModel.
**Then:**
- `@Binds abstract fun bindAudioRepository(impl: SherpaAudioRepository): AudioRepository` присутствует в `RepositoryModule`;
- `SherpaAudioRepository` аннотирован `@Singleton`;
- два независимых injection-сайта получают **тот же экземпляр** (identity-equal) на протяжении жизни Application.

**derived_from:** UC-1 (TTS speak), UC-2 (ASR).
**covers:** FR-1.
**verification:** DI — Hilt graph компилируется; unit-test инъекций проверяет `assertSame(repoA, repoB)`.

---

## 2. AC — TTS (speak)

### AC-2 TTS speak() — реальный синтез: Started → Progress → Completed (НЕ Started→Failed)

**Given:** `SherpaAudioRepository` с загруженной VITS-Piper моделью для `it` (TtsState.Ready, модель резидентна в `ResidentTtsCache`); OfflineTts подменён fake'ом, который эмитит PCM-float chunks.
**When:** вызывается `speak(text="ciao", languageId="it", speed=1.0f)`.
**Then:** `Flow<AudioEvent>` эмитит **именно последовательность** `Started` → (`Progress(fraction)` с `0 ≤ fraction ≤ 1`, ≥1 событие) → `Completed`. **Не** допускается `Started → Failed` (это была бы регрессия skeleton-заглушки). `Completed` — терминальное, поток завершается без `Failed`.

**derived_from:** UC-1.
**covers:** FR-2.
**verification:** UNIT — adapter unit test с mock `OfflineTts` (`fakeOfflineTts.generateWithConfigAndCallback` выдаёт ≥1 chunk); коллектор `toList()` проверяет `[Started, Progress(...), Completed]` и отсутствие `Failed`. Smoke-INSTR на эмуляторе с реальной моделью подтверждает audible-вывод.

### AC-3 speak() — пустой/blank text = no-op (no audible output)

**Given:** готовая модель `ru`.
**When:** вызывается `speak(text="   ", languageId="ru", speed=1.0f)` (blank).
**Then:** `Flow<AudioEvent>` **не эмитит ни одного события** (завершается пустым), `AudioTrack` не открывается, audible-вывода нет. Никакого `Failed`, никакого `Started`.

**derived_from:** UC-1 (BR-1.1).
**covers:** FR-2.
**verification:** UNIT — `speak(blank)` → `flow.toList() == emptyList()`; spy на `AudioTrack` не фиксирует `play()`.

### AC-4 ResidentTtsCache — LRU eviction при превышении maxSize=3

**Given:** `ResidentTtsCache(maxSize=3)` (internal, LinkedHashMap access-ordered) с зарегистрированным `freeFn`, который записывает каждый вызов `OfflineTts.free()`.
**When:** последовательно `put("en", m1)`, `put("it", m2)`, `put("ru", m3)` (кэш полон), затем `put("de", m4)`.
**Then:**
- вытесняется LRU-модель `m1` ("en") и вызывается `freeFn(m1)` (детерминированный native-release);
- `cache.size() == 3` после `put("de", m4)`;
- повторный `put` того же lang заменяет и освобождает старую модель.

**derived_from:** UC-1 (BR-1.3), UC-4 (BR-4.1).
**covers:** FR-3.
**verification:** UNIT (JVM, pure data structure) — `ResidentTtsCacheTest` (наследник legacy-теста); assertions на `freeFn` invocations и `size()`.

### AC-5 ResidentTtsCache — cache hit: переключение языка без native re-init (latency < 100 ms)

**Given:** в `ResidentTtsCache` резидентны модели `it` и `ru` (оба загружены ранее); alternation-сценарий `it→ru→it→ru`.
**When:** вызывается `speak("ciao", "it", 1.0f)`, затем `speak("привет", "ru", 1.0f)`, затем снова `speak("ciao", "it", 1.0f)`.
**Then:** **ни один** из этих вызовов не инициирует `OfflineTts(...)` constructor (нет native-load); модель берётся из резидентного кэша; latency переключения языка (от вызова `speak` до `AudioEvent.Started`) `< 100 ms`.

**derived_from:** UC-4 (BR-4.2, BR-4.1).
**covers:** FR-3, NFR-4.
**verification:** BENCH — spy/verifier на `OfflineTts` constructor подтверждает 0 вызовов на 2..N-м `speak` того же языка; timestamp'ы лога: `(t(Started) − t(speak)) < 100 ms` для resident-языков.

### AC-6 RU text-scale применяется только для lang=ru

**Given:** готовые модели `ru` и `en`; константа адаптера `RU_LENGTH_SCALE_OVERRIDE` (≠ `1.0f`) для VITS_PIPER при `lang=ru`.
**When:** вызывается `speak("Привет", "ru", 1.0f)`, затем `speak("hello", "en", 1.0f)`.
**Then:** в `GenerationConfig` / `OfflineTtsVitsModelConfig.lengthScale` для `ru` передаётся `RU_LENGTH_SCALE_OVERRIDE` (≠ 1.0f); для `en` передаётся baseline `1.0f`. Темп RU-аудио соответствует тюнингу.

**derived_from:** UC-1 (BR-1.4).
**covers:** FR-4.
**verification:** UNIT — argument-captor на `GenerationConfig`/`lengthScale` в mock `OfflineTts`: `ru → override`, `en → 1.0f`.

---

## 3. AC — TTS Fallback

### AC-7 Fallback на Android TextToSpeech при отсутствии Sherpa-модели

**Given:** `isTtsAvailable("fr") == false` (нет файлов VITS-Piper) и Android `TextToSpeech` для `Locale.FRENCH` доступен (`LANG_AVAILABLE`/`LANG_COUNTRY_AVAILABLE`).
**When:** вызывается `speak("bonjour", "fr", 1.0f)`.
**Then:** `Flow<AudioEvent>` эмитит `Started` → `Completed` (озвучка через Android `TextToSpeech`, а не через Sherpa); native-модель `OfflineTts` **не аллоцируется**. Если ни Sherpa, ни Android TTS для `lang` недоступны → `Failed(tts_unavailable)` (не молчание).

**derived_from:** UC-5 (BR-5.1, BR-5.3).
**covers:** FR-5.
**verification:** INSTR — на эмуляторе после переименования/удаления файлов VITS для `fr`: `speak("bonjour","fr")` не падает с `UnsatisfiedLinkError`/`SIGBUS`, `Completed` эмитится, источник аудио — Android TTS (детектируется через `TextToSpeech` mock/listener). Негативный кейс (оба недоступны) → `Failed`.

---

## 4. AC — ASR (recognizeSpeech)

### AC-8 ASR recognizeSpeech() — реальное распознавание: ListeningStarted → Partial → EndpointDetected → Final

**Given:** готовая ASR-модель (`isAsrAvailable() == true`), `OfflineRecognizer` (Whisper Small) и `Vad` (Silero) инициализированы (AsrState.READY); `AudioRecord` открывается на source `VOICE_RECOGNITION`, 16 кГц; на вход подаётся записанная фраза "cat".
**When:** вызывается `recognizeSpeech(languageId="en")`.
**Then:** `Flow<RecognitionEvent>` эмитит **последовательность** `ListeningStarted` → (`Partial(text)` ≥ 0, промежуточные гипотезы) → `EndpointDetected` (когда VAD нашёл паузу конца фразы) → `Final(text)`, где `text` — непустой распознанный транскрипт. Не `Started → Failed`.

**derived_from:** UC-2.
**covers:** FR-6.
**verification:** INSTR — на устройстве/эмуляторе с готовой моделью и микрофоном (или injected PCM-buffer): коллектор проверяет `[ListeningStarted, ...Partial, EndpointDetected, Final(nonBlank)]`. UNIT-вариант: fake `AudioRecord` + fake `OfflineRecognizer`/`Vad` проверяют порядок событий.

### AC-9 recognizeSpeech() — модель недоступна → Failed(model_missing) без crash

**Given:** `isAsrAvailable() == false` (нет `asr/whisper-small/` файлов или удалён `silero_vad.onnx`).
**When:** вызывается `recognizeSpeech(languageId="en")`.
**Then:** `Flow<RecognitionEvent>` эмитит `Failed(model_missing)` (или эквивалентный доменный код ошибки); процесс не крашится; `OfflineRecognizer`/`Vad` не аллоцируются.

**derived_from:** UC-2 (alt 2b).
**covers:** FR-6.
**verification:** UNIT/INSTR — удалить манифестные файлы ASR → `recognizeSpeech` → `Failed`, без `UnsatisfiedLinkError`/`SIGBUS`.

### AC-10 AudioRecord capture — VOICE_RECOGNITION 16 кГц + VAD-gated endpointing

**Given:** активная сессия `recognizeSpeech`, готовая модель.
**When:** `AudioRecord` открывается и читает 100 мс чанки.
**Then:**
- `AudioRecord` сконструирован с `MediaRecorder.AudioSource.VOICE_RECOGNITION`, `SAMPLE_RATE=16000`, `CHANNEL_IN_MONO`, `ENCODING_PCM_16BIT`, `bufferSize ≥ 3200`;
- чанки по 1600 short конвертируются (÷32768 → `FloatArray`) и кормятся в `Vad.acceptWaveform`;
- цикл завершается через VAD-endpoint (была речь → пауза), **не** через `maxDurationMs=10_000`, в нормальном сценарии;
- `AudioRecord.stop()+release()` вызываются в `finally` (включая cancellation).

**derived_from:** UC-2 (основной поток 2–4).
**covers:** FR-7.
**verification:** UNIT/INSTR — shadow `AudioRecord` фиксирует constructor-args (`VOICE_RECOGNITION`, 16000, MONO, PCM_16BIT); fake `Vad` инъектируется, проверяется `acceptWaveform` получает чанки; `finally`-release верифицируется через `release()` spy.

### AC-11 isTtsAvailable(lang) — точная per-language проверка манифеста

**Given:** `TtsModelRegistry.specFor("en")` декларирует `requiredFiles=["en_US-amy-low.onnx","tokens.txt"]`, `requiredDirs=["espeak-ng-data"]`; каталог `filesDir/tts/en_US/` существует и содержит все требуемые файлы (ненулевой размер).
**When:** вызывается `isTtsAvailable(languageId="en")`.
**Then:** возвращает `true`. Если удалить `tokens.txt` → `false`. Если `specFor(lang) == null` (язык не в реестре) → `false`.

**derived_from:** UC-1 (precondition), UC-8 (postcondition).
**covers:** FR-8.
**verification:** UNIT — temp-dir fixture: полный каталог → `true`; удалить файл → `false`; неизвестный lang → `false`.

### AC-12 isAsrAvailable() — проверка манифеста Whisper+VAD (regression-lock)

**Given:** skeleton уже реализует `isAsrAvailable()` (проверка `asr/whisper-small/` с `ASR_REQUIRED_FILES` + `asr/vad/silero_vad.onnx`, ненулевой размер).
**When:** выполняется regression-test.
**Then:** поведение сохранено: полный манифест → `true`; удалить `silero_vad.onnx` → `false`; удалить `small-decoder.int8.onnx` → `false`.

**derived_from:** UC-2 (precondition).
**covers:** FR-9.
**verification:** UNIT (regression) — snapshot текущей логики; те же assertions, что в legacy `AsrModelRegistry.isReady()`.

---

## 5. AC — Memory guard

### AC-13 MemoryChecker hard block при нехватке памяти (НЕ OOM crash)

**Given:** `MemoryChecker.hasEnoughMemory(...)` возвращает `false` (mocked: `availMem < requiredMb × 1.5`); пороги: `requiredMb≈150` для TTS, `requiredMb≈800` для ASR.
**When:** инициируется load TTS-модели (через `speak`, cache-miss) или load ASR-модели (через `recognizeSpeech`).
**Then:**
- **native-load НЕ вызывается** (`OfflineTts(...)`/`OfflineRecognizer(...)` constructor не выполняется);
- `TtsEngineWrapper.state` → `Error("Not enough memory to load voice model")` / `AsrEngineWrapper.state` → `ERROR`;
- `Flow<AudioEvent>` эмитит `Failed(memory)` (или `Flow<RecognitionEvent>` → `Failed(memory)`);
- процесс **жив**, никакого OOM-crash/SIGBUS.

**derived_from:** UC-6 (BR-6.1, BR-6.2).
**covers:** FR-10, NFR-2.
**verification:** UNIT — mock `MemoryChecker.hasEnoughMemory → false`; spy на `OfflineTts`/`OfflineRecognizer` constructor → 0 invocations; `flow` содержит `Failed`; процесс не упал.

---

## 6. AC — Bluetooth routing

### AC-14 BluetoothAudioRouter — SCO (API<31) / setCommunicationDevice (API≥31)

**Given:** подключена BT-гарнитура (SCO-профиль); флаг `AudioModelRepository.observeBluetoothMic()` эмитит `true`.
**When:** вызывается `recognizeSpeech(languageId)`.
**Then:**
- на API<31 вызывается `AudioManager.startBluetoothSco()` (+ `BroadcastReceiver` на `ACTION_SCO_AUDIO_STATE_UPDATED`, `BluetoothHeadset.startVoiceRecognition`);
- на API≥31 вызывается `AudioManager.setCommunicationDevice(btDevice)` (+ `addOnCommunicationDeviceChangedListener`);
- запись идёт через BT-микрофон.
**And given:** флаг `observeBluetoothMic() == false` (или BT-микрофон выключен).
**When:** вызывается `recognizeSpeech`.
**Then:** запись возвращается на встроенный микрофон (`MODE_NORMAL`, SCO разъединён).

**derived_from:** UC-7 (BR-7.1, BR-7.2).
**covers:** FR-11.
**verification:** UNIT/INSTR — Robolectric/`AudioManager` shadow фиксирует вызов `startBluetoothSco` (API<31) или `setCommunicationDevice` (API≥31) в зависимости от `Build.VERSION.SDK_INT`; переключение флага → откат на встроенный микрофон.

---

## 7. AC — SegmentPlayer

### AC-15 SegmentPlayer — pause → resume переозвучивает текущий сегмент с начала

**Given:** активное воспроизведение очереди `[s1, s2, s3]` (`Segment.Text`); текущий сегмент — `s2`; `isPaused()` возвращает `true` после завершения `s2`.
**When:** вызывающий вызывает `resume()` (`isPaused() → false`).
**Then:** `s2` **переозвучивается с начала** (re-speak того же сегмента, без advance индекса), а не продолжается с середины. Последовательность воспроизведения: `s1 → s2 → (pause) → s2(again) → s3`.

**derived_from:** UC-3 (BR-3.2, alt A2).
**covers:** FR-12.
**verification:** UNIT — fake `TtsEngineWrapper` с счётчиком `speak(text)` invocations по индексу/тексту сегмента; assertions: `s2.text` озвучен дважды, `s3` — один раз после resume.

### AC-16 SegmentPlayer — Mutex serialization (без наложений)

**Given:** две корутины одновременно вызывают `playSegments(...)` с разными очередями.
**When:** обе запущены.
**Then:** вызовы сериализуются внутренним `Mutex` — воспроизведение из двух корутин **не пересекается** во времени (одно `playSegments` ждёт освобождения Mutex другим).

**derived_from:** UC-3 (BR-3.1).
**covers:** FR-12.
**verification:** UNIT — два `playSegments` параллельно; `onSegmentStart`-коллбэк фиксирует непересекающиеся временные интервалы сегментов; `Mutex`_holder observable.

---

## 8. AC — Download моделей

### AC-17 AsrModelManager / TtsModelManager — 3-retry HTTP download

**Given:** модель отсутствует на диске (`isReady == false`); сеть доступна; первый и второй HTTP-запрос падают с сетевой ошибкой (`IOException`/timeout), третий succeeds.
**When:** вызывается `downloadAsrModel()` (или `downloadTtsModel("de")`).
**Then:** выполняется **до 3 retry** (loop `1..3`), на неудаче — `archive.delete()` + `delay(2000)` + повтор; на 3-й успешной попытке `Flow<DownloadProgress>` эмитит `Downloading(%) → Extracting → Initializing → Done`, после чего `isAsrAvailable()` (или `isTtsAvailable("de")`) возвращает `true`. Если все 3 попытки упали → `Failed`.

**derived_from:** UC-8 (BR-8.1).
**covers:** FR-13.
**verification:** UNIT — mock `HttpURLConnection` эмитит 2 ошибки затем 200 OK; verifier на retry-count; assertions на `Done` + `isAvailable==true`. Негативный кейс: 3 ошибки → `Failed`.

### AC-18 Download — cancellation → delete partial; StatFs pre-check блокирует

**Given A:** начат download, частичный файл на диске.
**When A:** коллектор `Flow<DownloadProgress>` отменяется (cancellation).
**Then A:** `connection.disconnect()` вызывается, **частичный файл удалён** (`archive.delete()`), нет мусора на диске.
**Given B:** `StatFs.getAvailableStorageBytes() < spec.minRequiredBytes` (недостаточно места).
**When B:** вызывается `downloadTtsModel(lang)` / `downloadAsrModel()`.
**Then B:** HTTP-запрос не инициируется, эмитится `Failed("Need Xmb, only Ymb free")`.

**derived_from:** UC-8 (BR-8.2, BR-8.3).
**covers:** FR-13.
**verification:** UNIT — (A) cancellation test: `runCurrent()`, `cancel()`, assert partial-file `exists()==false`; (B) StatFs mock: insufficient → `Failed` без HTTP-invocation.

---

## 9. AC — Звуковые эффекты

### AC-19 playSoundEffect(CORRECT_ANSWER) — SoundPool играет res/raw клип (regression-lock)

**Given:** `SoundPool` инициализирован (singleton, `init`), sample `R.raw.voicy_correct_answer` загружен (`loadedSamples` содержит готовый sampleId через `setOnLoadCompleteListener`).
**When:** вызывается `playSoundEffect(SoundEffect.CORRECT_ANSWER)`.
**Then:** `SoundPool.play(correctSampleId, ...)` вызывается **мгновенно** (pre-loaded, не сетевая загрузка/не синтез), caller не блокируется. `WRONG_ANSWER` → `R.raw.voicy_bad_answer`; `LESSON_COMPLETE` → временно переиспользует correct-answer клип.

**derived_from:** UC-9 (BR-9.1, BR-9.2).
**covers:** FR-14.
**verification:** UNIT/INSTR — spy на `SoundPool.play`: `CORRECT_ANSWER` → sampleId, соответствующий `voicy_correct_answer.mp3`; `WRONG_ANSWER` → `voicy_bad_answer.mp3`. Regression-snapshot текущей skeleton-логики.

---

## 10. AC — stop, state machines, model-repository DI

### AC-20 stop() — неблокирующая остановка TTS + ASR (< 100 ms, no ANR)

**Given:** активный `speak` (drain-loop AudioTrack) и/или активный `recognizeSpeech` (AudioRecord читает чанки).
**When:** вызывается `stop()` (с другой корутины).
**Then:**
- `stop()` возвращается `< 100 ms` (неблокирующий: флаги `wasStopped`/`isStopped` set, `generation.incrementAndGet()`, `speakJob.cancel()` без `join`, `audioRecord.stop()+release()` в try/catch);
- `AudioTrack` освобождается в `finally`; `AudioRecord` освобождается в `finally`;
- коллектор `speak`/`recognizeSpeech` получает нормальное завершение (не uncaught `CancellationException`);
- идемпотентен (повторный `stop()` — no-op).

**derived_from:** UC-1 (alt 0a), UC-2 (alt 0a).
**covers:** FR-15.
**verification:** UNIT — `withTimeout(100.millis) { audioRepo.stop() }` не падает; spy на `AudioTrack`/`AudioRecord` `release()` в `finally`; повторный `stop()` — те же эффекты (idempotent).

### AC-21 TTS/ASR state machines — корректные переходы состояний

**Given:** `TtsEngineWrapper.state: StateFlow<TtsState>` и `AsrEngineWrapper.state: StateFlow<AsrState>`.
**When:** выполняется последовательность `initialize → speak → stop` (TTS) и `initialize → record → endpoint → final` (ASR), плюс ошибка load.
**Then:**
- TTS: `Idle → Initializing → Ready → Speaking → Ready` (или `Speaking → Paused → Speaking → Ready`), при ошибке → `Error(reason)`;
- ASR: `IDLE → INITIALIZING → READY → RECORDING → RECOGNIZING → READY`, при ошибке → `ERROR`;
- `Error(reason)` хранит человеко-читаемую причину (OOM/native lib/timeout).
- Эти state machines **внутренние** для адаптера (data-слой); домен их не видит (ему достаточно `Flow<AudioEvent>`/`Flow<RecognitionEvent>`).

**derived_from:** UC-1, UC-2.
**covers:** FR-16.
**verification:** UNIT — `state` value-collected за сценарий; assertions на последовательность переходов и текст `reason`.

### AC-22 SherpaAudioModelRepository — DI Singleton, setBluetoothMic ↔ observeBluetoothMic round-trip

**Given:** `RepositoryModule` с `@Binds abstract fun bindAudioModelRepository(impl: SherpaAudioModelRepository): AudioModelRepository`; `SherpaAudioModelRepository` аннотирован `@Singleton`.
**When:** вызывается `setBluetoothMic(true)` (persist в `AppConfig.useBluetoothMic` через `SettingsRepository`).
**Then:** `observeBluetoothMic()` эмитит `true`; после `setBluetoothMic(false)` → эмитит `false`. DI: injection-сайты получают тот же экземпляр.

**derived_from:** UC-7 (шаг 3).
**covers:** FR-17.
**verification:** DI + UNIT — `@Binds` присутствует, graph валиден; round-trip test на `observeBluetoothMic` flow после `setBluetoothMic`.

---

## 11. AC — NFR (zero-leakage, memory, JNI, latency, cancellation)

### AC-23 NFR-1 Zero Sherpa leakage в домене (grep = 0)

**Given:** дерево исходников `app/src/main/java/com/alexpo/grammermate/v2/core/domain/`.
**When:** выполняется статическая проверка импортов.
**Then:**
- `grep -rEi "^import .*(sherpa|com\.k2fsa)" app/src/main/java/com/alexpo/grammermate/v2/core/domain/` → **0 совпадений**;
- `grep -rE "^import android\." app/src/main/java/com/alexpo/grammermate/v2/core/domain/audio/` → **0 совпадений**;
- Sherpa-импорты **присутствуют** в `v2/core/data/audio/TtsEngineWrapper.kt` / `AsrEngineWrapper.kt` (positive-control: grep ≠ 0 в data-слое).

**derived_from:** UC-1, UC-2 (zero-leakage инвариант).
**covers:** NFR-1.
**verification:** GREP — CI-шаг или pre-merge check.

### AC-24 NFR-2 Native memory safety — RSS возвращается к baseline после циклов load/release

**Given:** устройство с `largeHeap`, baseline RSS native-памяти зафиксирован (`dumpsys meminfo <pkg>`).
**When:** выполняется 20 циклов `initialize + speak + release` для 3 языков в rotation (it/ru/en), плюс ASR-load/release.
**Then:** после циклов RSS native-памяти процесса возвращается к baseline (в допуске); нет OOM/SIGBUS; `ResidentTtsCache` eviction (FR-3) и `MemoryChecker` hard-block (FR-10) обеспечивают bounded native-allocation (~450 MB TTS worst case + ASR по требованию).

**derived_from:** UC-6 (BR-6.3).
**covers:** NFR-2 (также покрывает FR-3, FR-10 на системном уровне).
**verification:** INSTR — Android Profiler / `dumpsys meminfo` до/после; assertion `|rss_final − rss_baseline| < ε`.

### AC-25 NFR-3 JNI stability — graceful degradation, no native crash propagation

**Given A:** 32-bit ARM эмулятор (armeabi-v7a без arm64-v8a) — VITS падает с SIGBUS.
**When A:** вызывается `speak("en")`.
**Then A:** SIGBUS не происходит; auto-fallback на system TTS (FR-5); `Completed` эмитится.
**Given B:** mock `OfflineTts` constructor бросает `OutOfMemoryError` / `UnsatisfiedLinkError`.
**When B:** инициируется load TTS.
**Then B:** исключение поймано try/catch, типизованная причина (`"Not enough memory to load voice model"` / `"Native TTS library error: ..."`), state → `Error(reason)`, `Flow<AudioEvent>` эмитит `Failed`, процесс жив.

**derived_from:** UC-5, UC-6.
**covers:** NFR-3 (также FR-5 на 32-bit-ветке).
**verification:** INSTR (A) — 32-bit ARM эмулятор; UNIT (B) — mock constructor throws → `Failed`, процесс жив.

### AC-26 NFR-4 Latency — resident cache = no re-init (переключение < 100 ms, Started ≤ 200 ms)

**Given:** в `ResidentTtsCache` резидентны модели (≥2 языка); alternation-сценарий.
**When:** повторный `speak` на уже загруженном языке.
**Then:**
- `OfflineTts(...)` constructor **не вызывается** (нет native-load ~1–3 с);
- время переключения языка (от `speak(...)` до готовности синтеза) `< 100 ms`;
- время от `speak(...)` до `AudioEvent.Started` для resident-языка `≤ 200 ms`.
- Для cold-load языка допускается 1–3 с (один раз). ASR `setLanguage` через `OfflineRecognizer.setConfig` — мгновенно.

**derived_from:** UC-4 (BR-4.2).
**covers:** NFR-4.
**verification:** BENCH — tooling-лог timestamp'ов: `(t(Started) − t(speak)) ≤ 200 ms` для resident; `(t(switch-ready) − t(speak)) < 100 ms`; verifier на `OfflineTts` constructor: 0 invocations на 2..N-м `speak`.

### AC-27 NFR-5 Coroutine cancellation safety — Flow cancel → native stop, без утечек

**Given:** активный `speak`/`recognizeSpeech`; коллектор `Flow<AudioEvent>`/`Flow<RecognitionEvent>`.
**When:** коллектор отменяется (cancellation) посередине воспроизведения/записи.
**Then:**
- TTS: `isStopped.set(true)` (callback возвращает 0, drain-loop выходит), `audioTrack.stop()+release()` в `finally`, `speakJob.cancel()`;
- ASR: `audioRecord.stop()+release()` в `finally`, `CancellationException` обрабатывается явно (не пробрасывается как обычная ошибка);
- `SegmentPlayer.playAudioFile`: `cont.invokeOnCancellation { player.release() }`;
- после GC: `AudioTrack`/`AudioRecord` instances → 0 (Android Profiler), повторный `speak` работает без ошибок.

**derived_from:** UC-1 (alt 0a), UC-2 (alt 0a).
**covers:** NFR-5.
**verification:** UNIT — cancellation test: запустить flow, `cancel()`, assert native-ресурсы освобождены (spy на `release()`); повторный `speak` → `Started→Completed`.

---

## 12. AC — GAP C4 / C5 (legacy-coverage-checklist §F)

### AC-28 GAP C4 — HomophoneReplacerConfig hook сохранён в AsrEngineWrapper

**Given:** перенос `AsrEngine` из legacy в `AsrEngineWrapper.kt`; legacy `AsrEngine.kt:126,215` принимал/использовал `HomophoneReplacerConfig` (hook для homophone-замены в распознанном тексте).
**When:** `AsrEngineWrapper` конструируется (DI) и/или выполняет `recognizeSpeech`.
**Then:** `HomophoneReplacerConfig` **plumbed** в `AsrEngineWrapper` — конструктор или метод принимает конфиг (даже если передан empty-config), вызов hook'а присутствует в коде. Hook **не удалён** при переносе.
**verification:** GREP — `grep -n "HomophoneReplacerConfig" app/src/main/java/com/alexpo/grammermate/v2/core/data/audio/AsrEngineWrapper.kt` → ≥ 1 совпадение (конструктор-параметр или поле). UNIT — empty-config не ломает `recognizeSpeech` (no-op hook).

**derived_from:** UC-2 (ASR hook preserved from legacy).
**covers:** FR-6 (hook — часть ASR-контракта; закрывает GAP C4).
**verification:** GREP + UNIT.

### AC-29 GAP C5 — ModelLoadLogger пишет load events в Downloads с memory info

**Given:** перенос `ModelLoadLogger` из legacy (`data/ModelLoadLogger.kt`) в E03 рядом с `MemoryChecker`; файл лога — `Downloads/BaseGrammy/model_load_log.txt`.
**When:** инициируется load модели (TTS через `speak`/cache-miss, или ASR через `recognizeSpeech`), либо download/extract модели.
**Then:** в файл `Downloads/BaseGrammy/model_load_log.txt` **дописывается строка** с: типом события load (TTS/ASR, lang), отметкой времени, и **memory info** (`MemoryChecker.getMemoryInfo()` — availMem/heap). Hook вызывается при load-событии (не удалён при переносе).
**verification:** GREP — `grep -n "ModelLoadLogger" app/src/main/java/com/alexpo/grammermate/v2/core/data/audio/` → ≥ 1 совпадение; INSTR/UNIT — после load-события assert: файл существует, последняя строка содержит memory-info.

**derived_from:** UC-6 (load + memory scenario), UC-1 (TTS load).
**covers:** FR-10 (load events + memory info; закрывает GAP C5).
**verification:** GREP + INSTR/UNIT.

---

## 13. Traceability matrix (AC ↔ UC-003 ↔ FR/NFR)

| AC | Заголовок | derived_from UC-003 | covers FR/NFR (artifact_id) | Verification |
|---|---|---|---|---|
| AC-1 | SherpaAudioRepository DI Singleton | UC-1, UC-2 | FR-1 (488) | DI |
| AC-2 | TTS speak real synthesis Started→Progress→Completed | UC-1 | FR-2 (489) | UNIT + INSTR |
| AC-3 | speak blank text no-op | UC-1 | FR-2 (489) | UNIT |
| AC-4 | ResidentTtsCache LRU eviction maxSize=3 | UC-1, UC-4 | FR-3 (490) | UNIT |
| AC-5 | LRU cache hit, no re-init, < 100 ms | UC-4 | FR-3 (490), NFR-4 (517) | BENCH |
| AC-6 | RU text-scale применяется | UC-1 | FR-4 (491) | UNIT |
| AC-7 | Fallback Android TextToSpeech | UC-5 | FR-5 (492) | INSTR |
| AC-8 | ASR real recognition ListeningStarted→…→Final | UC-2 | FR-6 (493) | UNIT + INSTR |
| AC-9 | ASR model missing → Failed(model_missing) | UC-2 | FR-6 (493) | UNIT/INSTR |
| AC-10 | AudioRecord VOICE_RECOGNITION 16 kHz + VAD endpoint | UC-2 | FR-7 (494) | UNIT/INSTR |
| AC-11 | isTtsAvailable(lang) per-language manifest | UC-1, UC-8 | FR-8 (495) | UNIT |
| AC-12 | isAsrAvailable() regression-lock | UC-2 | FR-9 (496) | UNIT |
| AC-13 | MemoryChecker hard block (no OOM) | UC-6 | FR-10 (497), NFR-2 (515) | UNIT |
| AC-14 | BluetoothAudioRouter SCO / setCommunicationDevice | UC-7 | FR-11 (498) | UNIT/INSTR |
| AC-15 | SegmentPlayer pause/resume re-speaks current | UC-3 | FR-12 (508) | UNIT |
| AC-16 | SegmentPlayer Mutex serialization | UC-3 | FR-12 (508) | UNIT |
| AC-17 | Tts/AsrModelManager 3-retry HTTP | UC-8 | FR-13 (509) | UNIT |
| AC-18 | Download cancel → delete partial; StatFs pre-check | UC-8 | FR-13 (509) | UNIT |
| AC-19 | playSoundEffect CORRECT_ANSWER SoundPool | UC-9 | FR-14 (510) | UNIT/INSTR |
| AC-20 | stop() non-blocking (< 100 ms) | UC-1, UC-2 | FR-15 (511) | UNIT |
| AC-21 | TTS/ASR state machines transitions | UC-1, UC-2 | FR-16 (512) | UNIT |
| AC-22 | SherpaAudioModelRepository DI + setBluetoothMic round-trip | UC-7 | FR-17 (513) | DI + UNIT |
| AC-23 | Zero Sherpa leakage grep=0 | UC-1, UC-2 | NFR-1 (514) | GREP |
| AC-24 | Native memory safety RSS → baseline | UC-6 | NFR-2 (515) | INSTR |
| AC-25 | JNI stability graceful degradation | UC-5, UC-6 | NFR-3 (516) | INSTR + UNIT |
| AC-26 | Latency resident cache switch < 100 ms / Started ≤ 200 ms | UC-4 | NFR-4 (517) | BENCH |
| AC-27 | Coroutine cancellation safety | UC-1, UC-2 | NFR-5 (518) | UNIT |
| AC-28 | HomophoneReplacerConfig hook preserved (GAP C4) | UC-2 | FR-6 (493) | GREP + UNIT |
| AC-29 | ModelLoadLogger writes Downloads with memory info (GAP C5) | UC-6, UC-1 | FR-10 (497) | GREP + INSTR/UNIT |

### Coverage check (каждый FR/NFR ≥ 1 AC)

| FR/NFR | artifact_id | Покрыт AC |
|---|---|---|
| FR-1 | 488 | AC-1 |
| FR-2 | 489 | AC-2, AC-3 |
| FR-3 | 490 | AC-4, AC-5 |
| FR-4 | 491 | AC-6 |
| FR-5 | 492 | AC-7 |
| FR-6 | 493 | AC-8, AC-9, AC-28 |
| FR-7 | 494 | AC-10 |
| FR-8 | 495 | AC-11 |
| FR-9 | 496 | AC-12 |
| FR-10 | 497 | AC-13, AC-29 |
| FR-11 | 498 | AC-14 |
| FR-12 | 508 | AC-15, AC-16 |
| FR-13 | 509 | AC-17, AC-18 |
| FR-14 | 510 | AC-19 |
| FR-15 | 511 | AC-20 |
| FR-16 | 512 | AC-21 |
| FR-17 | 513 | AC-22 |
| NFR-1 | 514 | AC-23 |
| NFR-2 | 515 | AC-13, AC-24 |
| NFR-3 | 516 | AC-25 |
| NFR-4 | 517 | AC-5, AC-26 |
| NFR-5 | 518 | AC-27 |

**Итого:** 29 AC, все 17 FR + 5 NFR покрыты; GAP C4 (HomophoneReplacerConfig, AC-28) и C5 (ModelLoadLogger, AC-29) закрыты.

---

## 14. Критичные (high-priority) AC — regression-якоря эпизода

Следующие AC фиксируют критичные контракты эпизода (потенциальные regression-points, отмечены в задаче E03):

- **AC-2** — TTS speak реальный синтез (Started→Progress→Completed, **НЕ Started→Failed**): якорь против skeleton-regression.
- **AC-8** — ASR recognizeSpeech реальное распознавание (ListeningStarted→Partial→EndpointDetected→Final).
- **AC-5 / AC-26** — LRU cache switching без реинициализации, latency переключения < 100 ms.
- **AC-13** — MemoryChecker hard block при availMem < 150 MB (TTS) / < 800 MB (ASR), без OOM crash.
- **AC-15** — SegmentPlayer pause/resume переозвучивает текущий сегмент с начала.
- **AC-19** — playSoundEffect(CORRECT_ANSWER) → `res/raw/voicy_correct_answer.mp3` (regression-lock уже реализованного skeleton).
- **AC-23** — zero Sherpa leakage в `v2/core/domain` (grep = 0).
- **AC-28 (GAP C4)** — HomophoneReplacerConfig hook сохранён в AsrEngineWrapper.
- **AC-29 (GAP C5)** — ModelLoadLogger пишет в `Downloads/BaseGrammy/model_load_log.txt` с memory info.

---

*AC-003 — мост к dev-задачам (saga-planner): каждый AC — кандидат на `implements`-trace к dev-задаче в эпизоде E03. Доменные порты и sealed-типы событий взяты как факт из SRS-001 (E01, NFR-4 drift-запрет); FR/NFR-коды — в SRS-003 (artifact_id=487). Легаси-референсы (TtsEngine, AsrEngine, `*ModelManager`, `*Registry`, BluetoothAudioRouter, SegmentPlayer, MemoryChecker, HomophoneReplacerConfig, ModelLoadLogger) прочитаны как рабочий источник логики на 2026-07-07.*

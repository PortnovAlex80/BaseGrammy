# SRS-003 — Аудио-адаптер: SherpaAudioRepository (системный контракт)

**Эпизод:** E03 (epic_id=87), проект GrammarMate (project_id=25)
**Волна:** 0 — Фундамент
**Стадия:** Formalization (SRS)
**Дата:** 2026-07-07
**Status:** Accepted

**Артефакт-источник:** PRD-003 (artifact_id=474, `01-prd.md`, status=accepted).
**Upstream-контракт:** SRS-001 (E01, artifact_id=450, FR-7 + §5.8/§5.9) — доменные порты
`AudioRepository` / `AudioModelRepository` и доменные sealed-типы событий
(`AudioEvent`, `RecognitionEvent`, `DownloadProgress`, `ModelStatus`, `SoundEffect`).
**НЕ дублируется и НЕ меняется в E03** (NFR-4 SRS-001 = drift-запрет на порты).
**Repository:** BaseGrammy (repository_id=7), ветка `rewrite/v2-clean-architecture`.
**Код:** SRS-003

> SRS фиксирует **системный контракт** адаптера: компонентную декомпозицию, FR/NFR
> с измеримыми метриками и **API-контракт `SegmentPlayer`** (shared-mutation-risk для
> E08/E09). Доменные порты взяты как факт из SRS-001 (E01); сигнатуры verified по
> фактическому коду `v2/core/domain/audio/*` (AudioRepository.kt, AudioModelRepository.kt)
> и `v2/core/data/audio/*` (SherpaAudioRepository.kt, SherpaAudioModelRepository.kt —
> skeletons с TODO) на 2026-07-07. Legacy-референсы (TtsEngine, AsrEngine,
> `*ModelManager`, `*Registry`, BluetoothAudioRouter, SegmentPlayer, MemoryChecker)
> прочитаны как рабочий источник логики.

---

## 1. Архитектурный обзор

E03 завершает **data-слойный адаптер** к нативной библиотеке Sherpa-ONNX
(`com.k2fsa.sherpa.onnx.*`) и Android audio API. Это граница **инверсии
зависимости**: домен (`v2/core/domain/audio/*`) зависит только от чистых
интерфейсов `AudioRepository` / `AudioModelRepository` (pure-Kotlin, без Android/
Sherpa — SRS-001 FR-7, NFR-1), а E03 реализует эти интерфейсы в
`app/.../v2/core/data/audio/*`, инкапсулируя всю нативную/платформенную специфику.

```
┌──────────────────────────────────────────────────────────────────────┐
│  v2/core/domain/audio   (pure Kotlin, E01, regression-locked)        │
│  ┌──────────────────────────┐    ┌─────────────────────────────────┐ │
│  │  AudioRepository         │    │  AudioModelRepository           │ │
│  │  speak/stop/recognize/   │    │  downloadTts/downloadAsr/      │ │
│  │  playSoundEffect/        │    │  getTts*/getAsr*Status/        │ │
│  │  isTtsAvailable/         │    │  observeBluetoothMic/          │ │
│  │  isAsrAvailable          │    │  setBluetoothMic               │ │
│  │  + AudioEvent/Recognition│    │  + DownloadProgress/ModelStatus│ │
│  │    Event/SoundEffect     │    │                                │ │
│  └────────────▲─────────────┘    └───────────────▲─────────────────┘ │
└───────────────┼──────────────────────────────────┼──────────────────┘
                │ implements                       │ implements
┌───────────────┴──────────────────────────────────┴──────────────────┐
│  v2/core/data/audio   (E03, @Singleton, Hilt-bound — RepositoryModule)│
│                                                                       │
│  ┌────────────────────────────┐   ┌─────────────────────────────────┐ │
│  │  SherpaAudioRepository     │   │  SherpaAudioModelRepository     │ │
│  │  (TTS + ASR + SFX)         │   │  (download + status + BT-pref)  │ │
│  └──────┬──────┬──────┬───────┘   └──────┬─────────────┬────────────┘ │
│         │      │      │                  │             │              │
│   ┌─────▼──┐ ┌─▼────┐ │            ┌─────▼──────┐ ┌────▼──────────┐   │
│   │TtsEng- │ │AsrEn-│ │            │TtsModelMgr │ │AsrModelMgr    │   │
│   │ine(Wrap│ │gine  │ │            │(.tar.bz2 + │ │(.tar.bz2 +    │   │
│   │per)    │ │(Wrap)│ │            │ 3-retry)   │ │ VAD+Whisper)  │   │
│   └──┬──┬──┘ └──┬───┘ │            └────────────┘ └───────────────┘   │
│      │  │       │     │                                                  │
│  ┌───▼──▼──┐ ┌──▼─────┐ │           ┌──────────────────┐               │
│  │Resident │ │AudioRe-│ │           │ BluetoothAudioR. │               │
│  │TtsCache │ │cord+   │ │           │ (SCO / comm.dev) │               │
│  │(LRU=3)  │ │Vad     │ │           └──────────────────┘               │
│  └─────────┘ └────────┘ │                                              │
│              ┌──────────▼─────────┐                                    │
│              │ SoundPool (res/raw)│  ← playSoundEffect (уже реализовано)│
│              └────────────────────┘                                    │
│                                                                       │
│  ──────────  shared helpers  ──────────                                │
│  SegmentPlayer (Mutex-serialized) ← shared с E08 (bg-vocab) / E09 (story)│
│  MemoryChecker (StatFs / ActivityManager.MemoryInfo pre-check)         │
└───────────────────────────────────────────────────────────────────────┘
                │ JNI
                ▼
        libs/sherpa-onnx-*-1.12.40.aar  (уже подключён, saga note id=2)
```

**Поток данных TTS (FR-2..FR-5):**
`AudioRepository.speak(text, lang, speed) → Flow<AudioEvent>` →
`SherpaAudioRepository` → `TtsEngineWrapper.speak` →
ResidentTtsCache.get(lang) [fast-path] | initialize(lang) [load] →
`OfflineTts.generateWithConfigAndCallback(text, GenerationConfig(sid,speed))` →
PCM-float chunks → `AudioTrack.write(...)` (USAGE_MEDIA, CONTENT_TYPE_SPEECH) →
эмит `Started → [Progress] → Completed | Failed`. На 32-bit ARM / отсутствующих
файлах VITS_PIPER → fallback на Android `TextToSpeech`.

**Поток данных ASR (FR-6..FR-7):**
`AudioRepository.recognizeSpeech(lang) → Flow<RecognitionEvent>` →
`SherpaAudioRepository` → (если включён BT — `BluetoothAudioRouter.startBluetoothAudio`) →
`AudioRecord(VOICE_RECOGNITION, 16 кГц, mono, PCM_16BIT)` → 100 мс чанки →
`Vad.acceptWaveform(chunk)` → при `isSpeechDetected` копим сегменты в
`Vad.front()/pop()` → по паузе `EndpointDetected` →
`OfflineRecognizer.createStream + acceptWaveform + decode + getResult` →
эмит `Final(text)`.

**Принципы:**
- **Zero leakage:** `import com.k2fsa.sherpa.onnx.*` и `import android.*` живут
  **только** в `v2/core/data/audio/*` (NFR-1). Домен остаётся pure.
- **Single instance:** `@Singleton` на обоих репозиториях (нативная модель
  резидентна в процессе, повторное создание = утечка native heap).
- **Резидентный кэш вместо re-init:** TTS-модели держатся загруженными в
  `ResidentTtsCache` (maxSize=3, LRU) — переключение языков мгновенно (R2 latency).
- **Hard memory gate:** перед каждым `OfflineTts(...)` / `OfflineRecognizer(...)`
  load — `MemoryChecker.hasEnoughMemory(requiredMb)` (R1 OOM).

---

## 2. Структура компонентов

Все файлы в `app/src/main/java/com/alexpo/grammermate/v2/core/data/audio/`.

### 2.1. `SherpaAudioRepository.kt` (завершить skeleton)
- `@Singleton class SherpaAudioRepository @Inject constructor(@ApplicationContext context, ttsEngine: TtsEngineWrapper, asrEngine: AsrEngineWrapper, memoryChecker: MemoryChecker, segmentPlayer: SegmentPlayer) : AudioRepository`
- Реализует: `speak`, `stop`, `recognizeSpeech`, `playSoundEffect` (готово),
  `isTtsAvailable`, `isAsrAvailable` (готово).
- Сегодня skeleton с TODO на speak/recognizeSpeech; FR-2/FR-6 закрывают TODO.
- SoundPool + `R.raw.voicy_correct_answer` / `voicy_bad_answer` — оставить как есть.

### 2.2. `SherpaAudioModelRepository.kt` (завершить skeleton)
- `@Singleton class SherpaAudioModelRepository @Inject constructor(@ApplicationContext context, settingsRepository: SettingsRepository, ttsModelManager: TtsModelManager, asrModelManager: AsrModelManager) : AudioModelRepository`
- Реализует: `downloadTtsModel`, `downloadAsrModel`, `getTtsModelStatus`,
  `getAsrModelStatus` (готово), `observeBluetoothMic` (готово), `setBluetoothMic`
  (готово).
- FR-13 закрывает TODO на сетевой загрузке/распаковке.

### 2.3. `TtsEngineWrapper.kt` (новый, перенос `TtsEngine` из legacy)
- `@Singleton class TtsEngineWrapper @Inject constructor(@ApplicationContext context, memoryChecker: MemoryChecker) : TtsEnginePort`
- Хостит `ResidentTtsCache`, `OfflineTts`, `AudioTrack`, fallback Android
  `TextToSpeech`, state machine `TtsState` (Idle/Initializing/Ready/Speaking/
  Paused/Error).
- Публичные suspend-методы: `initialize(languageId)`, `speak(text, languageId,
  speed)`, `stop()`, `pause()`, `resume()`, `release()`, `releaseLanguage(lang)`,
  `state: StateFlow<TtsState>`, `activeLanguageId: String?`.

### 2.4. `AsrEngineWrapper.kt` (новый, перенос `AsrEngine` из legacy)
- `@Singleton class AsrEngineWrapper @Inject constructor(@ApplicationContext context, memoryChecker: MemoryChecker, bluetoothRouter: BluetoothAudioRouter) : AsrEnginePort`
- Хостит `OfflineRecognizer` (Whisper Small), `Vad` (Silero), `AudioRecord`,
  state machine `AsrState` (IDLE/INITIALIZING/READY/RECORDING/RECOGNIZING/ERROR).
- Публичные suspend-методы: `initialize(language)`, `recordAndTranscribe(
  maxDurationMs): String` (или streaming-вариант для Partial-событий),
  `setLanguage(language)`, `stopRecording()`, `release()`, `state: StateFlow<AsrState>`.

### 2.5. `ResidentTtsCache.kt` (новый, перенос из legacy `TtsEngine.kt:68-143`)
- `internal class ResidentTtsCache(maxSize: Int = 3, freeFn: (OfflineTts) -> Unit = { it.free() })`
- LinkedHashMap access-ordered, явное eviction в `put`, `safeFree` swallow.
- Pure data structure (no Android) → unit-тестируется на JVM
  (см. legacy `ResidentTtsCacheTest.kt`).

### 2.6. `TtsModelRegistry.kt` + `AsrModelRegistry.kt` (новые, перенос из legacy)
- Pure-Kotlin реестры `TtsModelSpec` / `AsrModelSpec` (downloadUrl,
  archivePrefix, modelDirName, requiredFiles, requiredDirs, minRequiredBytes,
  fallbackDownloadSize, modelType, modelFileName). Переносятся как есть.

### 2.7. `TtsModelManager.kt` + `AsrModelManager.kt` (новые, перенос из legacy)
- `@Singleton class TtsModelManager @Inject constructor(@ApplicationContext context)` и
  аналогично `AsrModelManager`.
- `download(lang): Flow<DownloadProgress>` (или внутренний `DownloadState` →
  маппинг в доменный `DownloadProgress`): HttpURLConnection + redirect-handling
  (до 5), 3-retry, StatFs pre-check, `.tar.bz2` stream-extract
  (BZip2CompressorInputStream + TarArchiveInputStream), path-traversal guard,
  cancellation → delete partial.
- Зависимости: `org.apache.commons.compress` ( Tar / BZip2) — уже в legacy classpath.

### 2.8. `BluetoothAudioRouter.kt` (новый, перенос из legacy `shared/audio/`)
- `@Singleton class BluetoothAudioRouter @Inject constructor(@ApplicationContext context, scope: CoroutineScope) `
- `startBluetoothAudio(): Boolean`, `stopBluetoothAudio()`, `isBluetoothAvailable()`:
  Boolean`, `connectHeadsetProfile()`, `release()`, `isConnected: StateFlow<Boolean>`.
- Двухпутёвый: API<31 (`startBluetoothSco` + BroadcastReceiver) / API≥31
  (`setCommunicationDevice` + `OnCommunicationDeviceChangedListener`).

### 2.9. `SegmentPlayer.kt` (новый, перенос из legacy `shared/audio/SegmentPlayer.kt`)
- `class SegmentPlayer(ttsEngine: TtsEngineWrapper)` — см. **§5 API-контракт**
  (shared с E08/E09).

### 2.10. `MemoryChecker.kt` (новый, перенос из legacy `data/MemoryChecker.kt`)
- `@Singleton class MemoryChecker @Inject constructor(@ApplicationContext context)`
- `hasEnoughMemory(requiredMb: Long, safetyMargin: Float = 1.5f): Boolean`,
  `getMemoryInfo(): String`, `isLowMemoryDevice(): Boolean`.

### 2.11. Hilt: `di/RepositoryModule.kt`
- `@Binds abstract fun bindAudioRepository(impl: SherpaAudioRepository): AudioRepository`
- `@Binds abstract fun bindAudioModelRepository(impl: SherpaAudioModelRepository): AudioModelRepository`
- CoroutineScope для BluetoothAudioRouter — `@ApplicationScoped` (Dispatchers.Main
  для AudioManager-коллбэков,.launch в Executor-эквиваленте).

---

## 3. Functional Requirements (FR)

Каждый FR ссылается на PRD-003 §4 (scope) и SRS-001 FR-7 (upstream-контракт
портов). Доменные sealed-типы событий (`AudioEvent`, `RecognitionEvent`,
`DownloadProgress`, `ModelStatus`, `SoundEffect`) **не определяются здесь** —
они зафиксированы в SRS-001.

### FR-1 SherpaAudioRepository реализует доменный AudioRepository (DI-bound Singleton)

`SherpaAudioRepository` реализует интерфейс `AudioRepository` (SRS-001 FR-7) и
привязывается к нему в Hilt-модуле `di/RepositoryModule` как `@Singleton`.
Конструктор принимает `@ApplicationContext context`, `TtsEngineWrapper`,
`AsrEngineWrapper`, `MemoryChecker`, `SegmentPlayer`. Все методы делегируют в
helper-обёртки; ни один метод не выполняет нативную работу синхронно в
caller-потоке (все `suspend` или `Flow`).

**Проверка:** `RepositoryModule` содержит `@Binds` для `AudioRepository →
SherpaAudioRepository`; `@Inject` в любом VM возвращает тот же экземпляр на
протяжении жизни Application. Компилируется; Hilt graph валиден.

### FR-2 TTS speak() → Flow<AudioEvent> через OfflineTts (VITS-Piper + Kokoro)

`SherpaAudioRepository.speak(text, languageId, speed)` эмитит
`AudioEvent.Started` после готовности модели (TtsState.Ready), затем опционально
`AudioEvent.Progress(fraction)` по мере воспроизведения (playbackHeadPosition /
totalSamples), затем `AudioEvent.Completed` после drain'а AudioTrack либо
`AudioEvent.Failed(error)` при ошибке. Синтез через Sherpa-ONNX `OfflineTts`:
`OfflineTtsModelConfig` (VITS_PIPER → `OfflineTtsVitsModelConfig` с
`noiseScale=0.667f, noiseScaleW=0.8f, lengthScale=1.0f`; KOKORO →
`OfflineTtsKokoroModelConfig`), `generateWithConfigAndCallback(text,
GenerationConfig(sid, speed))` → PCM-float → `AudioTrack` (USAGE_MEDIA,
CONTENT_TYPE_SPEECH, ENCODING_PCM_FLOAT, mono, sampleRate из `tts.sampleRate()`).
`text.isBlank()` → no-op (Flow без эмитов). `speed` коэрсится в `[0.5f, 2.0f]`.
Перенос из legacy `TtsEngine.speak` (строки 465-687).

**Проверка (SM-1):** на устройстве с готовой TTS-моделью `speak("hello",
LanguageId("en"), 1.0f)` → коллектор получает `Started` и `Completed`,
наблюдается audible-вывод. На неготовой модели — `Failed` либо fallback (FR-5).

### FR-3 Резидентный LRU-кэш TTS-моделей (maxSize=3, eviction policy)

`ResidentTtsCache` (internal, LinkedHashMap access-ordered, maxSize=3 по
умолчанию) хранит до 3 нативных `OfflineTts` моделей одновременно. `get(lang)`
промоутит запись в MRU; `put(lang, tts)` при превышении maxSize вытесняет LRU и
вызывает `OfflineTts.free()` через `safeFree` (swallow в try/catch — freeing
best-effort, не должен корраптить кэш). `contains(lang)` — без промоушена.
Повторный `put` того же lang — заменяет и освобождает старую модель.
Перенос из legacy `TtsEngine.kt:68-143`. worst-case resident ≈ 450 MB (3 × ~150 MB).

**Проверка (SM-3):** после `speak` на 3 разных языках повторный `speak` на любом
из них не инициирует native-load (нет вызова `OfflineTts(...)` constructor);
`initialize(lang)` fast-path'ит на `offlineTtsCache.contains(lang)`.

### FR-4 RU text-scale (тюнинг скорости/высоты для русского языка)

Для `LanguageId("ru")` применяется тюнинг `lengthScale`/`speed` относительно
baseline (perceptual calibration под русскую фонетику) — реализуется через
отдельный `GenerationConfig`/`OfflineTtsVitsModelConfig.lengthScale` для RU
(legacy baseline `lengthScale=1.0f` для всех VITS_PIPER; RU получает
скорректированное значение из конфигурации адаптера). Конкретное числовое
значение фиксируется в `TtsModelRegistry.specFor("ru").lengthScaleOverride` либо
в константе адаптера (по переносу из legacy / продуктовой калибровке).

**Проверка:** на готовой RU-модели `speak("Привет", LanguageId("ru"), 1.0f)`
производит аудио с темпом, соответствующим RU-тюнингу (听觉/инструментальная
проверка на эмуляторе: `lengthScale` ≠ default 1.0f для RU, = 1.0f для прочих).

### FR-5 Fallback на Android TextToSpeech при недоступности Sherpa-модели

Если VITS_PIPER-файлы отсутствуют **или** устройство — 32-bit ARM (armeabi-v7a без
arm64-v8a, где VITS падает с SIGBUS), `TtsEngineWrapper.initialize(lang)` вместо
нативной модели инициализирует Android `TextToSpeech` с соответствующим `Locale`
(it→ITALIAN, en→US, ru→RU, de→GERMAN, zh→CHINESE, else→Locale(lang)).
`TextToSpeech.setLanguage` возвращает availability; `LANG_MISSING_DATA` /
`LANG_NOT_SUPPORTED` → `IllegalStateException`. Эмиты `AudioEvent` идут тем же
контрактом (Started/Completed/Failed), потребитель не различает источник. KOKORO
не имеет fallback (всегда требует offline-файлов — throw). Перенос из legacy
`TtsEngine.initSystemTts` (строки 354-431) + `is32BitArm` lazy.

**Проверка:** на 32-bit ARM эмуляторе (или при переименовании файлов VITS) `speak`
не падает с UnsatisfiedLinkError/SIGBUS — происходит fallback на system TTS, и
`Completed` эмитится. На полностью отсутствующем языке — `Failed`.

### FR-6 ASR recognizeSpeech() → Flow<RecognitionEvent> через OfflineRecognizer + SileroVad

`SherpaAudioRepository.recognizeSpeech(languageId)` эмитит
`RecognitionEvent.ListeningStarted` после `AsrState.READY`, затем
`RecognitionEvent.Partial(text)` (промежуточные гипотезы по мере распознавания
сегментов), затем `RecognitionEvent.EndpointDetected` когда VAD нашёл паузу
конца фразы, затем `RecognitionEvent.Final(text)` с итоговым распознанным текстом,
либо `RecognitionEvent.Failed(error)`. Реализация через Sherpa-ONNX
`OfflineRecognizer` (`OfflineWhisperModelConfig` с encoder/decoder/tokens/language/
task="transcribe") + `Vad` (`SileroVadModelConfig`: threshold=0.5f,
minSilenceDuration=0.25f, minSpeechDuration=0.25f, windowSize=512,
maxSpeechDuration=30.0f). Смена языка без перезагрузки модели —
`OfflineRecognizer.setConfig(config)` с новым `OfflineWhisperModelConfig.language`
(Whisper multilingual). Перенос из legacy `AsrEngine` (строки 65-170, 232-344).

**Проверка (SM-2):** на устройстве с готовой ASR-моделью при произнесённой фразе
коллектор получает `ListeningStarted` → `Partial` → `EndpointDetected` →
`Final(nonEmptyText)`. На неготовой модели — `Failed`.

### FR-7 AudioRecord capture (VOICE_RECOGNITION, 16 кГц) с VAD-gated endpointing

Запись через `AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
SAMPLE_RATE=16000, CHANNEL_IN_MONO, ENCODING_PCM_16BIT, bufferSize≥3200)`. Чанки
по 1600 short (100 мс) конвертируются в `FloatArray` (÷32768) и кормятся в
`Vad.acceptWaveform`. При `isSpeechDetected()` — копим сегменты (`Vad.front()/
pop()` → `segment.samples`). Цикл завершается, когда была речь и
`!isSpeechDetected()` (VAD нашёл паузу) — это и есть endpoint. Fallback: нет речи
3 с → empty результат; жёсткий потолок `maxDurationMs=10_000`. `AudioRecord`
всегда `.stop()+.release()` в `finally` (включая cancellation). Перенос из legacy
`AsrEngine.recordAndTranscribe` (строки 232-344).

**Проверка:** лог/инструментed-тест показывает `AudioRecord` открывается с
VOICE_RECOGNITION source, цикл чтения работает, `Vad` получает чанки; при тишине
после речи цикл выходит через endpoint (не по maxDurationMs).

### FR-8 isTtsAvailable(lang) — точная проверка по per-language файловому манифесту

`SherpaAudioRepository.isTtsAvailable(languageId)` возвращает `true` iff для
`TtsModelRegistry.specFor(languageId)` каталог `filesDir/tts/${spec.modelDirName}/`
существует и содержит все `spec.requiredFiles` (например для en:
`["en_US-amy-low.onnx", "tokens.txt"]`) с ненулевым размером, а также все
`spec.requiredDirs` (`["espeak-ng-data"]`). Сегодня проверяет только наличие
непустого каталога — TODO закрыть сопоставлением languageId → spec. Если
`specFor(lang) == null` (язык не в реестре) → `false`.

**Проверка:** после `downloadTtsModel(LanguageId("en")) → Done`,
`isTtsAvailable(LanguageId("en")) == true`; удалить `tokens.txt` → `false`.

### FR-9 isAsrAvailable() — проверка по манифесту Whisper+VAD (уже реализовано, сохранить)

`SherpaAudioRepository.isAsrAvailable()` проверяет наличие `asr/whisper-small/`
со всеми файлами из `ASR_REQUIRED_FILES = ["small-encoder.int8.onnx",
"small-decoder.int8.onnx", "small-tokens.txt"]` (ненулевой размер) **и**
`asr/vad/silero_vad.onnx` (ненулевой размер). Соответствует legacy
`AsrModelRegistry.isReady()`. **Реализовано в skeleton — regression-lock.**

**Проверка:** после `downloadAsrModel() → Done`, `isAsrAvailable() == true`;
удалить `silero_vad.onnx` → `false`.

### FR-10 MemoryChecker pre-check перед каждой загрузкой нативной модели

Перед `OfflineTts(...)` constructor (TTS) — `MemoryChecker.hasEnoughMemory(
requiredMb≈150, safetyMargin=1.5f)`. Перед `OfflineRecognizer(...)` + `Vad(...)`
(ASR) — `MemoryChecker.hasEnoughMemory(requiredMb≈800, safetyMargin=1.5f)`.
`hasEnoughMemory` суммирует native-available (`ActivityManager.MemoryInfo.availMem`)
и heap-available (`Runtime.maxMemory - (total - free)`), сравнивает с
`requiredMb × safetyMargin`. Hard block: при `false` load НЕ вызывается, вызывающий
код получает предсказуемый сигнал (`TtsState.Error("Not enough memory to load
voice model")` / `AsrState.ERROR`/`errorMessage`), поток `speak`/`recognizeSpeech`
эмитит `Failed`. Перенос из legacy `MemoryChecker.kt` (также используется в
`TtsEngine.initialize` pre-load и `AsrEngine.initialize` low-mem warning).

**Проверка (SM-5):** на устройстве с искусственно заниженной free-памятью (или
mock `MemoryChecker.hasEnoughMemory → false`) load блокируется, OOM-crash не
происходит, эмитится `Failed`/`Error`.

### FR-11 BluetoothAudioRouter — SCO (API<31) / setCommunicationDevice (API≥31)

Если `AudioModelRepository.observeBluetoothMic()` эмитит `true`, ASR-запись
маршрутизируется на bluetooth-гарнитуру через `BluetoothAudioRouter.startBluetoothAudio()`
(таймаут 3 с, до 2 retry). Двухпутёвый: API<31 → `AudioManager.startBluetoothSco()`
+ `BroadcastReceiver` на `ACTION_SCO_AUDIO_STATE_UPDATED` + `BluetoothHeadset.
startVoiceRecognition`; API≥31 → `AudioManager.setCommunicationDevice(btDevice)`
+ `addOnCommunicationDeviceChangedListener` (+ legacy SCO флаг для system-wide
routing). При выключении / `stopBluetoothAudio` → откат на встроенный микрофон,
`MODE_NORMAL`. Перенос из legacy `shared/audio/BluetoothAudioRouter.kt`.

**Проверка (SM-7):** с подключённой BT-гарнитурой и `useBluetoothMic=true`
`recognizeSpeech` пишет с BT-микрофона; при `false` — со встроенного.

### FR-12 SegmentPlayer — Mutex-serialized, pause/resume re-speaks current segment

`SegmentPlayer.playSegments(segments, speed, isPaused, onSegmentStart)` играет
список сегментов последовательно, удерживая внутренний `Mutex`. Для
`Segment.Text`: инициализирует TTS под язык сегмента (fast-path на резидентной
модели), озвучивает markdown-cleaned text, ждёт выхода из `TtsState.Speaking`.
Для `Segment.Pause`: `delay(ms)`. После завершения сегмента, если `isPaused()`
стал true — ждёт resume и **переозвучивает тот же сегмент** (continue, без
advance индекса). Concurrent callers — serialization через Mutex. См. **§5
API-контракт**. Перенос из legacy `shared/audio/SegmentPlayer.kt`.

**Проверка (SM-8):** две одновременных корутины `playSegments` не пересекаются
(Mutex); `pause → resume` переозвучивает текущий Text-сегмент с начала.

### FR-13 AsrModelManager / TtsModelManager — 3-retry HTTP, redirect handling, StatFs pre-check

`SherpaAudioModelRepository.downloadTtsModel(lang)` / `downloadAsrModel()`
делегируют в `TtsModelManager` / `AsrModelManager` соответственно. Каждый
`download(): Flow<DownloadProgress>`:
1. Если модель готова (`isReady`) → `DownloadProgress.Done` (no-op).
2. **StatFs pre-check** (`getAvailableStorageBytes`): если `availBytes <
   spec.minRequiredBytes` → `Failed("Need Xmb, only Ymb free")`.
3. (Опц.) local-file fallback: pre-placed архив в `Downloads/BaseGrammy/` или
   `getExternalFilesDir/asr-models/` → распаковка без сети.
4. HTTP: `HttpURLConnection(connectTimeout=60_000, readTimeout=60-120_000,
   instanceFollowRedirects=false)` + ручная обработка redirect (цикл по 301..399,
   до 5 redirect'ов, читая `Location`).
5. **3-retry loop** (`for (attempt in 1..3)`): на неудаче `archive.delete()` и
   повтор после `delay(2000)`; стриминг байтов с прогрессом →
   `DownloadProgress.Downloading(percent)`.
6. `.tar.bz2` stream-extract: `BZip2CompressorInputStream` + `TarArchiveInputStream`,
   strip `archivePrefix`, **path-traversal guard** (`canonicalDest.startsWith(
   destDir.canonicalPath + sep)`), прогресс → `DownloadProgress.Extracting`.
7. Проверка requiredFiles → `DownloadProgress.Initializing` → `Done`.
8. Cancellation (collector отменён) → `connection.disconnect()` + delete partial +
   `DownloadProgress.Error("Cancelled")` (или просто completing flow).

Metered-проверка НЕ делается здесь — это UI (E13). Перенос из legacy
`TtsModelManager` / `AsrModelManager.downloadVad + downloadAsr`.

**Проверка (SM-6):** на чистом эмуляторе `downloadAsrModel()` → коллектор видит
`Downloading(N) → Extracting → Initializing → Done`; после этого `isAsrAvailable()
== true`. Прерывание посередине — partial-файл удалён.

### FR-14 playSoundEffect (SoundPool + res/raw) — уже реализовано, regression-lock

`SherpaAudioRepository.playSoundEffect(effect)` играет клип через `SoundPool`
(USAGE_MEDIA, CONTENT_TYPE_MUSIC, maxStreams=2). `SoundEffect.CORRECT_ANSWER` →
`R.raw.voicy_correct_answer`, `WRONG_ANSWER` → `R.raw.voicy_bad_answer`,
`LESSON_COMPLETE` → пока переиспользует correct-answer клип (отдельный ресурс —
minor gap, отдельная задача). SoundPool грузится в `init` (singleton),
`loadedSamples` отслеживает готовые sampleId через `setOnLoadCompleteListener`;
не-готовый клип — log + skip. **Реализовано в skeleton — regression-lock.**

**Проверка (SM-9):** `playSoundEffect(CORRECT_ANSWER)` после прогрева SoundPool
играет мгновенно, не блокирует caller.

### FR-15 stop() — неблокирующая остановка TTS + ASR

`SherpaAudioRepository.stop()` делегирует в `TtsEngineWrapper.stop()` (неблокирующий:
`wasStopped.set(true)`, `generation.incrementAndGet()` для инвалидации drain-loop,
`isStopped.set(true)`, `systemTts?.stop()`, `currentTrack?.stop()` в try/catch,
`cancel` speakJob без `join` — legacy ANR-fix) и `AsrEngineWrapper.stopRecording()`
(`audioRecord?.stop()+release()` в try/catch, state → READY). Идемпотентен.

**Проверка:** во время активного `speak` вызов `stop()` возвращается < 100 мс, не
вызывает ANR; коллектор `speak` получает обычное завершение (не CancellationException
uncaught); AudioTrack освобождается в finally.

### FR-16 TTS/ASR state machines (Idle/Init/Ready/Speaking/Paused/Error)

`TtsEngineWrapper` экспонирует `state: StateFlow<TtsState>` с переходами:
`Idle → Initializing → Ready → Speaking ⇄ Paused → Ready | Error`.
`AsrEngineWrapper` экспонирует `state: StateFlow<AsrState>`:
`IDLE → INITIALIZING → READY → RECORDING → RECOGNIZING → READY | ERROR`.
Переходы детерминированы (CAS в legacy: `_state.compareAndSet(IDLE, INITIALIZING)`);
`Error(reason)` хранит человеко-читаемую причину (OOM, native lib error, timeout).
Эти state machines — **внутренние** для адаптера (data-слой); домен их не видит
(ему достаточно `Flow<AudioEvent>` / `Flow<RecognitionEvent>`).

**Проверка:** лог/инструментed-тест показывает корректные переходы состояний при
`initialize → speak → stop`, `initialize → record → endpoint → final`, ошибках
load.

### FR-17 SherpaAudioModelRepository реализует AudioModelRepository (DI-bound Singleton)

`SherpaAudioModelRepository` реализует интерфейс `AudioModelRepository` (SRS-001
FR-7) и привязывается в `di/RepositoryModule` как `@Singleton`. Конструктор:
`@ApplicationContext context`, `SettingsRepository`, `TtsModelManager`,
`AsrModelManager`. `observeBluetoothMic` / `setBluetoothMic` сохраняются как есть
(persist `AppConfig.useBluetoothMic` через `SettingsRepository`).

**Проверка:** `@Binds` для `AudioModelRepository → SherpaAudioModelRepository`
присутствует; Hilt graph валиден; `setBluetoothMic(true)` → `observeBluetoothMic`
эмитит `true`.

---

## 4. Non-Functional Requirements (NFR)

Каждый NFR имеет измеримую метрику.

### NFR-1 Zero Sherpa leakage в домене (grep `^import.*sherpa` в `v2/core/domain = 0`)

В `app/src/main/java/com/alexpo/grammermate/v2/core/domain/**` не должно быть
ни одного `import com.k2fsa.sherpa.onnx.*` (case-insensitive `sherpa`) и ни одного
`import android.*` (наследовано из SRS-001 NFR-1). Все Sherpa/Android-импорты
живут **только** в `v2/core/data/audio/*`.

**Метрика/проверка:** `grep -rEi "^import .*(sherpa|com\.k2fsa)" app/src/main/java/com/alexpo/grammermate/v2/core/domain/`
→ 0 совпадений; `grep -rE "^import android\." app/src/main/java/com/alexpo/grammermate/v2/core/domain/audio/`
→ 0 совпадений. Sherpa-импорты **присутствуют** в
`v2/core/data/audio/TtsEngineWrapper.kt` / `AsrEngineWrapper.kt`.

### NFR-2 Native memory safety (LRU eviction + MemoryChecker hard block)

Резидентный TTS-кэш ограничен maxSize=3 (~450 MB worst case) с автоматическим
`OfflineTts.free()` при вытеснении; ASR-модель грузится по требованию (не
сосуществует со всеми TTS). Каждый native-load предшествуется
`MemoryChecker.hasEnoughMemory` hard-gate (FR-10); при `false` load не вызывается.
Native handle'ы (`OfflineTts`, `OfflineRecognizer`, `Vad`, `AudioTrack`,
`AudioRecord`, `TextToSpeech`) освобождаются в `finally` / `release()` /
`stopRecording()`, без утечек между сессиями.

**Метрика/проверка:** после 20 циклов `initialize + speak + release` (3 языка в
rotation) RSS native-памяти процесса возвращается к baseline (Android Profiler /
`dumpsys meminfo <pkg>`); нет OOM/SIGBUS на устройствах с heap largeHeap.

### NFR-3 JNI stability (graceful degradation, no native crash propagation)

Все вызовы в `com.k2fsa.sherpa.onnx.*` обёрнуты в try/catch с типизованной
причиной ошибки: `OutOfMemoryError → "Not enough memory to load voice model"`,
`UnsatisfiedLinkError → "Native TTS library error: ..."`,
`TimeoutCancellationException → "Voice engine startup timed out"`, прочее →
`"Voice engine init failed: ..."`. Ошибка нативной инициализации НЕ крашит
процесс — переводит state machine в `Error(reason)` и эмитит `AudioEvent.Failed` /
`RecognitionEvent.Failed`. 32-bit ARM (armeabi-v7a без arm64-v8a) — auto-fallback
на system TTS без попытки load VITS (FR-5), предотвращая SIGBUS.

**Метрика/проверка:** на 32-bit ARM эмуляторе `speak("en")` не вызывает SIGBUS;
при симулированном `OutOfMemoryError` в `OfflineTts` constructor (mock) коллектор
получает `Failed`, процесс жив.

### NFR-4 Latency — resident cache = no re-init при переключении языка

При активном резидентном кэше повторный `speak` на уже загруженном языке не
вызывает `OfflineTts(...)` constructor (native-load ~1-3 с). Метрика: время от
`speak(...)` до `AudioEvent.Started` для resident-языка ≤ 200 мс; для
cold-load языка — допускается 1-3 с (один раз). ASR `setLanguage` через
`OfflineRecognizer.setConfig` (без reload файлов) — мгновенно.

**Метрика/проверка (SM-3):** tooling-лог показывает отсутствие `OfflineTts(...)`
constructor-вызовов на 2..N-ном `speak` того же языка; latency Started ≤ 200 мс
измеренная по timestamp'ам лога.

### NFR-5 Coroutine cancellation safety (Flow-collector cancel → native stop)

Отмена коллектора `Flow<AudioEvent>` / `Flow<RecognitionEvent>` корректно
останавливает нативную работу: TTS — `isStopped.set(true)` (callback возвращает
0, drain-loop выходит), `audioTrack.stop()+release()` в `finally`, `speakJob.
cancel()`; ASR — `audioRecord.stop()+release()` в `finally` (cancellation
обрабатывается явно, не пробрасывает CancellationException как обычную ошибку).
В `SegmentPlayer.playAudioFile` — `cont.invokeOnCancellation { player.release() }`.
Все ожидания используют `delay` (cooperative with structured concurrency).

**Метрика/проверка:** при отмене коллектора посередине `speak` / `recognizeSpeech`
нативные ресурсы освобождаются (Android Profiler: AudioTrack/AudioRecord instances
→ 0 после GC), нет утечек; повторный `speak` работает без ошибок.

---

## 5. API-контракт — SegmentPlayer (shared-mutation-risk: E08 + E09)

> **ОБЯЗАТЕЛЬНАЯ секция** (brief.shared_mutation_risk=true,
> `shared_mutation_risk_note="SegmentPlayer shared с E08/E09 — контракт зафиксировать
> в SRS-003"`). Без зафиксированного контракта E08 (bg-vocab) и E09 (story)
> независимо изобретут интерфейс → merge-lock на архитектуре, не на строках.

`SegmentPlayer` — единственная разделяемая поверхность между E03 (хост), E08
(bg-vocab) и E09 (story narration). Все три эпика **только вызывают**
`playSegments`; они **не** мутируют сигнатуру и **не** владеют внутренним
состоянием. Контракт фиксируется здесь; изменения после старта E08/E09 = drift.

### 5.1. Публичная поверхность

```kotlin
package com.alexpo.grammermate.v2.core.data.audio

/**
 * Sequential playback engine for ordered segment streams (text + pauses +
 * pre-rendered audio). Serializes concurrent callers via internal [Mutex];
 * owns no pause/resume flag of its own — that flows in through [playSegments].
 *
 * Shared dependency of E03 (host), E08 (bg-vocab), E09 (story narration).
 * The contract below is fixed in SRS-003 §5; do NOT change signatures after
 * E08/E09 start — raise a drift task instead.
 */
class SegmentPlayer(private val ttsEngine: TtsEngineWrapper) {

    /**
     * Sequentially play [segments]. Blocks the calling coroutine until the
     * stream completes (or the coroutine is cancelled). Concurrent invocations
     * are serialized by an internal Mutex.
     *
     * Per-segment semantics:
     *  - [Segment.Pause]: cooperatively `delay(ms)` (cancellable). If [isPaused]
     *    becomes true after the pause elapses, wait for it to clear, then
     *    ADVANCE (pauses are never re-emitted).
     *  - [Segment.Text]: ensure TTS Ready for the segment's language (cheap
     *    fast-path on resident models), speak markdown-cleaned text at [speed],
     *    poll until leaving TtsState.Speaking. If [isPaused] became true after
     *    the segment finishes, wait for resume then RE-SPEAK THE SAME SEGMENT
     *    (continue — do NOT advance the index).
     *  - [Segment.Audio]: play the referenced file via MediaPlayer; if missing,
     *    log + advance (no TTS fallback — an Audio segment carries no text).
     *    Same re-play-on-resume UX as the Text branch.
     *
     * Cancellation-safe: all waits use `delay`. On coroutine cancellation the
     * in-flight MediaPlayer/AudioTrack is released best-effort.
     *
     * @param segments       ordered segment stream (text + pauses + audio).
     * @param speed          TTS playback speed multiplier, forwarded to ttsEngine.
     * @param isPaused       returns true when the caller wants playback suspended.
     * @param onSegmentStart optional per-segment callback (index, segment) for UI.
     */
    suspend fun playSegments(
        segments: List<Segment>,
        speed: Float,
        isPaused: () -> Boolean,
        onSegmentStart: (Int, Segment) -> Unit = { _, _ -> }
    )
}
```

`Segment` — sealed-тип, поставляемый **вызывающим** (E08/E09). E03 НЕ определяет
доменный тип истории (это уровень E08/E09 + домена); SegmentPlayer зависит от
абстракции сегмента. На момент переноса из legacy используется
`MultilingualStoryParser.Segment` (legacy). При миграции в v2 sealed-тип
`Segment` должен быть определён в **домене** (E09 story-domain) либо в data-слое
как минимальный контракт:

```kotlin
sealed interface Segment {
    val languageId: String
    data class Text(override val languageId: String, val text: String) : Segment
    data class Pause(val ms: Long) : Segment {
        override val languageId: String get() = ""
    }
    data class Audio(override val languageId: String, val file: java.io.File) : Segment
}
```

**Extension point:** добавить новый тип сегмента = добавить ветку в `when (segment)`
в `playSegments`. НЕ добавлять dispatcher поверх — одна функция `playSegments`
обрабатывает весь поток. E08/E09 не расширяют SegmentPlayer, они поставляют
`List<Segment>` и `isPaused`-callback.

### 5.2. Module layout

- `v2/core/data/audio/SegmentPlayer.kt` — единственное место реализации.
- `v2/core/data/audio/TtsEngineWrapper.kt` — `ttsEngine: TtsEngineWrapper`
  (SegmentPlayer зависит только от публичной поверхности TtsEngineWrapper:
  `state: StateFlow<TtsState>`, `activeLanguageId`, `initialize(lang)`,
  `speak(text, lang, speed)`, `pause()`, `resume()`).
- `MultilingualStoryParser.Segment` (legacy) → мигрируется в домен или data-слой
  как `Segment` (см. выше) — задача E09 при старте (или drift-задача E03 если E09
  блокируется).

### 5.3. Что НЕ входит в контракт (OUT для E03)

- Sound packs (pre-rendered Ogg-Opus ZIP) — отдельный механизм в **E08**, не
  здесь. `Segment.Audio` — про одиночный `.wav` файл (legacy-контракт), не про
  ZIP-pack.
- Управление foreground-service lifecycle для bg-vocab — E08.
- Story-segmentation logic (нарезка текста на Text/Pause) — E09 (domain parser).

---

## 6. Зависимости и интеграция

### 6.1. Зависимости E03 (depends on)
- **E01 (SRS-001, artifact_id=450), FR-7 + §5.8/§5.9:** доменные порты
  `AudioRepository` / `AudioModelRepository` и sealed-типы событий. Status=accepted.
  **Precondition выполнен.**
- **Sherpa-ONNX AAR** (`libs/sherpa-onnx-static-link-onnxruntime-1.12.40.aar`,
  saga note id=2) — уже подключён.
- **Apache Commons Compress** (`org.apache.commons.compress`) — для tar/bzip2
  stream-extract; проверить наличие в `app/build.gradle.kts` dependencies (legacy
  использует — зависимость переносится).

### 6.2. Что блокирует E03 (dependents / downstream)
- **E04 Training, E05 Verb, E06 Vocab, E07 Daily** — используют `speak()` /
  `recognizeSpeech()` для голосового режима drill'ов.
- **E08 BgVocab** — использует `SegmentPlayer` (см. §5) для фонового словаря;
  отдельный sound-pack-механизм — в самом E08.
- **E09 Story narration** — использует `SegmentPlayer` (см. §5) для озвучки
  историй.

### 6.3. Drift-чувствительные поверхности
- **Доменные порты** (`AudioRepository` / `AudioModelRepository`) — NFR-4 SRS-001
  = drift-запрет. Любое изменение сигнатуры → отдельная drift-задача.
- **`SegmentPlayer.playSegments` сигнатура** — фиксируется здесь (§5). Изменение
  после старта E08/E09 = drift.
- **`Segment` sealed-тип** — фиксируется при миграции (домен или data-слой).

---

## 7. Риски (перенесены/детализированы из PRD-003 §8)

| Код | Риск | Mitigation в SRS-003 |
|---|---|---|
| R1 | native-memory-risk (TTS LRU ~450 MB + ASR ~375 MB → OOM) | FR-3 (maxSize=3 + eviction), FR-10 (MemoryChecker hard block), NFR-2 |
| R2 | latency-risk (нативная init модели секунды) | FR-3 (resident cache), NFR-4 (≤200 мс на resident-языке) |
| R3 | JNI-stability-risk (некорректный release native resources) | FR-15 (неблокирующий stop), NFR-3 (graceful degradation), NFR-5 (cancellation safety) |
| R4 | download-reliability-risk (обрывы, partial-файлы) | FR-13 (3-retry + redirect + StatFs pre-check + delete partial on cancel) |
| R5 | shared-mutation-risk (SegmentPlayer общий E08/E09) | §5 API-контракт зафиксирован |

---

## 8. Traceability

| Артефакт | Тип | Source (derived_from) | Примечание |
|---|---|---|---|
| **SRS-003** | SRS | PRD-003 (artifact_id=474) | системная формализация PRD |
| FR-1..FR-17 | FR | PRD-003 (§4.1..§4.9) | each FR → PRD scope section + SRS-001 FR-7 (upstream port) |
| NFR-1..NFR-5 | NFR | PRD-003 (§6 SM-4, §8 R1..R5) | each NFR → SM/R в PRD |
| Доменные порты | — | SRS-001 (artifact_id=450), FR-7 + §5.8/§5.9 | upstream, regression-locked, НЕ меняется |
| SegmentPlayer контракт | — | SRS-003 §5 (этот документ) | shared-mutation-risk fix для E08/E09 |

UC/AC (saga-analyst) будут `derived_from` FR-1..FR-17. Dev-задачи (saga-planner)
будут `implements` FR/NFR; предполагается SCAFFOLD-задача для создания stub'ов
helper-классов (TtsEngineWrapper, AsrEngineWrapper, ResidentTtsCache,
SegmentPlayer, MemoryChecker, BluetoothAudioRouter, TtsModelManager,
AsrModelManager, Registry) до body-задач.

---

*Системный контракт фиксирует компонентную декомпозицию, FR/NFR с измеримыми
метриками и API-контракт SegmentPlayer. Доменные порты — факт из SRS-001 (E01).
Legacy-референс (TtsEngine 939 строк, AsrEngine, *ModelManager, *Registry,
BluetoothAudioRouter, SegmentPlayer, MemoryChecker) — рабочий источник логики;
сигнатуры verified по фактическому коду 2026-07-07.*

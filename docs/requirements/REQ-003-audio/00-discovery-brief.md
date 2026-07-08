# Discovery Brief — REQ-003 Аудио-адаптер: завершение SherpaAudioRepository

**Эпизод:** E03 (epic_id=87), проект GrammarMate (id=25)
**Волна:** 0 — Фундамент
**Стадия:** Discovery → Formalization
**Дата:** 2026-07-07
**Upstream:** SRS-001 (E01, artifact_id=450) — AudioRepository/AudioModelRepository порты зафиксированы.

---

## 1. Бизнес-цели

Завершить аудио-адаптер `SherpaAudioRepository` (в v2 — skeleton с TODOs: `speak`/`recognizeSpeech`/`isTtsAvailable` эмитят `Started→Failed(IllegalStateException)`). Реализовать TTS-синтез и ASR-распознавание через Sherpa-ONNX, сохранив доменный порт `AudioRepository` чистым (zero Sherpa leakage).

**Критичность:** все drill-эпики (E04 Training, E05 Verb, E06 Vocab, E07 Daily) используют TTS для озвучки карточек и ASR для голосового ввода. Без аудио — нет голосового режима, нет озвучки, нет фонового словаря (E08), нет story-narration (E09).

## 2. Классификация

`classification: tech-task` — инфраструктурный аудио-адаптер.

## 3. Комплексность

`complexity: L` (t-shirt). Причины:
- Sherpa-ONNX интеграция (native lib, JNI, model loading) — тяжёлая инфраструктура;
- резидентный LRU-кэш TTS-моделей (~450MB heap, 3 модели) для мгновенного переключения it↔ru;
- ASR с VAD (Silero), AudioRecord capture, whisper-модели (~375MB);
- SegmentPlayer (Mutex-serialized, pause/resume re-speaks) — shared с E08/E09;
- BluetoothAudioRouter (SCO<31 / communicationDevice≥31);
- fallback на Android TextToSpeech.

Risk-triggers:
- `native-memory-risk`: TTS LRU ~450MB native heap + ASR ~375MB → MemoryChecker обязателен;
- `latency-risk`: задержки инициализации модели разрушают UX; резидентный кэш обязателен.

## 4. Гипотезы

- H1: доменные порты `AudioRepository` (speak→Flow<AudioEvent>, recognizeSpeech→Flow<RecognitionEvent>) и `AudioModelRepository` уже зафиксированы в SRS-001 (E01) → adapter пишет имплементацию под известный контракт.
- H2: legacy `TtsEngine`, `AsrEngine`, `AsrModelManager`, `AsrModelRegistry`, `TtsModelManager`, `TtsModelRegistry`, `SegmentPlayer`, `BluetoothAudioRouter` — рабочий референс; логика переносится в v2 clean arch как `SherpaAudioRepository` + helper-классы.
- H3: Sherpa-ONNX local AAR уже подключён к проекту (`libs/sherpa-onnx-static-link-onnxruntime-1.12.40.aar`, saga note id=2).

## 5. Quality-gate чек-лист

- [ ] `SherpaAudioRepository.speak()` → `Flow<AudioEvent>` (Started/Progress/Completed/Failed) — реальный синтез через OfflineTts.
- [ ] `SherpaAudioRepository.recognizeSpeech()` → `Flow<RecognitionEvent>` — реальное распознавание через OfflineRecognizer + SileroVad.
- [ ] `isTtsAvailable(lang)` / `isAsrAvailable()` — file-manifest check (реальный).
- [ ] Резидентный LRU TTS-кэш (maxSize=3) — мгновенное переключение языков без реинициализации.
- [ ] ASR VAD-gated (Silero), AudioRecord capture.
- [ ] MemoryChecker pre-check перед load модели (~800MB ASR / ~150MB TTS свободно).
- [ ] BluetoothAudioRouter: SCO (API<31) / setCommunicationDevice (API≥31).
- [ ] SegmentPlayer: Mutex-serialized, pause/resume re-speaks current segment.
- [ ] RU text-scale (setRuTextScale).
- [ ] Fallback Android TextToSpeech если Sherpa model недоступна.
- [ ] `playSoundEffect(SoundEffect)` (SoundPool + res/raw mp3) — уже реализовано, сохранить.
- [ ] Доменный порт AudioRepository остаётся pure (no Sherpa imports) — проверить grep.

## 6. Open questions

- OQ-1: Sound packs (pre-rendered Ogg-Opus ZIP, ITALIAN_SHORT ~615MB) — входит в E03 или в E08 (bg-vocab)? Решение: в E08 (sound packs для bg-vocab). E03 только TTS/ASR/SoundEffect.
- OQ-2: AsrModelManager/TtsModelManager (download с 3-retry HTTP) — входит в E03? Да, модели должны скачиваться из UI (E13 Settings), но менеджер загрузки = E03.

## 7. Decision-matrix

Внутри-эпизодная развилка отсутствует — путь однозначный: завершить skeleton через Sherpa-ONNX.

## 8. Decision

```
decision: go
reasoning: E03 — разблокирует голосовой ввод и озвучку для всех drill-эпиков;
           skeleton уже есть (v2 SherpaAudioRepository), legacy-референс рабочий,
           Sherpa AAR подключён. Доменный порт готов (SRS-001 E01).
source: direct sponsor interaction (note id=28) + product-level discovery (note id=4)
coverage: 1.0
```

## 9. Затронутые проекты/репозитории

- `BaseGrammy` (repository_id=7), ветка `dev` / feature `feature/req-003-audio`.
- Затрагивает: `app/.../v2/core/data/audio/SherpaAudioRepository.kt` (завершение), новый `SherpaAudioModelRepository.kt` (если не существует), helpers (SegmentPlayer, BluetoothAudioRouter, MemoryChecker, AsrModelManager/Registry, TtsModelManager/Registry).

## 10. Verdict-блок

```
VERDICT: go | REASONING: разблокирует voice/TTS для drill-эпиков; skeleton+референс готовы | override? (y)
```

Спонсор подтвердил go на product-level discovery. E03 = часть одобренного плана Modified D'.

## 11. Failover

- saga-mcp tracker DB доступна.
- Subagents доступны.
- Source = direct sponsor + product discovery. Degraded не активен.

## 12. Shared-mutation-risk

`AudioRepository` порт (домен) стабилен (зафиксирован в SRS-001). Adapter `SherpaAudioRepository` — реализация; может меняться, но контракт AudioEvent/RecognitionEvent/SoundEffect immutable. E08 (bg-vocab) и E09 (story) зависят от SegmentPlayer — его интерфейс должен быть зафиксирован в SRS-003.

**Mitigation:** SegmentPlayer контракт в SRS-003; TTS/ASR state machines (Idle/Init/Ready/Speaking/Paused/Error) формализованы; MemoryChecker всегда pre-check.

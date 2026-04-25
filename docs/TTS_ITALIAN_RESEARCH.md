# Italian TTS Research for GrammarMate

## 1. Available Italian TTS Models for Sherpa-ONNX

Two Piper voices are available for Italian through the sherpa-onnx ecosystem:

### it_IT-paola (medium)

- **Architecture:** VITS (Piper)
- **Quality:** Medium
- **Download URL:** `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-it_IT-paola-medium.tar.bz2`
- **Estimated compressed size:** ~60-70 MB
- **Estimated extracted size:** ~80-90 MB
- **Speaker:** Female voice

### it_IT-riccardo (x_low)

- **Architecture:** VITS (Piper)
- **Quality:** Extra-low (smallest model, fastest inference)
- **Download URL:** `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-it_IT-riccardo-x_low.tar.bz2`
- **Estimated compressed size:** ~15-25 MB
- **Estimated extracted size:** ~20-30 MB
- **Speaker:** Male voice

### Model file structure (Piper/VITS)

Piper models packaged for sherpa-onnx contain:

```
vits-piper-it_IT-<speaker>-<quality>/
  model.onnx          -- ONNX inference model
  tokens.txt          -- Token vocabulary
  espeak-ng-data/     -- Phonemizer data directory
```

Note: Piper/VITS models do **not** have a `voices.bin` file (unlike Kokoro). Speaker identity is embedded in the model itself.

### Recommendation

**paola (medium)** is the recommended choice for GrammarMate. The medium quality provides acceptable speech clarity for a language-learning context. The x_low quality of riccardo would sound noticeably robotic and could hinder comprehension for learners.

---

## 2. Does Kokoro Multi-Lang Include Italian?

**No.** Kokoro multi-language models do not support Italian.

- **Kokoro v0_19** (currently used in GrammarMate): English only, 82M parameters.
- **Kokoro v1_0** (multi-lang, 53 speakers): Chinese + English only.
- **Kokoro v1_1** (multi-lang, 103 speakers): Chinese + English only.

There is no Kokoro model with Italian support as of the current sherpa-onnx releases. Italian TTS requires switching to the Piper/VITS architecture.

---

## 3. How the App Determines Active Language

GrammarMate tracks the active language through `selectedLanguageId` in the UI state:

| Component | Role |
|-----------|------|
| `TrainingUiState.selectedLanguageId` | Current language, defaults to `"en"` |
| `TrainingViewModel.selectLanguage(languageId)` | Sets active language (line ~275) |
| `LessonStore` | Seeds two languages at first launch: `"en" -> "English"` and `"it" -> "Italian"` |
| `Models.kt` | `Language(id, displayName)` data class; `VocabEntry`, `Lesson`, `TrainingProgress` all carry `languageId` |
| `LessonStore.languageDir(languageId)` | Per-language data directories |

The language selection already flows through the entire data layer. TTS is the only subsystem that does not yet use it -- both `TtsEngine` and `TtsModelManager` are hardcoded to the English Kokoro model.

---

## 4. Recommended Approach for English + Italian TTS

### The core challenge

`TtsEngine` currently uses the Kokoro-specific API:

```kotlin
OfflineTtsModelConfig(
    kokoro = OfflineTtsKokoroModelConfig(
        model = ..., voices = ..., tokens = ..., dataDir = ...
    ),
    ...
)
```

Italian Piper/VITS models require a different config path:

```kotlin
OfflineTtsModelConfig(
    vits = OfflineTtsVitsModelConfig(
        model = ..., tokens = ..., dataDir = ...
    ),
    ...
)
```

These are different fields on the same `OfflineTtsModelConfig` class, but they require different file layouts and different config objects.

### Recommended architecture

**Option A: Two engine instances (simpler, recommended)**

Create separate `OfflineTts` instances for each language. At any time, only one is initialized and loaded in memory.

```
TtsEngine
  - languageId: String
  - engines: Map<String, OfflineTts>
  - speak(text) -> delegates to engines[languageId]
```

- `TtsModelManager` gains a per-language model directory and download URL map.
- On language switch, release the current engine and initialize the other.
- Each language's model is downloaded on demand (when the user first switches to that language and triggers TTS).
- Only one model lives in memory at a time (~80-350 MB of ONNX runtime memory).

**Option B: Abstract TTS engine interface (more flexible, more code)**

Define a `TtsProvider` interface with `initialize()`, `speak()`, `stop()`, `release()`. Create `KokoroTtsProvider` and `PiperTtsProvider` implementations. The factory selects based on language.

This is cleaner architecturally but requires more refactoring for a two-language app. Recommended only if more languages are planned.

### Model management changes

`TtsModelManager` would need:

- A map of language IDs to model directories and download URLs:
  ```kotlin
  "en" -> ModelInfo("tts/kokoro-en-v0_19", KOKORO_URL, ModelType.KOKORO)
  "it" -> ModelInfo("tts/vits-piper-it_IT-paola-medium", PIPER_IT_URL, ModelType.VITS_PIPER)
  ```
- Per-language `isModelReady()` and `download()` methods.
- The extraction logic stays the same (both are tar.bz2 archives with a top-level directory to strip).

### TtsEngine changes

- Accept `languageId` parameter in `initialize()` and `speak()`.
- Build the correct `OfflineTtsModelConfig` based on model type (Kokoro vs VITS).
- The `speak()` / `AudioTrack` streaming logic is identical for both -- the difference is only in config construction.

---

## 5. Estimated Download Sizes

| Model | Compressed | Extracted | Temporary Peak |
|-------|-----------|-----------|----------------|
| Kokoro English (existing) | ~350 MB | ~300 MB | ~650 MB |
| Piper Italian (paola-medium) | ~65 MB | ~85 MB | ~150 MB |
| **Total for both** | **~415 MB** | **~385 MB** | -- |

Notes:
- Temporary peak is compressed + extracted coexisting during extraction.
- The Italian model is much smaller than the English Kokoro model (~5x smaller compressed).
- Users who only study one language only download that language's model.
- The 700 MB minimum storage check in `TtsModelManager` covers both models comfortably.
- After extraction, the `.tar.bz2` archive is deleted, freeing the compressed size.

---

## Summary

| Question | Answer |
|----------|--------|
| Kokoro supports Italian? | No -- Chinese + English only |
| Best Italian option | Piper `it_IT-paola-medium` via VITS architecture |
| Download size (Italian only) | ~65 MB compressed |
| Download size (both languages) | ~415 MB compressed |
| Code changes needed | TtsModelManager: per-language model map. TtsEngine: dual config support (Kokoro + VITS). Both already receive language context from the app's language selection system. |
| Risk | Low. Same sherpa-onnx dependency, different config objects. No new native libraries needed. |

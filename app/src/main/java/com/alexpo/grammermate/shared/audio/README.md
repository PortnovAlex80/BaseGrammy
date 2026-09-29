# Shared Audio

## What
Manages all audio: TTS playback, ASR speech recognition, SoundPool effects,
and model download lifecycle. Owns the `StateFlow<AudioState>` which is combined
with core state in TrainingViewModel to produce the final TrainingUiState.

## API Surface

- **AudioCoordinator** — Public audio facade and the only owner of `StateFlow<AudioState>`.
  Constructor takes `TrainingStateAccess`, `Application`, `CoroutineScope`, `AppConfigStore`.
  Key methods:
  - Sound effects: `playSuccessSound()`, `playErrorSound()`
  - TTS playback: `onTtsSpeak(text, speed?)`, `stopTts()`, `setTtsSpeed()`, `setRuTextScale()`
  - TTS downloads: `startTtsDownload()`, `startTtsDownloadForLanguage(langId)`,
    `startBackgroundTtsDownload()`, `checkTtsModel()`, `checkAllTtsModels()`
  - ASR: `startOfflineRecognition(onResult)`, `stopAsr()`, `setUseOfflineAsr(enabled)`
  - ASR downloads: `startAsrDownload()`, `checkAsrModel()`
  - Config: `setVoiceAutoStart(enabled)` (persists to AppConfigStore)
  - Lifecycle: `startTtsStateCollection()`, `release()`
  - Metered network: `confirmTtsDownloadOnMetered()`, `dismissMeteredWarning()`
- **TtsModelDownloadController** — Owns TTS transfer jobs, metered request identity,
  readiness probes, and foreground/background scheduling. It publishes updates through
  the coordinator; it does not own another audio state flow.

## State owned
- `StateFlow<AudioState>` — the single source of truth for all audio state
  (TTS state, ASR state, download states, model readiness, metered network flags)
- Exposes engines publicly: `ttsEngine`, `ttsModelManager`, `asrModelManager`, `asrEngine`

## Dependencies
- Data: TtsProvider, TtsEngine, TtsModelManager, TtsModelRegistry, AsrEngine,
  AsrModelManager, AppConfigStore
- Feature: TrainingStateAccess interface (reads selectedLanguageId)
- Android: SoundPool, CoroutineScope, Application context

## Edit scope warnings
- AudioCoordinator owns its StateFlow -- do not duplicate audio fields in core TrainingUiState
- TTS download jobs live in `TtsModelDownloadController`; all TTS model-file writers must
  pass through its foreground/background scheduler. ASR jobs remain in `AudioCoordinator`.
- TTS engine auto-recovery from ERROR/IDLE state is wired into download completion callbacks
- release() must be called on ViewModel onCleared to free SoundPool, TTS, and ASR resources
- SoundPool load is async (onLoadCompleteListener) -- sounds may not play on first frame

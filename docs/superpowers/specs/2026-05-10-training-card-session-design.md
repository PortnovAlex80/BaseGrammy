# TrainingCardSession Reusable Template — Design Spec

**Date:** 2026-05-10
**Status:** Approved
**Depends on:** Verb Drill mode (feature/verb-drill)

## Overview

Extract the training card UI from GrammarMateApp.kt into a reusable composable component with customization slots. Both standard training and VerbDrill (and future modes) use this component. The difference between modes is only card selection/filtering logic, not the card training UI.

## Core Interface: CardSessionContract

```kotlin
interface SessionCard {
    val id: String
    val promptRu: String
    val acceptedAnswers: List<String>
}

data class SessionProgress(val current: Int, val total: Int)
data class AnswerResult(val correct: Boolean, val displayAnswer: String, val hintShown: Boolean = false)
data class InputModeConfig(val availableModes: Set<InputMode>, val defaultMode: InputMode, val showInputModeButtons: Boolean)

interface CardSessionCapabilities {
    val supportsTts: Boolean get() = false
    val supportsVoiceInput: Boolean get() = false
    val supportsWordBank: Boolean get() = false
    val supportsFlagging: Boolean get() = false
    val supportsNavigation: Boolean get() = false
    val supportsPause: Boolean get() = false
}

interface CardSessionContract : CardSessionCapabilities {
    val currentCard: SessionCard?
    val progress: SessionProgress
    val isComplete: Boolean
    val inputText: String
    val inputModeConfig: InputModeConfig
    val lastResult: AnswerResult?
    val sessionActive: Boolean
    fun onInputChanged(text: String)
    fun submitAnswer(): AnswerResult?
    fun showAnswer(): String?
    fun nextCard()
    fun prevCard()
    // Optional capabilities with default no-op implementations:
    fun getWordBankWords(): List<String> = emptyList()
    fun selectWordFromBank(word: String) {}
    fun removeLastSelectedWord() {}
    fun speakTts() {}
    fun stopTts() {}
    fun flagCurrentCard() {}
    fun unflagCurrentCard() {}
    fun isCurrentCardFlagged(): Boolean = false
    fun hideCurrentCard() {}
    fun exportFlaggedCards(): String? = null
    fun togglePause() {}
    fun requestExit() {}
    fun requestNextBatch() {}
}
```

## TrainingCardSession Composable

```kotlin
@Composable
fun TrainingCardSession(
    contract: CardSessionContract,
    header: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    cardContent: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    inputControls: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    resultContent: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    navigationControls: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    completionScreen: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    progressIndicator: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    onExit: () -> Unit,
    onComplete: () -> Unit = {},
    modifier: Modifier = Modifier
)
```

Customization slots (all optional lambdas, all have sensible defaults):
- **header** — top area. Default: prompt text + tense label. Override: VerbDrill shows group/tense labels.
- **cardContent** — card display. Default: Card with Russian text + TTS button. TTS hidden when supportsTts=false.
- **inputControls** — input area. Default: text field + word bank + input mode buttons + submit. Word bank/voice hidden based on capabilities.
- **resultContent** — answer feedback. Default: Correct/Incorrect + answer text + TTS replay.
- **navigationControls** — bottom buttons. Default: prev/pause/exit/next. VerbDrill overrides with minimal back button.
- **completionScreen** — shown on isComplete. Default: congratulations emoji + stats. VerbDrill overrides with stats + "Next Batch" + "Exit".
- **progressIndicator** — progress bar. Default: DrillProgressRow. VerbDrill overrides with simple LinearProgressIndicator.

## Adapters

- `TrainingCardSessionProvider` — wraps TrainingViewModel. Supports all capabilities (TTS, ASR, word bank, flagging, navigation, pause).
- `VerbDrillCardSessionProvider` — wraps VerbDrillViewModel. Keyboard-only, no TTS/ASR/word bank. Has custom completionScreen and navigationControls.

## Data Model Changes

- `SentenceCard` adds `: SessionCard` (fields already match)
- `VerbDrillCard` adds `: SessionCard` with `override val acceptedAnswers get() = listOf(answer)`

## New Files

| File | Purpose |
|------|---------|
| `data/CardSessionContract.kt` | Interface, SessionCard, SessionProgress, AnswerResult, InputModeConfig |
| `ui/TrainingCardSession.kt` | Main composable + scope + default implementations |
| `ui/TrainingCardSessionProvider.kt` | Adapter for standard training |
| `ui/VerbDrillCardSessionProvider.kt` | Adapter for VerbDrill |

## Modified Files

| File | Change |
|------|--------|
| `data/Models.kt` | Add `: SessionCard` to SentenceCard |
| `data/VerbDrillCard.kt` | Add `: SessionCard` to VerbDrillCard |
| `ui/GrammarMateApp.kt` | Replace TrainingScreen with TrainingCardSession + provider |
| `ui/VerbDrillScreen.kt` | Replace VerbDrillSessionScreen with TrainingCardSession + provider |

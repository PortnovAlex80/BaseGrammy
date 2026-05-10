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
- `VerbDrillCardSessionProvider` — wraps VerbDrillViewModel. Full capabilities: TTS, voice input, word bank, flagging, navigation. No pause.

## Canonical Training Screen Layout

This is the standard layout that ALL training modes must replicate. The only difference between modes is card source/selection logic, not the UI.

```
┌──────────────────────────────────────────┐
│  GrammarMate                    ⚙ (настр) │
│  Present Perfect                          │ ← tense label (conditional)
│  он готов (essere pronto)                 │ ← clean prompt (hints stripped)
│  ████████░░░ 3/10    (◎ 35wpm)           │ ← progress bar + speedometer
│  ┌────────────────────────────────────┐   │
│  │ RU                       🔊       │   │ ← card with TTS (4 states)
│  │ он готов (essere pronto)          │   │
│  └────────────────────────────────────┘   │
│  ┌─────────────────────────────────── 🎤│  ← input field + mic icon
│  │ Your translation                  │   │
│  └────────────────────────────────────┘   │
│  [🎤 Voice] [⌨ Keyboard] [📖 WordBank]   │ ← input mode selector
│  [👁 Show] [⚠ Report]                     │ ← show answer + flag/report
│  [        Check        ]                  │ ← submit button
│                                           │
│  ✓ Correct  🔊 Answer: Lui è pronto.     │ ← result with TTS replay
│                                           │
│  [◀]  [⏸]  [⏹]  [▶]                     │ ← navigation (prev/pause/exit/next)
└──────────────────────────────────────────┘
```

**Elements (top to bottom):**
1. **Title bar** — app name + settings gear (VerbDrill omits gear)
2. **Tense label** — card tense, primary/green color, 13sp semi-bold (conditional: only if card has tense)
3. **Clean prompt** — promptRu with parenthetical hints stripped via regex `\s*\([^)]+\)`
4. **Progress bar + speedometer** — green bar (70% width) with text overlay + circular wpm arc (30% width, 44dp, color by speed: red<=20, yellow<=40, green>40)
5. **Card prompt** — Material Card: "RU" label + full promptRu (20sp, semi-bold, WITH parenthetical hints) + TTS speaker button (4 states: speaking/stop, initializing/spinner, error/warning, idle/volume)
6. **Input field** — OutlinedTextField "Your translation" + Mic trailing icon (when supportsVoiceInput)
7. **Word bank** — FlowRow of FilterChip words + Undo (when mode is WORD_BANK)
8. **Input mode selector** — 3 FilledTonalIconButtons: Voice, Keyboard, WordBank (when multiple modes available)
9. **Show answer + Report** — Eye icon (reveals answer) + Warning icon (opens bottom sheet: flag/unflag, hide, export, copy)
10. **Check button** — full width, submits answer
11. **Result** — Correct (green) / Incorrect (red) + TTS replay button + "Answer: ..." text
12. **Navigation** — styled NavIconButtons (44dp, surfaceVariant, 3dp accent bar): Prev, Pause/Play, Exit (with confirmation dialog), Next

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

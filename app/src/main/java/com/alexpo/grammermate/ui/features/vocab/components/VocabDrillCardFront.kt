package com.alexpo.grammermate.ui.features.vocab.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.ThemeMode
import com.alexpo.grammermate.ui.GrammarMateTheme
import com.alexpo.grammermate.ui.MasteryGreen
import com.alexpo.grammermate.ui.VocabCorrectBackground
import com.alexpo.grammermate.ui.VocabIncorrectBackground
import com.alexpo.grammermate.ui.designsystem.foundation.GrammarMateRadii
import com.alexpo.grammermate.ui.designsystem.foundation.scaledSp

// =====================================================================
// Vocabulary drill flashcard front face — feature organism (vocab drill
// owner)
// =====================================================================

/**
 * UI-owned speaker state for [VocabDrillCardFront]. The integration boundary
 * maps the engine state (`data.TtsState`) onto it; the organism stays free of
 * data-layer imports (P2 `TtsButtonState` precedent).
 *
 * Deliberately NOT `TtsButtonState` (rule-4 verdict, P4c): the vocabulary
 * speaker is a REPLAY affordance — while the engine is [Speaking] the button
 * still shows `VolumeUp` in `colorScheme.primary` (`vocab_content_desc_speaking`)
 * and clicking it speaks the word again — whereas the training atom's speaking
 * state is a STOP affordance (`StopCircle`, error tint, `content_desc_stop`).
 * Adopting the atom would change icon, tint and a11y behavior — a visual/
 * behavior change, prohibited in a parity slice (same verdict class as the
 * P4a VerbReferenceBottomSheet and P4b daily speaker findings).
 */
sealed class VocabSpeakerState {
    /** Engine idle/ready/paused — play (replay) affordance. */
    object Idle : VocabSpeakerState()

    /** Engine/model is warming up — indeterminate spinner. */
    object Initializing : VocabSpeakerState()

    /** Currently speaking — vocabulary semantics keep the replay icon. */
    object Speaking : VocabSpeakerState()

    /** Engine failure; [reason] (if any) is shown in an error tooltip. */
    data class Error(val reason: String? = null) : VocabSpeakerState()
}

/**
 * UI-owned voice-recognition outcome for [VocabDrillCardFront]; mirrors the
 * scheduling-layer `data.VoiceResult` 1:1 (Correct / Wrong / Skipped). The
 * boundary maps it, so the organism imports no data-layer types.
 */
enum class VocabVoiceOutcome { Correct, Wrong, Skipped }

/**
 * Feature-owned vocabulary flashcard FRONT face (unflipped card): reference
 * badges, the main prompt line, the optional speaker button, and the
 * voice-input section (tap-to-speak microphone or the voice-result feedback
 * card).
 *
 * Display-only by contract (plan §4): it renders pre-computed values and
 * never imports ViewModels, session contracts, data-layer types, or the hint
 * calculator. The integration boundary (`VocabDrillCardScreen`) owns:
 * the direction-based prompt derivation — IT_TO_RU shows the raw Italian
 * word, RU_TO_IT runs `HintCalculator.calculateEffectiveHints` over the
 * Russian meaning (plan §4 keeps hint calculation out of display); the
 * `data.TtsState → VocabSpeakerState` mapping and the IT_TO_RU-only speaker
 * visibility (passed as a null [speakerState]); the `data.VoiceResult →
 * VocabVoiceOutcome` mapping; and the `ttsSpeak` / `voiceStart` audit
 * logging plus the recognizer launch itself (ledger row 3, D-A third path).
 *
 * Parity anchors preserved verbatim from the pre-extraction inline
 * `VocabDrillCardFront`:
 * - card `RoundedCornerShape(16.dp)` (no radius token exists for 16.dp);
 *   container `VocabCorrectBackground`/`VocabIncorrectBackground` @ 0.7f
 *   alpha after a completed correct/wrong voice attempt, else
 *   `surfaceVariant` @ 0.7f;
 * - badges row [PosBadge] + [RankBadge] (shared vocab atoms), 24.dp centered
 *   column;
 * - prompt line `scaledSp(32f, textScale)` Bold centered (was
 *   `(32f * textScale).sp` — identical math);
 * - speaker button enabled except while Initializing; Speaking keeps the
 *   replay `VolumeUp`/primary rendering (see [VocabSpeakerState]);
 * - voice section: `vocab_tap_to_speak` labelMedium @ 0.5f + 72.dp
 *   `FilledTonalIconButton` with a 36.dp mic; after completion the
 *   [VoiceResultFeedback] card ([GrammarMateRadii.small] radius, the token's
 *   exact 12.dp value).
 *
 * @param pos raw part-of-speech key for the badge (`"nouns"`, …).
 * @param rank word frequency rank for the badge.
 * @param displayText boundary-derived prompt (word or hint-calculated text).
 * @param speakerState mapped engine state, or null when the direction hides
 *   the speaker button (RU_TO_IT).
 * @param onSpeak speaker tap; boundary logs `ttsSpeak` audit and speaks the
 *   Italian word.
 * @param voiceCompleted whether voice recognition already finished for this
 *   card (switches mic section to feedback; tints the card).
 * @param onStartVoice microphone tap; boundary logs `voiceStart` audit and
 *   launches the recognizer (ledger row 3).
 * @param voiceOutcome mapped voice result, null when none yet.
 * @param voiceAttempts failed attempt count (drives the try-again line).
 * @param voiceRecognizedText what the recognizer heard (quoted in feedback).
 * @param textScale user RU text-scale factor.
 * @param modifier applied to the root [Card].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabDrillCardFront(
    pos: String,
    rank: Int,
    displayText: String,
    speakerState: VocabSpeakerState?,
    onSpeak: () -> Unit,
    voiceCompleted: Boolean,
    onStartVoice: () -> Unit,
    voiceOutcome: VocabVoiceOutcome?,
    voiceAttempts: Int,
    voiceRecognizedText: String?,
    textScale: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                voiceCompleted && voiceOutcome == VocabVoiceOutcome.Correct ->
                    VocabCorrectBackground.copy(alpha = 0.7f) // light green tint
                voiceCompleted && voiceOutcome == VocabVoiceOutcome.Wrong ->
                    VocabIncorrectBackground.copy(alpha = 0.7f) // light red tint
                else ->
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // POS badge + rank -- always visible (reference data, not hints)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PosBadge(pos = pos)
                RankBadge(rank = rank)
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Main word display (direction/hint derivation owned by the boundary)
            Text(
                text = displayText,
                fontSize = scaledSp(32f, textScale),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // TTS button (only for IT_TO_RU, speak the Italian word)
            if (speakerState != null) {
                Spacer(modifier = Modifier.height(8.dp))
                IconButton(
                    onClick = onSpeak,
                    enabled = speakerState !is VocabSpeakerState.Initializing
                ) {
                    when (speakerState) {
                        is VocabSpeakerState.Speaking -> Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = stringResource(R.string.vocab_content_desc_speaking),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        is VocabSpeakerState.Initializing -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        is VocabSpeakerState.Error -> {
                            val reason = speakerState.reason
                            val isOom = reason?.contains("memory", ignoreCase = true) == true
                            val errorIcon = if (isOom) Icons.Default.Warning else Icons.Default.ReportProblem
                            if (reason != null) {
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                    tooltip = { PlainTooltip { Text(reason) } },
                                    state = rememberTooltipState()
                                ) {
                                    Icon(errorIcon, stringResource(R.string.vocab_content_desc_tts_error), tint = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                Icon(errorIcon, stringResource(R.string.vocab_content_desc_tts_error), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        else -> Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = stringResource(R.string.vocab_content_desc_listen)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Centered microphone button ──
            if (!voiceCompleted) {
                // Prompt text
                Text(
                    text = stringResource(R.string.vocab_tap_to_speak),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Wrong attempts 1–2 keep the mic active, so their feedback
                // must not be hidden behind the completed-only branch.
                if (voiceOutcome == VocabVoiceOutcome.Wrong) {
                    VoiceResultFeedback(
                        voiceOutcome = voiceOutcome,
                        voiceAttempts = voiceAttempts,
                        voiceRecognizedText = voiceRecognizedText,
                        voiceCompleted = false
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                FilledTonalIconButton(
                    onClick = onStartVoice,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = stringResource(R.string.vocab_content_desc_voice_input),
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else {
                // Voice completed — show result feedback
                VoiceResultFeedback(
                    voiceOutcome = voiceOutcome,
                    voiceAttempts = voiceAttempts,
                    voiceRecognizedText = voiceRecognizedText,
                    voiceCompleted = voiceCompleted
                )
            }
        }
    }
}

// -- Voice Result Feedback --

@Composable
private fun VoiceResultFeedback(
    voiceOutcome: VocabVoiceOutcome?,
    voiceAttempts: Int,
    voiceRecognizedText: String?,
    voiceCompleted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GrammarMateRadii.small),
        colors = CardDefaults.cardColors(
            containerColor = when (voiceOutcome) {
                VocabVoiceOutcome.Correct -> VocabCorrectBackground // light green
                VocabVoiceOutcome.Wrong -> VocabIncorrectBackground   // light red
                VocabVoiceOutcome.Skipped -> MaterialTheme.colorScheme.surfaceVariant
                null -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Recognized text
            if (!voiceRecognizedText.isNullOrBlank()) {
                Text(
                    text = "\"$voiceRecognizedText\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Result indicator
            when (voiceOutcome) {
                VocabVoiceOutcome.Correct -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MasteryGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.vocab_voice_correct),
                            fontWeight = FontWeight.SemiBold,
                            color = MasteryGreen
                        )
                    }
                }
                VocabVoiceOutcome.Wrong -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        if (voiceCompleted) {
                            Text(
                                text = stringResource(R.string.vocab_voice_moving_on),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.vocab_voice_try_again, voiceAttempts),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                VocabVoiceOutcome.Skipped -> {
                    Text(
                        text = stringResource(R.string.vocab_voice_skipped),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                null -> { /* no result yet */ }
            }
        }
    }
}

// =====================================================================
// Preview fixtures — pure data only: no ViewModel, filesystem, installed
// pack or speech engine (plan §7). Each wraps the real GrammarMateTheme
// facade with an explicit ThemeMode, matching the P1/P2 preview convention.
// ======================================================================

@Preview(name = "Vocab card front — fresh, speaker idle (light)", showBackground = true, widthDp = 360, heightDp = 420)
@Composable
fun VocabCardFrontFreshLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        VocabDrillCardFront(
            pos = "verbs",
            rank = 42,
            displayText = "essere",
            speakerState = VocabSpeakerState.Idle,
            onSpeak = {},
            voiceCompleted = false,
            onStartVoice = {},
            voiceOutcome = null,
            voiceAttempts = 0,
            voiceRecognizedText = null,
            textScale = 1.0f
        )
    }
}

@Preview(name = "Vocab card front — speaker speaking, replay (light)", showBackground = true, widthDp = 360, heightDp = 420)
@Composable
fun VocabCardFrontSpeakingLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        VocabDrillCardFront(
            pos = "nouns",
            rank = 7,
            displayText = "casa",
            speakerState = VocabSpeakerState.Speaking,
            onSpeak = {},
            voiceCompleted = false,
            onStartVoice = {},
            voiceOutcome = null,
            voiceAttempts = 0,
            voiceRecognizedText = null,
            textScale = 1.0f
        )
    }
}

@Preview(name = "Vocab card front — speaker initializing (light)", showBackground = true, widthDp = 360, heightDp = 420)
@Composable
fun VocabCardFrontInitializingLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        VocabDrillCardFront(
            pos = "nouns",
            rank = 7,
            displayText = "casa",
            speakerState = VocabSpeakerState.Initializing,
            onSpeak = {},
            voiceCompleted = false,
            onStartVoice = {},
            voiceOutcome = null,
            voiceAttempts = 0,
            voiceRecognizedText = null,
            textScale = 1.0f
        )
    }
}

@Preview(name = "Vocab card front — speaker error tooltip (light)", showBackground = true, widthDp = 360, heightDp = 420)
@Composable
fun VocabCardFrontSpeakerErrorLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        VocabDrillCardFront(
            pos = "nouns",
            rank = 7,
            displayText = "casa",
            speakerState = VocabSpeakerState.Error(reason = "TTS engine not available"),
            onSpeak = {},
            voiceCompleted = false,
            onStartVoice = {},
            voiceOutcome = null,
            voiceAttempts = 0,
            voiceRecognizedText = null,
            textScale = 1.0f
        )
    }
}

@Preview(name = "Vocab card front — voice correct (light)", showBackground = true, widthDp = 360, heightDp = 420)
@Composable
fun VocabCardFrontVoiceCorrectLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        VocabDrillCardFront(
            pos = "verbs",
            rank = 42,
            displayText = "essere",
            speakerState = VocabSpeakerState.Idle,
            onSpeak = {},
            voiceCompleted = true,
            onStartVoice = {},
            voiceOutcome = VocabVoiceOutcome.Correct,
            voiceAttempts = 0,
            voiceRecognizedText = "essere",
            textScale = 1.0f
        )
    }
}

@Preview(name = "Vocab card front — voice wrong, try again (light)", showBackground = true, widthDp = 360, heightDp = 420)
@Composable
fun VocabCardFrontVoiceTryAgainLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        VocabDrillCardFront(
            pos = "verbs",
            rank = 42,
            displayText = "essere",
            speakerState = VocabSpeakerState.Idle,
            onSpeak = {},
            voiceCompleted = false,
            onStartVoice = {},
            voiceOutcome = VocabVoiceOutcome.Wrong,
            voiceAttempts = 1,
            voiceRecognizedText = "essero",
            textScale = 1.0f
        )
    }
}

@Preview(name = "Vocab card front — voice wrong, moving on (light)", showBackground = true, widthDp = 360, heightDp = 420)
@Composable
fun VocabCardFrontVoiceMovingOnLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        VocabDrillCardFront(
            pos = "verbs",
            rank = 42,
            displayText = "essere",
            speakerState = VocabSpeakerState.Idle,
            onSpeak = {},
            voiceCompleted = true,
            onStartVoice = {},
            voiceOutcome = VocabVoiceOutcome.Wrong,
            voiceAttempts = 3,
            voiceRecognizedText = "essero",
            textScale = 1.0f
        )
    }
}

@Preview(name = "Vocab card front — voice skipped (light)", showBackground = true, widthDp = 360, heightDp = 420)
@Composable
fun VocabCardFrontVoiceSkippedLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        VocabDrillCardFront(
            pos = "verbs",
            rank = 42,
            displayText = "essere",
            speakerState = VocabSpeakerState.Idle,
            onSpeak = {},
            voiceCompleted = true,
            onStartVoice = {},
            voiceOutcome = VocabVoiceOutcome.Skipped,
            voiceAttempts = 0,
            voiceRecognizedText = null,
            textScale = 1.0f
        )
    }
}

@Preview(name = "Vocab card front — RU_TO_IT, no speaker (light)", showBackground = true, widthDp = 360, heightDp = 420)
@Composable
fun VocabCardFrontRuToItLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        VocabDrillCardFront(
            pos = "adjectives",
            rank = 310,
            displayText = "быть; существовать",
            speakerState = null,
            onSpeak = {},
            voiceCompleted = false,
            onStartVoice = {},
            voiceOutcome = null,
            voiceAttempts = 0,
            voiceRecognizedText = null,
            textScale = 1.0f
        )
    }
}

@Preview(name = "Vocab card front — long IT word (dark)", showBackground = true, widthDp = 360, heightDp = 460)
@Composable
fun VocabCardFrontLongDarkPreview() {
    GrammarMateTheme(themeMode = ThemeMode.DARK) {
        VocabDrillCardFront(
            pos = "nouns",
            rank = 1200,
            displayText = "intrattenimento",
            speakerState = VocabSpeakerState.Idle,
            onSpeak = {},
            voiceCompleted = false,
            onStartVoice = {},
            voiceOutcome = null,
            voiceAttempts = 0,
            voiceRecognizedText = null,
            textScale = 1.0f
        )
    }
}

@Preview(name = "Vocab card front — text scale 1.5 + system font 1.5x", showBackground = true, widthDp = 360, heightDp = 560, fontScale = 1.5f)
@Composable
fun VocabCardFrontFontScalePreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        VocabDrillCardFront(
            pos = "verbs",
            rank = 42,
            displayText = "essere",
            speakerState = VocabSpeakerState.Idle,
            onSpeak = {},
            voiceCompleted = false,
            onStartVoice = {},
            voiceOutcome = null,
            voiceAttempts = 0,
            voiceRecognizedText = null,
            textScale = 1.5f
        )
    }
}

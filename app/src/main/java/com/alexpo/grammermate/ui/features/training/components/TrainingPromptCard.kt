package com.alexpo.grammermate.ui.features.training.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.data.ThemeMode
import com.alexpo.grammermate.ui.GrammarMateTheme
import com.alexpo.grammermate.ui.designsystem.atoms.TtsButtonState
import com.alexpo.grammermate.ui.designsystem.atoms.TtsSpeakerButton
import com.alexpo.grammermate.ui.designsystem.foundation.PromptTextScale
import com.alexpo.grammermate.ui.designsystem.foundation.scaledSp

// =====================================================================
// Training prompt card — feature organism (training feature owner)
// =====================================================================

/**
 * Feature-owned training prompt organism: the "RU" prompt card shown for
 * non-drill training modes (NORMAL, BOSS, ELITE, DAILY_TRANSLATE, ...).
 *
 * Display-only by contract (plan §4): it renders pre-computed values and never
 * imports ViewModels, session contracts, or data-layer types. The integration
 * boundary `TrainingScreen.kt::CardPrompt` owns everything effectful or
 * business-specific — hint calculation (`HintCalculator`), the
 * [com.alexpo.grammermate.data.SentenceCard] `contextRu` extraction, TTS state
 * mapping (`data.TtsState` -> [TtsButtonState]) and the TTS audit log — and
 * passes this composable narrow display inputs.
 *
 * Parity anchors preserved verbatim from the pre-extraction inline `CardPrompt`:
 * - `card_prompt_text` test tag stays on the prompt text node.
 * - [situationContext] (SentenceCard.contextRu) is visible independently of the
 *   graded hint ladder — rendered whenever non-null, even for blank strings.
 * - Font sizes use the [PromptTextScale] policy via [scaledSp]
 *   (prompt = 20f, context = 13f, multiplied by [textScale]).
 *
 * @param label small caption above the prompt (e.g. localized "RU").
 * @param displayPrompt hint-processed prompt text; the boundary supplies the
 *   localized no-cards fallback itself.
 * @param situationContext optional italic situation context line
 *   (SentenceCard.contextRu); always visible when non-null, never graded.
 * @param textScale the app RU text-scale factor (`ruTextScale`, 1.0f..2.0f).
 * @param audioState audio button presentation mapped by the boundary.
 * @param audioEnabled whether the audio button is clickable.
 * @param onAudioClick invoked on audio button click; audit logging stays with
 *   the boundary.
 */
@Composable
fun TrainingPromptCard(
    label: String,
    displayPrompt: String,
    situationContext: String?,
    textScale: Float,
    audioState: TtsButtonState,
    audioEnabled: Boolean,
    onAudioClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    modifier = Modifier.testTag("card_prompt_text"),
                    text = displayPrompt,
                    fontSize = scaledSp(PromptTextScale.prompt, textScale),
                    fontWeight = FontWeight.SemiBold
                )
                // Optional situation context (3rd CSV column): always visible —
                // it disambiguates the prompt (pronoun referent, ne antecedent,
                // discourse status) and is not part of the graded hint ladder.
                situationContext?.let { ctx ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ctx,
                        fontSize = scaledSp(PromptTextScale.context, textScale),
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TtsSpeakerButton(state = audioState, enabled = audioEnabled, onClick = onAudioClick)
        }
    }
}

// =====================================================================
// Preview fixtures — pure data only: no ViewModel, filesystem, installed
// pack or speech engine (plan §7). Each wraps the real GrammarMateTheme
// facade with an explicit ThemeMode, matching the P1 preview convention.
// ======================================================================

private const val PreviewShortRuPrompt = "русское слово 1"
private const val PreviewLongRuPrompt =
    "Я хотел бы записаться на приём к врачу на следующей неделе, если это возможно"
private const val PreviewLongItPrompt =
    "Vorrei prenotare un appuntamento dal medico la prossima settimana, se è possibile"
private const val PreviewItContext = "(risposta con il pronome di soggetto; contesto formale)"

@Preview(name = "Prompt card — Light", showBackground = true, widthDp = 360, heightDp = 180)
@Composable
fun TrainingPromptCardLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingPromptCard(
            label = "RU",
            displayPrompt = PreviewShortRuPrompt,
            situationContext = null,
            textScale = 1.0f,
            audioState = TtsButtonState.Idle,
            audioEnabled = true,
            onAudioClick = {}
        )
    }
}

@Preview(name = "Prompt card — Dark", showBackground = true, widthDp = 360, heightDp = 180)
@Composable
fun TrainingPromptCardDarkPreview() {
    GrammarMateTheme(themeMode = ThemeMode.DARK) {
        TrainingPromptCard(
            label = "RU",
            displayPrompt = PreviewShortRuPrompt,
            situationContext = null,
            textScale = 1.0f,
            audioState = TtsButtonState.Idle,
            audioEnabled = true,
            onAudioClick = {}
        )
    }
}

@Preview(name = "Prompt card — long RU prompt", showBackground = true, widthDp = 360, heightDp = 240)
@Composable
fun TrainingPromptCardLongRuPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingPromptCard(
            label = "RU",
            displayPrompt = PreviewLongRuPrompt,
            situationContext = null,
            textScale = 1.0f,
            audioState = TtsButtonState.Idle,
            audioEnabled = true,
            onAudioClick = {}
        )
    }
}

@Preview(name = "Prompt card — long IT prompt + context", showBackground = true, widthDp = 360, heightDp = 300)
@Composable
fun TrainingPromptCardLongItalianWithContextPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingPromptCard(
            label = "RU",
            displayPrompt = PreviewLongItPrompt,
            situationContext = PreviewItContext,
            textScale = 1.0f,
            audioState = TtsButtonState.Idle,
            audioEnabled = true,
            onAudioClick = {}
        )
    }
}

@Preview(name = "Prompt card — font scale 1.5x", showBackground = true, widthDp = 360, heightDp = 260)
@Composable
fun TrainingPromptCardFontScalePreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingPromptCard(
            label = "RU",
            displayPrompt = PreviewLongRuPrompt,
            situationContext = PreviewItContext,
            textScale = 1.5f,
            audioState = TtsButtonState.Idle,
            audioEnabled = true,
            onAudioClick = {}
        )
    }
}

@Preview(name = "Prompt card — audio idle", showBackground = true, widthDp = 360, heightDp = 180)
@Composable
fun TrainingPromptCardAudioIdlePreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingPromptCard(
            label = "RU",
            displayPrompt = PreviewShortRuPrompt,
            situationContext = null,
            textScale = 1.0f,
            audioState = TtsButtonState.Idle,
            audioEnabled = true,
            onAudioClick = {}
        )
    }
}

@Preview(name = "Prompt card — audio initializing", showBackground = true, widthDp = 360, heightDp = 180)
@Composable
fun TrainingPromptCardAudioInitializingPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingPromptCard(
            label = "RU",
            displayPrompt = PreviewShortRuPrompt,
            situationContext = null,
            textScale = 1.0f,
            audioState = TtsButtonState.Initializing,
            audioEnabled = true,
            onAudioClick = {}
        )
    }
}

@Preview(name = "Prompt card — audio speaking", showBackground = true, widthDp = 360, heightDp = 180)
@Composable
fun TrainingPromptCardAudioSpeakingPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingPromptCard(
            label = "RU",
            displayPrompt = PreviewShortRuPrompt,
            situationContext = null,
            textScale = 1.0f,
            audioState = TtsButtonState.Speaking,
            audioEnabled = true,
            onAudioClick = {}
        )
    }
}

@Preview(name = "Prompt card — audio error (tooltip reason)", showBackground = true, widthDp = 360, heightDp = 180)
@Composable
fun TrainingPromptCardAudioErrorPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingPromptCard(
            label = "RU",
            displayPrompt = PreviewShortRuPrompt,
            situationContext = null,
            textScale = 1.0f,
            audioState = TtsButtonState.Error(reason = "TTS engine out of memory"),
            audioEnabled = true,
            onAudioClick = {}
        )
    }
}

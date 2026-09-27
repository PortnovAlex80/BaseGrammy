package com.alexpo.grammermate.ui.features.daily.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.ThemeMode
import com.alexpo.grammermate.ui.GrammarMateTheme
import com.alexpo.grammermate.ui.designsystem.foundation.scaledSp

// =====================================================================
// Daily vocab flashcard display — feature organism (daily practice owner)
// =====================================================================

/**
 * Feature-owned daily-practice vocab flashcard display: the surfaceVariant
 * card shown for a `DailyBlockType.VOCAB` task (`ui/DailyPracticeScreen.kt`
 * `VocabFlashcardBlock`) — centered prompt, speaker + report controls, and
 * the revealed answer line.
 *
 * Display-only by contract (plan §4): it renders pre-computed values and
 * never imports ViewModels, session contracts, or data-layer types. The
 * integration boundary owns the direction-based prompt/answer derivation
 * (`VocabDrillDirection` mapping), the `showAnswer` flip state (also set by
 * the voice auto-rate path), the TTS/report audit logging and `onSpeak`
 * callback, the flag state read (`vm.reports.isDailyBadSentence`), and the
 * report/QR/export sheets that open from [onReportClick].
 *
 * Parity anchors preserved verbatim from the pre-extraction inline card:
 * - surfaceVariant card, 24.dp centered Column;
 * - prompt at `scaledSp(28f, textScale)` Bold (was `(28f * textScale).sp` —
 *   identical math; first adoption of the P1 scale token on this surface);
 * - speaker (`content_desc_listen`) + report (`content_desc_report_word`)
 *   IconButtons; report tint = `colorScheme.error` when flagged, else
 *   `onSurfaceVariant` — the flagged value is passed in, not read here;
 * - answer line rendered only when [answerText] is non-null (boundary maps
 *   the flip state to null = hidden): 8.dp spacer, `scaledSp(18f, textScale)`
 *   Medium, centered, `colorScheme.primary`.
 *
 * Deliberately NOT a `TrainingPromptCard` reuse and NOT a `ui/patterns`
 * candidate (rule-4 evidence, P4 slice 2): no label, no hint-ladder text,
 * no `card_prompt_text` tag, no `TtsSpeakerButton` state machine (the daily
 * speaker control is a stateless replay affordance, not a 4-state stop
 * button), different scale values (28f/18f vs the training 20f/13f).
 * `VocabDrillScreen`'s flashcard differs even more (voice-first front,
 * interval chips, 2×2 rating) — the P4 vocab slice judges its own surfaces.
 *
 * @param promptText direction-derived prompt (boundary-computed).
 * @param answerText direction-derived answer when revealed, null when hidden.
 * @param textScale user RU text-scale factor.
 * @param isFlagged current bad-sentence flag state (drives report tint).
 * @param onSpeakClick speaker tap; boundary logs `ttsSpeak` audit and speaks.
 * @param onReportClick report tap; boundary logs `report_sheet` audit and
 *   opens the sheet.
 * @param modifier applied to the root [Card].
 */
@Composable
fun DailyVocabCard(
    promptText: String,
    answerText: String?,
    textScale: Float,
    isFlagged: Boolean,
    onSpeakClick: () -> Unit,
    onReportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(promptText, fontSize = scaledSp(28f, textScale), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSpeakClick) { Icon(Icons.Default.VolumeUp, stringResource(R.string.content_desc_listen)) }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onReportClick) {
                    Icon(Icons.Default.ReportProblem, stringResource(R.string.content_desc_report_word), tint = if (isFlagged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (answerText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(answerText, fontSize = scaledSp(18f, textScale), fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// =====================================================================
// Preview fixtures — pure data only: no ViewModel, filesystem, installed
// pack or speech engine (plan §7). Each wraps the real GrammarMateTheme
// facade with an explicit ThemeMode, matching the P1/P2 preview convention.
// ======================================================================

@Preview(name = "Daily vocab card — front (light)", showBackground = true, widthDp = 360, heightDp = 200)
@Composable
fun DailyVocabCardFrontLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        DailyVocabCard(
            promptText = "casa",
            answerText = null,
            textScale = 1.0f,
            isFlagged = false,
            onSpeakClick = {},
            onReportClick = {}
        )
    }
}

@Preview(name = "Daily vocab card — revealed (light)", showBackground = true, widthDp = 360, heightDp = 240)
@Composable
fun DailyVocabCardRevealedLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        DailyVocabCard(
            promptText = "casa",
            answerText = "дом",
            textScale = 1.0f,
            isFlagged = false,
            onSpeakClick = {},
            onReportClick = {}
        )
    }
}

@Preview(name = "Daily vocab card — revealed, flagged (light)", showBackground = true, widthDp = 360, heightDp = 240)
@Composable
fun DailyVocabCardRevealedFlaggedLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        DailyVocabCard(
            promptText = "casa",
            answerText = "дом",
            textScale = 1.0f,
            isFlagged = true,
            onSpeakClick = {},
            onReportClick = {}
        )
    }
}

@Preview(name = "Daily vocab card — long content (dark)", showBackground = true, widthDp = 360, heightDp = 280)
@Composable
fun DailyVocabCardLongContentDarkPreview() {
    GrammarMateTheme(themeMode = ThemeMode.DARK) {
        DailyVocabCard(
            promptText = "intrattenimento",
            answerText = "развлечение, приятное проведение времени",
            textScale = 1.0f,
            isFlagged = false,
            onSpeakClick = {},
            onReportClick = {}
        )
    }
}

@Preview(name = "Daily vocab card — revealed, system font scale 1.5x", showBackground = true, widthDp = 360, heightDp = 320, fontScale = 1.5f)
@Composable
fun DailyVocabCardRevealedFontScalePreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        DailyVocabCard(
            promptText = "casa",
            answerText = "дом",
            textScale = 1.5f,
            isFlagged = false,
            onSpeakClick = {},
            onReportClick = {}
        )
    }
}

package com.alexpo.grammermate.ui.features.vocab.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.ThemeMode
import com.alexpo.grammermate.ui.GrammarMateTheme
import com.alexpo.grammermate.ui.MasteryGreen
import com.alexpo.grammermate.ui.VocabIntervalOrange

// =====================================================================
// Vocabulary SRS 2x2 interval-ladder rating — feature organism (vocab
// drill owner)
// =====================================================================

/**
 * UI-owned rating option for [VocabRatingLadder]. The integration boundary
 * maps it 1:1 (same name, same ordinal order) onto the scheduling-layer
 * `data.SrsRating`; the organism itself stays free of data-layer imports
 * (P2 `TtsButtonState` / P4b `SrsRatingOption` precedent).
 */
enum class VocabRatingOption { AGAIN, HARD, GOOD, EASY }

/**
 * Feature-owned vocabulary-drill rating ladder: the 2×2 Anki-style grid shown
 * under a flipped `VocabDrillScreen` card (Again + Hard outlined on the first
 * row, Good + Easy filled on the second), each button carrying its
 * interval-ladder subtitle.
 *
 * Display-only by contract (plan §4): it renders the four subtitle strings
 * handed to it and never imports ViewModels, session contracts, data-layer
 * types, or the interval ladder itself. The integration boundary
 * (`VocabDrillCardScreen`) owns the current interval step
 * (`card.mastery.intervalStepIndex`), the next-step preview math, the audit
 * logging (`vocabCardRate`), and the `answerRating` VM call that advances the
 * ladder.
 *
 * Parity anchors preserved verbatim from the pre-extraction inline grid:
 * - Column of two Rows, `Arrangement.spacedBy(8.dp)`, four `weight(1f)`
 *   buttons in the fixed order Again | Hard / Good | Easy;
 * - localized bold labels `vocab_rating_again/_hard/_good/_easy` (no fixed
 *   font size — default typography), 11.sp subtitles;
 * - color sources: Again = `colorScheme.error` (outlined, subtitle
 *   `error` @ 0.7f alpha); Hard = `VocabIntervalOrange` (outlined, subtitle
 *   same @ 0.7f); Good = filled `colorScheme.primary`; Easy = filled
 *   `MasteryGreen`; both filled subtitles `Color.White` @ 0.8f alpha;
 * - the Again subtitle is the localized `<1m` string
 *   (`vocab_rating_again_interval`), not a day count.
 *
 * Preview-value math ownership (documented choice): the Hard/Good/Easy
 * subtitles are computed at the integration boundary from the authoritative
 * `SpacedRepetitionConfig.INTERVAL_LADDER_DAYS` —
 * `ladder[currentStep]`, `(currentStep + 1).coerceAtMost(ladder.size - 1)`,
 * `(currentStep + 2).coerceAtMost(ladder.size - 1)` — the exact expressions
 * the inline version used over its byte-identical local ladder literal. The
 * display receives plain strings, so previews need no scheduling data.
 *
 * NOT a `ui/patterns` promotion with the daily `SrsRatingRow` (rule-4
 * verdict, completing P4 slice 2 §13.2): one-shot latch vs per-card
 * re-rating (this grid has NO disabled state — every button stays enabled
 * while the flipped card is visible), one Row vs 2×2, 4 outlined vs
 * 2 outlined + 2 filled, dedicated `Srs*` token pairs vs theme
 * error/primary + vocab tokens, `srs_*` vs `vocab_rating_*` strings, flat
 * labels vs ladder-preview subtitles coupling the display to scheduling
 * math. Similar "Anki buttons" look alone is insufficient (plan §3.4).
 *
 * @param againSubtitle Again's subtitle (the localized `<1m` string).
 * @param hardSubtitle Hard's preview: the CURRENT ladder interval (HARD
 *   keeps the same step, delta 0).
 * @param goodSubtitle Good's preview: next ladder step.
 * @param easySubtitle Easy's preview: two steps ahead.
 * @param onAnswer invoked with the tapped option; the boundary maps it to
 *   `data.SrsRating` (order-preserving), logs the audit and advances.
 * @param modifier applied to the root [Column].
 */
@Composable
fun VocabRatingLadder(
    againSubtitle: String,
    hardSubtitle: String,
    goodSubtitle: String,
    easySubtitle: String,
    onAnswer: (VocabRatingOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Again
            OutlinedButton(
                onClick = { onAnswer(VocabRatingOption.AGAIN) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.vocab_rating_again), fontWeight = FontWeight.Bold)
                    Text(againSubtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }
            // Hard
            OutlinedButton(
                onClick = { onAnswer(VocabRatingOption.HARD) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = VocabIntervalOrange
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.vocab_rating_hard), fontWeight = FontWeight.Bold)
                    Text(hardSubtitle, fontSize = 11.sp, color = VocabIntervalOrange.copy(alpha = 0.7f))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Good
            Button(
                onClick = { onAnswer(VocabRatingOption.GOOD) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.vocab_rating_good), fontWeight = FontWeight.Bold)
                    Text(goodSubtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
            // Easy
            Button(
                onClick = { onAnswer(VocabRatingOption.EASY) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MasteryGreen
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.vocab_rating_easy), fontWeight = FontWeight.Bold)
                    Text(easySubtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

// =====================================================================
// Preview fixtures — pure data only: no ViewModel, filesystem, installed
// pack or speech engine (plan §7). Each wraps the real GrammarMateTheme
// facade with an explicit ThemeMode, matching the P1/P2 preview convention.
// Subtitles mirror the boundary math over INTERVAL_LADDER_DAYS
// (1, 2, 4, 7, 10, 14, 20, 28, 42, 56): Hard = ladder[step],
// Good = ladder[min(step+1, 9)], Easy = ladder[min(step+2, 9)].
// ======================================================================

@Preview(name = "Vocab rating ladder — step 0 (light)", showBackground = true, widthDp = 360, heightDp = 160)
@Composable
fun VocabRatingLadderStep0LightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        VocabRatingLadder(
            againSubtitle = "<1m",
            hardSubtitle = "1d",
            goodSubtitle = "2d",
            easySubtitle = "4d",
            onAnswer = {}
        )
    }
}

@Preview(name = "Vocab rating ladder — mid step 5 (light)", showBackground = true, widthDp = 360, heightDp = 160)
@Composable
fun VocabRatingLadderMidLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        VocabRatingLadder(
            againSubtitle = "<1m",
            hardSubtitle = "14d",
            goodSubtitle = "20d",
            easySubtitle = "28d",
            onAnswer = {}
        )
    }
}

@Preview(name = "Vocab rating ladder — max step 9, capped previews (light)", showBackground = true, widthDp = 360, heightDp = 160)
@Composable
fun VocabRatingLadderMaxStepLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        // step 9: Good (9+1) and Easy (9+2) both coerceAtMost to the last rung.
        VocabRatingLadder(
            againSubtitle = "<1m",
            hardSubtitle = "56d",
            goodSubtitle = "56d",
            easySubtitle = "56d",
            onAnswer = {}
        )
    }
}

@Preview(name = "Vocab rating ladder — step 0 (dark)", showBackground = true, widthDp = 360, heightDp = 160)
@Composable
fun VocabRatingLadderStep0DarkPreview() {
    GrammarMateTheme(themeMode = ThemeMode.DARK) {
        VocabRatingLadder(
            againSubtitle = "<1m",
            hardSubtitle = "1d",
            goodSubtitle = "2d",
            easySubtitle = "4d",
            onAnswer = {}
        )
    }
}

@Preview(name = "Vocab rating ladder — step 0, system font scale 1.5x", showBackground = true, widthDp = 360, heightDp = 200, fontScale = 1.5f)
@Composable
fun VocabRatingLadderFontScalePreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        VocabRatingLadder(
            againSubtitle = "<1m",
            hardSubtitle = "1d",
            goodSubtitle = "2d",
            easySubtitle = "4d",
            onAnswer = {}
        )
    }
}

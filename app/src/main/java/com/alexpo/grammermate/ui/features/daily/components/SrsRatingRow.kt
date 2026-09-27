package com.alexpo.grammermate.ui.features.daily.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.ThemeMode
import com.alexpo.grammermate.ui.GrammarMateTheme
import com.alexpo.grammermate.ui.SrsAgainBackground
import com.alexpo.grammermate.ui.SrsAgainText
import com.alexpo.grammermate.ui.SrsEasyBackground
import com.alexpo.grammermate.ui.SrsEasyText
import com.alexpo.grammermate.ui.SrsGoodBackground
import com.alexpo.grammermate.ui.SrsGoodText
import com.alexpo.grammermate.ui.SrsHardBackground
import com.alexpo.grammermate.ui.SrsHardText

// =====================================================================
// Daily SRS one-shot rating row — feature organism (daily practice owner)
// =====================================================================

/**
 * UI-owned rating option for [SrsRatingRow]. The integration boundary maps it
 * to/from the scheduling-layer `data.SrsRating`; the organism itself stays
 * free of data-layer imports (P2 `TtsButtonState` precedent).
 */
enum class SrsRatingOption { AGAIN, HARD, GOOD, EASY }

/**
 * Feature-owned daily-practice SRS rating row: the single Row of four
 * colored buttons (Again / Hard / Good / Easy) shown under a daily VOCAB
 * flashcard once its answer is revealed (`ui/DailyPracticeScreen.kt`
 * `VocabFlashcardBlock`).
 *
 * Display-only by contract (plan §4): it renders the one-shot state handed to
 * it and never imports ViewModels, session contracts, or data-layer types.
 * The integration boundary owns the `isRated` latch (a `remember(task.id)`
 * in the block, also driven by the voice auto-rate path), the audit logging
 * (`daily_rate`), and the `onRateVocabCard` VM call.
 *
 * Parity anchors preserved verbatim from the pre-extraction inline row:
 * - one Row, `Arrangement.spacedBy(8.dp)`, four `weight(1f)` buttons in the
 *   fixed order Again / Hard / Good / Easy;
 * - localized labels `srs_again/_hard/_good/_easy`, flat single label,
 *   12.sp;
 * - per-rating colors from the dedicated `Srs*Background`/`Srs*Text` token
 *   getters (light+dark tuned), applied as outlined-button container/content
 *   colors;
 * - `enabled = !rated` — the one-shot semantics: after ANY rating the whole
 *   row goes inert for the rest of the card. The boundary additionally
 *   re-checks its latch inside the [onRate] callback (the pre-extraction
 *   inline `if (!isRated)` guard).
 *
 * NOT a `ui/patterns` promotion candidate (rule-4 verdict, P4 slice 2):
 * `VocabDrillScreen`'s 2×2 rating grid shares none of the contract axes —
 * different layout (2×2 grid vs one Row), different fill (2 outlined + 2
 * filled vs 4 outlined), different color sources (theme error/primary +
 * vocab tokens vs the dedicated Srs* pairs), different strings
 * (`vocab_rating_*` vs `srs_*`), interval-ladder subtitles with preview
 * next-step math vs flat labels, and per-card re-rating vs this one-shot
 * latch. Similar "Anki buttons" look alone is insufficient (plan §3.4).
 *
 * @param rated true once the current card has been rated (row rendered inert).
 * @param onRate invoked with the tapped option; the boundary guards and
 *   latches.
 * @param modifier applied to the root [Row].
 */
@Composable
fun SrsRatingRow(
    rated: Boolean,
    onRate: (SrsRatingOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            stringResource(R.string.srs_again) to SrsRatingOption.AGAIN,
            stringResource(R.string.srs_hard) to SrsRatingOption.HARD,
            stringResource(R.string.srs_good) to SrsRatingOption.GOOD,
            stringResource(R.string.srs_easy) to SrsRatingOption.EASY
        ).forEach { (label, option) ->
            val colors = when (option) {
                SrsRatingOption.AGAIN -> Pair(SrsAgainBackground, SrsAgainText)
                SrsRatingOption.HARD -> Pair(SrsHardBackground, SrsHardText)
                SrsRatingOption.GOOD -> Pair(SrsGoodBackground, SrsGoodText)
                SrsRatingOption.EASY -> Pair(SrsEasyBackground, SrsEasyText)
            }
            OutlinedButton(
                onClick = { onRate(option) },
                modifier = Modifier.weight(1f),
                enabled = !rated,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.first, contentColor = colors.second)
            ) { Text(label, fontSize = 12.sp) }
        }
    }
}

// =====================================================================
// Preview fixtures — pure data only: no ViewModel, filesystem, installed
// pack or speech engine (plan §7). Each wraps the real GrammarMateTheme
// facade with an explicit ThemeMode, matching the P1/P2 preview convention.
// ======================================================================

@Preview(name = "SRS rating row — unrated (light)", showBackground = true, widthDp = 360, heightDp = 80)
@Composable
fun SrsRatingRowUnratedLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        SrsRatingRow(rated = false, onRate = {})
    }
}

@Preview(name = "SRS rating row — rated, inert (light)", showBackground = true, widthDp = 360, heightDp = 80)
@Composable
fun SrsRatingRowRatedLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        SrsRatingRow(rated = true, onRate = {})
    }
}

@Preview(name = "SRS rating row — unrated (dark)", showBackground = true, widthDp = 360, heightDp = 80)
@Composable
fun SrsRatingRowUnratedDarkPreview() {
    GrammarMateTheme(themeMode = ThemeMode.DARK) {
        SrsRatingRow(rated = false, onRate = {})
    }
}

@Preview(name = "SRS rating row — rated, inert (dark)", showBackground = true, widthDp = 360, heightDp = 80)
@Composable
fun SrsRatingRowRatedDarkPreview() {
    GrammarMateTheme(themeMode = ThemeMode.DARK) {
        SrsRatingRow(rated = true, onRate = {})
    }
}

@Preview(name = "SRS rating row — unrated, system font scale 1.5x", showBackground = true, widthDp = 360, heightDp = 100, fontScale = 1.5f)
@Composable
fun SrsRatingRowUnratedFontScalePreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        SrsRatingRow(rated = false, onRate = {})
    }
}

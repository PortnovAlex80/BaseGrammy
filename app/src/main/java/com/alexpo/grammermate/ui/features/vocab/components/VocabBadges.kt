package com.alexpo.grammermate.ui.features.vocab.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.ui.designsystem.foundation.GrammarMateRadii

// =====================================================================
// Vocabulary card badges — feature atoms (vocab drill owner)
// =====================================================================

/**
 * Part-of-speech badge shown on vocab drill cards (front badges row and both
 * back-face header rows). Moved verbatim from the private `PosBadge` in
 * `ui/VocabDrillScreen.kt` (P4c) so the front-face organism
 * ([VocabDrillCardFront]) and the screen-local back face share one
 * implementation; `internal` to the module, consumed only by vocab surfaces.
 *
 * The raw `pos` key (`"nouns"`, `"verbs"`, …) is mapped to its localized
 * label and container color here — display-only, no data-layer imports.
 * Radius adopted from `GrammarMateRadii.small` (12.dp): the token's
 * documented slot ("chips, small badges") is this exact use; same value as
 * the pre-extraction inline `RoundedCornerShape(12.dp)`.
 */
@Composable
internal fun PosBadge(pos: String) {
    val (label, color) = when (pos) {
        "nouns" -> stringResource(R.string.vocab_badge_noun) to MaterialTheme.colorScheme.primaryContainer
        "verbs" -> stringResource(R.string.vocab_badge_verb) to MaterialTheme.colorScheme.secondaryContainer
        "adjectives" -> stringResource(R.string.vocab_badge_adj) to MaterialTheme.colorScheme.tertiaryContainer
        "adverbs" -> stringResource(R.string.vocab_badge_adv) to MaterialTheme.colorScheme.errorContainer
        "numbers" -> stringResource(R.string.vocab_badge_num) to MaterialTheme.colorScheme.surfaceVariant
        "pronouns" -> stringResource(R.string.vocab_badge_pronoun) to MaterialTheme.colorScheme.surfaceVariant
        else -> pos to MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        shape = RoundedCornerShape(GrammarMateRadii.small),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Word-frequency rank badge (`"#42"`), front face only. Moved verbatim from
 * the private `RankBadge` in `ui/VocabDrillScreen.kt` (P4c) together with
 * [PosBadge]; radius adopted from `GrammarMateRadii.small` (same 12.dp
 * value as the pre-extraction inline shape).
 */
@Composable
internal fun RankBadge(rank: Int) {
    Card(
        shape = RoundedCornerShape(GrammarMateRadii.small),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = "#$rank",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

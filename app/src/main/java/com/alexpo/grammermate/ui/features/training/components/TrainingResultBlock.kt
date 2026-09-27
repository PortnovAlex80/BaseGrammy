package com.alexpo.grammermate.ui.features.training.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.ThemeMode
import com.alexpo.grammermate.ui.CorrectGreen
import com.alexpo.grammermate.ui.IncorrectRed
import com.alexpo.grammermate.ui.GrammarMateTheme

// =====================================================================
// Training result block — feature organism (training feature owner)
// =====================================================================

/**
 * Feature-owned training result organism: the correct/incorrect feedback line
 * rendered under the answer input after a submit (`TrainingScreen.kt` column).
 *
 * Display-only by contract (plan §4): it renders pre-computed values and never
 * imports ViewModels, session contracts, or business state beyond nothing —
 * the integration boundary `TrainingScreen.kt::ResultBlock` reads
 * `TrainingUiState.cardSession.lastResult` and passes it as [isCorrect].
 *
 * Parity anchors preserved verbatim from the pre-extraction inline `ResultBlock`:
 * - the three variants are parameterized by the source data type itself
 *   (`Boolean?`), not by booleans hiding distinct semantics:
 *   `true` → localized "Correct" in [CorrectGreen], `false` → localized
 *   "Incorrect" in [IncorrectRed], `null` → blank text line (placeholder kept
 *   so the no-result layout occupies the same vertical space);
 * - localized strings `training_correct` / `training_incorrect`;
 * - bold weight, default typography/size (no scaled text in this block);
 * - 6.dp vertical arrangement, one centered Row.
 *
 * The block intentionally has NO answer text, audio control, or hint display:
 * on the current tree the accepted answer is shown by the input bar's hint card
 * and answer replay lives in the prompt card's TTS button (P2) — both stay in
 * their owners (P3b / P2 boundary). Scope evidence: `ResultBlock` contained
 * only the correctness label before extraction.
 *
 * @param isCorrect submit outcome as held by the session state:
 *   true correct / false incorrect / null no result yet.
 * @param modifier applied to the root [Column].
 */
@Composable
fun TrainingResultBlock(
    isCorrect: Boolean?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (isCorrect) {
                true -> Text(text = stringResource(R.string.training_correct), color = CorrectGreen, fontWeight = FontWeight.Bold)
                false -> Text(text = stringResource(R.string.training_incorrect), color = IncorrectRed, fontWeight = FontWeight.Bold)
                null -> Text(text = "")
            }
        }
    }
}

// =====================================================================
// Preview fixtures — pure data only: no ViewModel, filesystem, installed
// pack or speech engine (plan §7). Each wraps the real GrammarMateTheme
// facade with an explicit ThemeMode, matching the P1/P2 preview convention.
// ======================================================================

@Preview(name = "Result block — correct (light)", showBackground = true, widthDp = 360, heightDp = 80)
@Composable
fun TrainingResultBlockCorrectLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingResultBlock(isCorrect = true)
    }
}

@Preview(name = "Result block — incorrect (light)", showBackground = true, widthDp = 360, heightDp = 80)
@Composable
fun TrainingResultBlockIncorrectLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingResultBlock(isCorrect = false)
    }
}

@Preview(name = "Result block — no result (light)", showBackground = true, widthDp = 360, heightDp = 80)
@Composable
fun TrainingResultBlockNoneLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingResultBlock(isCorrect = null)
    }
}

@Preview(name = "Result block — correct (dark)", showBackground = true, widthDp = 360, heightDp = 80)
@Composable
fun TrainingResultBlockCorrectDarkPreview() {
    GrammarMateTheme(themeMode = ThemeMode.DARK) {
        TrainingResultBlock(isCorrect = true)
    }
}

@Preview(name = "Result block — incorrect (dark)", showBackground = true, widthDp = 360, heightDp = 80)
@Composable
fun TrainingResultBlockIncorrectDarkPreview() {
    GrammarMateTheme(themeMode = ThemeMode.DARK) {
        TrainingResultBlock(isCorrect = false)
    }
}

@Preview(name = "Result block — no result (dark)", showBackground = true, widthDp = 360, heightDp = 80)
@Composable
fun TrainingResultBlockNoneDarkPreview() {
    GrammarMateTheme(themeMode = ThemeMode.DARK) {
        TrainingResultBlock(isCorrect = null)
    }
}

package com.alexpo.grammermate.ui.features.verbdrill.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.ThemeMode
import com.alexpo.grammermate.ui.GrammarMateTheme

// =====================================================================
// Verb drill session card — feature organism (verb drill feature owner)
// =====================================================================

/**
 * Feature-owned verb drill session card: the inline saved-session panel on
 * `VerbDrillScreen` (VD-51) offering Repeat / Continue / Reset over the last
 * saved batch.
 *
 * Display-only by contract (plan §4): it renders pre-computed values and never
 * imports ViewModels or data-layer types. The integration boundary
 * `VerbDrillScreen.kt` owns everything business-specific — the
 * `VerbDrillLastSessionState` interpretation (including the shown-count
 * derivation from `todayShownCardIds`), `VerbDrillViewModel` reads, audit
 * logging, and the hasSavedSession visibility rule — and passes this
 * composable narrow display values plus the three action callbacks.
 *
 * Parity anchors preserved verbatim from the pre-extraction internal
 * `SessionCard` (`ui/VerbDrillScreen.kt`):
 * - `session_card` test tag on the card, `repeat_button` / `continue_button` /
 *   `reset_button` tags on the three controls;
 * - secondary-container card, 16.dp content padding, identical spacing order;
 * - localized strings `verb_session_card_title/_shown/_repeat/_continue/_reset`;
 * - context rows (tense/group/person) rendered only when non-null
 *   ([VerbDrillSessionContextInfo], also consumed by the start-fresh/resume
 *   dialog — single shared implementation).
 *
 * The Repeat/Continue/Reset semantics themselves (replay full saved batch in
 * order; Continue excludes checked/shown cards; Reset clears saved session
 * context but keeps learning progress) live in `VerbDrillViewModel` and the
 * boundary callbacks — NOT here.
 *
 * @param selectedTense saved filter context row; null hides the row.
 * @param selectedGroup saved filter context row; null hides the row.
 * @param selectedPerson saved filter context row; null hides the row.
 * @param shownCount cards already shown from the saved batch
 *   (boundary-derived from `todayShownCardIds.size`).
 * @param onRepeat replay the saved full batch in order.
 * @param onContinue resume with next cards excluding shown ones.
 * @param onReset discard the saved session context (learning progress kept).
 * @param modifier applied to the root [Card].
 */
@Composable
fun VerbDrillSessionCard(
    selectedTense: String?,
    selectedGroup: String?,
    selectedPerson: String?,
    shownCount: Int,
    onRepeat: () -> Unit,
    onContinue: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("session_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.verb_session_card_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            VerbDrillSessionContextInfo(
                selectedTense = selectedTense,
                selectedGroup = selectedGroup,
                selectedPerson = selectedPerson
            )

            Text(
                text = stringResource(R.string.verb_session_card_shown, shownCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRepeat,
                    modifier = Modifier.weight(1f).testTag("repeat_button")
                ) {
                    Text(stringResource(R.string.verb_session_card_repeat))
                }
                Button(
                    onClick = onContinue,
                    modifier = Modifier.weight(1f).testTag("continue_button")
                ) {
                    Text(stringResource(R.string.verb_session_card_continue))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth().testTag("reset_button")
            ) {
                Text(stringResource(R.string.verb_session_card_reset))
            }
        }
    }
}

/**
 * Saved filter context rows (tense/group/person) shared by the session card
 * and the start-fresh/resume dialog. Each row renders only when its value is
 * non-null; labels are the localized `verb_resume_dialog_*` strings.
 */
@Composable
fun VerbDrillSessionContextInfo(
    selectedTense: String?,
    selectedGroup: String?,
    selectedPerson: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Tense
        if (selectedTense != null) {
            VerbDrillSessionInfoRow(
                label = stringResource(R.string.verb_resume_dialog_tense),
                value = selectedTense
            )
        }
        // Group
        if (selectedGroup != null) {
            VerbDrillSessionInfoRow(
                label = stringResource(R.string.verb_resume_dialog_group),
                value = selectedGroup
            )
        }
        // Person
        if (selectedPerson != null) {
            VerbDrillSessionInfoRow(
                label = stringResource(R.string.verb_resume_dialog_person),
                value = selectedPerson
            )
        }
    }
}

/**
 * A single label/value row in the session context info display.
 */
@Composable
private fun VerbDrillSessionInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

// =====================================================================
// Preview fixtures — pure data only: no ViewModel, filesystem, installed
// pack or speech engine (plan §7). Each wraps the real GrammarMateTheme
// facade with an explicit ThemeMode, matching the P1/P2 preview convention.
// ======================================================================

@Preview(name = "Session card — full context (light)", showBackground = true, widthDp = 360, heightDp = 260)
@Composable
fun VerbDrillSessionCardFullContextLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        VerbDrillSessionCard(
            selectedTense = "Passato Prossimo",
            selectedGroup = "группа 3",
            selectedPerson = "io",
            shownCount = 12,
            onRepeat = {},
            onContinue = {},
            onReset = {}
        )
    }
}

@Preview(name = "Session card — tense only (light)", showBackground = true, widthDp = 360, heightDp = 200)
@Composable
fun VerbDrillSessionCardTenseOnlyLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        VerbDrillSessionCard(
            selectedTense = "Presente",
            selectedGroup = null,
            selectedPerson = null,
            shownCount = 0,
            onRepeat = {},
            onContinue = {},
            onReset = {}
        )
    }
}

@Preview(name = "Session card — no context (light)", showBackground = true, widthDp = 360, heightDp = 180)
@Composable
fun VerbDrillSessionCardNoContextLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        VerbDrillSessionCard(
            selectedTense = null,
            selectedGroup = null,
            selectedPerson = null,
            shownCount = 5,
            onRepeat = {},
            onContinue = {},
            onReset = {}
        )
    }
}

@Preview(name = "Session card — full context (dark)", showBackground = true, widthDp = 360, heightDp = 260)
@Composable
fun VerbDrillSessionCardFullContextDarkPreview() {
    GrammarMateTheme(themeMode = ThemeMode.DARK) {
        VerbDrillSessionCard(
            selectedTense = "Imperfetto",
            selectedGroup = "группа 1",
            selectedPerson = "tu",
            shownCount = 30,
            onRepeat = {},
            onContinue = {},
            onReset = {}
        )
    }
}

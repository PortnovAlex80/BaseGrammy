package com.alexpo.grammermate.ui.features.training.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.tooling.preview.Preview
import com.alexpo.grammermate.data.ThemeMode
import com.alexpo.grammermate.ui.ProgressGreen
import com.alexpo.grammermate.ui.ProgressFastGreen
import com.alexpo.grammermate.ui.ProgressLabelWhite
import com.alexpo.grammermate.ui.ProgressTrackGray
import com.alexpo.grammermate.ui.ProgressTrackGreen
import com.alexpo.grammermate.ui.MasteryGreen
import com.alexpo.grammermate.ui.SpeedSlowRed
import com.alexpo.grammermate.ui.SpeedMediumYellow
import com.alexpo.grammermate.ui.GrammarMateTheme

// =====================================================================
// Session progress indicator — feature component (training feature owner)
// =====================================================================

/**
 * Training session progress display: a rounded progress bar (70% width) with
 * text overlay and a circular speedometer arc (30% width) showing
 * words-per-minute.
 *
 * Feature-owned under `ui/features/training/components` per the P0/P3a
 * caller-verified destination decision (plan §3 dependency rule 4): every live
 * caller is `TrainingScreen.kt` (regular/verb-drill/daily/boss modes all render
 * through it) — no cross-feature reuse exists, so a shared `designsystem`
 * placement would be unsupported. Display-only by contract (plan §4): it
 * receives plain display values and owns no state, effects, or ViewModel —
 * `current`/`total`/`speedWpm` are computed by the `TrainingScreen` boundary
 * from `CardSessionState` (see effect ledger §4 rows 15/21–23); the fraction
 * formula and the 20/40 wpm arc thresholds are display math preserved verbatim
 * from the pre-move implementation.
 *
 * @param current Current card index (1-based for display).
 * @param total   Total number of cards in the session.
 * @param speedWpm Typing speed in words per minute, shown as the speedometer arc.
 * @param modifier applied to the root [Row].
 */
@Composable
fun SessionProgressIndicator(
    current: Int,
    total: Int,
    speedWpm: Int,
    modifier: Modifier = Modifier
) {
    val progressFraction = if (total > 0) current.toFloat() / total else 0f
    val barColor = ProgressGreen
    val trackColor = ProgressTrackGreen
    val speedColor = when {
        speedWpm <= 20 -> SpeedSlowRed
        speedWpm <= 40 -> SpeedMediumYellow
        else -> ProgressFastGreen
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Progress bar — 70% width, fill grows left to right
        Box(
            modifier = Modifier
                .weight(0.7f)
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(trackColor)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .height(24.dp)
                    .fillMaxWidth(progressFraction)
                    .background(barColor, RoundedCornerShape(12.dp))
            )
            Text(
                text = "$current / $total",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (progressFraction < 0.12f) MasteryGreen else ProgressLabelWhite,
                modifier = Modifier.align(Alignment.Center),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Speedometer circle — 30% width, constrained to square
        Box(
            modifier = Modifier
                .weight(0.3f)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            val sizeModifier = Modifier.size(44.dp)
            val arcTrackColor = ProgressTrackGray
            Canvas(modifier = sizeModifier) {
                val strokeWidth = 4.dp.toPx()
                drawArc(
                    color = arcTrackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth)
                )
                val sweep = 360f * (speedWpm.coerceAtMost(100) / 100f)
                drawArc(
                    color = speedColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Text(
                text = "$speedWpm",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = speedColor
            )
        }
    }
}

// =====================================================================
// Preview fixtures — pure data only: no ViewModel, filesystem, installed
// pack or speech engine (plan §7). Each wraps the real GrammarMateTheme
// facade with an explicit ThemeMode, matching the P1/P2 preview convention.
// ======================================================================

@Preview(name = "Progress — start 1/10, low-fraction label, slow arc", showBackground = true, widthDp = 360, heightDp = 72)
@Composable
fun SessionProgressIndicatorStartPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        SessionProgressIndicator(current = 1, total = 10, speedWpm = 0)
    }
}

@Preview(name = "Progress — mid 5/10, medium arc", showBackground = true, widthDp = 360, heightDp = 72)
@Composable
fun SessionProgressIndicatorMidPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        SessionProgressIndicator(current = 5, total = 10, speedWpm = 30)
    }
}

@Preview(name = "Progress — near complete 9/10, fast arc", showBackground = true, widthDp = 360, heightDp = 72)
@Composable
fun SessionProgressIndicatorNearCompletePreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        SessionProgressIndicator(current = 9, total = 10, speedWpm = 65)
    }
}

@Preview(name = "Progress — mid (dark)", showBackground = true, widthDp = 360, heightDp = 72)
@Composable
fun SessionProgressIndicatorDarkPreview() {
    GrammarMateTheme(themeMode = ThemeMode.DARK) {
        SessionProgressIndicator(current = 5, total = 10, speedWpm = 25)
    }
}

@Preview(name = "Progress — mid, system font scale 1.5x (12sp label clipping surface)", showBackground = true, widthDp = 360, heightDp = 72, fontScale = 1.5f)
@Composable
fun SessionProgressIndicatorLargeFontPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        SessionProgressIndicator(current = 5, total = 10, speedWpm = 30)
    }
}

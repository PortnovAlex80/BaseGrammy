package com.alexpo.grammermate.ui.designsystem.foundation

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// =====================================================================
// Prompt text-scale policy — app-level RU text scaling for prompt content
// =====================================================================

/**
 * Base sp sizes for training prompt content, before the user's RU text-scale
 * factor is applied via [scaledSp].
 *
 * The app-wide pattern is `fontSize = (base * ruTextScale).sp` (P0 inventory
 * §3.5: 17 live sites across TrainingScreen, VocabDrillScreen,
 * DailyPracticeScreen, SimpleMarkdownParser; `ruTextScale` ranges 1.0f..2.0f,
 * default 1.0f — see AppConfigStore). These anchors cover the P2 prompt-pilot
 * surface (`CardPrompt` in TrainingScreen and the drill-mode prompt line);
 * other scaled sizes adopt tokens in their own slices, not here.
 */
object PromptTextScale {
    /** Main RU prompt line — `card_prompt_text` in the prompt card. */
    const val prompt: Float = 20f

    /** Optional italic situation context (`SentenceCard.contextRu`), shown independently of hint grading. */
    const val context: Float = 13f

    /** Drill-mode prompt line (VERB_DRILL / DAILY_VERBS header text). */
    const val drillPrompt: Float = 18f
}

/**
 * Applies the app text-scale policy to a base sp value: `(baseSp * textScale).sp`.
 *
 * Identical to the inline `(Nf * ruTextScale).sp` expressions used today; the
 * helper only gives the pattern one documented owner.
 */
fun scaledSp(baseSp: Float, textScale: Float): TextUnit = (baseSp * textScale).sp

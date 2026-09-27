package com.alexpo.grammermate.ui.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.data.ThemeMode
import com.alexpo.grammermate.ui.GrammarMateTheme
import com.alexpo.grammermate.ui.designsystem.foundation.LocalGrammarMateColors
import com.alexpo.grammermate.ui.designsystem.foundation.PromptTextScale
import com.alexpo.grammermate.ui.designsystem.foundation.scaledSp

// =====================================================================
// Foundation preview fixtures — first @Preview surfaces in the repo.
//
// Pure fixture data only: no ViewModel, no filesystem, no installed pack,
// no context-dependent state (plan §7 / P1 exit evidence). Each preview
// wraps content in the real `GrammarMateTheme` facade with an explicit
// ThemeMode so tooling renders exactly what the app resolves.
// =====================================================================

/** `#RRGGBB` form of a resolved Compose color (alpha dropped for readability). */
private fun hexOf(color: Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)

/** All [com.alexpo.grammermate.ui.designsystem.foundation.GrammarMateColors] fields with names + hex. */
@Composable
private fun PaletteSwatchList() {
    val c = LocalGrammarMateColors.current
    val swatches: List<Pair<String, Color>> = listOf(
        "correctGreen" to c.correctGreen,
        "incorrectRed" to c.incorrectRed,
        "masteryGreen" to c.masteryGreen,
        "progressGreen" to c.progressGreen,
        "progressTrackGreen" to c.progressTrackGreen,
        "progressFastGreen" to c.progressFastGreen,
        "speedSlowRed" to c.speedSlowRed,
        "speedMediumYellow" to c.speedMediumYellow,
        "progressLabelWhite" to c.progressLabelWhite,
        "progressTrackGray" to c.progressTrackGray,
        "srsAgainBackground" to c.srsAgainBackground,
        "srsAgainText" to c.srsAgainText,
        "srsHardBackground" to c.srsHardBackground,
        "srsHardText" to c.srsHardText,
        "srsGoodBackground" to c.srsGoodBackground,
        "srsGoodText" to c.srsGoodText,
        "srsEasyBackground" to c.srsEasyBackground,
        "srsEasyText" to c.srsEasyText,
        "vocabIntervalOrange" to c.vocabIntervalOrange,
        "vocabCorrectBackground" to c.vocabCorrectBackground,
        "vocabIncorrectBackground" to c.vocabIncorrectBackground,
        "bossBronze" to c.bossBronze,
        "bossSilver" to c.bossSilver,
        "bossGold" to c.bossGold,
        "destructiveRed" to c.destructiveRed,
        "surfaceElevated" to c.surfaceElevated
    )
    Column(Modifier.padding(12.dp)) {
        Text(
            text = "GrammarMateColors (semantic palette)",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        swatches.forEach { (name, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .background(color, RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(text = name, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Text(
                    text = hexOf(color),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(name = "Palette — Light", showBackground = true, widthDp = 360, heightDp = 880)
@Composable
fun GrammarMateColorsLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        PaletteSwatchList()
    }
}

@Preview(name = "Palette — Dark", showBackground = true, widthDp = 360, heightDp = 880)
@Composable
fun GrammarMateColorsDarkPreview() {
    GrammarMateTheme(themeMode = ThemeMode.DARK) {
        PaletteSwatchList()
    }
}

/** The resolved Material 3 type scale under `GrammarMateTypography`. */
@Composable
private fun TypeScaleList() {
    val styles: List<Pair<String, androidx.compose.ui.text.TextStyle>> = listOf(
        "displayLarge" to MaterialTheme.typography.displayLarge,
        "displayMedium" to MaterialTheme.typography.displayMedium,
        "displaySmall" to MaterialTheme.typography.displaySmall,
        "headlineLarge" to MaterialTheme.typography.headlineLarge,
        "headlineMedium" to MaterialTheme.typography.headlineMedium,
        "headlineSmall" to MaterialTheme.typography.headlineSmall,
        "titleLarge" to MaterialTheme.typography.titleLarge,
        "titleMedium" to MaterialTheme.typography.titleMedium,
        "titleSmall" to MaterialTheme.typography.titleSmall,
        "bodyLarge" to MaterialTheme.typography.bodyLarge,
        "bodyMedium" to MaterialTheme.typography.bodyMedium,
        "bodySmall" to MaterialTheme.typography.bodySmall,
        "labelLarge" to MaterialTheme.typography.labelLarge,
        "labelMedium" to MaterialTheme.typography.labelMedium,
        "labelSmall" to MaterialTheme.typography.labelSmall
    )
    Column(Modifier.padding(12.dp)) {
        Text(
            text = "GrammarMateTypography (M3 scale)",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        styles.forEach { (name, style) ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = "GrammarMate 0123", style = style, maxLines = 1)
            }
        }
    }
}

@Preview(name = "Typography scale", showBackground = true, widthDp = 360, heightDp = 900)
@Composable
fun TypographyScalePreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TypeScaleList()
    }
}

/** Theme shapes resolved from [com.alexpo.grammermate.ui.designsystem.foundation.GrammarMateShapes]. */
@Composable
private fun ShapeSwatchRow() {
    val shapes = MaterialTheme.shapes
    val entries = listOf(
        "extraSmall" to shapes.extraSmall,
        "small" to shapes.small,
        "medium" to shapes.medium,
        "large" to shapes.large,
        "extraLarge" to shapes.extraLarge
    )
    Column(Modifier.padding(12.dp)) {
        Text(
            text = "GrammarMateShapes (8 / 12 / 16 / 20 / 28 dp)",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        entries.forEach { (name, shape) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, shape)
                )
                Spacer(Modifier.width(12.dp))
                Text(text = name, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(name = "Shapes", showBackground = true, widthDp = 240, heightDp = 420)
@Composable
fun ShapesPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        ShapeSwatchRow()
    }
}

/**
 * The prompt text-scale policy ([PromptTextScale] + [scaledSp]) at the default
 * 1.0f and a raised 1.5f app text scale — the values the P2 prompt pilot will
 * consume.
 */
@Composable
private fun PromptScaleSamples(textScale: Float) {
    Column(Modifier.padding(12.dp)) {
        Text(
            text = "PromptTextScale @ %.1fx".format(textScale),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "prompt (${PromptTextScale.prompt}f base) — Привет, как дела?",
            fontSize = scaledSp(PromptTextScale.prompt, textScale),
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "context (${PromptTextScale.context}f base) — ситуация подсказки",
            fontSize = scaledSp(PromptTextScale.context, textScale),
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "drillPrompt (${PromptTextScale.drillPrompt}f base) — Переведи предложение",
            fontSize = scaledSp(PromptTextScale.drillPrompt, textScale),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Preview(name = "Prompt text scale 1.0x", showBackground = true, widthDp = 360, heightDp = 220)
@Composable
fun PromptTextScaleDefaultPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        PromptScaleSamples(textScale = 1.0f)
    }
}

@Preview(name = "Prompt text scale 1.5x", showBackground = true, widthDp = 360, heightDp = 260)
@Composable
fun PromptTextScaleLargePreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        PromptScaleSamples(textScale = 1.5f)
    }
}

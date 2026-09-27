package com.alexpo.grammermate.ui.designsystem.foundation

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// =====================================================================
// Corner-radius tokens — repeated inline radii with a real owner
// =====================================================================

/**
 * Named corner radii for the two most-repeated inline values in screens and
 * components (P0 inventory §3.6: `12.dp` x16, `8.dp` x11 inline occurrences).
 *
 * [GrammarMateShapes] below is built from these tokens, so each radius has
 * exactly one source. Migration sites should prefer the matching slot of
 * `MaterialTheme.shapes` (which resolves to the same shapes); these Dp tokens
 * exist for sites that need the raw dimension (e.g. border + clip pairs).
 */
object GrammarMateRadii {
    /** Matches the [Shapes.extraSmall] slot: chips, small badges. */
    val extraSmall: Dp = 8.dp

    /** Matches the [Shapes.small] slot: cards, buttons, input surfaces. */
    val small: Dp = 12.dp
}

// =====================================================================
// Shapes — unified radii across the app (formerly M3 defaults)
// =====================================================================

val GrammarMateShapes = Shapes(
    extraSmall = RoundedCornerShape(GrammarMateRadii.extraSmall),
    small = RoundedCornerShape(GrammarMateRadii.small),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

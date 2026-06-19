package com.alexpo.grammermate.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/**
 * Premium Dark / Emerald design helpers.
 *
 * - [AppBackground]: full-screen gradient + faint emerald radial glow at top.
 * - [Modifier.glassSurface]: frosted-glass card (translucent surface + soft shadow +
 *   thin outline). Render-effect blur is applied on API 31+; older APIs degrade to an
 *   opaque elevated surface (still on-brand, no crash).
 * - [Modifier.emeraldGlow]: soft radial glow for active/primary elements.
 *
 * All look decisions live here; screens call modifiers, not raw colors.
 */

/**
 * App-wide background. Paints a vertical gradient and a soft emerald radial glow
 * near the top so glass cards read as layered over depth rather than flat black.
 * Place once per screen via [GrammarMateTheme] (already wraps all content).
 */
@Composable
fun AppBackground(
    useDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                if (useDarkTheme) {
                    drawDarkBackground()
                } else {
                    drawLightBackground()
                }
            }
    ) {
        content()
    }
}

private fun DrawScope.drawDarkBackground() {
    val w = size.width
    val h = size.height
    // Vertical gradient: deeper at bottom, slightly lifted near top
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0E1512),
                Color(0xFF0A1310)
            ),
            startY = 0f,
            endY = h
        )
    )
    // Faint emerald radial glow at the top center
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF34D6B4).copy(alpha = 0.16f),
                Color(0xFF34D6B4).copy(alpha = 0.00f)
            ),
            center = Offset(x = w * 0.5f, y = h * -0.05f),
            radius = h * 0.55f
        )
    )
}

private fun DrawScope.drawLightBackground() {
    val w = size.width
    val h = size.height
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF7F4EF),
                Color(0xFFEEEAE2)
            ),
            startY = 0f,
            endY = h
        )
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF0F7C66).copy(alpha = 0.10f),
                Color(0xFF0F7C66).copy(alpha = 0.00f)
            ),
            center = Offset(x = w * 0.5f, y = h * -0.05f),
            radius = h * 0.55f
        )
    )
}

/**
 * Frosted-glass card modifier.
 *
 * On API 31+: translucent container with a render-effect blur of the background
 * underneath, a soft drop shadow, and a thin outline — the "frosted glass" look.
 * On older APIs: opaque elevated surface (graceful degradation, still on-brand).
 *
 * @param shape       corner shape (defaults to theme large = 20dp)
 * @param container   card fill color (defaults to theme surface)
 * @param alpha       container translucency for the glass effect (ignored below API 31)
 * @param blurRadius  backdrop blur strength
 * @param shadowElevation drop shadow elevation
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape = RectangleShape,
    container: Color = MaterialTheme.colorScheme.surface,
    alpha: Float = 0.72f,
    blurRadius: Dp = 20.dp,
    shadowElevation: Dp = 12.dp
): Modifier {
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Hairline glass edge: light-on-dark uses white; on light theme a soft dark line.
    val borderColor = Color.White.copy(alpha = 0.08f)
    return this
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.45f),
            spotColor = Color.Black.copy(alpha = 0.45f)
        )
        .then(
            if (supportsBlur) {
                Modifier.graphicsLayer {
                    this.shape = shape
                    this.clip = true
                }.blur(blurRadius)
            } else {
                Modifier
            }
        )
        .background(
            color = if (supportsBlur) container.copy(alpha = alpha) else container,
            shape = shape
        )
        .border(width = 1.dp, color = borderColor, shape = shape)
}

/**
 * Soft emerald radial glow drawn behind an element (for active tiles, primary CTAs).
 */
@Composable
fun Modifier.emeraldGlow(
    radius: Dp = 40.dp,
    color: Color = Color(0xFF34D6B4).copy(alpha = 0.35f)
): Modifier = this.drawBehind {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = Offset(x = size.width * 0.5f, y = size.height * 0.5f),
            radius = radius.toPx().coerceAtLeast(1f)
        )
    )
}

package com.alexpo.grammermate.ui.components

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

/**
 * Shared progress indicator used across training sessions.
 * Displays a rounded progress bar (70% width) with text overlay
 * and a circular speedometer arc (30% width) showing words-per-minute.
 *
 * @param current Current card index (1-based for display).
 * @param total   Total number of cards in the session.
 * @param speedWpm Typing speed in words per minute, shown as the speedometer arc.
 */
@Composable
fun SessionProgressIndicator(
    current: Int,
    total: Int,
    speedWpm: Int
) {
    val progressFraction = if (total > 0) current.toFloat() / total else 0f
    val barColor = Color(0xFF4CAF50)
    val trackColor = Color(0xFFC8E6C9)
    val speedColor = when {
        speedWpm <= 20 -> Color(0xFFE53935)
        speedWpm <= 40 -> Color(0xFFFDD835)
        else -> Color(0xFF43A047)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
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
                color = if (progressFraction < 0.12f) Color(0xFF2E7D32) else Color.White,
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
            Canvas(modifier = sizeModifier) {
                val strokeWidth = 4.dp.toPx()
                drawArc(
                    color = Color(0xFFE0E0E0),
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

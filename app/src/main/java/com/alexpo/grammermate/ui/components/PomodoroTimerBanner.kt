package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.R

private val TomatoRed = Color(0xFFE53935)
private val SuccessGreen = Color(0xFF66BB6A)

@Composable
fun PomodoroTimerBanner(
    remainingSeconds: Int,
    totalSeconds: Int,
    cardsShown: Int,
    successRate: Int,
    isPaused: Boolean,
    onPauseResume: () -> Unit
) {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeText = String.format("%02d:%02d", minutes, seconds)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("pomodoro_banner"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_pomodoro_timer),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = timeText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }

            Text(
                text = "$cardsShown cards",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$successRate%",
                    fontWeight = FontWeight.Medium,
                    color = if (successRate >= 80) SuccessGreen else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onPauseResume,
                    modifier = Modifier.size(32.dp).testTag("pomodoro_pause_resume")
                ) {
                    Icon(
                        painter = if (isPaused) painterResource(android.R.drawable.ic_media_play)
                            else painterResource(android.R.drawable.ic_media_pause),
                        contentDescription = if (isPaused) "Resume" else "Pause",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.data.CardDifficultyRating
import kotlinx.coroutines.delay

private data class RatingStyle(val label: String, val color: Color)

private val ratingStyles = mapOf(
    CardDifficultyRating.AGAIN to RatingStyle("Again", Color(0xFFFFCDD2)),
    CardDifficultyRating.HARD to RatingStyle("Hard", Color(0xFFFFE0B2)),
    CardDifficultyRating.GOOD to RatingStyle("Good", Color(0xFFC8E6C9)),
    CardDifficultyRating.EASY to RatingStyle("Easy", Color(0xFFBBDEFB))
)

@Composable
fun DifficultyRatingRow(
    onRatingSelected: (CardDifficultyRating) -> Unit
) {
    var hasSelected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(3000)
        if (!hasSelected) {
            hasSelected = true
            onRatingSelected(CardDifficultyRating.GOOD)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "How was it?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CardDifficultyRating.entries.forEach { rating ->
                val style = ratingStyles[rating]!!
                FilterChip(
                    selected = false,
                    onClick = {
                        if (!hasSelected) {
                            hasSelected = true
                            onRatingSelected(rating)
                        }
                    },
                    label = { Text(style.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = style.color,
                        labelColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
    }
}

package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

/**
 * Popup/dialog с информацией о слове (по аналогии с VerbReferenceBottomSheet)
 *
 * Показывает:
 * - Слово и его ранг
 * - Перевод
 * - Часть речи
 * - Коллокации (до 3 штук)
 */
@Composable
fun WordInfoPopup(
    word: String,
    hint: WordHint,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Заголовок - слово и ранг
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = word,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "#${hint.rank}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Уровень (A1/A2/B1+)
            val level = WordHint.getLevelFromRank(hint.rank)
            val levelColor = when (level) {
                "A1" -> Color.Green.copy(alpha = 0.7f)
                "A2" -> Color.Blue.copy(alpha = 0.7f)
                else -> Color.Red.copy(alpha = 0.7f)
            }

            Text(
                text = "Уровень: $level",
                style = MaterialTheme.typography.bodyMedium,
                color = levelColor,
                fontWeight = FontWeight.Medium
            )

            // Перевод
            Text(
                text = hint.translation,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Часть речи
            Text(
                text = "Часть речи: ${hint.partOfSpeech}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )

            // Коллокации (если есть)
            if (hint.collocations.isNotEmpty()) {
                Spacer(modifier = Modifier.padding(top = 12.dp))
                Text(
                    text = "Коллокации:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                hint.collocations.forEach { collocation ->
                    Text(
                        text = "• $collocation",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }
            }

            // Кнопка закрытия
            Spacer(modifier = Modifier.padding(top = 16.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Закрыть")
            }
        }
    }
}

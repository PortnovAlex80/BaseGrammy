package com.alexpo.grammermate.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.LessonLadderRow
import com.alexpo.grammermate.data.TrainingUiState

@Composable
fun LadderScreen(
    state: TrainingUiState,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.ladder_back))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = stringResource(R.string.ladder_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.ladder_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.navigation.ladderRows.isEmpty()) {
            Text(
                text = stringResource(R.string.ladder_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            return
        }

        LadderHeaderRow()
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.navigation.ladderRows) { row ->
                LadderRowCard(row)
            }
        }
    }
}

@Composable
fun LadderHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.ladder_header_number),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(28.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = stringResource(R.string.ladder_header_lesson),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = stringResource(R.string.ladder_header_cards),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = stringResource(R.string.ladder_header_days),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = stringResource(R.string.ladder_header_interval),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(92.dp),
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun LadderRowCard(row: LessonLadderRow) {
    val isOverdue = row.intervalLabel?.startsWith("Просрочка") == true
    val containerColor = if (isOverdue) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isOverdue) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val cardsText = row.uniqueCardShows?.toString() ?: "-"
    val daysText = row.daysSinceLastShow?.toString() ?: "-"
    val intervalText = row.intervalLabel ?: "-"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = row.index.toString(),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.width(28.dp)
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = cardsText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(56.dp),
                textAlign = TextAlign.End
            )
            Text(
                text = daysText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.End
            )
            Text(
                text = intervalText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(92.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

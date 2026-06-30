package com.alexpo.grammermate.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.AuxDrillCatalog
import com.alexpo.grammermate.data.AuxDrillPair

/**
 * Aux (lead-in) drill menu. Lets the user pick a (verb×tense) pair and start a
 * training session. Training itself runs in the shared TrainingScreen —
 * [onStartTraining] receives the pair and the filtered deck, and the caller
 * hands them to [com.alexpo.grammermate.ui.TrainingViewModel.startVerbDrillSession].
 */
@Composable
fun AuxDrillScreen(
    viewModel: AuxDrillViewModel,
    onBack: () -> Unit,
    onStartTraining: (pair: AuxDrillPair) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.aux_drill_content_desc_back))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.aux_drill_title), fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.aux_drill_select_pair),
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AuxDrillCatalog.GROUPED.forEach { (category, pairs) ->
                item {
                    Text(
                        text = category,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(pairs) { pair ->
                    AuxPairCard(
                        pair = pair,
                        selected = state.selectedPair == pair,
                        totalCards = if (state.selectedPair == pair) state.totalCards else 0,
                        everShown = if (state.selectedPair == pair) state.everShownCount else 0,
                        onClick = { viewModel.selectPair(pair) },
                        onStart = {
                            viewModel.selectPair(pair)
                            onStartTraining(pair)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AuxPairCard(
    pair: AuxDrillPair,
    selected: Boolean,
    totalCards: Int,
    everShown: Int,
    onClick: () -> Unit,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${pair.verb} — ${pair.tense}", fontWeight = FontWeight.Medium)
                Text(
                    text = "→ ${pair.lessonId}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            if (selected) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.aux_drill_progress, everShown, totalCards), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (totalCards > 0) everShown.toFloat() / totalCards.toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.aux_drill_start))
                }
            }
        }
    }
}

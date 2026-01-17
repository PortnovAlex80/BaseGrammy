package com.alexpo.grammermate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.data.RestoreNotifier
import com.alexpo.grammermate.data.RestoreStatus

@Composable
fun AppRoot() {
    GrammarMateTheme {
        val restoreState by RestoreNotifier.restoreState.collectAsState()
        if (restoreState.status == RestoreStatus.DONE) {
            GrammarMateApp()
        } else {
            StartupScreen(status = restoreState.status)
        }
    }
}

@Composable
private fun StartupScreen(status: RestoreStatus) {
    val message = when (status) {
        RestoreStatus.IN_PROGRESS -> "Restoring backup..."
        RestoreStatus.NEEDS_USER -> "Waiting for backup folder..."
        else -> "Preparing..."
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            CircularProgressIndicator()
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

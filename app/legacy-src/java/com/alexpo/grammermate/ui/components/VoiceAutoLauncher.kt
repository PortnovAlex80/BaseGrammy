package com.alexpo.grammermate.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

/**
 * Auto-launches voice input when a new card appears and voice mode is active.
 * Shared between VocabDrillScreen and TrainingScreen.
 *
 * @param cardIndex changes when a new card is shown — triggers the auto-launch check
 * @param voiceModeEnabled whether voice input mode is currently active
 * @param isFlipped whether the card has been flipped (answer revealed)
 * @param voiceCompleted whether voice recognition already produced a result for this card
 * @param isVoiceActive whether voice recognition is currently running
 * @param onAutoStartVoice callback to launch voice recognition
 */
@Composable
fun VoiceAutoLauncher(
    cardIndex: Int,
    voiceModeEnabled: Boolean,
    isFlipped: Boolean,
    voiceCompleted: Boolean,
    isVoiceActive: Boolean,
    onAutoStartVoice: () -> Unit
) {
    LaunchedEffect(cardIndex, voiceModeEnabled) {
        if (voiceModeEnabled && !isFlipped && !voiceCompleted && !isVoiceActive) {
            delay(500L)
            if (!voiceCompleted && !isFlipped) {
                onAutoStartVoice()
            }
        }
    }
}

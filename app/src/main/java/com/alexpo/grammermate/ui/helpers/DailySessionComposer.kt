package com.alexpo.grammermate.ui.helpers

import com.alexpo.grammermate.data.DailyTask
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SentenceCard

class DailySessionComposer {
    fun buildSubLessonTasks(cards: List<SentenceCard>): List<DailyTask.TranslateSentence> {
        return cards.mapIndexed { index, card ->
            val mode = when (index % 3) {
                0 -> InputMode.VOICE
                1 -> InputMode.KEYBOARD
                else -> InputMode.WORD_BANK
            }
            DailyTask.TranslateSentence(
                id = "daily_${card.id}",
                card = card,
                inputMode = mode
            )
        }
    }
}

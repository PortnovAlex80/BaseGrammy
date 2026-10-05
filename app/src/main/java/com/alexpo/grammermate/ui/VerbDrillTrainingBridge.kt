package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.SubmitResult
import com.alexpo.grammermate.ui.navigation.Routes

/** Keeps drill persistence tied to the card displayed by the shared training runner. */
internal class VerbDrillTrainingBridge(
    private val trainingVm: TrainingViewModel,
    private val verbVm: VerbDrillViewModel
) {
    fun submit(): SubmitResult {
        val card = trainingVm.training.currentCard()
        val persistVerb = isVerbSession()
        val result = trainingVm.submitAnswer()
        if (persistVerb && card is VerbDrillCard && result.accepted) verbVm.submitCorrectAnswer(card.id)
        return result
    }

    fun next() {
        val card = trainingVm.training.currentCard()
        val reviewed = card is VerbDrillCard &&
            (trainingVm.training.isHintShown || trainingVm.uiState.value.cardSession.lastResult == false)
        if (reviewed && isVerbSession()) verbVm.markCardCompleted(card?.id)
        val progress = trainingVm.training.progress
        if (reviewed && progress.current == progress.total) {
            trainingVm.nextCard()
        } else {
            trainingVm.navigateNext()
            synchronizeCursor()
        }
    }

    fun previous() {
        trainingVm.navigatePrev()
        synchronizeCursor()
    }

    private fun synchronizeCursor() {
        if (isVerbSession()) trainingVm.training.currentCard()?.id?.let(verbVm::syncDisplayedCard)
    }

    private fun isVerbSession() = trainingVm.uiState.value.cardSession.returnTo == Routes.VERB_DRILL
}

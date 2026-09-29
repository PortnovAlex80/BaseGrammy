package com.alexpo.grammermate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.shared.AuditLogger
import com.alexpo.grammermate.shared.ScreenLogger
import com.alexpo.grammermate.ui.navigation.Routes

@Composable
internal fun VerbDrillDestination(
    state: TrainingUiState,
    vm: TrainingViewModel,
    verbDrillVm: VerbDrillViewModel,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onNavigateResetStack: (String) -> Unit
) {
    // Force reload when entering verb drill screen — ensures cards are loaded
    // even if the outer LaunchedEffect didn't fire (e.g. packId didn't change)
    val vdPackId = state.navigation.activePackId
    LaunchedEffect(vdPackId, state.navigation.selectedLanguageId) {
        if (vdPackId != null) {
            verbDrillVm.reloadForPack(vdPackId.value)
        } else {
            verbDrillVm.reloadForLanguage(state.navigation.selectedLanguageId?.value ?: "en")
        }
    }
    val verbDrillExit = remember(verbDrillVm) {
        {
            verbDrillVm.exitSession()
            vm.refreshStreakFromStore()
            onNavigateResetStack(Routes.HOME)
        }
    }
    BackHandler {
        ScreenLogger.nav(currentRoute, "BACK", trigger = "back_press")
        AuditLogger.getInstanceOrNull()?.backPress("verb_drill", "exit_to_home")
        verbDrillExit()
    }
    VerbDrillScreen(
        viewModel = verbDrillVm,
        onBack = verbDrillExit,
        onStartSession = remember { { cards: List<VerbDrillCard> ->
            vm.startVerbDrillSession(cards)
            vm.setReturnTo(Routes.VERB_DRILL)
            onNavigate(Routes.TRAINING)
        } }
    )
}

@Composable
internal fun VocabDrillDestination(
    state: TrainingUiState,
    vm: TrainingViewModel,
    currentRoute: String,
    onNavigateResetStack: (String) -> Unit
) {
    val vocabDrillVm = viewModel<VocabDrillViewModel>()
    val packId = state.navigation.activePackId
    LaunchedEffect(packId, state.navigation.selectedLanguageId) {
        if (packId != null) {
            vocabDrillVm.reloadForPack(packId.value, state.navigation.selectedLanguageId?.value ?: "en")
        } else {
            vocabDrillVm.reloadForLanguage(state.navigation.selectedLanguageId?.value ?: "en")
        }
    }
    val vocabExit = remember(vocabDrillVm) {
        {
            if (vocabDrillVm.hasRatedCards) {
                vm.refreshVocabMasteryCount()
            }
            vm.refreshStreakFromStore()
            onNavigateResetStack(Routes.HOME)
        }
    }
    BackHandler {
        ScreenLogger.nav(currentRoute, "BACK", trigger = "back_press")
        AuditLogger.getInstanceOrNull()?.backPress("vocab_drill", "exit_to_home")
        vocabExit()
    }
    VocabDrillScreen(
        viewModel = vocabDrillVm,
        onBack = vocabExit,
        hintLevel = state.cardSession.hintLevel,
        textScale = state.audio.ruTextScale,
        voiceAutoStart = state.audio.voiceAutoStart,
        onAcquireSpeechRoute = vm.audio::acquireSpeechRoute,
        onClearBadSentences = vocabDrillVm::clearBadSentences,
        badSentenceCount = vocabDrillVm.getBadSentenceCount()
    )
}

@Composable
internal fun AuxDrillDestination(
    state: TrainingUiState,
    vm: TrainingViewModel,
    onNavigate: (String) -> Unit,
    onNavigateResetStack: (String) -> Unit
) {
    val auxVm = viewModel<AuxDrillViewModel>()
    val packId = state.navigation.activePackId
    LaunchedEffect(packId, state.navigation.selectedLanguageId) {
        if (packId != null) {
            auxVm.reloadForPack(packId.value, state.navigation.selectedLanguageId?.value ?: "it")
        }
    }
    val auxExit = remember(auxVm) {
        {
            onNavigateResetStack(Routes.HOME)
        }
    }
    BackHandler {
        auxExit()
    }
    AuxDrillScreen(
        viewModel = auxVm,
        onBack = auxExit,
        onStartTraining = remember(auxVm) { { pair ->
            val deck = auxVm.sessionCardsFor(pair)
            if (!deck.isNullOrEmpty()) {
                // Record shown cards for aux-scoped progress, then hand the
                // deck to the shared training session (same path as Verb Drill).
                auxVm.recordShown(pair, deck.map { it.id }.toSet())
                vm.startVerbDrillSession(deck)
                vm.setReturnTo(Routes.AUX_DRILL)
                onNavigate(Routes.TRAINING)
            }
        } }
    )
}

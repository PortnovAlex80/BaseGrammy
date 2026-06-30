package com.alexpo.grammermate.feature.boss

/**
 * Command types for [BossOrchestrator] methods.
 * Replaces [BossCallbacks] — each method returns a list of commands
 * instead of calling callbacks.
 */
sealed class BossCommand {
    object PauseTimer : BossCommand()
    object ResumeTimer : BossCommand()
    object SaveProgress : BossCommand()
    object BuildSessionCards : BossCommand()
    object RefreshFlowerStates : BossCommand()
    /** Reset boss state to defaults (feature-owned state reset). */
    object ResetBoss : BossCommand()
    /** Reset daily session to defaults (feature-owned state reset). */
    object ResetDailySession : BossCommand()
    /** Reset story state to defaults (feature-owned state reset). */
    object ResetStory : BossCommand()
    /** Reset vocab sprint state to defaults (feature-owned state reset). */
    object ResetVocabSprint : BossCommand()
    data class Composite(val commands: List<BossCommand>) : BossCommand()
}

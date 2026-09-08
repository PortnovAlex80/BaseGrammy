package com.alexpo.grammermate.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.R
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Phase 2 exit criteria (docs/architecture/ARCHITECTURE_REVIEW_2026-09-08.md):
 * a click-UI test walks HOME → roadmap → chapter → lesson → training → back,
 * and every hop lands on the documented screen, repeatably.
 *
 * This drives the REAL navigation shell (GrammarMateApp + NavHost +
 * BackHandlers) with the real TrainingViewModel seeded from the bundled
 * chapters pack (EN_ENGLISH_FULL_COURSE), so it pins:
 * - F2: system back on lesson training shows the exit confirmation dialog
 *   (today an inner BackHandler silently finishes the session — red anchor);
 * - F3: after confirming exit the user lands on the lesson list, not HOME.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class NavigationJourneyClickUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var vm: TrainingViewModel

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication() as Application
        vm = TrainingViewModel(app)
        rule.setContent { GrammarMateTheme { GrammarMateApp(vm) } }
        rule.waitUntil(240_000) {
            // The VM init parks its state-seeding continuation on the
            // Robolectric main looper; drain it from each poll.
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            !vm.uiState.value.isLoading
        }
    }

    @Test
    fun home_roadmap_chapter_lesson_training_back_showsExitDialog() {
        // ── fresh install: the welcome dialog overlays HOME — dismiss it ──
        val skipLabel = rule.activity.getString(R.string.dialog_welcome_skip)
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(skipLabel).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(skipLabel).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(skipLabel).fetchSemanticsNodes().isEmpty()
        }

        // ── HOME: select the chapters pack ────────────────────────────────
        rule.waitUntil(30_000) {
            vm.packTiles.value.any { it.packId == "EN_ENGLISH_FULL_COURSE" }
        }
        if (rule.onAllNodesWithText("English Full Course").fetchSemanticsNodes().isEmpty()) {
            // Zero state: language cards first, then pack tiles.
            rule.onAllNodesWithText("English")[0].performClick()
            rule.waitUntil(10_000) {
                rule.onAllNodesWithText("English Full Course").fetchSemanticsNodes().isNotEmpty()
            }
        }
        rule.onNodeWithText("English Full Course").performClick()
        rule.waitUntil(10_000) {
            vm.uiState.value.navigation.activePackId?.value == "EN_ENGLISH_FULL_COURSE"
        }

        // ── HOME renders the Grammar Story Roadmap (2.8: HOME sub-state) ──
        rule.waitUntil(10_000) { vm.chapterCards.value.isNotEmpty() }
        // Chapters live in a LazyColumn — scroll the first actionable chapter
        // ("Continue") into composition before querying it.
        rule.onNode(hasScrollAction())
            .performScrollToNode(hasAnyDescendant(hasTextExactly("Continue")))

        // ── Continue → CHAPTER_LESSONS (first chapter that has lessons) ──
        rule.onAllNodesWithText("Continue")[0].performClick()
        rule.waitUntil(10_000) {
            vm.uiState.value.navigation.selectedChapter?.lessons?.isNotEmpty() == true
        }
        val chapter = vm.uiState.value.navigation.selectedChapter!!

        // ── lesson card → LESSON roadmap ─────────────────────────────────
        val lesson = vm.uiState.value.navigation.lessons.first { it.id.value in chapter.lessons }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(lesson.title).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText(lesson.title)[0].performClick()
        rule.waitUntil(10_000) {
            vm.uiState.value.navigation.selectedLessonId?.value == lesson.id.value
        }

        // ── first sub-lesson tile → TRAINING ──────────────────────────────
        // Tiles are clickable Cards that MERGE descendant semantics, so the
        // inner Texts must be queried on the unmerged tree.
        val newLabel = rule.activity.getString(R.string.roadmap_new)
        val firstTile = hasClickAction() and hasAnyDescendant(hasTextExactly("1"))
        rule.waitUntil(10_000) {
            rule.onAllNodes(firstTile, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(firstTile, useUnmergedTree = true)[0].performClick()
        rule.waitUntil(15_000) { vm.uiState.value.cardSession.currentCard != null }
        // The TRAINING screen (input field) must actually be composed —
        // currentCard state alone does not prove the route settled.
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("input_field").fetchSemanticsNodes().isNotEmpty()
        }

        // ── system back → exit confirmation dialog (F2 regression) ────────
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

        val exitTitle = rule.activity.getString(R.string.dialog_exit_title_training)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(exitTitle).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(exitTitle).assertIsDisplayed()

        // ── confirm exit → back on the lesson list (F3) ──────────────────
        rule.onNodeWithText(rule.activity.getString(R.string.dialog_exit_confirm)).performClick()
        // finishSession() pauses and keeps the first card for resume — the
        // observable is the SCREEN: TRAINING's input is gone…
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("input_field").fetchSemanticsNodes().isEmpty()
        }
        // The lesson roadmap (sub-lesson tiles) is visible again, not HOME.
        rule.waitUntil(10_000) {
            rule.onAllNodes(
                hasClickAction() and hasAnyDescendant(hasTextExactly("1")) and
                    hasAnyDescendant(hasTextExactly(newLabel)),
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

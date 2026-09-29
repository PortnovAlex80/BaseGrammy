"""Focused examples for the CI import guard."""

from pathlib import Path
import unittest

from check_package_boundaries import check_source


class PackageBoundaryTest(unittest.TestCase):
    def test_business_layers_reject_ui_compose_and_unlisted_feature_edges(self):
        source = """import com.alexpo.grammermate.ui.TrainingViewModel
import androidx.compose.runtime.Composable
import com.alexpo.grammermate.feature.vocab.VocabSprintRunner
"""
        self.assertEqual(3, len(check_source(Path("feature/training/SessionRunner.kt"), source)))
        self.assertEqual(1, len(check_source(
            Path("data/LessonStore.kt"), "import com.alexpo.grammermate.feature.training.SessionRunner"
        )))
        self.assertEqual(1, len(check_source(
            Path("data/LessonStore.kt"), "import com.alexpo.grammermate.shared.TrainingStateAccess"
        )))
        self.assertEqual(1, len(check_source(
            Path("shared/audio/AudioCoordinator.kt"),
            "import com.alexpo.grammermate.feature.backgroundvocab.StoryAudioResolver"
        )))

    def test_listed_edges_and_preview_are_allowed(self):
        self.assertEqual([], check_source(
            Path("feature/daily/DailyPracticeCoordinator.kt"),
            "import com.alexpo.grammermate.feature.progress.StreakManager"
        ))
        self.assertEqual([], check_source(
            Path("ui/designsystem/preview/FoundationPreviews.kt"),
            "import com.alexpo.grammermate.data.ThemeMode"
        ))

    def test_primitives_and_cross_feature_ui_are_isolated(self):
        self.assertEqual(2, len(check_source(
            Path("ui/designsystem/atoms/Example.kt"),
            "import com.alexpo.grammermate.data.ThemeMode\n"
            "import com.alexpo.grammermate.ui.TrainingViewModel"
        )))
        self.assertEqual(1, len(check_source(
            Path("ui/features/training/components/Example.kt"),
            "import com.alexpo.grammermate.ui.features.vocab.components.VocabBadges"
        )))


if __name__ == "__main__":
    unittest.main()

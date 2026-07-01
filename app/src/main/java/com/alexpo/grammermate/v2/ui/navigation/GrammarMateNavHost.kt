package com.alexpo.grammermate.v2.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.alexpo.grammermate.v2.feature.home.HomeScreen
import com.alexpo.grammermate.v2.feature.training.TrainingScreen
import com.alexpo.grammermate.v2.ui.components.PlaceholderScreen

/**
 * NavHost приложения GrammarMate v2 — регистрирует все [Destination]'ы.
 *
 * Навигация — presentation-слой: здесь маршруты превращаются в composable-экраны.
 * Аргументы (packId/lessonId/…) приходят как String и оборачиваются в value-class
 * на границе экрана ([HomeScreen]/[TrainingScreen]).
 *
 * Каждый composable-destination = один экран. Экраны, которые ещё не реализованы
 * (Settings, VerbDrill, DailyPractice, ChapterLessons), отрисовывают
 * [PlaceholderScreen] с TODO-меткой — это держит весь nav-graph компилируемым и
 * кликабельным, пока реальные экраны не готовы.
 *
 * @param navController контроллер навигации (создаётся в [com.alexpo.grammermate.v2.ui.GrammarMateApp]).
 * @param modifier      модификатор для растягивания NavHost в Scaffold.
 */
@Composable
fun GrammarMateNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Destination.START_ROUTE,
        modifier = modifier,
    ) {
        // ── Home: сетка паков ─────────────────────────────────────────────────
        composable(Destination.Home.routePattern) {
            HomeScreen(
                onPackClick = { packId ->
                    // TODO(Фаза 7): переход на ChapterLessons(packId, firstChapterId),
                    //   пока данных по главам нет — сразу в тренировку первого урока.
                    //   Используем заглушку lessonId = packId, чтобы стек не падал.
                    navController.navigate(Destination.Training(packId, packId).route())
                },
                onNavigateSettings = {
                    navController.navigate(Destination.Settings.route())
                },
            )
        }

        // ── ChapterLessons: уроки главы (TODO — полный экран) ─────────────────
        composable(
            route = Destination.ChapterLessons.PATTERN,
            arguments = listOf(
                navArgument(Destination.ARG_PACK_ID) { type = NavType.StringType },
                navArgument(Destination.ARG_CHAPTER_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val packId = entry.arguments?.getString(Destination.ARG_PACK_ID).orEmpty()
            val chapterId = entry.arguments?.getString(Destination.ARG_CHAPTER_ID).orEmpty()
            PlaceholderScreen("ChapterLessons\npack=$packId\nchapter=$chapterId")
        }

        // ── Training: тренировка карточек урока ───────────────────────────────
        composable(
            route = Destination.Training.PATTERN,
            arguments = listOf(
                navArgument(Destination.ARG_PACK_ID) { type = NavType.StringType },
                navArgument(Destination.ARG_LESSON_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val packId = entry.arguments?.getString(Destination.ARG_PACK_ID).orEmpty()
            val lessonId = entry.arguments?.getString(Destination.ARG_LESSON_ID).orEmpty()
            TrainingScreen(
                packId = packId,
                lessonId = lessonId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // ── Settings (TODO — полный экран) ────────────────────────────────────
        composable(Destination.Settings.routePattern) {
            PlaceholderScreen("Settings")
        }

        // ── VerbDrill (TODO — полный экран) ───────────────────────────────────
        composable(
            route = Destination.VerbDrill.PATTERN,
            arguments = listOf(navArgument(Destination.ARG_PACK_ID) { type = NavType.StringType }),
        ) { entry ->
            val packId = entry.arguments?.getString(Destination.ARG_PACK_ID).orEmpty()
            PlaceholderScreen("VerbDrill\npack=$packId")
        }

        // ── DailyPractice (TODO — полный экран) ───────────────────────────────
        composable(
            route = Destination.DailyPractice.PATTERN,
            arguments = listOf(navArgument(Destination.ARG_PACK_ID) { type = NavType.StringType }),
        ) { entry ->
            val packId = entry.arguments?.getString(Destination.ARG_PACK_ID).orEmpty()
            PlaceholderScreen("DailyPractice\npack=$packId")
        }
    }
}

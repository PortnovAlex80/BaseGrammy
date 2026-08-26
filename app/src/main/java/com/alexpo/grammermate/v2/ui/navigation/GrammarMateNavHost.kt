package com.alexpo.grammermate.v2.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.alexpo.grammermate.v2.feature.home.HomeScreen
import com.alexpo.grammermate.v2.feature.packcontent.PackContentScreen
import com.alexpo.grammermate.v2.feature.training.TrainingScreen
import com.alexpo.grammermate.v2.ui.components.InvalidRouteScreen
import com.alexpo.grammermate.v2.ui.components.PlaceholderScreen

/**
 * NavHost приложения GrammarMate v2 — регистрирует все [Destination]'ы.
 *
 * Навигация — presentation-слой: маршруты превращаются в composable-экраны.
 * Аргументы (packId/lessonId/…) приходят как String и оборачиваются в value-class
 * на границе экрана.
 *
 * Golden journey (Фаза 1 плана стабилизации 2026-08-26):
 * **Home → PackContent (главы + уроки) → Training(packId, lessonId)** —
 * реальный `lessonId`, а не `packId`-заглушка (P0-дефект закрыт).
 *
 * Экраны, которые ещё не реализованы (Settings, VerbDrill, DailyPractice),
 * отрисовывают [PlaceholderScreen] — их маршруты не публикуются как доступные
 * действия из golden journey.
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
                    // launchSingleTop: двойной тап по паку не создаёт дубль
                    // destination в back stack (гейт Фазы 3 «без дублей»).
                    navController.navigate(Destination.PackContent(packId).route()) {
                        launchSingleTop = true
                    }
                },
                onNavigateSettings = {
                    navController.navigate(Destination.Settings.route()) {
                        launchSingleTop = true
                    }
                },
            )
        }

        // ── PackContent: главы + уроки пака ───────────────────────────────────
        composable(
            route = Destination.PackContent.PATTERN,
            arguments = listOf(
                navArgument(Destination.ARG_PACK_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            // Typed route-аргумент (Фаза 3): required ID без `orEmpty()` —
            // невалидный/deep-link-маршрут получает явный InvalidRoute, а не
            // экран с пустым идентификатором.
            val packId = entry.requiredId(Destination.ARG_PACK_ID)
                ?: return@composable InvalidRouteScreen(
                    missing = Destination.ARG_PACK_ID,
                    onBack = { navController.popBackStack() },
                )
            PackContentScreen(
                packId = packId,
                onNavigateBack = { navController.popBackStack() },
                onOpenLesson = { lessonId ->
                    // launchSingleTop: повторный тап по уроку не дублирует
                    // Training (две VM параллельно писали бы одну сессию).
                    navController.navigate(Destination.Training(packId, lessonId).route()) {
                        launchSingleTop = true
                    }
                },
            )
        }

        // ── Training: тренировка карточек урока ───────────────────────────────
        composable(
            route = Destination.Training.PATTERN,
            arguments = listOf(
                navArgument(Destination.ARG_PACK_ID) { type = NavType.StringType },
                navArgument(Destination.ARG_LESSON_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val packId = entry.requiredId(Destination.ARG_PACK_ID)
                ?: return@composable InvalidRouteScreen(
                    missing = Destination.ARG_PACK_ID,
                    onBack = { navController.popBackStack() },
                )
            val lessonId = entry.requiredId(Destination.ARG_LESSON_ID)
                ?: return@composable InvalidRouteScreen(
                    missing = Destination.ARG_LESSON_ID,
                    onBack = { navController.popBackStack() },
                )
            TrainingScreen(
                packId = packId,
                lessonId = lessonId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // ── Settings (TODO — полный экран, Фаза 3) ────────────────────────────
        composable(Destination.Settings.routePattern) {
            PlaceholderScreen("Settings")
        }

        // ── VerbDrill (TODO — вертикальный срез Фазы 4) ───────────────────────
        composable(
            route = Destination.VerbDrill.PATTERN,
            arguments = listOf(navArgument(Destination.ARG_PACK_ID) { type = NavType.StringType }),
        ) { entry ->
            val packId = entry.arguments?.getString(Destination.ARG_PACK_ID).orEmpty()
            PlaceholderScreen("VerbDrill\npack=$packId")
        }

        // ── DailyPractice (TODO — вертикальный срез Фазы 4) ───────────────────
        composable(
            route = Destination.DailyPractice.PATTERN,
            arguments = listOf(navArgument(Destination.ARG_PACK_ID) { type = NavType.StringType }),
        ) { entry ->
            val packId = entry.arguments?.getString(Destination.ARG_PACK_ID).orEmpty()
            PlaceholderScreen("DailyPractice\npack=$packId")
        }
    }
}

/**
 * Обязательный строковый nav-аргумент (Фаза 3 плана: «запретить `orEmpty()`
 * для required IDs»). Невалидный deep-link/restore не должен попадать в экран
 * с пустым идентификатором — вызывающий показывает InvalidRouteScreen.
 */
private fun NavBackStackEntry.requiredId(key: String): String? =
    arguments?.getString(key)?.takeIf { it.isNotBlank() }

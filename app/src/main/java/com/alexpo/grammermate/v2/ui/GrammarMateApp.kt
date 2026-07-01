package com.alexpo.grammermate.v2.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.alexpo.grammermate.v2.core.ui.collectState
import com.alexpo.grammermate.v2.ui.navigation.Destination
import com.alexpo.grammermate.v2.ui.navigation.GrammarMateNavHost
import com.alexpo.grammermate.v2.ui.theme.GrammarMateTheme

/**
 * Корневой composable приложения GrammarMate v2 — точка подключения к
 * [com.alexpo.grammermate.v2.MainActivityV2] (`setContent { GrammarMateApp() }`).
 *
 * Архитектура:
 *  1. **Тема** ([GrammarMateTheme]) — оборачивает всё: цветовая схема, типографика,
 *     шкала отступов, dynamic color, edge-to-edge системные бары. Режим темы
 *     (LIGHT/DARK/SYSTEM) берётся реактивно из настроек через [AppViewModel].
 *  2. **Scaffold** — каркас Material 3: content + адаптивная навигация.
 *  3. **Адаптивная навигация** — зависит от ширины окна (Material 3 Adaptive):
 *     - [WindowWidthSizeClass.COMPACT] (телефон) — [NavigationBar] внизу;
 *     - medium/expanded (планшет/фолдабл) — [NavigationRail] слева.
 *     Маршруты навигации живут в [GrammarMateNavHost].
 *
 * @param appViewModel app-scoped ViewModel для глобального state (тема).
 */
@Composable
fun GrammarMateApp(
    appViewModel: AppViewModel = hiltViewModel(),
) {
    // ── Тема: режим из настроек пользователя (SYSTEM по умолчанию). ───────────
    val appState = collectState(appViewModel.state)

    GrammarMateTheme(themeMode = appState.themeMode) {
        // ── Адаптивный выбор chrome: bottom bar (телефон) или rail (планшет). ──
        val windowWidthSizeClass = currentWindowAdaptiveInfo()
            .windowSizeClass.windowWidthSizeClass
        // Compact = телефон (bottom bar); Medium/Expanded = планшет/фолдабл (rail).
        val useNavRail = windowWidthSizeClass != WindowWidthSizeClass.COMPACT

        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        // Активный таб (Home / Settings) — для подсветки в bar/rail.
        val selectedTab = remember(currentRoute) { resolveTopTab(currentRoute) }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            AdaptiveNavigationScaffold(
                useNavRail = useNavRail,
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    navController.navigate(tab.destination.route()) {
                        // Bottom/rail-навигация: поднимаемся до стартового, сохраняем
                        // и восстанавливаем state, чтобы не плодить копии экранов.
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                scaffoldPadding = innerPadding,
            ) { contentPadding ->
                GrammarMateNavHost(
                    navController = navController,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                )
            }
        }
    }
}

/**
 * Доступные top-level табы навигации (bottom bar / nav rail).
 *
 * Лимитированный набор — только основные точки входа; глубокие экраны (Training,
 * ChapterLessons, drill'ы) открываются изнутри и не имеют постоянного таба.
 *
 * @property label      человекочитаемое имя таба.
 * @property destination маршрут [Destination], на который ведёт таб.
 */
private enum class TopTab(val label: String, val destination: Destination) {
    /** Главная — обучение/паки. */
    HOME("Учить", Destination.Home),

    /** Настройки. */
    SETTINGS("Настройки", Destination.Settings),
}

/** Иконка таба. */
private val TopTab.icon: ImageVector
    get() = when (this) {
        TopTab.HOME -> Icons.Filled.School
        TopTab.SETTINGS -> Icons.Filled.Settings
    }

/**
 * Сопоставить текущий nav-route с активным [TopTab] (подсветка в bar/rail).
 *
 * Экраны, не являющиеся табами (Training и т.д.), наследуют подсветку от Home,
 * т.к. они вложены в него по IA (обучение → тренировка).
 */
private fun resolveTopTab(currentRoute: String?): TopTab =
    when {
        currentRoute == null -> TopTab.HOME
        currentRoute.startsWith(Destination.ROUTE_SETTINGS) -> TopTab.SETTINGS
        else -> TopTab.HOME
    }

/**
 * Адаптивный контейнер навигации: [NavigationRail] (medium+) или
 * [NavigationBar] (compact) + контент.
 *
 * Отрисовывает только навигационный chrome; сам контент передаётся в [content]
 * как `PaddingValues`, чтобы экраны сами занимали оставшееся пространство.
 *
 * @param useNavRail      true → rail (планшет/фолдабл), false → bottom bar (телефон).
 * @param selectedTab     текущий активный таб.
 * @param onTabSelected   колбэк выбора таба.
 * @param scaffoldPadding padding от внешнего Scaffold (status/gesture bars).
 * @param content         lambda, отрисовывающая [GrammarMateNavHost].
 */
@Composable
private fun AdaptiveNavigationScaffold(
    useNavRail: Boolean,
    selectedTab: TopTab,
    onTabSelected: (TopTab) -> Unit,
    scaffoldPadding: PaddingValues,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (useNavRail) {
        // Medium/Expanded: навигационная rail слева + контент справа.
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(modifier = Modifier.padding(scaffoldPadding)) {
                TopTab.entries.forEach { tab ->
                    NavigationRailItem(
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
            content(PaddingValues(0.dp))
        }
    } else {
        // Compact: контент (растягивается) + навигационная bottom-bar.
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .weight(1f, fill = true),
            ) {
                content(PaddingValues(0.dp))
            }
            NavigationBar {
                TopTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    }
}

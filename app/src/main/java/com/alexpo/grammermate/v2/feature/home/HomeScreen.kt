package com.alexpo.grammermate.v2.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alexpo.grammermate.domain.model.Pack
import com.alexpo.grammermate.domain.model.PackLessonProgress
import com.alexpo.grammermate.v2.core.ui.collectState

/**
 * Экран Home — сетка доступных паков обучения (точка входа в приложение).
 *
 * Presentation-слой: читает [HomeViewState] из [HomeViewModel] через
 * [collectState] (lifecycle-aware) и перерисовывается при изменениях. Вся
 * интерактивность — stateless: клики прокидываются наружу через колбэки
 * ([onPackClick]/[onNavigateSettings]), навигация владеется родителем.
 *
 * Три состояния отображения:
 *  * **Loading** — центрированный спиннер (паки грузятся из Room).
 *  * **Error** — сообщение + retry-подсказка.
 *  * **Content** — адаптивная сетка карточек паков [LazyVerticalGrid].
 *
 * @param onPackClick         колбэк выбора пака (передаётся `packId.value`).
 * @param onNavigateSettings  колбэк перехода в настройки.
 * @param viewModel           Home-ViewModel (Hilt-injected по умолчанию).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPackClick: (String) -> Unit,
    onNavigateSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state = collectState(viewModel.state)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("GrammarMate", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = onNavigateSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Настройки",
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(innerPadding))
            state.error != null -> ErrorState(
                message = state.error!!,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(innerPadding),
            )

            state.packs.isEmpty() -> EmptyState(modifier = Modifier.padding(innerPadding))
            else -> PacksGrid(
                packs = state.packs,
                packProgress = state.packProgress,
                onPackClick = onPackClick,
                contentPadding = innerPadding,
            )
        }
    }
}

/**
 * Сетка карточек паков — адаптивная (2 колонки на телефоне, шире — авто).
 *
 * [GridCells.Adaptive] подбирает число колонок под ширину, переиспользуя ячейки,
 * что даёт «естественную» адаптивность без ручных window-size проверок.
 */
@Composable
private fun PacksGrid(
    packs: List<Pack>,
    packProgress: Map<String, PackLessonProgress>,
    onPackClick: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = packs,
            key = { it.id.value },
        ) { pack ->
            PackCard(
                pack = pack,
                progress = packProgress[pack.id.value],
                onClick = { onPackClick(pack.id.value) },
            )
        }
    }
}

/**
 * Стабильный test-tag карточки пака: один тег на ВСЕ карточки LazyGrid —
 * journey-тесты адресуют элементы через `onAllNodesWithTag`, не `onNodeWithTag`.
 */
const val HOME_PACK_CARD_TAG = "home_pack_card"

/** Кнопка Retry в error-state Home (Фаза 3). */
const val HOME_RETRY_BUTTON_TAG = "home_retry_button"

/** Test-tag полосы прогресса внутри карточки пака (ADR-002 слой 1). */
const val HOME_PACK_PROGRESS_TAG = "home_pack_progress"

/**
 * Карточка одного пака обучения.
 *
 * Красивая заливка `primaryContainer` + закруглённые углы + тонкая тень.
 * Прогресс — реактивный `completedLessons/totalLessons` (Фаза 3 slice 2,
 * ADR-002 слой 1); null = уроков с данными нет (0%).
 * Имя пака берётся из [Pack.displayName] либо fallback на `id.value`.
 *
 * Statelesss: клик пробрасывается в [onClick], карточка сама ничего не мутирует.
 */
@Composable
private fun PackCard(
    pack: Pack,
    progress: PackLessonProgress?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HOME_PACK_CARD_TAG),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = pack.displayName ?: pack.id.value,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Язык: ${pack.languageId.value.uppercase()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            // Реактивный прогресс (ADR-002 слой 1): completedAtMs-агрегат.
            LinearProgressIndicator(
                progress = { progress?.fraction ?: 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(HOME_PACK_PROGRESS_TAG),
            )
            Text(
                text = "Уроков пройдено: ${progress?.completedLessons ?: 0} / ${progress?.totalLessons ?: 0}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
            )
        }
    }
}

/** Состояние загрузки — центрированный спиннер. */
@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** Состояние ошибки — сообщение по центру. */
@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "Упс!",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Фаза 3 плана: Retry повторяет неуспешную операцию (re-query),
            // а не работает кнопкой Back.
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .testTag(HOME_RETRY_BUTTON_TAG)
                    .padding(top = 8.dp),
            ) {
                Text("Повторить")
            }
        }
    }
}

/** Состояние «нет контента» — подсказка импортировать пак. */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Пока нет установленных паков.\nИмпортируйте курс, чтобы начать обучение.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

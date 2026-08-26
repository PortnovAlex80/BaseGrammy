package com.alexpo.grammermate.v2.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.alexpo.grammermate.v2.ui.theme.spacing

/**
 * Явный экран невалидного маршрута (Фаза 3 плана: «недоступный или
 * незавершённый route не показывается пользователю» + «запретить `orEmpty()`
 * для required IDs»).
 *
 * Сюда попадает deep-link/restore с отсутствующим или пустым обязательным
 * nav-аргументом: раньше такой маршрут «оживал» с пустым packId/lessonId и
 * вёл к пустым экранам; теперь пользователь видит честное сообщение и выход.
 *
 * @param missing имя отсутствующего аргумента (для диагностики в тексте).
 * @param onBack  выход (popBackStack) — повторная попытка невозможна, маршрута нет.
 */
@Composable
fun InvalidRouteScreen(
    missing: String,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.large),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Маршрут недоступен",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Ссылка неполная (отсутствует «$missing»). " +
                    "Откройте обучение из списка паков.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MaterialTheme.spacing.small),
            )
            Button(
                onClick = onBack,
                modifier = Modifier
                    .padding(top = MaterialTheme.spacing.medium)
                    .semantics { contentDescription = "invalid_route_back_button" },
            ) {
                Text("Вернуться")
            }
        }
    }
}

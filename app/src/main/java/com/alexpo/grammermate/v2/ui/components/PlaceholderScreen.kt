package com.alexpo.grammermate.v2.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.alexpo.grammermate.v2.ui.theme.spacing

/**
 * Заглушка для нереализованных экранов.
 *
 * Отрисовывает centred TODO-метку с именем экрана, чтобы навигационный граф был
 * полностью кликабельным, пока реальные экраны (Settings, VerbDrill,
 * DailyPractice, ChapterLessons) не готовы. Это намеренно убирает «магические»
 * пустые composable и держит сборку зелёной.
 *
 * @param label текст-метка (обычно имя destination + его аргументы).
 */
@Composable
fun PlaceholderScreen(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.large),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

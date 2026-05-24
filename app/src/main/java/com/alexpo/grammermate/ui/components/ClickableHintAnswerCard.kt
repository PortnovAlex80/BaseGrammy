package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.ClickableText

/**
 * Расширенная версия HintAnswerCard с кликабельными словами.
 *
 * Заменяет обычный HintAnswerCard для показа интерактивных подсказок:
 * - Слова из drill файлов подсвечиваются по рангу (3 цвета)
 * - Клик на подсвеченное слово открывает WordInfoPopup
 * - Использует существующий дизайн (розовая карточка)
 */
@Composable
fun ClickableHintAnswerCard(
    answerText: String,
    wordsInfo: Map<String, WordHint>,
    onWordClick: (String, WordHint) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Заголовок ответа
            Text(
                text = "Ответ: ",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )

            // Кликабельный текст ответа с подсветкой
            var showPopupForWord by remember { mutableStateOf<String?>(null) }
            var showPopupForHint by remember { mutableStateOf<WordHint?>(null) }

            ClickableAnswerText(
                text = answerText,
                wordsInfo = wordsInfo,
                onWordClick = { word, hint ->
                    showPopupForWord = word
                    showPopupForHint = hint
                }
            )

            // Popup для показа информации о слове
            if (showPopupForWord != null && showPopupForHint != null) {
                WordInfoPopup(
                    word = showPopupForWord,
                    hint = showPopupForHint,
                    onDismiss = {
                        showPopupForWord = null
                        showPopupForHint = null
                    }
                )
            }
        }
    }
}

/**
 * Кликабельный текст с подсветкой слов по рангу.
 *
 * Подсветка:
 * - A1 (ранг 1-500): Зеленый
 * - A2 (ранг 501-2000): Синий
 * - B1+ (ранг 2001+): Красный + жирный
 */
@Composable
private fun ClickableAnswerText(
    text: String,
    wordsInfo: Map<String, WordHint>,
    onWordClick: (String, WordHint) -> Unit
) {
    val words = text.split(" ")
    val textLayoutResult = androidx.compose.runtime.mutableStateOf<TextLayoutResult?>(null)

    // Создаем аннотированную строку с цветовой подсветкой
    val annotatedString = buildAnnotatedString {
        words.forEachIndexed { index, word ->
            val cleanWord = word.replace(Regex("[.,!?;:]"), "")
            val hint = wordsInfo[cleanWord]

            if (hint != null) {
                // Определяем цвет по рангу
                val (color, fontWeight) = when {
                    hint.rank <= 500 -> {
                        // A1 - зеленый
                        Color.Green.copy(alpha = 0.4f) to FontWeight.Normal
                    }
                    hint.rank <= 2000 -> {
                        // A2 - синий
                        Color.Blue.copy(alpha = 0.6f) to FontWeight.Medium
                    }
                    else -> {
                        // B1+ - красный + жирный
                        Color.Red.copy(alpha = 0.8f) to FontWeight.Bold
                    }
                }

                // Подсветить слово
                withStyle(
                    style = SpanStyle(
                        color = color.first,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = color.second
                    )
                ) {
                    append(word)
                }

                // Добавить тег для клика
                addStringAnnotation(
                    tag = "word_click_$index",
                    annotation = cleanWord
                )
            } else {
                // Обычные слова - обычный текст
                append(word)
            }

            // Добавить пробел (кроме последнего слова)
            if (index < words.size - 1) {
                append(" ")
            }
        }
    }

    // Текст с отслеживанием позиций для кликов
    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.error
        ),
        onClick = { offset ->
            // Найти слово по позиции клика
            textLayoutResult.value?.let { layoutResult ->
                val clickedWord = findWordAtOffset(text, offset, layoutResult)
                clickedWord?.let { cleanWord ->
                    wordsInfo[cleanWord]?.let { hint ->
                        onWordClick(cleanWord, hint)
                    }
                }
            }
        },
        onTextLayout = { textLayoutResult.value = it }
    )
}

/**
 * Найти слово по позиции клика в тексте.
 */
private fun findWordAtOffset(
    text: String,
    offset: Int,
    layoutResult: TextLayoutResult
): String? {
    val words = text.split(" ")
    var currentOffset = 0

    for (word in words) {
        val startOffset = currentOffset
        val endOffset = startOffset + word.length

        if (offset in startOffset until endOffset) {
            return word.replace(Regex("[.,!?;:]"), "")
        }

        currentOffset = endOffset + 1 // +1 for space
    }

    return null
}

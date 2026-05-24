package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

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
            Text(
                text = "Ответ: ",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )

            ClickableAnswerText(
                text = answerText,
                wordsInfo = wordsInfo,
                onWordClick = onWordClick
            )
        }
    }
}

@Composable
private fun ClickableAnswerText(
    text: String,
    wordsInfo: Map<String, WordHint>,
    onWordClick: (String, WordHint) -> Unit
) {
    val words = text.split(Regex("\\s+"))
    val annotatedString = buildAnnotatedString {
        words.forEachIndexed { index, word ->
            val cleanWord = word.cleanLookupWord()
            val hint = wordsInfo[cleanWord]

            if (hint != null) {
                val color = when {
                    hint.rank <= 500 -> Color(0xFF2E7D32)
                    hint.rank <= 2000 -> Color(0xFF1565C0)
                    else -> Color(0xFFC62828)
                }
                val fontWeight = when {
                    hint.rank <= 500 -> FontWeight.Normal
                    hint.rank <= 2000 -> FontWeight.Medium
                    else -> FontWeight.Bold
                }
                val start = length
                withStyle(
                    SpanStyle(
                        color = color,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = fontWeight
                    )
                ) {
                    append(word)
                }
                addStringAnnotation(
                    tag = WORD_TAG,
                    annotation = cleanWord,
                    start = start,
                    end = length
                )
            } else {
                append(word)
            }

            if (index < words.lastIndex) append(" ")
        }
    }

    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.error
        ),
        onClick = { offset ->
            annotatedString
                .getStringAnnotations(tag = WORD_TAG, start = offset, end = offset + 1)
                .firstOrNull()
                ?.item
                ?.let { cleanWord ->
                    wordsInfo[cleanWord]?.let { hint ->
                        onWordClick(cleanWord, hint)
                    }
                }
        }
    )
}

private fun String.cleanLookupWord(): String =
    replace(Regex("""[.,!?;:"'\[\](){}]"""), "").lowercase()

private const val WORD_TAG = "word"

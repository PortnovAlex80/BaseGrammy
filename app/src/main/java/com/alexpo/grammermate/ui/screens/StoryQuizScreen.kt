package com.alexpo.grammermate.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.data.StoryPhase
import com.alexpo.grammermate.data.StoryQuiz

@Composable
fun StoryQuizScreen(
    story: StoryQuiz?,
    testMode: Boolean,
    onClose: () -> Unit,
    onComplete: (Boolean) -> Unit
) {
    if (story == null) {
        onClose()
        return
    }
    val selections = remember(story.storyId) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val results = remember(story.storyId) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var questionIndex by remember(story.storyId) { mutableStateOf(0) }
    var showResult by remember(story.storyId) { mutableStateOf(false) }
    var errorMessage by remember(story.storyId) { mutableStateOf<String?>(null) }
    if (story.questions.isEmpty()) {
        onComplete(true)
        return
    }
    val question = story.questions.getOrNull(questionIndex) ?: run {
        val allCorrect = results.value.size == story.questions.size &&
            results.value.values.all { it }
        onComplete(allCorrect)
        return
    }
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = if (story.phase == StoryPhase.CHECK_IN) {
                "Story Check-in"
            } else {
                "Story Check-out"
            },
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = story.text, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Question ${questionIndex + 1} / ${story.questions.size}")
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = question.prompt, fontWeight = FontWeight.SemiBold)
        question.options.forEachIndexed { index, option ->
            val selected = selections.value[question.qId] == index
            val isCorrectOption = index == question.correctIndex
            val optionSuffix = when {
                showResult && isCorrectOption -> " (correct)"
                showResult && selected && !isCorrectOption -> " (your choice)"
                else -> ""
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selections.value = selections.value + (question.qId to index)
                        errorMessage = null
                        if (showResult) {
                            results.value = results.value - question.qId
                            showResult = false
                        }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = if (selected) ">" else " ")
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = option + optionSuffix)
            }
        }
        if (showResult) {
            Spacer(modifier = Modifier.height(8.dp))
            val correctText = question.options.getOrNull(question.correctIndex).orEmpty()
            Text(text = "Correct: $correctText", color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val prevIndex = (questionIndex - 1).coerceAtLeast(0)
                    if (prevIndex != questionIndex) {
                        questionIndex = prevIndex
                        val prevId = story.questions[questionIndex].qId
                        showResult = results.value.containsKey(prevId)
                        errorMessage = null
                    }
                },
                enabled = questionIndex > 0,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Prev")
            }
            Button(
                onClick = {
                    val selected = selections.value[question.qId]
                    if (selected == null) {
                        errorMessage = "Select an answer"
                        return@Button
                    }
                    val correct = selected == question.correctIndex
                    results.value = results.value + (question.qId to correct)
                    showResult = true
                    errorMessage = if (correct) null else "Incorrect"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Check")
            }
            Button(
                onClick = {
                    val selected = selections.value[question.qId]
                    if (selected == null) {
                        errorMessage = "Select an answer"
                        return@Button
                    }
                    val correct = selected == question.correctIndex
                    results.value = results.value + (question.qId to correct)
                    val isLast = questionIndex >= story.questions.lastIndex
                    if (isLast) {
                        val allCorrect = results.value.size == story.questions.size &&
                            results.value.values.all { it }
                        onComplete(testMode || allCorrect)
                        return@Button
                    }
                    questionIndex += 1
                    val nextId = story.questions[questionIndex].qId
                    showResult = results.value.containsKey(nextId)
                    errorMessage = null
                },
                modifier = Modifier.weight(1f)
            ) {
                val isLast = questionIndex >= story.questions.lastIndex
                Text(text = if (isLast) "Finish" else "Next")
            }
        }
        if (scrollState.maxValue > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Scroll to continue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

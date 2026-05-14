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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.R
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
    val selectAnAnswerText = stringResource(R.string.story_select_an_answer)
    val incorrectText = stringResource(R.string.story_incorrect)
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
                stringResource(R.string.story_check_in)
            } else {
                stringResource(R.string.story_check_out)
            },
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = story.text, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.story_question_of, questionIndex + 1, story.questions.size))
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = question.prompt, fontWeight = FontWeight.SemiBold)
        question.options.forEachIndexed { index, option ->
            val selected = selections.value[question.qId] == index
            val isCorrectOption = index == question.correctIndex
            val correctSuffix = stringResource(R.string.story_correct_suffix)
            val yourChoiceSuffix = stringResource(R.string.story_your_choice_suffix)
            val optionSuffix = when {
                showResult && isCorrectOption -> correctSuffix
                showResult && selected && !isCorrectOption -> yourChoiceSuffix
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
            Text(text = stringResource(R.string.story_correct_label, correctText), color = MaterialTheme.colorScheme.primary)
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
                Text(text = stringResource(R.string.story_prev))
            }
            Button(
                onClick = {
                    val selected = selections.value[question.qId]
                    if (selected == null) {
                        errorMessage = selectAnAnswerText
                        return@Button
                    }
                    val correct = selected == question.correctIndex
                    results.value = results.value + (question.qId to correct)
                    showResult = true
                    errorMessage = if (correct) null else incorrectText
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.story_check))
            }
            Button(
                onClick = {
                    val selected = selections.value[question.qId]
                    if (selected == null) {
                        errorMessage = selectAnAnswerText
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
                Text(text = if (isLast) stringResource(R.string.story_finish) else stringResource(R.string.story_next))
            }
        }
        if (scrollState.maxValue > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.story_scroll_to_continue),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

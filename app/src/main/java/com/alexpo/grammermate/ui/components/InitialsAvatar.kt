package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InitialsAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    onClick: () -> Unit = {}
) {
    val initials = buildInitials(name)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = (size.value / 2.5f).sp,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

private fun buildInitials(name: String): String {
    if (name.isBlank()) return "?"
    val words = name.trim().split("\\s+".toRegex())
    return if (words.size >= 2) {
        "${words[0].first()}${words[1].first()}".uppercase()
    } else {
        val single = words[0]
        if (single.length >= 2 && single[0].isUpperCase() && single[1].isLowerCase()) {
            single.take(1).uppercase()
        } else {
            single.take(2).uppercase()
        }
    }
}

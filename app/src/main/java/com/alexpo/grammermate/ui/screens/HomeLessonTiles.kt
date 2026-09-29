package com.alexpo.grammermate.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.FlowerCalculator
import com.alexpo.grammermate.data.FlowerVisual
import com.alexpo.grammermate.shared.AuditLogger
import com.alexpo.grammermate.shared.ScreenLogger
import com.alexpo.grammermate.data.Language
import com.alexpo.grammermate.data.Lesson
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.alexpo.grammermate.ui.components.emeraldGlow
import com.alexpo.grammermate.ui.components.cardOutline

enum class LessonTileState {
    SEED,
    SPROUT,
    FLOWER,
    LOCKED,
    UNLOCKED,  // Available but not started yet (open lock)
    EMPTY,     // No lesson in this slot (pack has fewer than 12 lessons)
    VERB_DRILL
}

data class LessonTileUi(
    val index: Int,
    val lessonId: String?,
    val state: LessonTileState
)

/**
 * Generate initials from user name (first letter of first two words, max 2 chars)
 */
fun getUserInitials(name: String): String {
    return name.trim()
        .split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercase() }
        .joinToString("")
        .ifEmpty { "GM" } // Fallback: GrammarMate
}

@Composable
fun ZeroStateLanguageSelector(
    languages: List<Language>,
    onLanguageSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.home_choose_language),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(languages) { _, language ->
                val flagIcon = when (language.id.value.lowercase()) {
                    "it", "italian" -> R.drawable.ic_flag_it
                    "en", "english" -> R.drawable.ic_flag_en
                    "ru", "russian" -> R.drawable.ic_flag_ru
                    "el", "greek" -> R.drawable.ic_flag_el
                    "de", "german" -> R.drawable.ic_flag_de
                    "zh", "chinese" -> R.drawable.ic_flag_zh
                    else -> R.drawable.ic_flag_en
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clickable { onLanguageSelected(language.id.value) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(flagIcon),
                            contentDescription = language.displayName,
                            modifier = Modifier.size(48.dp, 36.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = language.displayName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LessonTile(
    tile: LessonTileUi,
    flower: FlowerVisual?,
    onSelect: () -> Unit,
    onLockedClick: (() -> Unit)? = null
) {
    val isEmpty = tile.state == LessonTileState.EMPTY

    if (tile.state == LessonTileState.VERB_DRILL) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .cardOutline(MaterialTheme.shapes.medium)
                .clickable(onClick = onSelect),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = stringResource(R.string.home_verb), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = stringResource(R.string.home_verb_drill),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        return
    }

    // Determine emoji and scale based on flower state
    val (emoji, scale) = when {
        isEmpty -> "●" to 1.0f  // gray dot for empty slots
        tile.state == LessonTileState.LOCKED -> "🔒" to 1.0f  // locked
        tile.state == LessonTileState.UNLOCKED -> "🔓" to 1.0f  // unlocked
        flower == null -> "🌱" to 1.0f  // seed default
        else -> FlowerCalculator.getEmoji(flower.state) to flower.scaleMultiplier
    }

    val masteryPercent = flower?.masteryPercent ?: 0f

    // Active/glow states: the next-available lesson and bloomed lessons get an emerald tint.
    val isActive = tile.state == LessonTileState.UNLOCKED
    val isBloomed = masteryPercent > 0f &&
        tile.state != LessonTileState.LOCKED &&
        tile.state != LessonTileState.UNLOCKED &&
        !isEmpty

    // Empty tiles use a faded container
    val containerColor = if (isEmpty) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    } else if (isActive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isEmpty) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    } else if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .then(if (isBloomed || isActive) Modifier.emeraldGlow(radius = 30.dp) else Modifier)
            .cardOutline(MaterialTheme.shapes.medium)
            .then(
                if (isEmpty) Modifier
                else Modifier.clickable {
                    if (tile.state == LessonTileState.LOCKED) {
                        onLockedClick?.invoke()
                    } else {
                        onSelect()
                    }
                }
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "${tile.index + 1}", fontWeight = FontWeight.SemiBold)
            Text(
                text = emoji,
                fontSize = (18 * scale).sp
            )
            // Show mastery percentage if > 0 (but not for locked/unlocked/empty states)
            if (masteryPercent > 0f && tile.state != LessonTileState.LOCKED && tile.state != LessonTileState.UNLOCKED && !isEmpty) {
                Text(
                    text = "${(masteryPercent * 100).toInt()}%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun LanguageSelector(
    label: String,
    languages: List<Language>,
    selectedLanguageId: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) {
        Image(
            painter = painterResource(
                when (selectedLanguageId.lowercase()) {
                    "it", "italian" -> R.drawable.ic_flag_it
                    "en", "english" -> R.drawable.ic_flag_en
                    "ru", "russian" -> R.drawable.ic_flag_ru
                    "el", "greek" -> R.drawable.ic_flag_el
                    "de", "german" -> R.drawable.ic_flag_de
                    "zh", "chinese" -> R.drawable.ic_flag_zh
                    else -> R.drawable.ic_flag_en
                }
            ),
            contentDescription = label,
            modifier = Modifier.size(24.dp, 18.dp),
            contentScale = ContentScale.Fit
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        languages.forEach { language ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(
                                when (language.id.value.lowercase()) {
                                    "it", "italian" -> R.drawable.ic_flag_it
                                    "en", "english" -> R.drawable.ic_flag_en
                                    "ru", "russian" -> R.drawable.ic_flag_ru
                                    "el", "greek" -> R.drawable.ic_flag_el
                                    "de", "german" -> R.drawable.ic_flag_de
                                    "zh", "chinese" -> R.drawable.ic_flag_zh
                                    else -> R.drawable.ic_flag_en
                                }
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp, 15.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = language.displayName)
                    }
                },
                onClick = {
                    expanded = false
                    if (language.id.value != selectedLanguageId) {
                        ScreenLogger.tap("language_select", details = "lang=${language.id.value}")
                        AuditLogger.getInstanceOrNull()?.langSelect(language.id.value, language.displayName, zeroState = false)
                        onSelect(language.id.value)
                    }
                }
            )
        }
    }
}

fun buildLessonTiles(
    lessons: List<Lesson>,
    testMode: Boolean,
    lessonFlowers: Map<String, FlowerVisual>,
    selectedLessonId: String?,
    activePackLessonIds: List<String>?
): List<LessonTileUi> {
    // Filter lessons to only those in the active pack
    val packLessons = if (activePackLessonIds != null) {
        // Preserve the order from the pack's lesson list
        activePackLessonIds.mapNotNull { id -> lessons.firstOrNull { it.id.value == id } }
    } else {
        lessons
    }
    val total = 12
    val tiles = mutableListOf<LessonTileUi>()

    // Find the highest lesson with any progress (masteryPercent > 0)
    var lastLessonWithProgress = -1
    for (i in packLessons.indices) {
        val flower = lessonFlowers[packLessons[i].id.value]
        android.util.Log.d("GrammarMate", "buildLessonTiles: lesson $i (${packLessons[i].id}) -> masteryPercent=${flower?.masteryPercent}")
        if (flower != null && flower.masteryPercent > 0f) {
            lastLessonWithProgress = i
        }
    }
    android.util.Log.d("GrammarMate", "buildLessonTiles: lastLessonWithProgress=$lastLessonWithProgress, next UNLOCKED will be at index ${lastLessonWithProgress + 1}")

    for (i in 0 until total) {
        val lesson = packLessons.getOrNull(i)
        val state = when {
            // No lesson exists at this index in the pack - show empty slot
            lesson == null -> LessonTileState.EMPTY
            testMode -> LessonTileState.SEED
            i == 0 -> LessonTileState.SPROUT
            else -> {
                val currentFlower = lessonFlowers[lesson.id.value]

                when {
                    // Current lesson has progress - show flower
                    currentFlower != null && currentFlower.masteryPercent > 0f -> {
                        LessonTileState.SEED
                    }
                    // This is the lesson right after the last one with progress - UNLOCKED (open lock)
                    i == lastLessonWithProgress + 1 -> {
                        LessonTileState.UNLOCKED
                    }
                    // This lesson is before the last with progress - check if previous has progress
                    i < lastLessonWithProgress + 1 -> {
                        val prevLesson = packLessons.getOrNull(i - 1)
                        val prevFlower = prevLesson?.let { lessonFlowers[it.id.value] }
                        if (prevFlower != null && prevFlower.masteryPercent > 0f) {
                            LessonTileState.UNLOCKED
                        } else {
                            LessonTileState.LOCKED
                        }
                    }
                    // All other lessons are locked
                    else -> {
                        LessonTileState.LOCKED
                    }
                }
            }
        }
        tiles.add(LessonTileUi(i, lesson?.id?.value, state))
    }
    return tiles
}

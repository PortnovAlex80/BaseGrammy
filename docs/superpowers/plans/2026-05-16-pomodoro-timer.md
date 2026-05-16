# Pomodoro Timer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Pomodoro-style focused training sessions with countdown timer, Anki-style difficulty ratings, and session summary to GrammarMate.

**Architecture:** Pomodoro wraps existing training sessions as an overlay. A PomodoroHelper (feature layer) manages timer logic and stats, PomodoroState lives in TrainingUiState, and UI components render the timer banner, difficulty chips, and summary screen. The feature reuses existing training infrastructure and fire streak system without modification.

**Tech Stack:** Kotlin 1.9.22, Jetpack Compose (BOM 2024.02.00), Material 3, SnakeYAML 2.2, coroutines for timer

**Design spec:** `docs/superpowers/specs/2026-05-16-pomodoro-timer-design.md`
**Task file:** `docs/specification/tasks/TASK-056-pomodoro-timer.md`

---

## File Structure

### New files
| File | Responsibility |
|------|---------------|
| `app/src/main/res/drawable/ic_tomato.xml` | Tomato vector drawable icon |
| `app/src/main/java/com/alexpo/grammermate/data/PomodoroSettingsStore.kt` | Persist last selected duration |
| `app/src/main/java/com/alexpo/grammermate/feature/pomodoro/PomodoroHelper.kt` | Timer logic, stats, difficulty tracking |
| `app/src/main/java/com/alexpo/grammermate/ui/components/PomodoroSelectorSheet.kt` | Time selection bottom sheet |
| `app/src/main/java/com/alexpo/grammermate/ui/components/PomodoroTimerBanner.kt` | Countdown + stats banner during training |
| `app/src/main/java/com/alexpo/grammermate/ui/components/DifficultyRatingRow.kt` | Anki-style rating chips |
| `app/src/main/java/com/alexpo/grammermate/ui/components/PomodoroSummaryScreen.kt` | Post-session summary overlay |

### Modified files
| File | Change scope |
|------|-------------|
| `app/src/main/java/com/alexpo/grammermate/data/Models.kt` | Add 4 new types + 1 field to TrainingUiState |
| `app/src/main/java/com/alexpo/grammermate/ui/screens/HomeScreen.kt` | Add tomato icon + bottom sheet |
| `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt` | Add PomodoroHelper integration |
| `app/src/main/java/com/alexpo/grammermate/ui/screens/TrainingScreen.kt` | Render timer banner, difficulty row, summary |
| `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` | Wire onStartPomodoro callback |

---

## Wave 1: Data Layer

Build checkpoint after this wave: `assembleDebug` must pass.

### Task 1: Add Pomodoro Data Model Types

**Files:**
- Modify: `app/src/main/java/com/alexpo/grammermate/data/Models.kt`

- [ ] **Step 1: Read Models.kt to find TrainingUiState**

Read `app/src/main/java/com/alexpo/grammermate/data/Models.kt`. Find the `TrainingUiState` data class and the `CardSessionState` data class. Note their exact line numbers.

- [ ] **Step 2: Add new enum types**

After the existing enums in the file (find the last enum, likely `SessionState` or `PracticeType`), add:

```kotlin
enum class PomodoroPreset(val minutes: Int, val label: String) {
    QUICK(5, "Quick"),
    FOCUS(15, "Focus"),
    CLASSIC(20, "Classic")
}

enum class CardDifficultyRating {
    AGAIN,
    HARD,
    GOOD,
    EASY
}
```

- [ ] **Step 3: Add PomodoroSessionStats data class**

After the new enums, add:

```kotlin
data class PomodoroSessionStats(
    val cardsShown: Int = 0,
    val cardsCorrect: Int = 0,
    val cardsIncorrect: Int = 0,
    val difficultyRatings: Map<CardDifficultyRating, Int> = emptyMap(),
    val wordsPerMinute: Double = 0.0,
    val durationMinutes: Int = 0,
    val completedAtMs: Long = 0
)
```

- [ ] **Step 4: Add PomodoroState data class**

```kotlin
data class PomodoroState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val isComplete: Boolean = false,
    val selectedDurationMinutes: Int = 20,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val stats: PomodoroSessionStats = PomodoroSessionStats(),
    val showRatingPrompt: Boolean = false,
    val showExitConfirm: Boolean = false
)
```

- [ ] **Step 5: Add pomodoro field to TrainingUiState**

Find `TrainingUiState` and add `val pomodoro: PomodoroState = PomodoroState()` as the last field. This ensures backward compatibility — all existing code works because the default is an inactive PomodoroState.

- [ ] **Step 6: Build check**

Run: `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/data/Models.kt
git commit -m "feat(pomodoro): add data model types for PomodoroState and CardDifficultyRating

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 2: Create PomodoroSettingsStore

**Files:**
- Create: `app/src/main/java/com/alexpo/grammermate/data/PomodoroSettingsStore.kt`

- [ ] **Step 1: Create PomodoroSettingsStore.kt**

```kotlin
package com.alexpo.grammermate.data

import android.content.Context
import com.esotericsoftware.yamlbeans.YamlReader
import com.esotericsoftware.yamlbeans.YamlWriter
import java.io.File
import java.io.FileReader
import java.io.StringWriter
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PomodoroSettingsStore(context: Context) {
    private val file = File(context.filesDir, "grammarmate/pomodoro_settings.yaml")
    private val lock = ReentrantLock()

    fun load(): Int {
        return lock.withLock {
            if (!file.exists()) return DEFAULT_DURATION
            try {
                val reader = YamlReader(FileReader(file))
                val map = reader.read(java.util.HashMap::class.java) as? Map<String, Any>
                reader.close()
                (map?.get(KEY_DURATION) as? Number)?.toInt() ?: DEFAULT_DURATION
            } catch (_: Exception) {
                DEFAULT_DURATION
            }
        }
    }

    fun save(durationMinutes: Int) {
        lock.withLock {
            val data = mapOf(KEY_DURATION to durationMinutes)
            val writer = StringWriter()
            val yamlWriter = YamlWriter(writer)
            yamlWriter.write(data)
            yamlWriter.close()
            AtomicFileWriter.writeAtomically(file, writer.toString())
        }
    }

    companion object {
        private const val KEY_DURATION = "lastDurationMinutes"
        private const val DEFAULT_DURATION = 20
    }
}
```

Note: This uses the existing `AtomicFileWriter` from the codebase for safe file writes, and `YamlReader`/`YamlWriter` from SnakeYAML (already a dependency).

- [ ] **Step 2: Build check**

Run: `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/data/PomodoroSettingsStore.kt
git commit -m "feat(pomodoro): add PomodoroSettingsStore for duration persistence

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 3: Add Tomato Icon Asset

**Files:**
- Create: `app/src/main/res/drawable/ic_tomato.xml`

- [ ] **Step 1: Create the tomato vector drawable**

Create `app/src/main/res/drawable/ic_tomato.xml` with a simple tomato-like vector. Since we need a Material-style monochrome icon:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <!-- Tomato body (circle) -->
    <path
        android:fillColor="@android:color/black"
        android:pathData="M12,4C8.13,4 5,7.13 5,11C5,14.87 8.13,18 12,18C15.87,18 19,14.87 19,11C19,7.13 15.87,4 12,4ZM12,16C9.24,16 7,13.76 7,11C7,8.24 9.24,6 12,6C14.76,6 17,8.24 17,11C17,13.76 14.76,16 12,16Z"/>
    <!-- Tomato stem -->
    <path
        android:fillColor="@android:color/black"
        android:pathData="M12,2C11.45,2 11,2.45 11,3L11,4.5C11,4.5 11.5,4 12,4C12.5,4 13,4.5 13,4.5L13,3C13,2.45 12.55,2 12,2Z"/>
    <!-- Tomato leaves -->
    <path
        android:fillColor="@android:color/black"
        android:pathData="M9,3.5C9,3.5 8,2 6.5,2.5C8,3 9,3.5 9,3.5Z"/>
    <path
        android:fillColor="@android:color/black"
        android:pathData="M15,3.5C15,3.5 16,2 17.5,2.5C16,3 15,3.5 15,3.5Z"/>
</vector>
```

This is a simplified tomato outline. If the user later provides a specific CC0 SVG, it can be replaced via Android Studio's "New > Vector Asset > Local file" conversion.

- [ ] **Step 2: Build check**

Run: `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/drawable/ic_tomato.xml
git commit -m "feat(pomodoro): add tomato icon vector drawable

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Wave 1 Checkpoint

- [ ] Run full build: `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug`
- [ ] Verify no new compile errors
- [ ] All 3 data layer tasks committed

---

## Wave 2: Feature Layer

Build checkpoint after this wave.

### Task 4: Create PomodoroHelper

**Files:**
- Create: `app/src/main/java/com/alexpo/grammermate/feature/pomodoro/PomodoroHelper.kt`

- [ ] **Step 1: Create the pomodoro directory**

```bash
mkdir -p "app/src/main/java/com/alexpo/grammermate/feature/pomodoro"
```

- [ ] **Step 2: Create PomodoroHelper.kt**

Read `app/src/main/java/com/alexpo/grammermate/feature/daily/DailySessionHelper.kt` first to understand the `TrainingStateAccess` interface pattern. Then create:

```kotlin
package com.alexpo.grammermate.feature.pomodoro

import com.alexpo.grammermate.data.CardDifficultyRating
import com.alexpo.grammermate.data.PomodoroSessionStats
import com.alexpo.grammermate.data.PomodoroState
import com.alexpo.grammermate.data.TrainingUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PomodoroHelper(
    private val stateProvider: () -> TrainingUiState,
    private val onUpdateState: (TrainingUiState) -> Unit,
    private val scope: CoroutineScope
) {
    private var timerJob: Job? = null

    fun startPomodoro(durationMinutes: Int) {
        val totalSeconds = durationMinutes * 60
        val state = PomodoroState(
            isActive = true,
            isPaused = false,
            isComplete = false,
            selectedDurationMinutes = durationMinutes,
            remainingSeconds = totalSeconds,
            totalSeconds = totalSeconds
        )
        updatePomodoroState(state)
        startTimer()
    }

    fun pausePomodoro() {
        timerJob?.cancel()
        timerJob = null
        updatePomodoro { it.copy(isPaused = true) }
    }

    fun resumePomodoro() {
        updatePomodoro { it.copy(isPaused = false) }
        startTimer()
    }

    fun cancelPomodoro() {
        timerJob?.cancel()
        timerJob = null
        updatePomodoroState(PomodoroState())
    }

    fun tick() {
        val current = stateProvider().pomodoro
        if (!current.isActive || current.isPaused || current.isComplete) return

        val newRemaining = current.remainingSeconds - 1
        if (newRemaining <= 0) {
            completePomodoro()
            return
        }

        updatePomodoro { it.copy(remainingSeconds = newRemaining) }
    }

    fun completePomodoro() {
        timerJob?.cancel()
        timerJob = null
        val current = stateProvider()
        val cardSession = current.cardSession

        val stats = PomodoroSessionStats(
            cardsShown = cardSession.correctCount + cardSession.incorrectCount,
            cardsCorrect = cardSession.correctCount,
            cardsIncorrect = cardSession.incorrectCount,
            difficultyRatings = current.pomodoro.stats.difficultyRatings,
            wordsPerMinute = if (cardSession.voiceActiveMs > 0)
                cardSession.voiceWordCount / (cardSession.voiceActiveMs / 60000.0) else 0.0,
            durationMinutes = current.pomodoro.selectedDurationMinutes,
            completedAtMs = System.currentTimeMillis()
        )

        updatePomodoro {
            it.copy(
                isComplete = true,
                isActive = false,
                remainingSeconds = 0,
                stats = stats
            )
        }
    }

    fun recordDifficultyRating(rating: CardDifficultyRating) {
        updatePomodoro { state ->
            val ratings = state.stats.difficultyRatings.toMutableMap()
            ratings[rating] = (ratings[rating] ?: 0) + 1
            state.copy(
                stats = state.stats.copy(difficultyRatings = ratings),
                showRatingPrompt = false
            )
        }
    }

    fun setShowRatingPrompt(show: Boolean) {
        updatePomodoro { it.copy(showRatingPrompt = show) }
    }

    fun setShowExitConfirm(show: Boolean) {
        updatePomodoro { it.copy(showExitConfirm = show) }
    }

    fun onLifecycleStop() {
        if (stateProvider().pomodoro.isActive && !stateProvider().pomodoro.isPaused) {
            pausePomodoro()
        }
    }

    fun onLifecycleStart() {
        val pomodoro = stateProvider().pomodoro
        if (pomodoro.isActive && pomodoro.isPaused && !pomodoro.isComplete) {
            resumePomodoro()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                tick()
            }
        }
    }

    private fun updatePomodoro(transform: (PomodoroState) -> PomodoroState) {
        val current = stateProvider()
        onUpdateState(current.copy(pomodoro = transform(current.pomodoro)))
    }

    private fun updatePomodoroState(newState: PomodoroState) {
        val current = stateProvider()
        onUpdateState(current.copy(pomodoro = newState))
    }
}
```

- [ ] **Step 3: Build check**

Run: `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/feature/pomodoro/PomodoroHelper.kt
git commit -m "feat(pomodoro): add PomodoroHelper with timer logic and stats tracking

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Wave 2 Checkpoint

- [ ] Run full build
- [ ] Verify no new compile errors
- [ ] Task 4 committed

---

## Wave 3: UI Components

Build checkpoint after this wave.

### Task 5: Create PomodoroSelectorSheet

**Files:**
- Create: `app/src/main/java/com/alexpo/grammermate/ui/components/PomodoroSelectorSheet.kt`

- [ ] **Step 1: Create PomodoroSelectorSheet composable**

```kotlin
package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.PomodoroPreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroSelectorSheet(
    showSheet: Boolean,
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit,
    lastDuration: Int
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedMinutes by remember(lastDuration) { mutableIntStateOf(lastDuration) }
    var customMinutes by remember { mutableIntStateOf(25) }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tomato),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Pomodoro Training",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Focus. Practice. Grow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PomodoroPreset.entries.forEach { preset ->
                        val isSelected = selectedMinutes == preset.minutes
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedMinutes = preset.minutes }
                                .then(
                                    if (isSelected) Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(12.dp)
                                    ) else Modifier
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${preset.minutes} min",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Custom:", style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { customMinutes = (customMinutes - 1).coerceAtLeast(1) }) {
                        Text("−", fontSize = 20.sp)
                    }
                    Text(
                        text = "$customMinutes",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    IconButton(onClick = { customMinutes = (customMinutes + 1).coerceAtMost(60) }) {
                        Text("+", fontSize = 20.sp)
                    }
                    Text("min", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { selectedMinutes = customMinutes }) {
                        Text("Set")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onStart(selectedMinutes) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tomato),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
```

- [ ] **Step 2: Build check**

Run assemble debug. Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/ui/components/PomodoroSelectorSheet.kt
git commit -m "feat(pomodoro): add PomodoroSelectorSheet bottom sheet UI

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 6: Create PomodoroTimerBanner

**Files:**
- Create: `app/src/main/java/com/alexpo/grammermate/ui/components/PomodoroTimerBanner.kt`

- [ ] **Step 1: Create PomodoroTimerBanner composable**

```kotlin
package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.FontFeatureSettings
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.R

private val TomatoTint = Color(0xFFFFEBEE)

@Composable
fun PomodoroTimerBanner(
    remainingSeconds: Int,
    totalSeconds: Int,
    cardsShown: Int,
    successRate: Int,
    isPaused: Boolean,
    onPauseResume: () -> Unit
) {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeText = String.format("%02d:%02d", minutes, seconds)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TomatoTint),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_tomato),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFFE53935)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = timeText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFeatureSettings = "tnum"
                )
            }

            Text(
                text = "$cardsShown cards",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$successRate%",
                    fontWeight = FontWeight.Medium,
                    color = if (successRate >= 80) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onPauseResume,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            if (isPaused) android.R.drawable.ic_media_play
                            else android.R.drawable.ic_media_pause
                        ),
                        contentDescription = if (isPaused) "Resume" else "Pause",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build check**

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/ui/components/PomodoroTimerBanner.kt
git commit -m "feat(pomodoro): add PomodoroTimerBanner with countdown display

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 7: Create DifficultyRatingRow

**Files:**
- Create: `app/src/main/java/com/alexpo/grammermate/ui/components/DifficultyRatingRow.kt`

- [ ] **Step 1: Create DifficultyRatingRow composable**

```kotlin
package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.data.CardDifficultyRating
import kotlinx.coroutines.delay

private data class RatingStyle(val label: String, val color: Color)

private val ratingStyles = mapOf(
    CardDifficultyRating.AGAIN to RatingStyle("Again", Color(0xFFFFCDD2)),
    CardDifficultyRating.HARD to RatingStyle("Hard", Color(0xFFFFE0B2)),
    CardDifficultyRating.GOOD to RatingStyle("Good", Color(0xFFC8E6C9)),
    CardDifficultyRating.EASY to RatingStyle("Easy", Color(0xFFBBDEFB))
)

@Composable
fun DifficultyRatingRow(
    onRatingSelected: (CardDifficultyRating) -> Unit
) {
    var hasSelected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(3000)
        if (!hasSelected) {
            hasSelected = true
            onRatingSelected(CardDifficultyRating.GOOD)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(
            text = "How was it?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CardDifficultyRating.entries.forEach { rating ->
                val style = ratingStyles[rating]!!
                FilterChip(
                    selected = false,
                    onClick = {
                        if (!hasSelected) {
                            hasSelected = true
                            onRatingSelected(rating)
                        }
                    },
                    label = { Text(style.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = style.color,
                        labelColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
    }
}
```

- [ ] **Step 2: Build check**

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/ui/components/DifficultyRatingRow.kt
git commit -m "feat(pomodoro): add DifficultyRatingRow with Anki-style chips and auto-select

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 8: Create PomodoroSummaryScreen

**Files:**
- Create: `app/src/main/java/com/alexpo/grammermate/ui/components/PomodoroSummaryScreen.kt`

- [ ] **Step 1: Create PomodoroSummaryScreen composable**

```kotlin
package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.data.CardDifficultyRating
import com.alexpo.grammermate.data.PomodoroSessionStats
import com.alexpo.grammermate.data.PomodoroState

private val RingTrackColor = Color(0xFFE0E0E0)
private val RingFillColor = Color(0xFF4CAF50)

@Composable
fun PomodoroSummaryScreen(
    pomodoro: PomodoroState,
    currentStreak: Int,
    todayFireCount: Int,
    onDone: () -> Unit
) {
    val stats = pomodoro.stats
    val timeCompletedPercent = if (pomodoro.totalSeconds > 0)
        ((pomodoro.totalSeconds - pomodoro.remainingSeconds).toFloat() / pomodoro.totalSeconds * 100).toInt()
    else 100
    val isEarlyCompletion = pomodoro.remainingSeconds > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🎉", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Session Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        if (isEarlyCompletion) {
            Text(
                text = "Early completion!",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        CircularTimeRing(
            percent = timeCompletedPercent,
            remainingText = formatDuration(pomodoro.selectedDurationMinutes),
            modifier = Modifier.size(120.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        StatsGrid(stats)
        Spacer(modifier = Modifier.height(16.dp))

        if (stats.difficultyRatings.isNotEmpty()) {
            DifficultyBreakdown(stats.difficultyRatings)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (currentStreak > 0 || todayFireCount > 0) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (currentStreak >= 7) Color(0xFFFFF3E0)
                    else MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val fireEmoji = "🔥".repeat(todayFireCount.coerceAtMost(4))
                    Text(text = fireEmoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$currentStreak day streak!",
                        fontWeight = FontWeight.Bold,
                        color = if (currentStreak >= 7) Color(0xFFFF8F00)
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun CircularTimeRing(
    percent: Int,
    remainingText: String,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            drawArc(
                color = RingTrackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = RingFillColor,
                startAngle = -90f,
                sweepAngle = 360f * percent / 100f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = remainingText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(text = "$percent%", fontSize = 14.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun StatsGrid(stats: PomodoroSessionStats) {
    val successRate = if (stats.cardsShown > 0)
        (stats.cardsCorrect * 100 / stats.cardsShown) else 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard("📊", "${stats.cardsShown}", "cards", Modifier.weight(1f))
        StatCard("✅", "$successRate%", "correct", Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard("🗣️", String.format("%.0f", stats.wordsPerMinute), "WPM", Modifier.weight(1f))
        StatCard("🔥", "${stats.cardsCorrect}", "correct", Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(emoji: String, value: String, label: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = emoji, fontSize = 20.sp)
            Text(text = value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun DifficultyBreakdown(ratings: Map<CardDifficultyRating, Int>) {
    val total = ratings.values.sum().coerceAtLeast(1)
    val colors = mapOf(
        CardDifficultyRating.AGAIN to Color(0xFFFFCDD2),
        CardDifficultyRating.HARD to Color(0xFFFFE0B2),
        CardDifficultyRating.GOOD to Color(0xFFC8E6C9),
        CardDifficultyRating.EASY to Color(0xFFBBDEFB)
    )

    Column {
        Text("Difficulty breakdown:", fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        CardDifficultyRating.entries.forEach { rating ->
            val count = ratings[rating] ?: 0
            if (count > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        text = rating.name.lowercase().replaceFirstChar { it.uppercase() },
                        modifier = Modifier.width(48.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    LinearProgressIndicator(
                        progress = { count.toFloat() / total },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp),
                        color = colors[rating]!!,
                        trackColor = Color(0xFFEEEEEE)
                    )
                    Text(
                        text = " $count",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(24.dp)
                    )
                }
            }
        }
    }
}

private fun formatDuration(minutes: Int): String {
    return String.format("%02d:00", minutes)
}
```

- [ ] **Step 2: Build check**

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/ui/components/PomodoroSummaryScreen.kt
git commit -m "feat(pomodoro): add PomodoroSummaryScreen with circular ring and stats

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Wave 3 Checkpoint

- [ ] Run full build
- [ ] Verify all 4 UI components compile
- [ ] All Wave 3 tasks committed

---

## Wave 4: Integration

Build checkpoint after EVERY task in this wave (each modifies existing code).

### Task 9: Update HomeScreen — Add Tomato Icon

**Files:**
- Modify: `app/src/main/java/com/alexpo/grammermate/ui/screens/HomeScreen.kt`

- [ ] **Step 1: Read HomeScreen.kt**

Read the full HomeScreen.kt. Find:
1. The HomeScreen composable function signature
2. The header Row (around lines 155-180)
3. The language selector and settings icon positioning

- [ ] **Step 2: Add onStartPomodoro parameter**

Add `onStartPomodoro: (Int) -> Unit = {}` parameter to HomeScreen function signature, after `onProfileClick`.

- [ ] **Step 3: Add showPomodoroSheet state**

Inside HomeScreen composable body, after existing state variables, add:
```kotlin
var showPomodoroSheet by remember { mutableStateOf(false) }
```

- [ ] **Step 4: Add tomato icon in header Row**

Find the settings `IconButton` in the header Row. Before it, add the tomato icon:
```kotlin
IconButton(onClick = { showPomodoroSheet = true }) {
    Icon(
        painter = painterResource(R.drawable.ic_tomato),
        contentDescription = "Pomodoro Timer",
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.primary
    )
}
```

Add the import for `painterResource` and `R` if not already present.

- [ ] **Step 5: Add PomodoroSelectorSheet rendering**

After all the AlertDialogs at the bottom of HomeScreen (after the closing braces of the 3 AlertDialogs), add:
```kotlin
PomodoroSelectorSheet(
    showSheet = showPomodoroSheet,
    onDismiss = { showPomodoroSheet = false },
    onStart = { duration ->
        showPomodoroSheet = false
        onStartPomodoro(duration)
    },
    lastDuration = 20
)
```

Note: `lastDuration` will be connected to PomodoroSettingsStore in Task 12. For now, hardcoded to 20.

- [ ] **Step 6: Build check**

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/ui/screens/HomeScreen.kt
git commit -m "feat(pomodoro): add tomato icon and selector sheet to HomeScreen

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 10: Update TrainingViewModel — Pomodoro Integration

**Files:**
- Modify: `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt`

This is the highest-blast-radius change. Read carefully and modify minimally.

- [ ] **Step 1: Read TrainingViewModel.kt header**

Read the first 100 lines to understand:
1. Class declaration and constructor
2. Existing helper instances (BossHelper, DailySessionHelper, etc.)
3. The `updateState` method pattern
4. Import section

- [ ] **Step 2: Add PomodoroHelper instance**

After existing helper declarations, add:
```kotlin
private val pomodoroHelper = PomodoroHelper(
    stateProvider = { state.value },
    onUpdateState = { newState -> _state.value = newState },
    scope = viewModelScope
)
```

Add import for `PomodoroHelper` and `viewModelScope`.

- [ ] **Step 3: Add PomodoroSettingsStore initialization**

Find where other stores are initialized. Add:
```kotlin
private val pomodoroSettingsStore = PomodoroSettingsStore(getApplication())
```

- [ ] **Step 4: Add public Pomodoro methods**

After the existing helper delegation methods, add:

```kotlin
fun startPomodoro(durationMinutes: Int) {
    pomodoroSettingsStore.save(durationMinutes)
    pomodoroHelper.startPomodoro(durationMinutes)
    startOrResumeSession()
}

fun pausePomodoro() {
    pomodoroHelper.pausePomodoro()
}

fun resumePomodoro() {
    pomodoroHelper.resumePomodoro()
}

fun cancelPomodoro() {
    pomodoroHelper.cancelPomodoro()
}

fun rateCardDifficulty(rating: CardDifficultyRating) {
    pomodoroHelper.recordDifficultyRating(rating)
}

fun confirmPomodoroExit() {
    pomodoroHelper.cancelPomodoro()
}

fun dismissPomodoroExit() {
    pomodoroHelper.setShowExitConfirm(false)
}

fun getPomodoroLastDuration(): Int {
    return pomodoroSettingsStore.load()
}
```

- [ ] **Step 5: Hook into answer submission for rating prompt**

Find the `submitAnswer` method (or wherever correct/incorrect results are processed). After the result is set, add:
```kotlin
if (state.value.pomodoro.isActive) {
    pomodoroHelper.setShowRatingPrompt(true)
}
```

- [ ] **Step 6: Hook into lifecycle events**

Find the `onCleared` method or lifecycle handling. If there are existing onStop/onStart hooks, add:
```kotlin
fun onAppBackgrounded() {
    pomodoroHelper.onLifecycleStop()
}

fun onAppForegrounded() {
    pomodoroHelper.onLifecycleStart()
}
```

If no lifecycle hooks exist, skip this step -- lifecycle handling can be added in GrammarMateApp.

- [ ] **Step 7: Build check**

Run assemble debug. Expected: BUILD SUCCESSFUL. If failures, check imports and method signatures.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt
git commit -m "feat(pomodoro): integrate PomodoroHelper into TrainingViewModel

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 11: Update TrainingScreen — Timer Banner and Summary

**Files:**
- Modify: `app/src/main/java/com/alexpo/grammermate/ui/screens/TrainingScreen.kt`

- [ ] **Step 1: Read TrainingScreen.kt**

Understand the current layout structure and where TrainingCardSession is rendered.

- [ ] **Step 2: Add PomodoroTimerBanner above TrainingCardSession**

Find where TrainingCardSession (or the card content) is rendered. Before it, add conditional rendering:
```kotlin
if (state.pomodoro.isActive && !state.pomodoro.isComplete) {
    val successRate = if ((state.cardSession.correctCount + state.cardSession.incorrectCount) > 0)
        (state.cardSession.correctCount * 100 / (state.cardSession.correctCount + state.cardSession.incorrectCount))
    else 0
    PomodoroTimerBanner(
        remainingSeconds = state.pomodoro.remainingSeconds,
        totalSeconds = state.pomodoro.totalSeconds,
        cardsShown = state.cardSession.correctCount + state.cardSession.incorrectCount,
        successRate = successRate,
        isPaused = state.pomodoro.isPaused,
        onPauseResume = {
            if (state.pomodoro.isPaused) vm.resumePomodoro() else vm.pausePomodoro()
        }
    )
    Spacer(modifier = Modifier.height(8.dp))
}
```

- [ ] **Step 3: Add PomodoroSummaryScreen for completed state**

When pomodoro is complete, replace the training card content:
```kotlin
if (state.pomodoro.isComplete) {
    PomodoroSummaryScreen(
        pomodoro = state.pomodoro,
        currentStreak = state.cardSession.currentStreak,
        todayFireCount = state.cardSession.todayFireCount,
        onDone = { vm.cancelPomodoro() }
    )
}
```

This should replace (or be shown instead of) the normal TrainingCardSession content.

- [ ] **Step 4: Add DifficultyRatingRow in result area**

Find where the correct/incorrect result is displayed. After the result content, add:
```kotlin
if (state.pomodoro.isActive && state.pomodoro.showRatingPrompt) {
    DifficultyRatingRow(
        onRatingSelected = { rating ->
            vm.rateCardDifficulty(rating)
        }
    )
}
```

- [ ] **Step 5: Add BackHandler for Pomodoro exit confirmation**

Find existing BackHandler. Add or modify to handle Pomodoro:
```kotlin
if (state.pomodoro.isActive) {
    BackHandler {
        vm.dismissPomodoroExit()
        // Show exit confirm via pomodoro state
        // pomodoroHelper.setShowExitConfirm(true) is called through VM
    }
}
```

Note: The exact BackHandler integration depends on existing training screen navigation. Check how back is currently handled and add the Pomodoro check before the existing handler.

- [ ] **Step 6: Build check**

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/ui/screens/TrainingScreen.kt
git commit -m "feat(pomodoro): add timer banner, summary, and difficulty rating to TrainingScreen

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 12: Update GrammarMateApp — Wire Callbacks

**Files:**
- Modify: `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt`

- [ ] **Step 1: Read GrammarMateApp.kt HomeScreen wiring**

Find the HomeScreen composable call (around lines 226-268 based on earlier research). Identify the existing callback wiring.

- [ ] **Step 2: Add onStartPomodoro callback**

In the HomeScreen call, add:
```kotlin
onStartPomodoro = { duration ->
    vm.startPomodoro(duration)
    navController.navigate(Routes.TRAINING) {
        popUpTo(Routes.HOME) { inclusive = false }
        launchSingleTop = true
    }
}
```

The route `Routes.TRAINING` should match the existing training navigation pattern. Check what route the "Continue Learning" button uses and use the same one.

- [ ] **Step 3: Build check**

- [ ] **Step 4: Final full build + test**

Run: `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt
git commit -m "feat(pomodoro): wire Pomodoro start callback from HomeScreen to ViewModel

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Wave 4 Checkpoint

- [ ] Full build passes
- [ ] All integration tasks committed
- [ ] Manual smoke test: tomato icon visible, bottom sheet opens, Start navigates to training
- [ ] Manual smoke test: timer counts down during training
- [ ] Manual smoke test: difficulty chips appear after each card
- [ ] Manual smoke test: summary screen shows on timer expiry

---

## Final Verification

1. **Build:** `assembleDebug` passes
2. **Tests:** `test` -- no new failures
3. **Cross-feature regression:**
   - Regular training (non-Pomodoro) works unchanged
   - HomeScreen avatar/language/settings still functional
   - Fire streak on normal sessions still works
   - TrainingCardSession renders when Pomodoro inactive
   - Boss battle unaffected
   - Daily practice unaffected
4. **UC/AC check:** Verify UC-75 through UC-83 ACs hold
5. **Spec sync:** Update CHANGELOG if code diverged from spec

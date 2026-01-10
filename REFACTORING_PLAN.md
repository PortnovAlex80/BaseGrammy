# План рефакторинга: Механика цветов и интервального повторения

## Обзор задачи

Реализовать систему отслеживания прогресса закрепления навыков через механику "цветов", которая визуально отражает уровень освоения материала и его увядание по кривой забывания.

---

## 1. Новые модели данных

### 1.1 LessonMasteryState (состояние освоения урока)

```kotlin
// Models.kt

/**
 * Состояние освоения урока (цветок)
 */
data class LessonMasteryState(
    val lessonId: String,
    val languageId: String,
    val uniqueCardShows: Int = 0,           // Уникальные показы карточек (макс 150 для 100%)
    val totalCardShows: Int = 0,            // Всего показов (включая повторы)
    val lastShowDate: Long = 0L,            // Timestamp последнего показа
    val intervalStepIndex: Int = 0,         // Текущий шаг в лестнице интервалов (0-9)
    val completedAt: Long? = null           // Когда урок был завершен (все карточки пройдены)
)

/**
 * Состояние цветка для отображения в UI
 */
enum class FlowerState {
    LOCKED,         // 🔒 Урок заблокирован
    SEED,           // 🌱 Только начат (0-33% показов)
    SPROUT,         // 🌿 Формирование паттерна (33-66% показов)
    BLOOM,          // 🌸 Цветущий (66-100% показов)
    WILTING,        // 🥀 Увядающий (50-99% от нормы)
    WILTED,         // 🍂 Увядший (< 50% от нормы)
    GONE            // ⚫ Исчез (> 3 месяцев без повторения)
}

/**
 * Визуальное представление цветка
 */
data class FlowerVisual(
    val state: FlowerState,
    val masteryPercent: Float,      // 0.0 - 1.0 (процент закрепления)
    val healthPercent: Float,       // 0.5 - 1.0 (здоровье цветка)
    val scaleMultiplier: Float      // Множитель масштаба иконки (0.5 - 1.0)
)
```

### 1.2 Лестница интервалов

```kotlin
// SpacedRepetitionConfig.kt

object SpacedRepetitionConfig {
    /**
     * Лестница интервальных повторов (в днях)
     * +1, +2, +4, +7, +10, +14, +20, +28, +42, +56
     */
    val INTERVAL_LADDER = listOf(1, 2, 4, 7, 10, 14, 20, 28, 42, 56)

    /**
     * Минимальное количество показов для 100% закрепления
     */
    const val MASTERY_THRESHOLD = 150

    /**
     * Порог забывания (ниже 50% = увядший)
     */
    const val WILTED_THRESHOLD = 0.5f

    /**
     * Дней до полного исчезновения цветка
     */
    const val GONE_DAYS = 90

    /**
     * Расчет процента забывания на основе дней с последнего показа
     * и текущего шага интервала
     */
    fun calculateDecayPercent(
        daysSinceLastShow: Int,
        intervalStepIndex: Int
    ): Float {
        if (daysSinceLastShow <= 0) return 1.0f
        if (intervalStepIndex >= INTERVAL_LADDER.size) return 1.0f

        val expectedInterval = INTERVAL_LADDER[intervalStepIndex]
        val overdueDays = daysSinceLastShow - expectedInterval

        if (overdueDays <= 0) return 1.0f

        // Логарифмическое затухание от 100% до 50%
        // Формула: health = 1.0 - 0.5 * log2(1 + overdueDays / expectedInterval)
        val decayFactor = kotlin.math.ln(1.0 + overdueDays.toDouble() / expectedInterval) / kotlin.math.ln(2.0)
        return (1.0f - 0.5f * decayFactor.toFloat()).coerceIn(WILTED_THRESHOLD, 1.0f)
    }
}
```

---

## 2. Хранилище данных о прогрессе

### 2.1 MasteryStore (новый класс)

```kotlin
// data/MasteryStore.kt

class MasteryStore(private val context: Context) {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file = File(baseDir, "mastery.yaml")

    /**
     * Загрузить все состояния освоения
     */
    fun loadAll(): Map<String, LessonMasteryState>

    /**
     * Сохранить состояние освоения урока
     */
    fun save(state: LessonMasteryState)

    /**
     * Обновить показ карточки для урока
     * @param lessonId ID урока
     * @param cardId ID показанной карточки
     * @param isUniqueShow true если это уникальный показ (новая карточка)
     */
    fun recordCardShow(
        lessonId: String,
        languageId: String,
        cardId: String,
        isUniqueShow: Boolean
    )

    /**
     * Отметить урок как завершенный (все карточки пройдены)
     */
    fun markLessonCompleted(lessonId: String, languageId: String)

    /**
     * Получить визуальное состояние цветка
     */
    fun getFlowerVisual(lessonId: String, languageId: String): FlowerVisual

    /**
     * Получить состояние цветка по данным урока
     */
    fun getFlowerState(mastery: LessonMasteryState): FlowerState

    /**
     * Очистить все данные
     */
    fun clear()
}
```

### 2.2 Структура файла mastery.yaml

```yaml
schemaVersion: 1
data:
  en:
    lesson_001:
      uniqueCardShows: 45
      totalCardShows: 120
      lastShowDate: 1704067200000
      intervalStepIndex: 3
      completedAt: 1703980800000
      shownCardIds:
        - card_001
        - card_002
        - card_003
    lesson_002:
      uniqueCardShows: 150
      totalCardShows: 300
      lastShowDate: 1704153600000
      intervalStepIndex: 5
      completedAt: 1703894400000
      shownCardIds: [...]
  it:
    lesson_001:
      uniqueCardShows: 20
      totalCardShows: 40
      lastShowDate: 1703894400000
      intervalStepIndex: 1
      completedAt: null
      shownCardIds: [...]
```

---

## 3. Изменения в TrainingViewModel

### 3.1 Новые зависимости

```kotlin
class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    // Существующие
    private val lessonStore = LessonStore(application)
    private val progressStore = ProgressStore(application)
    private val configStore = AppConfigStore(application)

    // НОВОЕ: хранилище состояний освоения
    private val masteryStore = MasteryStore(application)

    // Кеш показанных карточек в текущей сессии
    private val sessionShownCards = mutableSetOf<String>()
}
```

### 3.2 Отслеживание показов карточек

Изменения в методе `submitAnswer()`:

```kotlin
fun submitAnswer(): SubmitResult {
    // ... существующий код ...

    if (accepted) {
        // НОВОЕ: записать показ карточки
        val card = currentCard()
        if (card != null) {
            val lessonId = resolveCardLessonId(card)
            val isUnique = !sessionShownCards.contains(card.id)
            if (isUnique) {
                sessionShownCards.add(card.id)
            }
            masteryStore.recordCardShow(
                lessonId = lessonId,
                languageId = state.selectedLanguageId,
                cardId = card.id,
                isUniqueShow = isUnique
            )
        }

        // ... остальной код ...
    }
}
```

### 3.3 Метод определения урока карточки

```kotlin
/**
 * Определить к какому уроку принадлежит карточка
 * (важно для Mixed-режима где карточки из разных уроков)
 */
private fun resolveCardLessonId(card: SentenceCard): String {
    // Поиск урока по ID карточки
    return _uiState.value.lessons
        .find { lesson -> lesson.cards.any { it.id == card.id } }
        ?.id
        ?: _uiState.value.selectedLessonId
        ?: "unknown"
}
```

### 3.4 Обновление завершения сабурока

```kotlin
// В submitAnswer() когда isLastCard == true:
if (isLastCard) {
    // ... существующий код ...

    // НОВОЕ: проверить завершение урока
    val allSubLessonsCompleted = (state.completedSubLessonCount + 1) >= state.subLessonCount
    if (allSubLessonsCompleted) {
        masteryStore.markLessonCompleted(
            lessonId = state.selectedLessonId ?: "",
            languageId = state.selectedLanguageId
        )
    }
}
```

---

## 4. Изменения в TrainingUiState

### 4.1 Новые поля

```kotlin
data class TrainingUiState(
    // ... существующие поля ...

    // НОВОЕ: состояния цветков для уроков
    val lessonFlowers: Map<String, FlowerVisual> = emptyMap(),

    // НОВОЕ: текущее состояние цветка для выбранного урока
    val currentLessonFlower: FlowerVisual? = null,

    // НОВОЕ: состояния цветков для упражнений (копируют урок)
    val exerciseFlowers: Map<Int, FlowerVisual> = emptyMap()
)
```

### 4.2 Метод обновления цветков

```kotlin
// В TrainingViewModel
private fun refreshFlowerStates() {
    val languageId = _uiState.value.selectedLanguageId
    val lessons = _uiState.value.lessons

    val flowerStates = lessons.associate { lesson ->
        lesson.id to masteryStore.getFlowerVisual(lesson.id, languageId)
    }

    val currentFlower = _uiState.value.selectedLessonId?.let { flowerStates[it] }

    _uiState.update {
        it.copy(
            lessonFlowers = flowerStates,
            currentLessonFlower = currentFlower,
            exerciseFlowers = buildExerciseFlowers(currentFlower)
        )
    }
}

private fun buildExerciseFlowers(lessonFlower: FlowerVisual?): Map<Int, FlowerVisual> {
    if (lessonFlower == null) return emptyMap()
    val subLessonCount = _uiState.value.subLessonCount
    return (0 until subLessonCount).associate { index ->
        index to lessonFlower.copy() // Копируют состояние урока
    }
}
```

---

## 5. Изменения в UI (GrammarMateApp.kt)

### 5.1 Обновление LessonTile

```kotlin
@Composable
private fun LessonTile(
    tile: LessonTileUi,
    flower: FlowerVisual?,
    onSelect: () -> Unit
) {
    val (emoji, scale) = when {
        tile.state == LessonTileState.LOCKED -> "🔒" to 1.0f
        flower == null -> "🌱" to 1.0f
        else -> when (flower.state) {
            FlowerState.LOCKED -> "🔒" to 1.0f
            FlowerState.SEED -> "🌱" to flower.scaleMultiplier
            FlowerState.SPROUT -> "🌿" to flower.scaleMultiplier
            FlowerState.BLOOM -> "🌸" to flower.scaleMultiplier
            FlowerState.WILTING -> "🥀" to flower.scaleMultiplier
            FlowerState.WILTED -> "🍂" to flower.scaleMultiplier
            FlowerState.GONE -> "⚫" to 0.5f
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(enabled = tile.state != LessonTileState.LOCKED, onClick = onSelect)
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
                fontSize = (18 * scale).sp,  // Масштабирование размера
                modifier = Modifier.graphicsLayer(
                    scaleX = scale,
                    scaleY = scale
                )
            )
            // Показать процент закрепления если есть
            if (flower != null && flower.masteryPercent > 0) {
                Text(
                    text = "${(flower.masteryPercent * 100).toInt()}%",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
```

### 5.2 Обновление roadmap tiles

```kotlin
// В LessonRoadmapScreen
@Composable
private fun ExerciseTile(
    index: Int,
    type: SubLessonType,
    isCompleted: Boolean,
    isActive: Boolean,
    flower: FlowerVisual?,
    onStart: () -> Unit
) {
    val scale = flower?.scaleMultiplier ?: 1.0f
    val emoji = when {
        !isCompleted -> "🔒"
        flower == null -> "🌸"
        else -> when (flower.state) {
            FlowerState.BLOOM -> "🌸"
            FlowerState.WILTING -> "🥀"
            FlowerState.WILTED -> "🍂"
            else -> "🌸"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(enabled = isCompleted || isActive, onClick = onStart)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "${index + 1}", fontWeight = FontWeight.SemiBold)
            Text(
                text = emoji,
                fontSize = (18 * scale).sp
            )
            Text(text = type.name.take(4), fontSize = 10.sp)
        }
    }
}
```

---

## 6. Логика расчета состояния цветка

### 6.1 FlowerCalculator (утилитный класс)

```kotlin
// data/FlowerCalculator.kt

object FlowerCalculator {

    /**
     * Рассчитать визуальное состояние цветка
     */
    fun calculateFlowerVisual(mastery: LessonMasteryState?): FlowerVisual {
        if (mastery == null) {
            return FlowerVisual(
                state = FlowerState.LOCKED,
                masteryPercent = 0f,
                healthPercent = 1f,
                scaleMultiplier = 1f
            )
        }

        // Процент закрепления (0-100%, max 150 показов)
        val masteryPercent = (mastery.uniqueCardShows.toFloat() / SpacedRepetitionConfig.MASTERY_THRESHOLD)
            .coerceIn(0f, 1f)

        // Дней с последнего показа
        val daysSinceLastShow = calculateDaysSince(mastery.lastShowDate)

        // Проверка на исчезновение (> 90 дней)
        if (daysSinceLastShow > SpacedRepetitionConfig.GONE_DAYS) {
            return FlowerVisual(
                state = FlowerState.GONE,
                masteryPercent = 0f,
                healthPercent = 0f,
                scaleMultiplier = 0.5f
            )
        }

        // Здоровье цветка (учет кривой забывания)
        val healthPercent = SpacedRepetitionConfig.calculateDecayPercent(
            daysSinceLastShow = daysSinceLastShow,
            intervalStepIndex = mastery.intervalStepIndex
        )

        // Определение состояния
        val state = when {
            mastery.completedAt == null && mastery.uniqueCardShows == 0 -> FlowerState.LOCKED
            healthPercent < SpacedRepetitionConfig.WILTED_THRESHOLD -> FlowerState.WILTED
            healthPercent < 1.0f -> FlowerState.WILTING
            masteryPercent < 0.33f -> FlowerState.SEED
            masteryPercent < 0.66f -> FlowerState.SPROUT
            else -> FlowerState.BLOOM
        }

        // Масштаб = процент закрепления * здоровье
        val scale = (masteryPercent * healthPercent).coerceIn(0.5f, 1.0f)

        return FlowerVisual(
            state = state,
            masteryPercent = masteryPercent,
            healthPercent = healthPercent,
            scaleMultiplier = scale
        )
    }

    private fun calculateDaysSince(timestamp: Long): Int {
        if (timestamp == 0L) return 0
        val now = System.currentTimeMillis()
        val diffMs = now - timestamp
        return (diffMs / (24 * 60 * 60 * 1000)).toInt()
    }
}
```

---

## 7. Миграция данных

### 7.1 Миграция существующего прогресса

При первом запуске с новой версией:

```kotlin
// MasteryStore.kt
fun migrateFromProgressStore(progressStore: ProgressStore, lessonStore: LessonStore) {
    val progress = progressStore.load()
    val languages = lessonStore.getLanguages()

    for (language in languages) {
        val lessons = lessonStore.getLessons(language.id)
        for (lesson in lessons) {
            // Инициализировать базовое состояние
            val mastery = LessonMasteryState(
                lessonId = lesson.id,
                languageId = language.id,
                uniqueCardShows = 0,
                totalCardShows = 0,
                lastShowDate = System.currentTimeMillis(),
                intervalStepIndex = 0,
                completedAt = null
            )
            save(mastery)
        }
    }
}
```

---

## 8. Порядок реализации

### Этап 1: Модели и хранение (приоритет: высокий)
1. [ ] Добавить новые модели в Models.kt
2. [ ] Создать SpacedRepetitionConfig.kt
3. [ ] Создать MasteryStore.kt
4. [ ] Создать FlowerCalculator.kt

### Этап 2: ViewModel интеграция (приоритет: высокий)
1. [ ] Добавить masteryStore в TrainingViewModel
2. [ ] Обновить submitAnswer() для записи показов
3. [ ] Добавить resolveCardLessonId()
4. [ ] Добавить refreshFlowerStates()
5. [ ] Обновить TrainingUiState с новыми полями

### Этап 3: UI обновления (приоритет: средний)
1. [ ] Обновить LessonTile с поддержкой масштабирования
2. [ ] Обновить LessonRoadmapScreen
3. [ ] Обновить HomeScreen с новыми иконками
4. [ ] Добавить легенду с новыми состояниями

### Этап 4: Тестирование (приоритет: высокий)
1. [ ] Unit-тесты для FlowerCalculator
2. [ ] Unit-тесты для SpacedRepetitionConfig
3. [ ] Integration-тесты для MasteryStore
4. [ ] UI-тесты для отображения цветков

---

## 9. Визуальное представление состояний

| Состояние | Emoji | Описание | Условие |
|-----------|-------|----------|---------|
| LOCKED | 🔒 | Заблокирован | Урок не начат |
| SEED | 🌱 | Семя | 0-33% показов |
| SPROUT | 🌿 | Росток | 33-66% показов |
| BLOOM | 🌸 | Цветок | 66-100% показов |
| WILTING | 🥀 | Увядает | Здоровье 50-99% |
| WILTED | 🍂 | Увял | Здоровье < 50% |
| GONE | ⚫ | Исчез | > 90 дней без повтора |

---

## 10. Формулы

### 10.1 Процент закрепления (masteryPercent)
```
masteryPercent = min(uniqueCardShows / 150, 1.0)
```

### 10.2 Здоровье цветка (healthPercent)
```
if daysSinceLastShow <= expectedInterval:
    healthPercent = 1.0
else:
    overdueDays = daysSinceLastShow - expectedInterval
    decayFactor = log2(1 + overdueDays / expectedInterval)
    healthPercent = max(0.5, 1.0 - 0.5 * decayFactor)
```

### 10.3 Масштаб иконки (scaleMultiplier)
```
scaleMultiplier = max(0.5, masteryPercent * healthPercent)
```

---

## 11. Примеры сценариев

### Сценарий 1: Новый урок
- uniqueCardShows: 0
- healthPercent: 1.0
- state: LOCKED → SEED (после первого показа)

### Сценарий 2: Урок на 50 карточек завершен
- uniqueCardShows: 50
- masteryPercent: 50/150 = 33%
- state: SEED/SPROUT
- scale: 0.33

### Сценарий 3: Урок полностью освоен
- uniqueCardShows: 150
- masteryPercent: 100%
- healthPercent: 1.0 (повторяли вовремя)
- state: BLOOM
- scale: 1.0

### Сценарий 4: Цветок увядает
- uniqueCardShows: 150
- lastShowDate: 10 дней назад
- expectedInterval: 7 дней (шаг 4)
- overdueDays: 3
- healthPercent: 1.0 - 0.5 * log2(1 + 3/7) ≈ 0.78
- state: WILTING
- scale: 1.0 * 0.78 = 0.78

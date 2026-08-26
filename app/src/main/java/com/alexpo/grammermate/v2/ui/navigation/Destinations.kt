package com.alexpo.grammermate.v2.ui.navigation

/**
 * Карта маршрутов (destinations) навигации GrammarMate v2.
 *
 * Каждый экран представлен `sealed interface`-вариантом [Destination] — это даёт
 * exhaustive `when` и type-safe построение route-строк. Строковые шаблоны
 * (route-pattern для NavHost + конкретный route с аргументами) хранятся рядом с
 * вариантом, чтобы избежать «магических строк» в нескольких местах.
 *
 * Аргументы навигации (packId/lessonId и т.д.) — это простые String, т.к.
 * navigation-compose работает со строковыми аргументами; value-class
 * ([com.alexpo.grammermate.domain.model.PackId] и др.) наворачивается
 * на границе экрана (в composable-destination).
 *
 * **Идёмпотентный дизайн:** повторный навигационный переход на тот же маршрут —
 * no-op (NavHost по умолчанию), что соответствует ожиданиям bottom-bar навигации.
 */
sealed interface Destination {

    /** route-pattern для регистрации в NavHost (без подставленных аргументов). */
    val routePattern: String

    /**
     * Готовая route-строка с подставленными аргументами — для
     * `navController.navigate(...)`.
     */
    fun route(): String

    /**
     * Главная — сетка паков (точка входа в приложение).
     */
    data object Home : Destination {
        override val routePattern: String = ROUTE_HOME
        override fun route(): String = ROUTE_HOME
    }

    /**
     * Содержимое пака — главы с уроками (Фаза 1 плана: путь Pack → Chapter →
     * Lesson; вместо устранённого `ChapterLessons(packId, chapterId)`-placeholder'а).
     *
     * @property packId пак, чьё содержимое показывается.
     */
    data class PackContent(val packId: String) : Destination {
        override val routePattern: String = "$ROUTE_PACK_CONTENT/{$ARG_PACK_ID}"
        override fun route(): String = "$ROUTE_PACK_CONTENT/$packId"

        companion object {
            /** Полный route-pattern для регистрации destination. */
            const val PATTERN = "pack_content/{packId}"
        }
    }

    /**
     * Экран тренировки — прохождение карточек урока.
     *
     * @property packId   пак тренировки.
     * @property lessonId урок (для drill/daily — см. отдельные маршруты).
     */
    data class Training(val packId: String, val lessonId: String) : Destination {
        override val routePattern: String = "$ROUTE_TRAINING/{$ARG_PACK_ID}/{$ARG_LESSON_ID}"
        override fun route(): String = "$ROUTE_TRAINING/$packId/$lessonId"

        companion object {
            /** Полный route-pattern для регистрации destination в NavHost. */
            const val PATTERN = "training/{packId}/{lessonId}"
        }
    }

    /**
     * Настройки приложения (тема, TTS, язык интерфейса и т.д.).
     */
    data object Settings : Destination {
        override val routePattern: String = ROUTE_SETTINGS
        override fun route(): String = ROUTE_SETTINGS
    }

    /**
     * Drill-тренировка глаголов пака.
     *
     * @property packId пак глаголов.
     */
    data class VerbDrill(val packId: String) : Destination {
        override val routePattern: String = "$ROUTE_VERB_DRILL/{$ARG_PACK_ID}"
        override fun route(): String = "$ROUTE_VERB_DRILL/$packId"

        companion object {
            /** Полный route-pattern для регистрации destination в NavHost. */
            const val PATTERN = "verb_drill/{packId}"
        }
    }

    /**
     * Словарный drill (Anki-style карточки слов, Фаза 4 срез 3).
     *
     * @property packId пак словаря.
     */
    data class VocabDrill(val packId: String) : Destination {
        override val routePattern: String = "$ROUTE_VOCAB_DRILL/{$ARG_PACK_ID}"
        override fun route(): String = "$ROUTE_VOCAB_DRILL/$packId"

        companion object {
            /** Полный route-pattern для регистрации destination в NavHost. */
            const val PATTERN = "vocab_drill/{packId}"
        }
    }

    /**
     * Дневная норма практики пака.
     *
     * @property packId пак дневной нормы.
     */
    data class DailyPractice(val packId: String) : Destination {
        override val routePattern: String = "$ROUTE_DAILY_PRACTICE/{$ARG_PACK_ID}"
        override fun route(): String = "$ROUTE_DAILY_PRACTICE/$packId"

        companion object {
            /** Полный route-pattern для регистрации destination в NavHost. */
            const val PATTERN = "daily_practice/{packId}"
        }
    }

    companion object {
        // ── Базовые route-строки ───────────────────────────────────────────────
        const val ROUTE_HOME = "home"
        const val ROUTE_PACK_CONTENT = "pack_content"
        const val ROUTE_TRAINING = "training"
        const val ROUTE_SETTINGS = "settings"
        const val ROUTE_VERB_DRILL = "verb_drill"
        const val ROUTE_VOCAB_DRILL = "vocab_drill"
        const val ROUTE_DAILY_PRACTICE = "daily_practice"

        // ── Имена nav-аргументов ───────────────────────────────────────────────
        const val ARG_PACK_ID = "packId"
        const val ARG_LESSON_ID = "lessonId"

        /** Стартовый маршрут приложения (для NavHost startDestination). */
        const val START_ROUTE: String = ROUTE_HOME
    }
}

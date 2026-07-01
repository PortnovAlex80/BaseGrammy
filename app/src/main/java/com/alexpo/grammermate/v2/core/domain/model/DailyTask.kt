package com.alexpo.grammermate.v2.core.domain.model

/**
 * Задачи дневной нормы (Daily) — единицы работы для каждого блока дня.
 *
 * Каждый блок дневной нормы ([DailyBlockType]) раскрывается в один из вариантов
 * [DailyTask], который несёт конкретную карточку/слово и параметры отрисовки
 * (направление drill, способ ввода). Чистый Kotlin: immutable data class'ы,
 * без Android-зависимостей.
 *
 * @property id стабильный идентификатор задачи (для ключей UI/state).
 * @property blockType тип блока дневной нормы, из которого получена задача.
 */
sealed interface DailyTask {
    val id: String
    val blockType: DailyBlockType

    /**
     * Задача перевода предложения.
     *
     * @property id идентификатор задачи.
     * @property card карточка-предложение для перевода.
     * @property inputMode способ ввода ответа (голос/клавиатура/word bank).
     */
    data class TranslateSentence(
        override val id: String,
        val card: Card,
        val inputMode: InputMode,
    ) : DailyTask {
        override val blockType: DailyBlockType = DailyBlockType.TRANSLATE
    }

    /**
     * Задача флешкарты словарного слова (vocab sprint).
     *
     * @property id идентификатор задачи.
     * @property word словарное слово для показа.
     * @property direction направление drill (IT→RU либо RU→IT).
     */
    data class VocabFlashcard(
        override val id: String,
        val word: VocabWord,
        val direction: VocabDrillDirection,
    ) : DailyTask {
        override val blockType: DailyBlockType = DailyBlockType.VOCAB
    }

/**
 * Задача спряжения глагола.
 *
 * @property id идентификатор задачи.
 * @property card карточка drill глагола (см. [VerbDrillCard] в Drills.kt).
 * @property inputMode способ ввода ответа.
 */
    data class ConjugateVerb(
        override val id: String,
        val card: VerbDrillCard,
        val inputMode: InputMode,
    ) : DailyTask {
        override val blockType: DailyBlockType = DailyBlockType.VERBS
    }
}

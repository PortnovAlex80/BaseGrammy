package com.alexpo.grammermate.domain.daily

import com.alexpo.grammermate.domain.model.Card
import com.alexpo.grammermate.domain.model.CardType
import com.alexpo.grammermate.domain.model.DailyBlockType
import com.alexpo.grammermate.domain.model.DailyCursor
import com.alexpo.grammermate.domain.model.DailyTask
import com.alexpo.grammermate.domain.model.InputMode
import com.alexpo.grammermate.domain.model.VerbDrillCard
import com.alexpo.grammermate.domain.model.VocabDrillDirection
import com.alexpo.grammermate.domain.model.VocabWord

/**
 * Композиторы дневной нормы (gap #2, AC-16).
 *
 * Pure-Kotlin сборка дневного списка [DailyTask] из конфигурации блоков
 * ([DailyBlockType] → сколько задач) и текущего [DailyCursor]
 * (`ProgressRepository.getDailyCursor`). Композиторы — чистые функции от
 * `(cursor, content, settings)`: никаких Android-зависимостей, никаких
 * побочных эффектов, идентичный вход → идентичный выход.
 *
 * Подтипы [DailyTask] переиспользуют уже существующие доменные модели
 * ([Card], [VocabWord], [InputMode], [VocabDrillDirection], [VerbDrillCard]) —
 * дублирования моделей нет.
 *
 * @see DailyTask
 * @see DailyCursor
 * @see DailyBlockType
 */

/**
 * Исходный контент для сборки дневной нормы.
 *
 * Каждый список — нефильтрованный пул кандидатов; композитор берёт из него срез
 * по смещениям курсора ([DailyCursor.sentenceOffset] / [DailyCursor.verbOffset])
 * и числу задач в конфигурации.
 *
 * @property sentenceCards карточки-предложения (для [DailyBlockType.TRANSLATE]).
 * @property verbCards карточки drill-глаголов (для [DailyBlockType.VERBS]).
 * @property vocabWords словарные слова (для [DailyBlockType.VOCAB]).
 */
data class DailyContent(
    val sentenceCards: List<Card>,
    val verbCards: List<VerbDrillCard>,
    val vocabWords: List<VocabWord>,
)

/**
 * Настройки сборки дневной нормы.
 *
 * @property blockConfig конфигурация блоков дня: каждому [DailyBlockType] —
 *  сколько задач этого блока собрать (порядок ключей = порядок задач в выводе).
 *  Пример: `{TRANSLATE: 5, VOCAB: 3, VERBS: 2}` → 10 упорядоченных задач.
 * @property inputMode способ ввода для переводов и спряжений глаголов.
 * @property vocabDirection направление drill для словарных флешкарт.
 */
data class DailySettings(
    val blockConfig: Map<DailyBlockType, Int>,
    val inputMode: InputMode,
    val vocabDirection: VocabDrillDirection,
) {
    init {
        require(blockConfig.values.all { it >= 0 }) {
            "blockConfig counts must be non-negative, got: $blockConfig"
        }
    }
}

/**
 * Объект-композитор дневной нормы (gap #2, AC-16).
 *
 * Единственный публичный метод [compose] — pure-функция:
 * `(DailyCursor, DailyContent, DailySettings) → List<DailyTask>`.
 *
 * Контракт:
 *  1. Длина вывода = сумме значений [DailySettings.blockConfig]
 *     (для `{TRANSLATE:5, VOCAB:3, VERBS:2}` → 10);
 *  2. Порядок задач = порядок блоков в [blockConfig] (LinkedHashMap сохраняет
 *     порядок вставки); у каждой задачи корректный [DailyTask.blockType];
 *  3. Каждый [DailyTask] несёт стабильный `id` вида `"daily:<block>:<index>"`,
 *     где `<block>` — нижний регистр имени [DailyBlockType], `<index>` —
 *     порядковый номер задачи внутри блока (0-based). Одинаковые входы →
 *     одинаковые id (стабильность для UI-ключей);
 *  4. Подтипы переиспользуют существующие модели ([Card]/[VocabWord]/
 *     [InputMode]/[VocabDrillDirection]/[VerbDrillCard]) — без дублирования;
 *  5. Чистый Kotlin, 0 `import android`.
 *
 * Пулы обрезаются по доступному остатку (если в контенте меньше кандидатов,
 * чем требует конфигурация — композитор отдаёт столько, сколько есть, не падая;
 * невозместимый дефицит — это ответственность data-слоя, а не pure-композитора).
 */
object DailyTaskComposer {

    /**
     * Собирает дневную норму из блоков.
     *
     * @param cursor курсор дневной нормы (смещения по пулам предложений/глаголов).
     * @param content исходные пулы контента.
     * @param settings конфигурация блоков + inputMode + vocabDirection.
     * @return упорядоченный [List] задач длиной = сумме [DailySettings.blockConfig]
     *         (или меньше, если пул исчерпан); каждый [DailyTask] со стабильным id
     *         и корректным [DailyTask.blockType].
     */
    fun compose(
        cursor: DailyCursor,
        content: DailyContent,
        settings: DailySettings,
    ): List<DailyTask> {
        val tasks = ArrayList<DailyTask>(settings.blockConfig.values.sum())
        settings.blockConfig.forEach { (blockType, count) ->
            repeat(count) { index ->
                val task = when (blockType) {
                    DailyBlockType.TRANSLATE -> composeTranslate(
                        cursor,
                        content,
                        settings,
                        index,
                    )
                    DailyBlockType.VOCAB -> composeVocab(content, settings, index)
                    DailyBlockType.VERBS -> composeVerbs(
                        cursor,
                        content,
                        settings,
                        index,
                    )
                }
                if (task != null) tasks.add(task)
            }
        }
        return tasks
    }

    /**
     * Собирает [DailyTask.TranslateSentence] из пула карточек-предложений.
     *
     * Берёт карточку по смещению [DailyCursor.sentenceOffset] + [index],
     * пропуская не-предложения (фльтр по [CardType.SENTENCE]).
     * `null`, если пул исчерпан.
     */
    private fun composeTranslate(
        cursor: DailyCursor,
        content: DailyContent,
        settings: DailySettings,
        index: Int,
    ): DailyTask.TranslateSentence? {
        val sentences = content.sentenceCards.filter { it.type == CardType.SENTENCE }
        val position = cursor.sentenceOffset + index
        val card = sentences.getOrNull(position) ?: return null
        return DailyTask.TranslateSentence(
            id = stableId(DailyBlockType.TRANSLATE, index),
            card = card,
            inputMode = settings.inputMode,
        )
    }

    /**
     * Собирает [DailyTask.VocabFlashcard] из пула словарных слов.
     *
     * Берёт слово по индексу [index] (vocab sprint идёт по курсору частотности,
     * порядок слов в [DailyContent.vocabWords] уже отсортирован data-слоем).
     * `null`, если пул исчерпан.
     */
    private fun composeVocab(
        content: DailyContent,
        settings: DailySettings,
        index: Int,
    ): DailyTask.VocabFlashcard? {
        val word = content.vocabWords.getOrNull(index) ?: return null
        return DailyTask.VocabFlashcard(
            id = stableId(DailyBlockType.VOCAB, index),
            word = word,
            direction = settings.vocabDirection,
        )
    }

    /**
     * Собирает [DailyTask.ConjugateVerb] из пула drill-карточек глаголов.
     *
     * Берёт карточку по смещению [DailyCursor.verbOffset] + [index].
     * `null`, если пул исчерпан.
     */
    private fun composeVerbs(
        cursor: DailyCursor,
        content: DailyContent,
        settings: DailySettings,
        index: Int,
    ): DailyTask.ConjugateVerb? {
        val position = cursor.verbOffset + index
        val card = content.verbCards.getOrNull(position) ?: return null
        return DailyTask.ConjugateVerb(
            id = stableId(DailyBlockType.VERBS, index),
            card = card,
            inputMode = settings.inputMode,
        )
    }

    /**
     * Стабильный идентификатор задачи: `"daily:<block-lowercase>:<index>"`.
     *
     * Зависит только от типа блока и индекса внутри блока — не от содержимого
     * карточки, поэтому одинаковые входы всегда дают одинаковые id (требование
     * AC-16 Then-2 для UI-ключей).
     */
    private fun stableId(blockType: DailyBlockType, index: Int): String =
        "daily:" + blockType.name.lowercase() + ":" + index
}

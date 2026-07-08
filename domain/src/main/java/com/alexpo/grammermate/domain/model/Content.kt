package com.alexpo.grammermate.domain.model

/**
 * Модели контента — read-only данные, импортируемые из паков.
 *
 * Чистый Kotlin: data class с val-свойствами, без Room-аннотаций.
 * Идентификаторы — value classes из [Pack.kt]. Этот слой ничего не знает
 * ни об Android, ни о способе хранения контента.
 */

/**
 * Язык обучения — верхний уровень навигации. Один язык объединяет несколько [Pack]'ов.
 *
 * @property id          стабильный идентификатор языка.
 * @property displayName человекочитаемое имя для UI (например, "English").
 */
data class Language(
    val id: LanguageId,
    val displayName: String,
)

/**
 * Контент-пак: единица импорта (языковой курс или дополнение).
 *
 * @property id            стабильный идентификатор пака.
 * @property languageId    язык, которому учит пак.
 * @property displayName   человекочитаемое имя (null — взять по умолчанию).
 * @property version       версия контента пака (для инкрементальных обновлений).
 * @property importedAtMs  epoch-мс момента импорта пака на устройство.
 */
data class Pack(
    val id: PackId,
    val languageId: LanguageId,
    val displayName: String?,
    val version: String,
    val importedAtMs: Long,
)

/**
 * Глава пака — группировка уроков.
 *
 * @property id        идентификатор главы.
 * @property packId    пак, которому принадлежит глава.
 * @property order     порядковый номер главы внутри пака.
 * @property title     заголовок главы.
 * @property subtitle  подзаголовок (опционально).
 * @property storyFile путь к файлу истории/сторителлинга (опционально).
 * @property lessonIds идентификаторы уроков главы в порядке прохождения.
 */
data class Chapter(
    val id: ChapterId,
    val packId: PackId,
    val order: Int,
    val title: String,
    val subtitle: String?,
    val storyFile: String?,
    val lessonIds: List<LessonId>,
)

/**
 * Урок пака — атомарная обучающая единица со списком карточек.
 *
 * @property id            идентификатор урока.
 * @property packId        пак урока.
 * @property chapterId     глава урока (null для уроков вне глав).
 * @property order         порядковый номер внутри пака/главы.
 * @property title         заголовок урока.
 * @property cefrLevel     уровень CEFR (например, "A1", "B2") либо null.
 * @property grammarChipKey ключ грамматической справки (chip), либо null.
 * @property cards         карточки урока в порядке отображения.
 */
data class Lesson(
    val id: LessonId,
    val packId: PackId,
    val chapterId: ChapterId?,
    val order: Int,
    val title: String,
    val cefrLevel: String?,
    val grammarChipKey: String?,
    val cards: List<Card>,
)

/**
 * Карточка — минимальная единица упражнения.
 *
 * @property id               идентификатор карточки.
 * @property packId           пак карточки.
 * @property lessonId         урок карточки.
 * @property ord              порядковый номер в уроке.
 * @property type             тип карточки (см. [CardType]).
 * @property promptRu         промпт на русском (что перевести/спрягать).
 * @property acceptedAnswers  список принимаемых ответов (нормализованных).
 * @property tense            время глагола (для verb/aux drill), либо null.
 * @property verb             начальная форма глагола, либо null.
 * @property verbGroup        группа спряжения глагола, либо null.
 * @property person           лицо/число (для drill), либо null.
 * @property frequencyRank    ранг частотности слова (для сортировки), либо null.
 */
data class Card(
    val id: CardId,
    val packId: PackId,
    val lessonId: LessonId,
    val ord: Int,
    val type: CardType,
    val promptRu: String,
    val acceptedAnswers: List<String>,
    val tense: String?,
    val verb: String?,
    val verbGroup: String?,
    val person: String?,
    val frequencyRank: Int?,
)

/**
 * Грамматическая справка (chip) — теория по теме урока.
 *
 * @property key        ключ справки (соответствует [Lesson.grammarChipKey]).
 * @property title      заголовок.
 * @property essence    краткая суть правила.
 * @property formula    формула/шаблон спряжения, либо null.
 * @property base       базовая форма для примеров, либо null.
 * @property examples   примеры употребления.
 * @property dontConfuse предупреждение «не путать с…», либо null.
 * @property notes      дополнительные заметки (локализованные) по ключу.
 */
data class GrammarChip(
    val key: String,
    val title: String,
    val essence: String,
    val formula: String?,
    val base: String?,
    val examples: List<GrammarExample>,
    val dontConfuse: String?,
    val notes: Map<String, String>,
)

/**
 * Пример внутри грамматической справки.
 *
 * @property target целевое предложение на изучаемом языке.
 * @property ru     перевод на русский.
 * @property note   пояснение (опционально).
 */
data class GrammarExample(
    val target: String,
    val ru: String,
    val note: String?,
)

/**
 * История с квизами — сторителлинг на рубежах урока.
 *
 * @property storyId   идентификатор истории.
 * @property lessonId  привязка к уроку.
 * @property phase     фаза истории (начало/конец урока).
 * @property text      текст истории.
 * @property questions вопросы-квизы по тексту.
 */
data class StoryQuiz(
    val storyId: String,
    val lessonId: LessonId,
    val phase: StoryPhase,
    val text: String,
    val questions: List<StoryQuestion>,
)

/**
 * Вопрос квиза по истории.
 *
 * @property qId          идентификатор вопроса.
 * @property prompt       текст вопроса.
 * @property options      варианты ответа.
 * @property correctIndex индекс правильного варианта в [options].
 * @property explain      пояснение правильного ответа (опционально).
 */
data class StoryQuestion(
    val qId: String,
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    val explain: String?,
)

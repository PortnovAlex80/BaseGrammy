package com.alexpo.grammermate.v2.core.domain.model

/**
 * Пользовательские метаданные — данные, создаваемые самим пользователем:
 * скрытые карточки, «плохие» предложения, фоновый словарь, профиль.
 *
 * Чистый Kotlin, без Room-аннотаций. Идентификаторы — value classes из [Pack.kt].
 */

/**
 * Скрытая пользователем карточка (исключается из выдачи).
 *
 * @property cardId     скрытая карточка.
 * @property hiddenAtMs epoch-мс момента скрытия.
 */
data class HiddenCard(
    val cardId: CardId,
    val hiddenAtMs: Long,
)

/**
 * «Плохое» предложение — карточка, отмеченная пользователем как проблемная.
 *
 * @property packId      пак карточки.
 * @property cardId      карточка.
 * @property languageId  язык.
 * @property sentence    целевое предложение.
 * @property translation перевод.
 * @property mode        режим/контекст, в котором отметили.
 * @property addedAtMs   epoch-мс добавления в список.
 */
data class BadSentence(
    val packId: PackId,
    val cardId: CardId,
    val languageId: LanguageId,
    val sentence: String,
    val translation: String,
    val mode: String,
    val addedAtMs: Long,
)

/**
 * Слово фонового словаря (bg vocab) — пассивно изучаемая лексика.
 *
 * @property id            идентификатор записи слова.
 * @property packId        пак.
 * @property ord           порядковый номер слова в списке.
 * @property rank          ранг частотности слова.
 * @property text          само слово/фраза.
 * @property meaningRu     значение на русском (опционально).
 * @property collocations  устойчивые словосочетания со словом.
 */
data class BgVocabWord(
    val id: String,
    val packId: PackId,
    val ord: Int,
    val rank: Int,
    val text: String,
    val meaningRu: String?,
    val collocations: List<String>,
)

/**
 * Пометка фонового словарного слова пользователем.
 *
 * @property word       слово (как есть из словаря).
 * @property mark       тип пометки (зелёная/красная/нет).
 * @property updatedAtMs epoch-мс последнего обновления пометки.
 */
data class BgVocabMarkEntry(
    val word: String,
    val mark: BgVocabMark,
    val updatedAtMs: Long,
)

/**
 * Профиль пользователя.
 *
 * @property userName               имя пользователя.
 * @property welcomeDialogAttempts сколько раз показывали приветственный диалог.
 */
data class UserProfile(
    val userName: String,
    val welcomeDialogAttempts: Int,
)

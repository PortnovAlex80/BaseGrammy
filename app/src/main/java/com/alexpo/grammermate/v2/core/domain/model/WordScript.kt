package com.alexpo.grammermate.v2.core.domain.model

/**
 * Модели bg-vocab playback — план озвучивания фонового словарного слова.
 *
 * Чистый Kotlin, без Android-зависимостей. [WordScript] — это полное содержание
 * воспроизведения одного слова (само слово, перевод, коллокация, предложения),
 * которое планировщик озвучки разворачивает в последовательность [SpeakItem].
 *
 * [SpeakSlot] (импортируется из Enums.kt) маркирует, какую часть слова озвучивают
 * в данный момент — для подсинхронного подсвечивания в UI во время проигрывания.
 */

/**
 * Пара фраз (target/translation) для bg-vocab — коллокация либо пример-предложение.
 *
 * @property target      фраза на целевом языке (например, итальянском).
 * @property translation перевод фразы на родной язык (например, русский).
 */
data class PhrasePair(
    val target: String,
    val translation: String,
)

/**
 * Паузы в скрипте произношения (мс) — внутри-словесные интервалы.
 *
 * Пауза МЕЖДУ двумя словами в колоде намеренно НЕ входит сюда: ею владеет
 * DeckPlayer, чтобы колода могла варьировать её независимо (например, под темп
 * пользователя) без перерисовки каждого слова.
 *
 * @property afterWord          пауза после самого слова (целевой формы).
 * @property afterTranslation   пауза после перевода.
 * @property afterCollocation   пауза после коллокации (целевой формы).
 * @property afterSentence      пауза после предложения.
 */
data class ScriptPauses(
    val afterWord: Long = 300,
    val afterTranslation: Long = 500,
    val afterCollocation: Long = 300,
    val afterSentence: Long = 400,
)

/**
 * Один элемент произношения в плане — атомарная единица воспроизведения.
 *
 * Текст уже brace-escaped на этапе построения плана (фигурные скобки заменены
 * на визуально-похожие Unicode-скобки), чтобы контент поля не мог породить
 * ложный `{lang}`/`{pause:N}` токен.
 *
 * @property text         (brace-escaped) текст для озвучивания.
 * @property lang         язык синтеза («it», «ru», …).
 * @property pauseAfterMs пауза после элемента, мс.
 * @property slot         слот озвучивания — какая часть слова звучит (см. [SpeakSlot]).
 */
data class SpeakItem(
    val text: String,
    val lang: String,
    val pauseAfterMs: Long,
    val slot: SpeakSlot,
)

/**
 * Скрипт слова для bg-vocab проигрывания — полное содержание воспроизведения.
 *
 * Планировщик озвучки разворачивает скрипт в упорядоченную последовательность
 * [SpeakItem] (слово IT → перевод RU → коллокация IT → перевод коллокации RU →
 * пары предложений IT/RU). [pauses] задаёт внутри-словесные интервалы.
 *
 * @property rank      ранг частотности/презентации (1-based, по возрастанию).
 * @property wordIt    целевое слово (например, «casa»).
 * @property wordRu    перевод слова; может содержать варианты через «/» (например, «дом / жилище»).
 * @property colloIt   коллокация на целевом языке с этим словом.
 * @property colloRu   перевод коллокации [colloIt].
 * @property sentences 3–5 пар примеров-предложений (target/translation).
 * @property pauses    расписание пауз при озвучивании (по умолчанию [ScriptPauses]).
 */
data class WordScript(
    val rank: Int,
    val wordIt: String,
    val wordRu: String,
    val colloIt: String,
    val colloRu: String,
    val sentences: List<PhrasePair>,
    val pauses: ScriptPauses = ScriptPauses(),
)

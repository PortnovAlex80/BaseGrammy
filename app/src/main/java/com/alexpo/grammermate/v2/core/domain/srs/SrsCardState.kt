package com.alexpo.grammermate.v2.core.domain.srs

/**
 * Состояние памяти одной карточки в модели FSRS v6.
 *
 * Полностью persisted-значение: содержит всю информацию, необходимую
 * [SrsScheduler] для расчёта следующего интервала, а UI — для отображения
 * «здоровья» карточки. Immutable (data class из val-полей).
 *
 * Все моменты времени хранятся в epoch-миллисекундах ([Long]),
 * чтобы состояние было платформенно-независимым и тривиально сериализуемым
 * (JSON / Proto / DataStore). Никаких `java.time` в этом слое.
 *
 * Это прямая замена legacy-полей `intervalStepIndex` + `LessonMasteryState`
 * из v1: вместо лестницы `[1,2,4,7,10,14,20,28,42,56]` здесь —
 * непрерывные значения [stability] и [difficulty], как в FSRS.
 *
 * @property stability S — stability: как долго память «держится» (в днях).
 *  При `requestRetention = 0.9` следующий интервал ≈ S. Чем больше успешных
 *  повторений, тем больше S.
 * @property difficulty D — сложность карточки, диапазон [1.0, 10.0].
 *  10 = очень сложная, 1 = лёгкая. Растёт на AGAIN, падает на EASY,
 *  с mean reversion к центру шкалы.
 * @property lastReviewMs Момент последнего review (epoch ms) либо 0,
 *  если карточка ещё ни разу не показывалась.
 * @property reps Количество успешных повторений (отзывов).
 * @property lapses Количество провалов (рейтинг AGAIN в состоянии REVIEW →
 *  переход в RELEARNING). Каждый lapse сбрасывает stability.
 * @property state Текущее состояние памяти (см. [SrsMemoryState]).
 * @property dueAtMs Когда карточку нужно показать снова (epoch ms).
 *  Для карточек в шагах learning/relearning это может быть «через 10 минут»,
 *  для REVIEW — через несколько дней/недель.
 */
data class SrsCardState(
    val stability: Double,
    val difficulty: Double,
    val lastReviewMs: Long,
    val reps: Int,
    val lapses: Int,
    val state: SrsMemoryState,
    val dueAtMs: Long,
)

/**
 * Жизненное состояние карточки в цикле FSRS v6.
 *
 * Расширяет канонические три состояния FSRS (Learning / Review / Relearning)
 * вспомогательным состоянием [NEW], которое удобно для app-логики: новая
 * карточка, которую пользователь ещё ни разу не видел. [SrsScheduler]
 * трактует `state == NEW` как первый вход в Learning (стабильность и
 * сложность инициализируются из весов `w[0..5]`).
 *
 * Порядок значений (ordinal) сознительно не используется как wire-формат —
 * используйте имя enum при сериализации, чтобы оставаться совместимым
 * при будущих изменениях.
 *
 * @property NEW Карточка ещё ни разу не показывалась. Нет S/D.
 * @property LEARNING Первичное заучивание (короткие шаги: минуты).
 * @property REVIEW Заученная карточка на длинных интервалах (дни/недели).
 * @property RELEARNING Провал в REVIEW → возврат к заучиванию (после AGAIN).
 */
enum class SrsMemoryState { NEW, LEARNING, REVIEW, RELEARNING }

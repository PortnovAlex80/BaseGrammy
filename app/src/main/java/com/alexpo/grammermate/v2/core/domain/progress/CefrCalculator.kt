package com.alexpo.grammermate.v2.core.domain.progress

/**
 * Чистый калькулятор уровня CEFR по рангам частотности слов.
 *
 * Перенесено из v1
 * `app/legacy-src/java/com/alexpo/grammermate/data/CefrCalculator.kt`.
 *
 * Берёт медиану списка рангов и мапит её на уровень Общеевропейских компетенций
 * владения иностранным языком. Ноль Android-зависимостей, детерминированная
 * чистая функция.
 */
object CefrCalculator {

    /**
     * Рассчитать уровень CEFR по списку рангов частотности.
     *
     * Перенесено 1:1 из v1 `CefrCalculator.calculate`, строки 5–24.
     *
     * Алгоритм:
     *  - пустой список → "—";
     *  - меньше 10 рангов → "A1" (слишком мало данных для медианы);
     *  - иначе медиана рангов; пороги:
     *    `<500` → A1, `<1000` → A2, `<2000` → B1, `<4000` → B2, `<8000` → C1, иначе C2.
     *
     * @param ranks ранги частотности слов (чем меньше — тем частотнее).
     * @return уровень CEFR ("A1".."C2") либо "—" для пустого списка.
     */
    fun calculate(ranks: List<Int>): String {
        if (ranks.isEmpty()) return "—"
        if (ranks.size < 10) return "A1"

        val sorted = ranks.sorted()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        } else {
            sorted[sorted.size / 2]
        }

        return when {
            median < 500 -> "A1"
            median < 1000 -> "A2"
            median < 2000 -> "B1"
            median < 4000 -> "B2"
            median < 8000 -> "C1"
            else -> "C2"
        }
    }
}

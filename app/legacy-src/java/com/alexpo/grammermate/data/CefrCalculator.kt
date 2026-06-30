package com.alexpo.grammermate.data

object CefrCalculator {

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

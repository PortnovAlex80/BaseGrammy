package com.alexpo.grammermate.ui.components

/**
 * Информация о слове из drill файлов.
 * Используется для показа подсказок при клике на слова в ответах.
 */
data class WordHint(
    val translation: String,
    val rank: Int,
    val partOfSpeech: String,
    val collocations: List<String> = emptyList()
) {
    companion object {
        /**
         * Определить CEFR уровень по частотному рангу.
         * A1: 1-500, A2: 501-2000, B1+: 2001+
         */
        fun getLevelFromRank(rank: Int): String {
            return when {
                rank <= 500 -> "A1"
                rank <= 2000 -> "A2"
                else -> "B1+"
            }
        }
    }
}

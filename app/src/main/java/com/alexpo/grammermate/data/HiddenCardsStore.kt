package com.alexpo.grammermate.data

import java.io.File

/**
 * Хранилище для скрытых карточек.
 * Лог скрытых карточек: grammarmate/../hidden_cards.txt
 * Формат записи: lessonId:cardId (по одной строке)
 */
class HiddenCardsStore(private val baseDir: File) {
    private val hiddenCardsFile = File(baseDir.parentFile, "hidden_cards.txt")

    /**
     * Скрыть карточку из показа
     */
    fun hideCard(lessonId: String, cardId: String) {
        val line = "$lessonId:$cardId"
        // Проверяем, что такая запись еще не существует
        if (!isCardHidden(lessonId, cardId)) {
            hiddenCardsFile.appendText("$line\n")
        }
    }

    /**
     * Получить список скрытых карточек для урока
     */
    fun getHiddenCards(lessonId: String): Set<String> {
        if (!hiddenCardsFile.exists()) return emptySet()
        return try {
            hiddenCardsFile.readLines()
                .filter { it.isNotBlank() && it.startsWith("$lessonId:") }
                .map { it.substringAfter(":") }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * Проверить, скрыта ли карточка
     */
    fun isCardHidden(lessonId: String, cardId: String): Boolean {
        return getHiddenCards(lessonId).contains(cardId)
    }
}

package com.alexpo.grammermate.ui.components

import android.content.Context
import com.alexpo.grammermate.data.ItalianDrillRow
import com.alexpo.grammermate.data.ItalianDrillVocabParser
import com.alexpo.grammermate.data.LessonStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Кеш для хранения информации о словах из drill файлов.
 * Предоставляет быстрый доступ к переводам, рангам и коллокациям.
 */
@Singleton
class WordInfoCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lessonStore: LessonStore
) {
    private var cache: Map<String, WordHint>? = null
    private var loadedPackId: String? = null

    /**
     * Загружает drill данные для указанного пакета и языка.
     * Результат кешируется для быстрого доступа.
     */
    suspend fun loadPackData(packId: String, languageId: String) = withContext(Dispatchers.IO) {
        // Проверяем нужно ли перезагрузить
        if (cache != null && loadedPackId == packId) {
            return // Уже загружено для этого пакета
        }

        val drillFiles = lessonStore.getVocabDrillFiles(packId, languageId)
        val wordMap = mutableMapOf<String, WordHint>()

        for (file in drillFiles) {
            try {
                val inputStream = FileInputStream(file)
                val rows = ItalianDrillVocabParser.parse(inputStream, file.name)
                inputStream.close()

                for (row in rows) {
                    val word = row.word.lowercase()
                    val partOfSpeech = when {
                        file.name.contains("nouns") -> "noun"
                        file.name.contains("verbs") -> "verb"
                        file.name.contains("adjectives") -> "adjective"
                        file.name.contains("adverbs") -> "adverb"
                        else -> "other"
                    }

                    // Создаем WordHint только если есть перевод
                    val translation = row.meaningRu ?: continue
                    val collocations = row.collocations.take(3) // Максимум 3 коллокации

                    wordMap[word] = WordHint(
                        translation = translation,
                        rank = row.rank,
                        partOfSpeech = partOfSpeech,
                        collocations = collocations
                    )
                }
            } catch (e: Exception) {
                // Логируем ошибку, но продолжаем с другими файлами
                e.printStackTrace()
            }
        }

        cache = wordMap
        loadedPackId = packId
    }

    /**
     * Получить информацию о словах из drill файлов.
     * Анализирует предложение и возвращает Map<слово, WordHint>
     */
    fun getWordsInfoFromDrill(answerText: String): Map<String, WordHint> {
        if (cache == null) {
            return emptyMap()
        }

        val wordsInfo = mutableMapOf<String, WordHint>()
        val words = answerText.split(" ")

        for (word in words) {
            // Убираем знаки препинания
            val cleanWord = word.lowercase()
                .replace(Regex("[.,!?;:»«„""]"), "")
                .replace(Regex("'"), "")

            // Ищем в кеше
            cache?.get(cleanWord)?.let { hint ->
                wordsInfo[cleanWord] = hint
            }
        }

        return wordsInfo
    }

    /**
     * Получить информацию об одном слове.
     */
    fun getWordInfo(word: String): WordHint? {
        return cache?.get(word.lowercase())
    }

    /**
     * Очистить кеш (например, при смене пакета).
     */
    fun clearCache() {
        cache = null
        loadedPackId = null
    }
}

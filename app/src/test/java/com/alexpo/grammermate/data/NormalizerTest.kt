package com.alexpo.grammermate.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Normalizer - защита проверки ответов пользователя.
 *
 * Покрывает требования:
 * - FR-6.1.1-6.1.6: Нормализация текста
 * - FR-6.2.1: Проверка ответов
 */
class NormalizerTest {

    // ========================================
    // 5.1 Удаление пробелов
    // ========================================

    @Test
    fun normalize_multipleSpaces_becomesOne() {
        // FR-6.1.1: Множественные пробелы → один пробел
        assertEquals("hello world", Normalizer.normalize("hello    world"))
        assertEquals("hello world test", Normalizer.normalize("hello   world  test"))
    }

    @Test
    fun normalize_leadingTrailingSpaces_removed() {
        // FR-6.1.6: Удаление пробелов в начале и конце
        assertEquals("hello", Normalizer.normalize("  hello  "))
        assertEquals("hello world", Normalizer.normalize("   hello world   "))
    }

    @Test
    fun normalize_tabsAndNewlines_becomeSpaces() {
        // FR-6.1.1: Табуляция и переносы строк → пробелы
        assertEquals("hello world", Normalizer.normalize("hello\tworld"))
        assertEquals("hello world", Normalizer.normalize("hello\nworld"))
        assertEquals("hello world", Normalizer.normalize("hello\r\nworld"))
    }

    // ========================================
    // 5.2 Регистр
    // ========================================

    @Test
    fun normalize_upperCase_becomesLowerCase() {
        // FR-6.1.2: Приведение к нижнему регистру
        assertEquals("hello", Normalizer.normalize("HELLO"))
        assertEquals("hello world", Normalizer.normalize("HELLO WORLD"))
    }

    @Test
    fun normalize_mixedCase_becomesLowerCase() {
        // FR-6.1.2: Смешанный регистр → нижний регистр
        assertEquals("hello world", Normalizer.normalize("HeLLo WoRLd"))
        assertEquals("i am learning", Normalizer.normalize("I Am Learning"))
    }

    // ========================================
    // 5.3 Пунктуация
    // ========================================

    @Test
    fun normalize_period_removed() {
        // FR-6.1.3: Точка удаляется
        assertEquals("hello", Normalizer.normalize("hello."))
        assertEquals("hello world", Normalizer.normalize("hello. world"))
    }

    @Test
    fun normalize_comma_removed() {
        // FR-6.1.3: Запятая удаляется
        assertEquals("hello world", Normalizer.normalize("hello, world"))
        assertEquals("apples oranges bananas", Normalizer.normalize("apples, oranges, bananas"))
    }

    @Test
    fun normalize_questionMark_removed() {
        // FR-6.1.3: Вопросительный знак удаляется
        assertEquals("what is your name", Normalizer.normalize("what is your name?"))
        assertEquals("really", Normalizer.normalize("really?"))
    }

    @Test
    fun normalize_exclamationMark_removed() {
        // FR-6.1.3: Восклицательный знак удаляется
        assertEquals("wow", Normalizer.normalize("wow!"))
        assertEquals("hello world", Normalizer.normalize("hello world!"))
    }

    @Test
    fun normalize_colon_removed() {
        // FR-6.1.3: Двоеточие удаляется
        assertEquals("hello world", Normalizer.normalize("hello: world"))
    }

    @Test
    fun normalize_semicolon_removed() {
        // FR-6.1.3: Точка с запятой удаляется
        assertEquals("hello world", Normalizer.normalize("hello; world"))
    }

    @Test
    fun normalize_quotes_removed() {
        // FR-6.1.3: Кавычки удаляются
        assertEquals("hello world", Normalizer.normalize("\"hello world\""))
        assertEquals("hello world", Normalizer.normalize("<hello world>"))
    }

    @Test
    fun normalize_brackets_removed() {
        // FR-6.1.3: Скобки удаляются
        assertEquals("hello world", Normalizer.normalize("(hello world)"))
        assertEquals("hello world", Normalizer.normalize("[hello world]"))
        assertEquals("hello world", Normalizer.normalize("{hello world}"))
    }

    @Test
    fun normalize_hyphen_preserved() {
        // FR-6.1.4: Дефис сохраняется
        assertEquals("well-known", Normalizer.normalize("well-known"))
        assertEquals("twenty-one", Normalizer.normalize("twenty-one"))
    }

    @Test
    fun normalize_multiplePunctuation_removed() {
        // FR-6.1.3: Множественная пунктуация удаляется
        assertEquals("hello", Normalizer.normalize("hello..."))
        assertEquals("what", Normalizer.normalize("what!?!"))
        assertEquals("test", Normalizer.normalize("test.,;:"))
    }

    // ========================================
    // 5.4 Время
    // ========================================

    @Test
    fun normalize_timeThreeColon00_becomesThree() {
        // FR-6.1.5: "3:00" → "3"
        assertEquals("it is 3", Normalizer.normalize("it is 3:00"))
        assertEquals("at 3", Normalizer.normalize("at 3:00"))
    }

    @Test
    fun normalize_timeTwelveColon30_becomesTwelve() {
        // FR-6.1.5: "12:30" → "12"
        assertEquals("meet at 12", Normalizer.normalize("meet at 12:30"))
        assertEquals("12 o'clock", Normalizer.normalize("12:00 o'clock")) // апостроф сохраняется
    }

    @Test
    fun normalize_timeSingleDigit_preserved() {
        // FR-6.1.5: Одна цифра сохраняется
        assertEquals("at 5", Normalizer.normalize("at 5:15"))
        assertEquals("9 am", Normalizer.normalize("9:45 am"))
    }

    @Test
    fun normalize_timeWithoutMinutes_unchanged() {
        // Время без минут не изменяется
        assertEquals("3", Normalizer.normalize("3"))
        assertEquals("12", Normalizer.normalize("12"))
    }

    // ========================================
    // 5.5 Комплексные случаи
    // ========================================

    @Test
    fun normalize_realUserAnswer_matchesExpected() {
        // FR-6.2.1: Реальные примеры ответов
        assertEquals("i am learning english", Normalizer.normalize("I am learning English."))
        assertEquals("what is your name", Normalizer.normalize("What is your name?"))
        assertEquals("it's 3", Normalizer.normalize("It's 3:00!")) // апостроф сохраняется
        assertEquals("well-known fact", Normalizer.normalize("Well-known fact."))
    }

    @Test
    fun normalize_multipleTransformations_appliedCorrectly() {
        // Все трансформации применяются последовательно
        val input = "  HELLO, World!  How are you?  It's 5:30.  "
        val expected = "hello world how are you it's 5" // апостроф сохраняется
        assertEquals(expected, Normalizer.normalize(input))
    }

    @Test
    fun normalize_emptyString_returnsEmpty() {
        // Граничный случай: пустая строка
        assertEquals("", Normalizer.normalize(""))
        assertEquals("", Normalizer.normalize("   "))
    }

    @Test
    fun normalize_onlyPunctuation_returnsEmpty() {
        // Граничный случай: только пунктуация
        assertEquals("", Normalizer.normalize("..."))
        assertEquals("", Normalizer.normalize("!?!"))
        assertEquals("", Normalizer.normalize(".,;:"))
    }

    // ========================================
    // 5.6 Edge cases
    // ========================================

    @Test
    fun normalize_unicodeCharacters_preserved() {
        // Юникодные символы сохраняются
        assertEquals("привет", Normalizer.normalize("Привет"))
        assertEquals("café", Normalizer.normalize("Café"))
        assertEquals("naïve", Normalizer.normalize("Naïve"))
    }

    @Test
    fun normalize_apostropheInContraction_handled() {
        // Апостроф в сокращениях
        // Примечание: текущая реализация не удаляет апострофы явно,
        // но они могут обрабатываться как часть слова
        val result = Normalizer.normalize("it's a beautiful day")
        assertTrue("Result should contain 'its' or 'it's'", result.contains("its") || result.contains("it's"))
    }

    @Test
    fun normalize_multipleDashes_preserved() {
        // FR-6.1.4: Множественные дефисы сохраняются
        assertEquals("well-well-known", Normalizer.normalize("well-well-known"))
    }

    @Test
    fun normalize_numbersPreserved() {
        // Числа сохраняются
        assertEquals("i have 5 apples", Normalizer.normalize("I have 5 apples"))
        assertEquals("123", Normalizer.normalize("123"))
    }

    @Test
    fun normalize_mixedPunctuationAndText() {
        // Смешанная пунктуация и текст
        assertEquals("hello world", Normalizer.normalize("hello!!! world???"))
        assertEquals("yes no maybe", Normalizer.normalize("yes... no... maybe..."))
    }

    // ========================================
    // 5.7 Проверка идемпотентности
    // ========================================

    @Test
    fun normalize_idempotent() {
        // Нормализация идемпотентна: normalize(normalize(x)) == normalize(x)
        val inputs = listOf(
            "Hello, World!",
            "  What is your name?  ",
            "It's 3:00.",
            "UPPERCASE text",
            "multiple    spaces"
        )

        for (input in inputs) {
            val once = Normalizer.normalize(input)
            val twice = Normalizer.normalize(once)
            assertEquals("Normalize should be idempotent for: $input", once, twice)
        }
    }

    // ========================================
    // 5.8 Проверка корректности ответов
    // ========================================

    @Test
    fun normalize_correctAnswersMatch() {
        // FR-6.2.1: Проверка, что разные варианты ответа нормализуются одинаково
        val expected = "i am learning english"

        val variants = listOf(
            "I am learning English",
            "I am learning English.",
            "i am learning english",
            "  I am learning English.  ",
            "I AM LEARNING ENGLISH"
        )

        for (variant in variants) {
            assertEquals(expected, Normalizer.normalize(variant))
        }
    }

    @Test
    fun normalize_timeAnswersMatch() {
        // FR-6.1.5: Проверка, что время нормализуется корректно
        val expected = "it is 3"

        val variants = listOf(
            "It is 3:00",
            "it is 3:00",
            "IT IS 3:00",
            "It is 3:00.",
            "  it is 3:00  "
        )

        for (variant in variants) {
            assertEquals(expected, Normalizer.normalize(variant))
        }
    }
}

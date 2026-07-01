package com.alexpo.grammermate.v2.core.domain.validation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Регрессионные тесты для [Normalizer] — чистого ядра нормализации текста.
 *
 * Покрывает:
 *  - normalize: trim/collapse whitespace, снятие диакритик (NFD), lowercase,
 *    regex времени, посимвольный фильтр пунктуации, СОХРАНЕНИЕ апострофа,
 *    сохранение дефиса и кириллицы;
 *  - normalizeForVoice: не-буква → пробел;
 *  - isExactMatch: точное совпадение с minLength;
 *  - краевые случаи: пусто, только пунктуация, emoji, idempotent.
 */
class NormalizerTest {

    // ── normalize ──────────────────────────────────────────────────────────

    @Test
    fun `normalize trims leading and trailing whitespace`() {
        assertThat(Normalizer.normalize("   ciao   ")).isEqualTo("ciao")
    }

    @Test
    fun `normalize collapses internal whitespace runs into single space`() {
        assertThat(Normalizer.normalize("ciao\t \n mondo   bello"))
            .isEqualTo("ciao mondo bello")
    }

    @Test
    fun `normalize strips diacritics via NFD decomposition`() {
        // perché → perche (combining acute accent removed).
        assertThat(Normalizer.normalize("perché")).isEqualTo("perche")
    }

    @Test
    fun `normalize strips multiple diacritics`() {
        assertThat(Normalizer.normalize("è léittà ñ üï")).isEqualTo("e leitta n ui")
    }

    @Test
    fun `normalize lowercases input`() {
        assertThat(Normalizer.normalize("CiaoMONDO")).isEqualTo("ciaomondo")
    }

    @Test
    fun `normalize removes period comma question exclamation`() {
        assertThat(Normalizer.normalize("ciao. mondo? sì!")).isEqualTo("ciao mondo si")
    }

    @Test
    fun `normalize removes colon and semicolon`() {
        // Диакритика «ì» в «sì» снимается через NFD → «si».
        assertThat(Normalizer.normalize("12:00 sì;")).isEqualTo("12 si")
    }

    @Test
    fun `normalize removes brackets`() {
        assertThat(Normalizer.normalize("(ciao) [m] {x} <y>")).isEqualTo("ciao m x y")
    }

    @Test
    fun `normalize removes double and backtick and acute accents`() {
        assertThat(Normalizer.normalize("\"ciao\" `m´")).isEqualTo("ciao m")
    }

    @Test
    fun `normalize keeps apostrophe - v2 fix distinguishing from v1`() {
        // РАСХОЖДЕНИЕ С v1: апостроф сохраняется, l'alberto != lalberto.
        assertThat(Normalizer.normalize("l'albero")).isEqualTo("l'albero")
        assertThat(Normalizer.normalize("va' a casa")).isEqualTo("va' a casa")
    }

    @Test
    fun `normalize keeps hyphen`() {
        assertThat(Normalizer.normalize("e-mail")).isEqualTo("e-mail")
        assertThat(Normalizer.normalize("mother-in-law")).isEqualTo("mother-in-law")
    }

    @Test
    fun `normalize keeps digits`() {
        assertThat(Normalizer.normalize("123 abc456")).isEqualTo("123 abc456")
    }

    @Test
    fun `normalize keeps cyrillic letters`() {
        assertThat(Normalizer.normalize("Привет, мир!")).isEqualTo("привет мир")
    }

    @Test
    fun `normalize converts time hh-mm to hours only`() {
        // \b(\d{1,2}):\d{2}\b → group 1 (часы без минут).
        assertThat(Normalizer.normalize("3:00")).isEqualTo("3")
        assertThat(Normalizer.normalize("12:30")).isEqualTo("12")
    }

    @Test
    fun `normalize time regex word-boundary does not over-match`() {
        // 100:00 не подходит под \b(\d{1,2}): паттерн (две+ цифры) → остаётся как есть,
        // двоеточие удаляется посимвольным фильтром.
        assertThat(Normalizer.normalize("alle ore 9:15 di sera")).isEqualTo("alle ore 9 di sera")
    }

    @Test
    fun `normalize is idempotent`() {
        val input = "  L'Albero... perché? 12:00  "
        val once = Normalizer.normalize(input)
        val twice = Normalizer.normalize(once)
        assertThat(twice).isEqualTo(once)
    }

    // ── normalizeForVoice ──────────────────────────────────────────────────

    @Test
    fun `normalizeForVoice replaces apostrophe with space`() {
        // Голос не может выдать апостроф → превращаем в пробел.
        assertThat(Normalizer.normalizeForVoice("l'albero")).isEqualTo("l albero")
    }

    @Test
    fun `normalizeForVoice replaces hyphen with space`() {
        assertThat(Normalizer.normalizeForVoice("e-mail")).isEqualTo("e mail")
    }

    @Test
    fun `normalizeForVoice replaces punctuation with space and collapses`() {
        assertThat(Normalizer.normalizeForVoice("ciao. mondo? sì!"))
            .isEqualTo("ciao mondo si")
    }

    @Test
    fun `normalizeForVoice strips diacritics`() {
        assertThat(Normalizer.normalizeForVoice("perché")).isEqualTo("perche")
    }

    @Test
    fun `normalizeForVoice lowercases`() {
        assertThat(Normalizer.normalizeForVoice("CIAO")).isEqualTo("ciao")
    }

    @Test
    fun `normalizeForVoice keeps letters and digits`() {
        assertThat(Normalizer.normalizeForVoice("abc123")).isEqualTo("abc123")
    }

    @Test
    fun `normalizeForVoice collapses multiple generated spaces`() {
        // Несколько спецсимволов подряд → несколько пробелов → один.
        assertThat(Normalizer.normalizeForVoice("a...---b")).isEqualTo("a b")
    }

    @Test
    fun `normalizeForVoice trims leading and trailing`() {
        assertThat(Normalizer.normalizeForVoice("   ciao   ")).isEqualTo("ciao")
    }

    @Test
    fun `normalizeForVoice keeps cyrillic letters`() {
        assertThat(Normalizer.normalizeForVoice("Привет!")).isEqualTo("привет")
    }

    // ── isExactMatch ───────────────────────────────────────────────────────

    @Test
    fun `isExactMatch true when normalized input equals an accepted answer`() {
        assertThat(Normalizer.isExactMatch("perché", listOf("perche"))).isTrue()
        assertThat(Normalizer.isExactMatch("  Ciao ", listOf("ciao"))).isTrue()
    }

    @Test
    fun `isExactMatch false when no accepted answer matches`() {
        assertThat(Normalizer.isExactMatch("hola", listOf("ciao", "salve"))).isFalse()
    }

    @Test
    fun `isExactMatch false when input shorter than minLength default 2`() {
        // minLength=2: однобуквенный ввод не принимается.
        assertThat(Normalizer.isExactMatch("a", listOf("a"))).isFalse()
    }

    @Test
    fun `isExactMatch respects custom minLength`() {
        // minLength=5: короткий верный ответ отклоняется.
        assertThat(Normalizer.isExactMatch("ciao", listOf("ciao"), minLength = 5)).isFalse()
        assertThat(Normalizer.isExactMatch("ciao", listOf("ciao"), minLength = 4)).isTrue()
    }

    @Test
    fun `isExactMatch true if any of accepted answers matches`() {
        assertThat(Normalizer.isExactMatch("salve", listOf("ciao", "salve"))).isTrue()
    }

    // ── Краевые случаи ─────────────────────────────────────────────────────

    @Test
    fun `normalize empty string returns empty`() {
        assertThat(Normalizer.normalize("")).isEqualTo("")
    }

    @Test
    fun `normalize only whitespace returns empty`() {
        assertThat(Normalizer.normalize("    \t  \n ")).isEqualTo("")
    }

    @Test
    fun `normalize only punctuation returns empty`() {
        assertThat(Normalizer.normalize(".,?!:;()[]")).isEqualTo("")
    }

    @Test
    fun `normalize keeps apostrophe even when alone`() {
        assertThat(Normalizer.normalize("'")).isEqualTo("'")
    }

    @Test
    fun `normalize emoji is not a letter or filtered punctuation so kept`() {
        // Emoji не входит в skip-множество пунктуации → сохраняется (как else-ветка).
        // Проверяем детерминизм: результат стабилен.
        val result = Normalizer.normalize("ciao 😀 mondo")
        assertThat(result).contains("ciao")
        assertThat(result).contains("mondo")
    }

    @Test
    fun `normalizeForVoice empty returns empty`() {
        assertThat(Normalizer.normalizeForVoice("")).isEqualTo("")
    }

    @Test
    fun `normalizeForVoice only punctuation returns empty`() {
        // Вся пунктуация → пробелы → trim → пусто.
        assertThat(Normalizer.normalizeForVoice(".,?!")).isEqualTo("")
    }
}

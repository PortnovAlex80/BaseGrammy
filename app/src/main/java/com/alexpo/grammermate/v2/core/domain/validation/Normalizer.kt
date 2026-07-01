package com.alexpo.grammermate.v2.core.domain.validation

/**
 * Чистая нормализация текста для сравнения ответов.
 *
 * Перенесено из v1 `app/legacy-src/java/com/alexpo/grammermate/data/Normalization.kt`.
 * Это вычислительное ядро проверки ответов: trim+collapse whitespace,
 * NFD-декомпозиция со снятием диакритик, нормализация времени, lowercase и
 * посимвольная фильтрация пунктуации. Чистый Kotlin, ноль Android-зависимостей.
 *
 * **ВАЖНОЕ РАСХОЖЕНИЕ С v1 (исправление бага): апостроф сохраняется.**
 * В v1 `Normalization.normalize` (строка 20) апостроф и его варианты
 * (`'`, `` ` ``, `´`, `'`, `'`) удалялись вместе с пунктуацией. Это приводило к
 * тому, что `"l'albero"` и `"lalbero"` считались равными, а `"l albero"`
 * (через normalizeForVoice) — нет: inconsistence между keyboard и voice.
 * В v2 апостроф НЕ входит в skip-множество — он сохраняется как есть,
 * поэтому keyboard-нормализация не «съедает» апостроф. Это унифицирует
 * сравнение и чинит ложные срабатывания.
 */
object Normalizer {

    // Предкомпилированные regex — без пересборки на каждый вызов normalize().
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val DIACRITICAL_MARKS_REGEX = Regex("\\p{M}")

    /** `\b(\d{1,2}):\d{2}\b` → группа 1 (часы без минут), напр. "12:30" → "12". */
    private val TIME_MINUTES_REGEX = Regex("\\b(\\d{1,2}):\\d{2}\\b")

    /**
     * Нормализовать ввод для сравнения с клавиатуры (KEYBOARD).
     *
     * Алгоритм (перенесён 1:1 из v1 `Normalization.normalize`, строки 10–30):
     * 1. trim + схлопывание whitespace в одиночные пробелы;
     * 2. NFD-декомпозиция + удаление combining diacritical marks (é → e);
     * 3. regex времени `\b(\d{1,2}):\d{2}\b` → `$1` (минуты отбрасываются);
     * 4. lowercase;
     * 5. посимвольный фильтр: skip — `` ` ``, `´`, `.`, `,`, `?`, `!`, `:`, `;`,
     *    `"`, `<`, `>`, `(`, `)`, `[`, `]`, `{`, `}`; keep — `-` (дефис) и все
     *    буквы/цифры. **Апостроф `'` — keep (расхождение с v1, см. KDoc класса).**
     * 6. финальный collapse whitespace + trim.
     */
    fun normalize(input: String): String {
        val trimmed = input.trim().replace(WHITESPACE_REGEX, " ")
        // NFD-декомпозиция + снятие combining diacritical marks ("perche" == "perché")
        val decomposed = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFD)
        val noDiacritics = decomposed.replace(DIACRITICAL_MARKS_REGEX, "")
        val timeFixed = noDiacritics.replace(TIME_MINUTES_REGEX, "$1")
        val lower = timeFixed.lowercase()
        val builder = StringBuilder()
        for (ch in lower) {
            when (ch) {
                // v1 также skip-ал апостроф здесь; v2 — СОХРАНЯЕТ (исправление бага).
                '`', '´', '.', ',', '?', '!', ':', ';', '"', '<', '>', '(', ')', '[', ']', '{', '}' -> {
                    // skip пунктуацию
                }
                else -> builder.append(ch)
            }
        }
        return builder.toString().replace(WHITESPACE_REGEX, " ").trim()
    }

    /**
     * Универсальная нормализация для голосового ввода (VOICE).
     *
     * Перенесено 1:1 из v1 `Normalization.normalizeForVoice`, строки 54–70.
     *
     * Распознавание речи способно выдать ТОЛЬКО буквы, цифры и пробелы — никогда
     * апострофы, дефисы или пунктуацию. Поэтому любой спецсимвол в ожидаемом
     * ответе превращается в пробел перед сравнением.
     *
     * Алгоритм:
     * 1. trim + collapse whitespace;
     * 2. NFD-декомпозиция + снятие диакритик (é → e);
     * 3. lowercase;
     * 4. КАЖДЫЙ не-буквенный/не-цифровой символ → пробел (универсально для любого
     *    языка: апострофы, дефисы, тире, кавычки, любая Unicode-пунктуация);
     * 5. collapse пробелов + trim.
     *
     * Примеры: "un'insegnante" → "un insegnante", "va' a casa" → "va a casa".
     */
    fun normalizeForVoice(input: String): String {
        val trimmed = input.trim().replace(WHITESPACE_REGEX, " ")
        val decomposed = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFD)
        val noDiacritics = decomposed.replace(DIACRITICAL_MARKS_REGEX, "")
        val lower = noDiacritics.lowercase()
        val builder = StringBuilder()
        for (ch in lower) {
            if (ch.isLetterOrDigit()) {
                builder.append(ch)
            } else {
                builder.append(' ')
            }
        }
        return builder.toString().replace(WHITESPACE_REGEX, " ").trim()
    }

    /**
     * Точное совпадение [input] с любым из [acceptedAnswers] после нормализации.
     *
     * Перенесено 1:1 из v1 `Normalization.isExactMatch`, строки 77–83.
     * Используется для auto-submit: true только если пользователь ввёл ПОЛНЫЙ
     * ответ (не префикс). [minLength] защищает от срабатывания на коротком вводе.
     *
     * @param input           сырой ввод пользователя.
     * @param acceptedAnswers список принимаемых ответов.
     * @param minLength       минимальная длина нормализованного ввода (по умолчанию 2).
     * @return true, если нормализованный ввод совпадает с нормализованным ответом.
     */
    fun isExactMatch(input: String, acceptedAnswers: List<String>, minLength: Int = 2): Boolean {
        val normalizedInput = normalize(input)
        if (normalizedInput.length < minLength) return false
        return acceptedAnswers.any { ans ->
            normalizedInput == normalize(ans)
        }
    }
}

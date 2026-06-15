package com.alexpo.grammermate.data

/**
 * A parallel Italian/Russian phrase pair (used for both collocations and
 * example sentences inside a [WordScript]).
 */
data class PhrasePair(
    val it: String,
    val ru: String
)

/**
 * Pause durations (milliseconds) inserted between the parts of a [WordScript]
 * when it is rendered to playback markup via [WordScript.toMarkup].
 *
 * These are the in-word pauses only. The pause *between* two consecutive words
 * in a deck is intentionally NOT part of this structure — it is owned by the
 * DeckPlayer so the deck can vary it independently (e.g. per-user pacing)
 * without re-rendering every word.
 *
 * Defaults match the background-vocab MVP echo-script design.
 */
data class ScriptPauses(
    val afterWord: Long = 300,
    val afterTranslation: Long = 500,
    val afterCollocation: Long = 300,
    val afterSentence: Long = 400
)

/**
 * Full playback content for a single background-vocab word.
 *
 * A word is rendered (via [toMarkup]) into the `{lang}…{/lang}{pause:N}` markup
 * that [MultilingualStoryParser.parseSegments] + AudioCoordinator.playSegments
 * already consume. The rendered string is a flat, well-formed sequence of
 * language-tagged text runs and explicit pauses — no trailing between-words
 * pause is appended (the DeckPlayer supplies that).
 *
 * @property rank     Frequency/presentation rank (1-based, ascending).
 * @property wordIt   The Italian word itself, e.g. "casa".
 * @property wordRu   Russian translation(s) of the word. May contain "/"
 *                    alternatives (kept verbatim), e.g. "дом / жилище".
 * @property colloIt  One Italian collocation phrase featuring the word.
 * @property colloRu  Russian translation of [colloIt].
 * @property sentences 3-5 Italian/Russian example sentence pairs.
 * @property pauses   Pause schedule used by [toMarkup].
 */
data class WordScript(
    val rank: Int,
    val wordIt: String,
    val wordRu: String,
    val colloIt: String,
    val colloRu: String,
    val sentences: List<PhrasePair>,
    val pauses: ScriptPauses = ScriptPauses()
) {

    /**
     * Render this word's full playback script as marked-up text.
     *
     * Structure (all on a single logical line — newlines are not significant
     * to the parser, which splits on `{lang}`/`{pause}` markers):
     *
     * ```
     * {it}<wordIt>{/it}{pause:<afterWord>}
     * {ru}<wordRu>{/ru}{pause:<afterTranslation>}
     * {it}<colloIt>{/it}{pause:<afterCollocation>}
     * {ru}<colloRu>{/ru}{pause:<afterTranslation>}
     * {it}<s1.it>{/it}{pause:<afterSentence>}
     * {ru}<s1.ru>{/ru}{pause:<afterSentence>}
     * … (one it/ru pair per sentence, each followed by afterSentence)
     * ```
     *
     * No trailing pause is emitted: the DeckPlayer inserts the between-words
     * gap, so a word's markup must end exactly on the last sentence's pause.
     *
     * Any `{`, `}` characters present in the field values are escaped to
     * Unicode angle brackets so they cannot break the parser's marker regex.
     */
    fun toMarkup(): String = buildString {
        appendSegment("it", wordIt)
        appendPause(pauses.afterWord)

        appendSegment("ru", wordRu)
        appendPause(pauses.afterTranslation)

        appendSegment("it", colloIt)
        appendPause(pauses.afterCollocation)

        appendSegment("ru", colloRu)
        appendPause(pauses.afterTranslation)

        for (s in sentences) {
            appendSegment("it", s.it)
            appendPause(pauses.afterSentence)
            appendSegment("ru", s.ru)
            appendPause(pauses.afterSentence)
        }
    }

    private fun StringBuilder.appendSegment(lang: String, raw: String) {
        append('{').append(lang).append('}')
        append(escapeBraces(raw))
        append("{/").append(lang).append('}')
    }

    private fun StringBuilder.appendPause(ms: Long) {
        append("{pause:").append(ms).append('}')
    }

    /**
     * Replace `{` and `}` with visually-similar Unicode brackets so the field
     * content can never introduce a stray `{lang}`/`{pause:N}`/`{/lang}` token.
     * The TTS engine speaks the substituted glyphs identically enough for
     * playback; the important property is that the parser stays well-formed.
     */
    private fun escapeBraces(s: String): String =
        if ('{' !in s && '}' !in s) s
        else s.replace('{', '〈').replace('}', '〉')
}

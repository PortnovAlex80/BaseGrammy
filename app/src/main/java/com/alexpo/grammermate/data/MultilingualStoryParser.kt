package com.alexpo.grammermate.data

import android.util.Log
import kotlin.math.min

/**
 * Parser for multilingual story content with language-specific insertions.
 *
 * Supports markers like {it}Italian text{/it} within story content.
 * The parser splits text into segments with associated languages.
 *
 * Additionally supports `{pause:N}` markers (N in milliseconds) which produce
 * [Segment.Pause] entries in [parseSegments]. The legacy [parseStory] entry
 * point filters pauses out so existing story playback is unchanged.
 *
 * Example:
 * ```
 * This is English. {it}Questo è italiano.{/it} Back to English.
 * ```
 *
 * Produces:
 * - Segment("This is English. ", "en")
 * - Segment("Questo è italiano.", "it")
 * - Segment(" Back to English.", "en")
 */
object MultilingualStoryParser {

    private const val TAG = "MultilingualStoryParser"

    /**
     * Sealed hierarchy of segments produced by [parseSegments].
     *
     * A playback loop consumes this list and either speaks the [Text] segment via
     * TTS or waits for [Pause.ms] milliseconds. Text-only consumers (e.g. story
     * playback) can ignore [Pause] entries via [parseStory].
     */
    sealed interface Segment {
        /**
         * A run of text belonging to [languageId] (one of the supported marker
         * languages or the caller-supplied default language id).
         */
        data class Text(val text: String, val languageId: String) : Segment

        /**
         * An explicit pause of [ms] milliseconds. Emitted from `{pause:N}` markup.
         */
        data class Pause(val ms: Long) : Segment

        /**
         * A pre-rendered audio clip on disk, played instead of TTS-synthesizing the
         * corresponding text. Emitted only by background-vocab playback (Wave 3
         * wiring in DeckPlayer) when a `.wav` clip resolves for a given slot;
         * stories NEVER produce [Audio] segments, so the story path is unaffected.
         *
         * The [SegmentPlayer] plays [file] via `MediaPlayer` when it exists on disk;
         * if the file is missing it logs a warning and advances (no TTS fallback
         * here — fallback happens upstream at segment-construction time, since an
         * [Audio] segment carries no text to synthesize). [languageId] is kept for
         * symmetry with [Text] and for logging/diagnostics.
         */
        data class Audio(val file: java.io.File, val languageId: String) : Segment
    }

    /**
     * Represents a text segment with its associated language.
     *
     * Kept as a top-level data class for backward compatibility with existing
     * callers of [parseStory] (e.g. AudioCoordinator). New callers should prefer
     * [Segment.Text] via [parseSegments].
     */
    data class TextSegment(
        val text: String,
        val languageId: String
    )

    /**
     * Language marker pattern: {lang}content{/lang}
     * Supported languages: it, en, ru, el, de (German), zh (Chinese)
     */
    private val languagePattern = Regex("""\{(it|en|ru|el|de|zh)\}(.+?)\{/\1\}""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Pause marker pattern: {pause:N} where N is a positive integer of milliseconds.
     * Used by background-vocab scripts to insert explicit delays between TTS segments.
     */
    private val pausePattern = Regex("""\{pause:(\d+)\}""")

    /**
     * Detect the language of a text sample by character analysis and word patterns.
     * Returns "ru", "en", "it", "el", or defaultLanguageId.
     */
    private fun detectLanguage(text: String, defaultLanguageId: String): String {
        val sample = text.take(500) // Analyze first 500 chars

        // Count character patterns for each language
        val ruChars = sample.count { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
        val enChars = sample.count { it in 'a'..'z' || it in 'A'..'Z' }
        val itChars = sample.count { it in "àèéìòùÀÈÉÌÒÙ" } // Common Italian accented chars
        val elChars = sample.count { it in 'Ͱ'..'Ͽ' || it in 'ἀ'..'῿' } // Greek and Coptic / Greek Extended
        val deChars = sample.count { it in "äöüßÄÖÜ" } // German specific chars
        val zhChars = sample.count { it.code in 0x4E00..0x9FFF } // CJK Unified Ideographs

        // Count common Italian words for better detection
        val italianWords = listOf("questo", "quella", "questa", "essere", "avere", "per", "con", "da", "il", "lo", "la", "le", "un", "uno", "una", "in", "su", "a", "ad", "da", "di", "del", "dello", "della", "dei", "degli", "delle", "su", "sul", "sullo", "sulla", "sui", "sugli", "sulle", "tra", "fra", "anche", "ancora", "caso", "cosa", "fare", "dire", "vedere", "parlare", "essere")
        val italianWordMatches = italianWords.count { word -> sample.contains(word, ignoreCase = true) }

        // Count common Greek words for detection
        val greekWords = listOf("είμαι", "έχω", "είναι", "αυτό", "αυτή", "αυτός", "που", "με", "σε", "για", "το", "η", "τα", "τις", "τα", "και", "δεν", "στα", "στην", "στον", "μια", "ένα", "να", "με", "θα", "είναι", "μπορώ", "πρέπει", "ελληνικά")
        val greekWordMatches = greekWords.count { word -> sample.contains(word, ignoreCase = true) }

        // German common words
        val germanWords = listOf("und", "der", "die", "das", "ist", "ein", "eine", "nicht", "ich", "mit", "auf", "für", "sich", "auch", "als", "nach", "wie", "noch", "werden", "haben", "sein", "dieser", "welche", "mich", "dich", "sich", "uns", "euch", "mein", "dein", "kein", "werden", "wurde", "worden", "konnte", "gemacht", "gegangen")
        val germanWordMatches = germanWords.count { word -> sample.contains(word, ignoreCase = true) }

        // Common English words to exclude Italian
        val englishWords = listOf("this", "that", "with", "from", "have", "been", "will", "would", "could", "should", "about", "which", "their", "there", "where", "when", "what", "how", "then", "than", "more", "some", "such", "only", "into", "over", "after", "before", "being", "under", "while", "because", "though", "until", "again", "where", "through", "each", "much", "own", "same", "so", "good", "new", "first", "last", "long", "great", "little", "own", "other", "old", "right", "big", "high", "different", "small", "large", "next", "early", "young", "important", "public", "bad", "able", "free", "best", "better", "during", "enough", "both", "full", "tonight", "always", "anything", "anywhere", "being", "beautiful", "before", "believe", "between", "both", "bring", "build", "business", "but", "by", "call", "came", "can", "come", "could", "course", "develop", "different", "do", "does", "done", "don", "down", "during", "early", "education", "enough", "even", "ever", "every", "example", "face", "family", "far", "fast", "field", "fight", "find", "first", "for", "from", "get", "give", "go", "good", "great", "group", "grow", "had", "has", "have", "he", "head", "help", "her", "here", "high", "history", "home", "how", "however", "if", "important", "in", "include", "into", "is", "it", "its", "just", "keep", "know", "large", "last", "late", "learn", "leave", "life", "like", "line", "little", "long", "look", "make", "man", "many", "may", "me", "member", "might", "mile", "million", "miss", "more", "most", "much", "music", "must", "my", "name", "never", "new", "news", "next", "night", "no", "not", "now", "of", "off", "often", "old", "on", "once", "one", "only", "or", "other", "our", "out", "over", "own", "part", "people", "place", "play", "point", "political", "possible", "present", "president", "problem", "program", "provide", "public", "purpose", "question", "rather", "really", "result", "return", "right", "run", "same", "say", "school", "second", "see", "seem", "see", "service", "set", "several", "should", "since", "small", "so", "social", "some", "something", "special", "start", "statement", "still", "such", "system", "take", "talk", "teach", "tell", "than", "that", "the", "their", "them", "then", "there", "these", "they", "thing", "think", "this", "those", "though", "three", "through", "time", "to", "today", "together", "too", "toward", "travel", "try", "turn", "two", "under", "understand", "unit", "until", "up", "upon", "use", "usually", "value", "very", "want", "way", "we", "week", "well", "west", "what", "whatever", "when", "where", "whether", "which", "while", "white", "who", "whole", "whose", "why", "will", "with", "within", "without", "word", "work", "world", "would", "write", "year", "you", "your", "yours")
        val englishWordMatches = englishWords.count { word -> sample.contains(word, ignoreCase = true) }

        val total = ruChars + enChars + itChars + elChars + deChars + zhChars
        if (total == 0) {
            Log.d(TAG, "detectLanguage: no alphabetic chars, using default: $defaultLanguageId")
            return defaultLanguageId
        }

        val ruRatio = ruChars.toFloat() / total
        val enRatio = enChars.toFloat() / total
        val itRatio = itChars.toFloat() / total
        val elRatio = elChars.toFloat() / total
        val deRatio = deChars.toFloat() / total

        // Enhanced detection with word analysis
        val detected = when {
            // Chinese detection (CJK characters are distinctive)
            zhChars >= 3 -> "zh"

            // Greek detection (Greek script is very distinctive)
            elChars >= 3 && greekWordMatches >= 1 -> "el"
            greekWordMatches >= 3 -> "el"
            elRatio > 0.15f && elChars >= 5 -> "el"

            // Italian detection (HIGHEST PRIORITY - check first)
            // If we have multiple Italian words, it's Italian regardless of Cyrillic count
            italianWordMatches >= 3 && italianWordMatches > englishWordMatches -> "it"
            italianWordMatches >= 5 -> "it"
            itChars >= 3 && italianWordMatches >= 1 -> "it"
            itRatio > 0.2f && itChars >= 2 -> "it"

            // German detection (umlauts + common German words)
            deChars >= 2 && germanWordMatches >= 2 -> "de"
            germanWordMatches >= 5 -> "de"
            deRatio > 0.15f && deChars >= 2 -> "de"

            // Russian detection (Cyrillic is very distinctive)
            ruRatio > 0.1f && ruChars > 10 -> "ru"

            // English detection (default for Latin script)
            enRatio > 0.3f -> "en"

            // Fallback to default
            else -> defaultLanguageId
        }

        Log.d(TAG, "detectLanguage: ruChars=$ruChars ruRatio=$ruRatio, enChars=$enChars enRatio=$enRatio, itChars=$itChars itRatio=$itRatio, elChars=$elChars elRatio=$elRatio")
        Log.d(TAG, "detectLanguage: italianWords=$italianWordMatches, englishWords=$englishWordMatches, greekWords=$greekWordMatches")
        Log.d(TAG, "detectLanguage: detected='$detected', default='$defaultLanguageId'")
        Log.d(TAG, "detectLanguage: sample preview='${sample.take(50).replace("\n", "\\n")}'")

        return detected
    }

    /**
     * Parse story content and extract language-specific segments.
     * Detects paragraph languages automatically for proper default language handling.
     *
     * @param content The full story content (may contain markdown)
     * @param defaultLanguageId Default language for text without markers (e.g., "en", "ru")
     * @return List of text segments with associated languages
     */
    fun parseStory(content: String, defaultLanguageId: String = "en"): List<TextSegment> {
        if (content.isBlank()) return emptyList()

        // Delegate to parseSegments and filter pauses out. Stories currently
        // contain no {pause:N} markers, so behavior is identical to before.
        // This keeps the legacy TextSegment return type for existing callers
        // (AudioCoordinator etc.) while sharing one implementation.
        return parseSegments(content, defaultLanguageId)
            .mapNotNull { segment ->
                when (segment) {
                    is Segment.Text -> TextSegment(segment.text, segment.languageId)
                    is Segment.Pause -> null
                    // Audio clips carry no speakable text; they are skipped on the
                    // text-only story path. (parseSegments never emits Audio today —
                    // stories contain no audio markup — but the branch is required
                    // for exhaustivity once the variant exists.)
                    is Segment.Audio -> null
                }
            }
    }

    /**
     * Parse content into a mixed stream of [Segment.Text] and [Segment.Pause].
     *
     * Same paragraph/language-marker logic as [parseStory], but additionally
     * recognizes `{pause:N}` markers and emits [Segment.Pause] at the correct
     * stream position (between the surrounding text segments).
     *
     * Example: `{it}casa{/it}{pause:300}{ru}дом{/ru}` produces
     * `[Text("casa","it"), Pause(300), Text("дом","ru")]`.
     *
     * Text segments are trimmed and the same default-language detection rules
     * as [parseStory] apply. Pause ordering is preserved exactly as written.
     *
     * @param content The marked-up content (may contain markdown, {lang}…{/lang} and {pause:N})
     * @param defaultLanguageId Default language for text without markers
     * @return Ordered list of [Segment]s (text + pauses interleaved)
     */
    fun parseSegments(content: String, defaultLanguageId: String = "en"): List<Segment> {
        if (content.isBlank()) return emptyList()

        // Normalize CRLF to LF for consistent paragraph splitting
        val normalized = content.replace("\r\n", "\n")

        Log.d(TAG, "=== parseSegments START ===")
        Log.d(TAG, "Content length: ${normalized.length}, defaultLanguageId: $defaultLanguageId")

        val segments = mutableListOf<Segment>()

        // Split into paragraphs (double newline or markdown headers)
        val paragraphSplitRegex = Regex("""(\n\n+|^#{1,6}\s+.*$)""", RegexOption.MULTILINE)
        val paragraphs = normalized.split(paragraphSplitRegex)

        Log.d(TAG, "Found ${paragraphs.size} paragraphs")

        var globalSegmentIndex = 0

        for ((paraIndex, paragraph) in paragraphs.withIndex()) {
            if (paragraph.isBlank()) continue

            // Find all language markers within this paragraph
            val langMatches = languagePattern.findAll(paragraph).toList()

            if (langMatches.isEmpty()) {
                // No language markers: unmarked text belongs to the CALLER'S
                // default language (the parameter is the contract — auto-
                // detection would make it meaningless). Pauses are still split
                // out so pause-interleaved paragraphs produce Pause segments.
                val paraLang = defaultLanguageId
                Log.d(TAG, "Paragraph $paraIndex: detected language='$paraLang', preview=${paragraph.take(30).replace("\n", "\\n")}...")
                Log.d(TAG, "  → No lang markers, using detected language: $paraLang")
                val added = emitTextWithPauses(paragraph, paraLang, segments)
                globalSegmentIndex += added
            } else {
                // Language markers present — non-marked text is in defaultLanguageId.
                // detectLanguage() on raw paragraph with {it} tags skews toward Italian.
                val paraLang = defaultLanguageId
                Log.d(TAG, "Paragraph $paraIndex: has ${langMatches.size} lang markers, using defaultLanguage='$paraLang'")

                // Walk the paragraph left-to-right, interleaving pauses with text.
                // For each gap between markers (and before/after the marker run),
                // we emit the text via emitTextWithPauses so pauses inside the
                // default-language gaps are preserved. Marker inner text is also
                // routed through emitTextWithPauses so {pause:N} inside markers
                // (rare but possible) is handled too.
                var lastIndex = 0

                for (match in langMatches) {
                    val (fullMatch, langId, segmentText) = match.groupValues
                    val startIndex = match.range.first

                    // Text before this marker belongs to the paragraph language.
                    if (startIndex > lastIndex) {
                        val beforeText = paragraph.substring(lastIndex, startIndex)
                        if (beforeText.isNotBlank()) {
                            Log.d(TAG, "  → Gap before marker: language=$paraLang, preview=${beforeText.take(30).replace("\n", "\\n")}...")
                            globalSegmentIndex += emitTextWithPauses(beforeText, paraLang, segments)
                        }
                    }

                    // The language-specific segment.
                    if (segmentText.isNotBlank()) {
                        Log.d(TAG, "  → Segment ${globalSegmentIndex}: language=$langId (MARKER), preview=${segmentText.take(30).replace("\n", "\\n")}...")
                        globalSegmentIndex += emitTextWithPauses(segmentText, langId, segments)
                    }

                    lastIndex = match.range.last + 1
                }

                // Remaining text after the last marker.
                if (lastIndex < paragraph.length) {
                    val afterText = paragraph.substring(lastIndex)
                    if (afterText.isNotBlank()) {
                        Log.d(TAG, "  → Tail after markers: language=$paraLang, preview=${afterText.take(30).replace("\n", "\\n")}...")
                        globalSegmentIndex += emitTextWithPauses(afterText, paraLang, segments)
                    }
                }
            }
        }

        Log.d(TAG, "=== parseSegments END ===")
        Log.d(TAG, "Total segments: $globalSegmentIndex")
        Log.d(TAG, "Segment kinds: ${segments.map { kindLabel(it) }}")
        return segments
    }

    /**
     * Emit text from [raw] into [out], splitting on `{pause:N}` markers.
     *
     * Text between pauses becomes [Segment.Text] with the given [languageId]
     * (trimmed; blank chunks are dropped). Each `{pause:N}` becomes a
     * [Segment.Pause] with N milliseconds, preserving order.
     *
     * Returns the number of segments appended.
     */
    private fun emitTextWithPauses(raw: String, languageId: String, out: MutableList<Segment>): Int {
        val pauseMatches = pausePattern.findAll(raw).toList()
        if (pauseMatches.isEmpty()) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return 0
            out.add(Segment.Text(trimmed, languageId))
            return 1
        }

        var added = 0
        var lastIndex = 0
        for (match in pauseMatches) {
            val start = match.range.first
            if (start > lastIndex) {
                val chunk = raw.substring(lastIndex, start).trim()
                if (chunk.isNotEmpty()) {
                    out.add(Segment.Text(chunk, languageId))
                    added++
                }
            }
            val msValue = match.groupValues[1].toLong()
            out.add(Segment.Pause(msValue))
            added++
            lastIndex = match.range.last + 1
        }
        // Trailing text after the final pause.
        if (lastIndex < raw.length) {
            val chunk = raw.substring(lastIndex).trim()
            if (chunk.isNotEmpty()) {
                out.add(Segment.Text(chunk, languageId))
                added++
            }
        }
        return added
    }

    /**
     * Human-readable label for a [Segment], used for debug logging. Handles all
     * three variants (Text / Pause / Audio) without an exhaustive `when` so it
     * is robust to future sealed-subclass additions.
     */
    private fun kindLabel(segment: Segment): String = when (segment) {
        is Segment.Pause -> "Pause(${segment.ms})"
        is Segment.Audio -> "Audio(${segment.file.name}/${segment.languageId})"
        is Segment.Text -> "Text(${segment.languageId})"
    }

    /**
     * Parse story content and strip all language markers, returning plain text.
     * Also strips `{pause:N}` markers so plain-text fallbacks (clipboard copy,
     * on-screen rendering) stay clean.
     *
     * @param content The story content with markers
     * @return Plain text without markers
     */
    fun stripMarkers(content: String): String {
        val withoutLang = languagePattern.replace(content) { match ->
            // Extract just the text content without markers
            match.groupValues[2]
        }
        return pausePattern.replace(withoutLang) { "" }
    }

    /**
     * Detect if content contains any language markers.
     *
     * @param content The story content to check
     * @return true if content contains {lang}...{/lang} markers
     */
    fun hasMarkers(content: String): Boolean {
        return languagePattern.containsMatchIn(content)
    }

    /**
     * Clean markdown syntax from text before TTS playback.
     * Removes headers, bold, italic, code blocks while preserving content.
     *
     * @param markdown Text with markdown syntax
     * @return Clean text suitable for TTS
     */
    fun cleanMarkdown(markdown: String): String {
        val normalized = markdown.replace("\r\n", "\n")
        return normalized
            .replace(Regex("""^#+\s+.*$"""), "") // Headers
            .replace(Regex("""\*\*([^*]+)\*\*"""), "$1") // Bold
            .replace(Regex("""\*([^*]+)\*"""), "$1") // Italic
            .replace(Regex("""```[^`]*```"""), "") // Code blocks
            .replace(Regex("""```"""), "") // Code block markers
            .replace(Regex("""[-*]\s+"""), "") // List markers
            .replace(Regex("""\n\n+"""), "\n") // Multiple newlines
            .trim()
    }
}

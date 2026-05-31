package com.alexpo.grammermate.data

import android.util.Log
import kotlin.math.min

/**
 * Parser for multilingual story content with language-specific insertions.
 *
 * Supports markers like {it}Italian text{/it} within story content.
 * The parser splits text into segments with associated languages.
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
     * Represents a text segment with its associated language.
     */
    data class TextSegment(
        val text: String,
        val languageId: String
    )

    /**
     * Language marker pattern: {lang}content{/lang}
     * Supported languages: it (Italian), en (English), ru (Russian), el (Greek)
     */
    private val languagePattern = Regex("""\{(it|en|ru|el)\}(.+?)\{/\1\}""", RegexOption.DOT_MATCHES_ALL)

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

        // Count common Italian words for better detection
        val italianWords = listOf("questo", "quella", "questa", "essere", "avere", "per", "con", "da", "il", "lo", "la", "le", "un", "uno", "una", "in", "su", "a", "ad", "da", "di", "del", "dello", "della", "dei", "degli", "delle", "su", "sul", "sullo", "sulla", "sui", "sugli", "sulle", "tra", "fra", "anche", "ancora", "caso", "cosa", "fare", "dire", "vedere", "parlare", "essere")
        val italianWordMatches = italianWords.count { word -> sample.contains(word, ignoreCase = true) }

        // Count common Greek words for detection
        val greekWords = listOf("είμαι", "έχω", "είναι", "αυτό", "αυτή", "αυτός", "που", "με", "σε", "για", "το", "η", "τα", "τις", "τα", "και", "δεν", "στα", "στην", "στον", "μια", "ένα", "να", "με", "θα", "είναι", "μπορώ", "πρέπει", "ελληνικά")
        val greekWordMatches = greekWords.count { word -> sample.contains(word, ignoreCase = true) }

        // Common English words to exclude Italian
        val englishWords = listOf("this", "that", "with", "from", "have", "been", "will", "would", "could", "should", "about", "which", "their", "there", "where", "when", "what", "how", "then", "than", "more", "some", "such", "only", "into", "over", "after", "before", "being", "under", "while", "because", "though", "until", "again", "where", "through", "each", "much", "own", "same", "so", "good", "new", "first", "last", "long", "great", "little", "own", "other", "old", "right", "big", "high", "different", "small", "large", "next", "early", "young", "important", "public", "bad", "able", "free", "best", "better", "during", "enough", "both", "full", "tonight", "always", "anything", "anywhere", "being", "beautiful", "before", "believe", "between", "both", "bring", "build", "business", "but", "by", "call", "came", "can", "come", "could", "course", "develop", "different", "do", "does", "done", "don", "down", "during", "early", "education", "enough", "even", "ever", "every", "example", "face", "family", "far", "fast", "field", "fight", "find", "first", "for", "from", "get", "give", "go", "good", "great", "group", "grow", "had", "has", "have", "he", "head", "help", "her", "here", "high", "history", "home", "how", "however", "if", "important", "in", "include", "into", "is", "it", "its", "just", "keep", "know", "large", "last", "late", "learn", "leave", "life", "like", "line", "little", "long", "look", "make", "man", "many", "may", "me", "member", "might", "mile", "million", "miss", "more", "most", "much", "music", "must", "my", "name", "never", "new", "news", "next", "night", "no", "not", "now", "of", "off", "often", "old", "on", "once", "one", "only", "or", "other", "our", "out", "over", "own", "part", "people", "place", "play", "point", "political", "possible", "present", "president", "problem", "program", "provide", "public", "purpose", "question", "rather", "really", "result", "return", "right", "run", "same", "say", "school", "second", "see", "seem", "see", "service", "set", "several", "should", "since", "small", "so", "social", "some", "something", "special", "start", "statement", "still", "such", "system", "take", "talk", "teach", "tell", "than", "that", "the", "their", "them", "then", "there", "these", "they", "thing", "think", "this", "those", "though", "three", "through", "time", "to", "today", "together", "too", "toward", "travel", "try", "turn", "two", "under", "understand", "unit", "until", "up", "upon", "use", "usually", "value", "very", "want", "way", "we", "week", "well", "west", "what", "whatever", "when", "where", "whether", "which", "while", "white", "who", "whole", "whose", "why", "will", "with", "within", "without", "word", "work", "world", "would", "write", "year", "you", "your", "yours")
        val englishWordMatches = englishWords.count { word -> sample.contains(word, ignoreCase = true) }

        val total = ruChars + enChars + itChars + elChars
        if (total == 0) {
            Log.d(TAG, "detectLanguage: no alphabetic chars, using default: $defaultLanguageId")
            return defaultLanguageId
        }

        val ruRatio = ruChars.toFloat() / total
        val enRatio = enChars.toFloat() / total
        val itRatio = itChars.toFloat() / total
        val elRatio = elChars.toFloat() / total

        // Enhanced detection with word analysis
        val detected = when {
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

        // Normalize CRLF to LF for consistent paragraph splitting
        val normalized = content.replace("\r\n", "\n")

        Log.d(TAG, "=== parseStory START ===")
        Log.d(TAG, "Content length: ${normalized.length}, defaultLanguageId: $defaultLanguageId")

        val segments = mutableListOf<TextSegment>()

        // Split into paragraphs (double newline or markdown headers)
        val paragraphSplitRegex = Regex("""(\n\n+|^#{1,6}\s+.*$)""", RegexOption.MULTILINE)
        val paragraphs = normalized.split(paragraphSplitRegex)

        Log.d(TAG, "Found ${paragraphs.size} paragraphs")

        var globalSegmentIndex = 0

        for ((paraIndex, paragraph) in paragraphs.withIndex()) {
            if (paragraph.isBlank()) continue

            // Find all language markers within this paragraph
            val matches = languagePattern.findAll(paragraph).toList()

            if (matches.isEmpty()) {
                // No markers - detect language for entire paragraph
                val paraLang = detectLanguage(paragraph, defaultLanguageId)
                Log.d(TAG, "Paragraph $paraIndex: detected language='$paraLang', preview=${paragraph.take(30).replace("\n", "\\n")}...")
                Log.d(TAG, "  → No markers, using detected language: $paraLang")
                segments.add(TextSegment(paragraph.trim(), paraLang))
                globalSegmentIndex++
            } else {
                // Markers present — non-marked text is in defaultLanguageId.
                // detectLanguage() on raw paragraph with {it} tags skews toward Italian.
                val paraLang = defaultLanguageId
                Log.d(TAG, "Paragraph $paraIndex: has ${matches.size} markers, using defaultLanguage='$paraLang'")

                // Process paragraph with markers
                var lastIndex = 0

                for ((matchIndex, match) in matches.withIndex()) {
                    val (fullMatch, langId, segmentText) = match.groupValues
                    val startIndex = match.range.first

                    // Add text before the marker (paragraph language, NOT global default)
                    if (startIndex > lastIndex) {
                        val beforeText = paragraph.substring(lastIndex, startIndex)
                        if (beforeText.isNotBlank()) {
                            Log.d(TAG, "  → Segment ${globalSegmentIndex}: language=$paraLang, preview=${beforeText.take(30).replace("\n", "\\n")}...")
                            segments.add(TextSegment(beforeText.trim(), paraLang))
                            globalSegmentIndex++
                        }
                    }

                    // Add the language-specific segment
                    if (segmentText.isNotBlank()) {
                        Log.d(TAG, "  → Segment ${globalSegmentIndex}: language=$langId (MARKER), preview=${segmentText.take(30).replace("\n", "\\n")}...")
                        segments.add(TextSegment(segmentText.trim(), langId))
                        globalSegmentIndex++
                    }

                    lastIndex = match.range.last + 1
                }

                // Add remaining text after the last marker (paragraph language)
                if (lastIndex < paragraph.length) {
                    val afterText = paragraph.substring(lastIndex)
                    if (afterText.isNotBlank()) {
                        Log.d(TAG, "  → Segment ${globalSegmentIndex}: language=$paraLang, preview=${afterText.take(30).replace("\n", "\\n")}...")
                        segments.add(TextSegment(afterText.trim(), paraLang))
                        globalSegmentIndex++
                    }
                }
            }
        }

        Log.d(TAG, "=== parseStory END ===")
        Log.d(TAG, "Total segments: $globalSegmentIndex")
        Log.d(TAG, "Segment languages: ${segments.map { "${it.languageId}: [${it.text.take(20).replace("\n", " ")}]" }}")
        return segments
    }

    /**
     * Parse story content and strip all language markers, returning plain text.
     * Useful for fallback when multilingual playback is not available.
     *
     * @param content The story content with markers
     * @return Plain text without markers
     */
    fun stripMarkers(content: String): String {
        return languagePattern.replace(content) { match ->
            // Extract just the text content without markers
            match.groupValues[2]
        }
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

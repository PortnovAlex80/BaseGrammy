package com.alexpo.grammermate.data

object Normalizer {
    fun normalize(input: String): String {
        val trimmed = input.trim().replace(Regex("\\s+"), " ")
        // NFD decomposition + strip combining diacritical marks (e.g. "perche" == "perché")
        val decomposed = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFD)
        val noDiacritics = decomposed.replace(Regex("\\p{M}"), "")
        val timeFixed = noDiacritics.replace(Regex("\\b(\\d{1,2}):\\d{2}\\b"), "$1")
        val lower = timeFixed.lowercase()
        val builder = StringBuilder()
        for (ch in lower) {
            when (ch) {
                '.', ',', '?', '!', ':', ';', '"', '<', '>', '(', ')', '[', ']', '{', '}' -> {
                    // Skip punctuation.
                }
                '-' -> {
                    builder.append(ch)
                }
                else -> builder.append(ch)
            }
        }
        return builder.toString().replace(Regex("\\s+"), " ").trim()
    }
}

package com.alexpo.grammermate.data

object Normalizer {
    fun normalize(input: String): String {
        val trimmed = input.trim().replace(Regex("\\s+"), " ")
        val lower = trimmed.lowercase()
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

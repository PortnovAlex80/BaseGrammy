package com.alexpo.grammermate.data

import org.json.JSONObject
import org.yaml.snakeyaml.Yaml

/** Structural validation of formats actually read by the stores, including historical wrappers. */
internal object BackupContentValidator {
    private val numbers = setOf("uniqueCardShows", "totalCardShows", "lastShowDateMs", "intervalStepIndex",
        "completedAtMs", "lastReviewMs", "effortAtLastReview", "correctCount", "incorrectCount",
        "lastReviewDateMs", "nextReviewDateMs", "totalCards", "currentIndex", "activeSubLessonIndex",
        "incorrectAttemptsForCard", "activeTimeMs", "voiceActiveMs", "voiceWordCount", "hintCount",
        "eliteStepIndex", "dailyLevel", "dailyTaskIndex", "welcomeDialogAttempts", "currentStreak",
        "longestStreak", "lastCompletionDateMs", "totalSubLessonsCompleted", "todayFireCount", "lastFireDateMs",
        "sentenceOffset", "currentLessonIndex", "lastSessionHash", "verbOffset", "cardIndex",
        "addedAtMs", "lastCorrectMs", "lastIncorrectMs", "intervalStep", "completedAtMs",
        "durationMinutes", "totalSeconds", "remainingSeconds", "cardsShown", "cardsCorrect", "cardsIncorrect", "wordsPerMinute")
    private val strings = setOf("languageId", "lessonId", "activePackId", "packId", "userName", "state",
        "mode", "currentScreen", "bossMegaReward", "group", "verb", "tense", "lastDate", "selectedTense",
        "selectedGroup", "selectedPerson", "firstSessionDate", "firstSessionLessonId", "cardId", "sentence",
        "translation", "id")
    private val stringLists = setOf("shownCardIds", "everShownCardIds", "todayShownCardIds", "sessionCardIds",
        "hiddenCardIds", "firstSessionSentenceCardIds", "firstSessionVerbCardIds", "completedTypesToday")
    private val booleans = setOf("isLearned", "sortByFrequency")

    fun isValid(path: String, content: String): Boolean { return try {
        if (content.isBlank()) false
        else if (path == "bg_vocab_marks.json") {
            val json = JSONObject(content)
            json.keys().asSequence().all { key -> json.get(key) in setOf("GREEN", "RED", "NONE") }
        } else if (path == "bg_vocab_position.json") {
            val json = JSONObject(content)
            !json.has("word") || json.get("word") is String
        } else {
            val root = Yaml().load<Any>(content) as? Map<*, *> ?: return false
            val name = path.substringAfterLast('/')
            when {
                name == "mastery.yaml" -> nested(payload(root, setOf(1, 2))) { lessons ->
                    nested(lessons) { state -> record(state, setOf("uniqueCardShows", "totalCardShows", "shownCardIds", "cardEncounterCounts", "intervalStepIndex", "lastShowDateMs", "lastReviewMs", "completedAtMs", "effortAtLastReview")) }
                }
                name == "progress.yaml" -> payload(root, setOf(1), keepLanguage = true)?.let { progress ->
                    record(progress, setOf("languageId", "mode", "lessonId", "activePackId", "dailyCursor", "currentIndex", "bossLessonRewards", "bossMegaRewards", "currentScreen")) &&
                        (progress["mode"] == null || TrainingMode.entries.any { it.name == progress["mode"] }) &&
                        (progress["dailyCursor"] == null || (progress["dailyCursor"] as? Map<*, *>)?.let(::cursor) == true)
                } == true
                name == "hidden_cards.yaml" -> version(root, setOf(1)) && stringList(root["hiddenCardIds"])
                name == "bad_sentences.yaml" -> badSentences(root)
                name == "profile.yaml" -> record(root, setOf("userName", "welcomeDialogAttempts"), false)
                name == "pomodoro_history.yaml" -> (root["sessions"] as? List<*>)?.all {
                    val entry = it as? Map<*, *> ?: return@all false
                    entry["languageId"] is String && entry["completedAtMs"] is Number && record(entry, setOf("languageId", "completedAtMs"))
                } == true
                name == "word_mastery.yaml" -> nested(payload(root, setOf(1))) { record(it, setOf("intervalStepIndex", "correctCount", "incorrectCount", "lastReviewDateMs", "nextReviewDateMs", "isLearned")) }
                name == "vocab_progress.yaml" -> nested(payload(root, setOf(1))) { lessons ->
                    nested(lessons) { lesson ->
                        (lesson["completedIndices"] == null || numberList(lesson["completedIndices"])) &&
                            (lesson["entries"] == null || nested(lesson["entries"] as? Map<*, *>) { record(it, setOf("lastCorrectMs", "lastIncorrectMs", "intervalStep")) }) &&
                            (lesson.isEmpty() || lesson.containsKey("completedIndices") || lesson.containsKey("entries"))
                    }
                }
                name.startsWith("verb_drill_last_session") -> version(root, setOf(1)) && record(root, setOf("sessionCardIds", "currentIndex", "selectedTense", "selectedGroup", "todayShownCardIds"), false)
                name.startsWith("verb_drill_progress") -> nested(payload(root, setOf(1))) { it["group"] is String && it["tense"] is String && record(it, setOf("group", "tense")) }
                name.startsWith("aux_drill_progress") -> nested(payload(root, setOf(1))) { it["verb"] is String && it["tense"] is String && record(it, setOf("verb", "tense")) }
                name.startsWith("streak_") -> record(root, setOf("currentStreak", "longestStreak", "lastCompletionDateMs", "totalSubLessonsCompleted"), false)
                name.startsWith("lesson_progress_") -> version(root, setOf(1)) && root["packId"] == name.removePrefix("lesson_progress_").removeSuffix(".yaml") && nested(root["lessonProgress"] as? Map<*, *>) { record(it, setOf("currentIndex", "state", "activeTimeMs", "activeSubLessonIndex")) }
                name.startsWith("daily_cursor_") -> version(root, setOf(1)) && root["packId"] == name.removePrefix("daily_cursor_").removeSuffix(".yaml") && cursor(root)
                name.startsWith("drill_progress_") -> root["lessonId"] is String && root["cardIndex"] is Number
                else -> false
            }
        }
    } catch (_: Exception) { false } }

    private fun version(root: Map<*, *>, supported: Set<Int>): Boolean =
        !root.containsKey("schemaVersion") || (root["schemaVersion"] as? Number)?.let { it.toDouble() == it.toInt().toDouble() && it.toInt() in supported } == true

    private fun payload(root: Map<*, *>, supported: Set<Int>, keepLanguage: Boolean = false): Map<*, *>? {
        if (!version(root, supported)) return null
        if (root.containsKey("data")) return root["data"] as? Map<*, *>
        val flat = root.filterKeys { it != "schemaVersion" && (keepLanguage || it != "languageId") }
        if (root.containsKey("schemaVersion") && flat.isEmpty()) return null
        return flat
    }

    private fun nested(map: Map<*, *>?, check: (Map<*, *>) -> Boolean): Boolean =
        map != null && map.all { (key, value) -> key is String && value is Map<*, *> && check(value) }

    private fun stringList(value: Any?): Boolean = value is List<*> && value.all { it is String }
    private fun numberList(value: Any?): Boolean = value is List<*> && value.all { it is Number }

    private fun record(map: Map<*, *>, signature: Set<String>, emptyAllowed: Boolean = true): Boolean {
        if (map.isEmpty()) return emptyAllowed
        if (map.keys.none { it in signature }) return false
        return map.all { (key, value) ->
            if (key !is String) false
            else if (value == null) true // Historical serializers wrote explicit nulls.
            else when (key) {
                "state" -> value is String && SessionState.entries.any { it.name == value }
                in numbers -> value is Number && value.toDouble().isFinite()
                in strings -> value is String
                in stringLists -> stringList(value)
                in booleans -> value is Boolean
                "eliteBestSpeeds", "completedIndices" -> numberList(value)
                "cardEncounterCounts" -> value is Map<*, *> && value.all { it.key is String && it.value is Number }
                "bossLessonRewards", "bossMegaRewards" -> value is Map<*, *> && value.all { it.key is String && it.value is String }
                "schemaVersion" -> value is Number && value.toInt() in 1..2
                else -> true // Forward-compatible optional fields do not invalidate known data.
            }
        }
    }

    private fun cursor(map: Map<*, *>): Boolean = record(map, setOf("sentenceOffset", "currentLessonIndex", "verbOffset", "firstSessionDate", "firstSessionSentenceCardIds", "firstSessionVerbCardIds"))

    private fun badSentences(root: Map<*, *>): Boolean {
        if (!version(root, setOf(1, 2))) return false
        fun items(value: Any?): Boolean = value is List<*> && value.all {
            val item = it as? Map<*, *> ?: return@all false
            item["cardId"] is String && record(item, setOf("cardId"))
        }
        return if (root.containsKey("packs")) nested(root["packs"] as? Map<*, *>) { items(it["items"]) }
        else items(root["items"])
    }
}

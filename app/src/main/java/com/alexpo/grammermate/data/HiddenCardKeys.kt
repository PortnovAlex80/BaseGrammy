package com.alexpo.grammermate.data

/** A CSV row ID is unique only within its lesson; never treat old raw hidden IDs as wildcards. */
object HiddenCardKeys {
    fun packPrefix(packId: String): String = "scope:${packId.length}:$packId:"

    fun scoped(packId: String, lessonId: String, cardId: String): String =
        "${packPrefix(packId)}${lessonId.length}:$lessonId:$cardId"

    fun forCard(
        card: SessionCard,
        fallbackPackId: String? = null,
        fallbackLessonId: String? = null
    ): String? {
        val sentence = card as? SentenceCard
        val sourcePack = sentence?.sourcePackId
        if (sourcePack != null && fallbackPackId != null && sourcePack != fallbackPackId) return null
        val pack = sourcePack ?: fallbackPackId ?: return null
        val lesson = sentence?.sourceLessonId ?: fallbackLessonId ?: return null
        return scoped(pack, lesson, card.id)
    }

    fun contains(
        hidden: Set<String>,
        card: SessionCard,
        fallbackPackId: String? = null,
        fallbackLessonId: String? = null
    ): Boolean = forCard(card, fallbackPackId, fallbackLessonId)?.let { it in hidden } == true
}

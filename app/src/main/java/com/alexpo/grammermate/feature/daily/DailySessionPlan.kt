package com.alexpo.grammermate.feature.daily

import com.alexpo.grammermate.data.DailyBlock
import com.alexpo.grammermate.data.DailyCursorState
import com.alexpo.grammermate.data.DailyTask
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.PackDailyCursorState

/** The pack lesson and manifest level used to build one daily session. */
internal data class DailySessionIdentity(
    val packId: String,
    val languageId: String,
    val lessonId: String,
    val level: Int
)

/** Pure selection rules; browsing and mastery progress are only fallback inputs. */
internal object DailySessionIdentityResolver {
    fun forStart(
        packId: String,
        languageId: String,
        cursor: DailyCursorState,
        orderedLessonIds: List<String>,
        progressFallback: Pair<String, Int>?
    ): DailySessionIdentity? {
        val index = cursor.currentLessonIndex
        val selected = if (index in orderedLessonIds.indices) {
            orderedLessonIds[index] to (index + 1)
        } else progressFallback
        return selected?.let { DailySessionIdentity(packId, languageId, it.first, it.second) }
    }

    /** Recover old cursors whose lesson metadata came from the browsed lesson. */
    fun forStoredRepeat(
        packId: String,
        languageId: String,
        cursor: PackDailyCursorState,
        today: String,
        orderedLessonIds: List<String>,
        lessons: List<Lesson>
    ): DailySessionIdentity? {
        if (cursor.firstSessionDate != today) return null
        val sentenceIds = cursor.firstSessionSentenceCardIds
        if (sentenceIds.isEmpty() && cursor.firstSessionVerbCardIds.isEmpty()) return null
        val containingLesson = if (sentenceIds.isNotEmpty()) {
            lessons.filter { lesson ->
                val cardIds = lesson.cards.mapTo(HashSet<String>()) { it.id }
                sentenceIds.all { it in cardIds }
            }.singleOrNull()?.id?.value ?: return null
        } else null
        val lessonId = containingLesson ?: cursor.firstSessionLessonId
        val index = orderedLessonIds.indexOf(lessonId)
        if (index < 0) return null
        return DailySessionIdentity(packId, languageId, lessonId, index + 1)
    }
}

internal data class DailyPrebuildKey(
    val session: DailySessionIdentity,
    val date: String,
    val sentenceOffset: Int,
    val verbOffset: Int,
    val sessionSize: Int
)

internal data class DailyRepeatKey(
    val session: DailySessionIdentity,
    val date: String,
    val sentenceIds: List<String>,
    val verbIds: List<String>
)

internal fun List<DailyBlock>.firstSessionCardIds(): Pair<List<String>, List<String>> {
    val tasks = flatMap { it.tasks }
    return tasks.filterIsInstance<DailyTask.TranslateSentence>().map { it.card.id } to
        tasks.filterIsInstance<DailyTask.ConjugateVerb>().map { it.card.id }
}

/** Owns atomic, identity-checked snapshots built on the UI and IO threads. */
internal class DailySessionPlanCache {
    private data class Entry<K>(val key: K, val blocks: List<DailyBlock>)
    private var prebuilt: Entry<DailyPrebuildKey>? = null
    private var firstSession: Entry<DailyRepeatKey>? = null

    @Synchronized fun putPrebuilt(key: DailyPrebuildKey, blocks: List<DailyBlock>) {
        prebuilt = Entry(key, blocks)
    }

    @Synchronized fun takePrebuilt(key: DailyPrebuildKey): List<DailyBlock>? {
        val entry = prebuilt
        prebuilt = null
        return entry?.blocks?.takeIf { entry.key == key }
    }

    @Synchronized fun rememberFirst(key: DailyRepeatKey, blocks: List<DailyBlock>) {
        firstSession = Entry(key, blocks)
    }

    @Synchronized fun repeat(key: DailyRepeatKey): List<DailyBlock>? =
        firstSession?.takeIf { it.key == key }?.blocks

    @Synchronized fun clearPrebuilt() {
        prebuilt = null
    }

    @Synchronized fun clear() {
        prebuilt = null
        firstSession = null
    }
}

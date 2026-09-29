package com.alexpo.grammermate.feature.daily

import com.alexpo.grammermate.data.DailyBlock
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.DailyCursorState
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.PackDailyCursorState
import com.alexpo.grammermate.data.SentenceCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailySessionPlanTest {
    private val ordered = listOf("l1", "l2", "l3")
    private val lessons = ordered.map { id ->
        Lesson(LessonId(id), LanguageId("it"), id, listOf(SentenceCard("$id-card", id, listOf("answer"))))
    }

    @Test
    fun startUsesCursorLessonEvenWhenProgressOrBrowsingPointsElsewhere() {
        val identity = DailySessionIdentityResolver.forStart(
            "pack", "it", DailyCursorState(currentLessonIndex = 1), ordered, "l3" to 3
        )
        assertEquals(DailySessionIdentity("pack", "it", "l2", 2), identity)
    }

    @Test
    fun storedRepeatUsesCardLessonAfterCursorAdvancesAndOldMetadataIsWrong() {
        val cursor = PackDailyCursorState(
            packId = "pack", currentLessonIndex = 2,
            firstSessionDate = "2026-09-29",
            firstSessionLessonId = "l1", // old ViewModel stored the browsed lesson
            firstSessionSentenceCardIds = listOf("l2-card")
        )
        val identity = DailySessionIdentityResolver.forStoredRepeat(
            "pack", "it", cursor, "2026-09-29", ordered, lessons
        )
        assertEquals(DailySessionIdentity("pack", "it", "l2", 2), identity)
        assertNull(DailySessionIdentityResolver.forStoredRepeat(
            "pack", "it", cursor, "2026-09-30", ordered, lessons
        ))
        assertNull(DailySessionIdentityResolver.forStoredRepeat(
            "pack", "it", cursor.copy(firstSessionLessonId = "missing", firstSessionSentenceCardIds = listOf("missing")),
            "2026-09-29", ordered, lessons
        ))
        assertNull(DailySessionIdentityResolver.forStoredRepeat(
            "pack", "it", cursor.copy(firstSessionSentenceCardIds = listOf("missing")),
            "2026-09-29", ordered, lessons
        ))
    }

    @Test
    fun prebuiltAndRepeatCachesRequireMatchingIdentityAndInputs() {
        val cache = DailySessionPlanCache()
        val identity = DailySessionIdentity("pack", "it", "l2", 2)
        val blocks = listOf(DailyBlock(DailyBlockType.TRANSLATE, emptyList()))
        val prebuild = DailyPrebuildKey(identity, "2026-09-29", 5, 7, 10)
        cache.putPrebuilt(prebuild, blocks)
        assertNull(cache.takePrebuilt(prebuild.copy(sentenceOffset = 6)))
        cache.putPrebuilt(prebuild, blocks)
        assertNull(cache.takePrebuilt(prebuild.copy(session = identity.copy(lessonId = "l1"))))
        cache.putPrebuilt(prebuild, blocks)
        assertEquals(blocks, cache.takePrebuilt(prebuild))
        assertNull(cache.takePrebuilt(prebuild))

        val repeat = DailyRepeatKey(identity, "2026-09-29", listOf("l2-card"), listOf("verb"))
        cache.rememberFirst(repeat, blocks)
        assertEquals(blocks, cache.repeat(repeat))
        assertNull(cache.repeat(repeat.copy(date = "2026-09-30")))
        assertNull(cache.repeat(repeat.copy(sentenceIds = listOf("other"))))
        cache.clear()
        assertNull(cache.repeat(repeat))
    }
}

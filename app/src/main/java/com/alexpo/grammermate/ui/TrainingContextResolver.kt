package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.NavigationState
import com.alexpo.grammermate.data.PackId

/** One target language/pack/lesson snapshot; callers own transition effects. */
internal data class TrainingContext(
    val languageId: LanguageId?,
    val packId: PackId?,
    val lessonIds: List<String>?,
    val lessons: List<Lesson>,
    val selectedLessonId: LessonId?
) {
    fun navigationFrom(current: NavigationState): NavigationState = current.copy(
        selectedLanguageId = languageId,
        activePackId = packId,
        activePackLessonIds = lessonIds,
        lessons = lessons,
        selectedLessonId = selectedLessonId,
        selectedChapter = current.selectedChapter.takeIf {
            packId != null && current.activePackId == packId
        }
    )

    fun scheduleLessons(): List<Lesson> =
        if (lessonIds == null) lessons else lessons.filter { it.id.value in lessonIds }
}

/** Resolves content before the ViewModel changes feature state or rebuilds schedules. */
internal class TrainingContextResolver(private val lessonStore: LessonStore) {
    fun forLanguage(languageId: LanguageId): TrainingContext =
        TrainingContext(languageId, null, emptyList(), emptyList(), null)

    fun withoutPack(current: NavigationState): TrainingContext =
        TrainingContext(current.selectedLanguageId, null, emptyList(), emptyList(), null)

    fun forPack(packId: String, preferredLessonId: LessonId? = null): TrainingContext? {
        val pack = lessonStore.getInstalledPacks().firstOrNull { it.packId.value == packId } ?: return null
        val lessons = lessonStore.getLessons(packId, pack.languageId.value)
        val lessonIds = lessonStore.getLessonIdsForPack(packId)
        val lessonById = lessons.associateBy { it.id.value }
        val selected = preferredLessonId?.takeIf { it.value in lessonIds && it.value in lessonById }
            ?: lessonIds.firstNotNullOfOrNull { lessonById[it]?.id }
        return TrainingContext(pack.languageId, pack.packId, lessonIds, lessons, selected)
    }

    fun forLesson(lessonId: String, explicitPackId: String?, current: NavigationState): TrainingContext? {
        val packId = explicitPackId
            ?: current.activePackId?.value?.takeIf { lessonId in current.activePackLessonIds.orEmpty() }
            ?: lessonStore.getPackIdForLesson(lessonId)
        if (packId == null) {
            val lesson = current.lessons.firstOrNull { it.id.value == lessonId } ?: return null
            return TrainingContext(current.selectedLanguageId, null, null, current.lessons, lesson.id)
        }
        val target = forPack(packId, LessonId(lessonId)) ?: return null
        return target.takeIf { it.selectedLessonId?.value == lessonId }
    }
}

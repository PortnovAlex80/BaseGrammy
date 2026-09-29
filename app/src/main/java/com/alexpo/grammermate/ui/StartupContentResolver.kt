package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.Language
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonPack
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.PackId

/** One validated language/pack/lesson selection for startup and progress restoration. */
internal data class StartupContent(
    val languageId: LanguageId?,
    val packId: PackId?,
    val lessonIds: List<String>?,
    val lessons: List<Lesson>,
    val selectedLessonId: LessonId?
)

internal class StartupContentResolver(private val lessonStore: LessonStore) {
    fun resolve(
        preferredLanguageId: LanguageId?,
        preferredPackId: PackId?,
        preferredLessonId: String?,
        languages: List<Language>,
        packs: List<LessonPack>,
        fallbackLanguageId: LanguageId? = null
    ): StartupContent {
        val languageId = preferredLanguageId?.takeIf { preferred ->
            languages.any { it.id == preferred }
        } ?: fallbackLanguageId?.takeIf { fallback ->
            languages.isEmpty() || languages.any { it.id == fallback }
        }
        val packId = packs.firstOrNull { pack ->
            pack.packId == preferredPackId && pack.languageId == languageId
        }?.packId
        val lessons = if (languageId != null && packId != null) {
            lessonStore.getLessons(packId.value, languageId.value)
        } else {
            emptyList()
        }
        val lessonIds = packId?.let { lessonStore.getLessonIdsForPack(it.value) }
        val selectedLessonId = lessons.firstOrNull { it.id.value == preferredLessonId }?.id
            ?: lessons.firstOrNull()?.id
        return StartupContent(languageId, packId, lessonIds, lessons, selectedLessonId)
    }
}

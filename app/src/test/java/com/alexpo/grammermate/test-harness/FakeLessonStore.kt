package com.alexpo.grammermate.testharness

import android.content.ContentResolver
import android.net.Uri
import com.alexpo.grammermate.data.Language
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonPack
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.PackId

/**
 * In-memory fake implementation of LessonStore for testing.
 * No file I/O, no YAML parsing, no Android dependencies.
 */
class FakeLessonStore(
    private var languages: List<Language> = emptyList(),
    private var lessons: List<Lesson> = emptyList(),
    private var packs: List<LessonPack> = emptyList()
) : LessonStore {

    override fun ensureSeedData() {
        // No-op for tests
    }

    override fun seedDefaultPacksIfNeeded(): Boolean {
        return false
    }

    override fun updateDefaultPacksIfNeeded(): Boolean {
        return false
    }

    override fun forceReloadDefaultPacks(): Boolean {
        return false
    }

    override fun getLanguages(): List<Language> = languages

    override fun addLanguage(name: String): Language {
        val newLang = Language(id = LanguageId(name.lowercase()), displayName = name)
        languages = languages + newLang
        return newLang
    }

    override fun getInstalledPacks(): List<LessonPack> = packs

    override fun getPackIdForLesson(lessonId: String): String? {
        return packs.firstOrNull { pack ->
            lessons.any { it.id.value == lessonId }
        }?.packId?.value
    }

    override fun getLessonIdsForPack(packId: String): List<String> {
        return lessons.map { it.id.value }
    }

    override fun getCumulativeTenses(packId: String, lessonLevel: Int): List<String> {
        return emptyList()
    }

    override fun importPackFromUri(uri: Uri, resolver: ContentResolver): LessonPack {
        throw UnsupportedOperationException("Not implemented in fake")
    }

    override fun importPackFromAssets(assetPath: String): LessonPack {
        throw UnsupportedOperationException("Not implemented in fake")
    }

    override fun removeInstalledPackData(packId: String): Boolean {
        packs = packs.filter { it.packId.value != packId }
        return true
    }

    override fun importFromUri(languageId: String, uri: Uri, resolver: ContentResolver): Lesson {
        throw UnsupportedOperationException("Not implemented in fake")
    }

    override fun getLessons(languageId: String): List<Lesson> = lessons

    override fun deleteAllLessons(languageId: String) {
        lessons = emptyList()
    }

    override fun deleteLesson(languageId: String, lessonId: String) {
        lessons = lessons.filter { it.id.value != lessonId }
    }

    override fun createEmptyLesson(languageId: String, title: String): Lesson {
        val newLesson = Lesson(
            id = com.alexpo.grammermate.data.LessonId("lesson_${System.currentTimeMillis()}"),
            languageId = LanguageId(languageId),
            title = title,
            cards = emptyList()
        )
        lessons = lessons + newLesson
        return newLesson
    }

    // Additional methods not used in this test but required by interface
    override fun getStoryQuizzes(lessonId: String, phase: com.alexpo.grammermate.data.StoryPhase, languageId: String): List<com.alexpo.grammermate.data.StoryQuiz> = emptyList()

    override fun getVocabEntries(lessonId: String, languageId: String): List<com.alexpo.grammermate.data.VocabEntry> = emptyList()

    // Drill file queries - not implemented for fake
    override fun getVerbDrillFiles(packId: String, languageId: String): List<java.io.File> = emptyList()

    override fun getVerbDrillFilesForPack(packId: String): List<java.io.File> = emptyList()

    override fun getVocabDrillFiles(packId: String, languageId: String): List<java.io.File> = emptyList()

    override fun getVocabDrillFilesForPack(packId: String): List<java.io.File> = emptyList()

    override fun getVocabWordsByRankRange(packId: String, languageId: String, fromRank: Int, toRank: Int): List<com.alexpo.grammermate.data.VocabWord> = emptyList()

    override fun hasVerbDrill(packId: String, languageId: String): Boolean = false

    override fun hasVocabDrill(packId: String, languageId: String): Boolean = false

    override fun getVerbDrillFiles(languageId: String): List<java.io.File> = emptyList()

    override fun hasVerbDrillLessons(languageId: String): Boolean = false

    // Test helpers
    fun setLessons(newLessons: List<Lesson>) {
        lessons = newLessons
    }

    fun addLesson(lesson: Lesson) {
        lessons = lessons + lesson
    }
}

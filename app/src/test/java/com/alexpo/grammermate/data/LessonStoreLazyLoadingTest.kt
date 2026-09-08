package com.alexpo.grammermate.data

import org.junit.Assert.*
import org.junit.Test

class LessonStoreLazyLoadingTest {

    // ========================================
    // LessonMetadata data class contract tests
    // These PASS immediately (pure data class, no Android needed)
    // ========================================

    @Test
    fun lessonMetadata_holdsIdLanguageIdTitle() {
        val meta = LessonMetadata(
            id = LessonId("lesson_01_A01"),
            languageId = LanguageId("it"),
            title = "A01 - Presente Indicativo"
        )
        assertEquals("lesson_01_A01", meta.id.value)
        assertEquals("it", meta.languageId.value)
        assertEquals("A01 - Presente Indicativo", meta.title)
    }

    @Test
    fun lessonMetadata_dataClassEquality() {
        val m1 = LessonMetadata(LessonId("l1"), LanguageId("it"), "Title")
        val m2 = LessonMetadata(LessonId("l1"), LanguageId("it"), "Title")
        assertEquals(m1, m2)
    }

    @Test
    fun lessonMetadata_differentId_notEqual() {
        val m1 = LessonMetadata(LessonId("l1"), LanguageId("it"), "Title")
        val m2 = LessonMetadata(LessonId("l2"), LanguageId("it"), "Title")
        assertNotEquals(m1, m2)
    }

    @Test
    fun lessonMetadata_differentTitle_notEqual() {
        val m1 = LessonMetadata(LessonId("l1"), LanguageId("it"), "Title A")
        val m2 = LessonMetadata(LessonId("l1"), LanguageId("it"), "Title B")
        assertNotEquals(m1, m2)
    }

    @Test
    fun lessonMetadata_differentLanguageId_notEqual() {
        val m1 = LessonMetadata(LessonId("l1"), LanguageId("it"), "Title")
        val m2 = LessonMetadata(LessonId("l1"), LanguageId("en"), "Title")
        assertNotEquals(m1, m2)
    }

    @Test
    fun lessonMetadata_copyWorks() {
        val original = LessonMetadata(LessonId("l1"), LanguageId("it"), "Original")
        val modified = original.copy(title = "Modified")
        assertEquals("Original", original.title)
        assertEquals("Modified", modified.title)
        assertEquals(original.id, modified.id)
        assertEquals(original.languageId, modified.languageId)
    }

    @Test
    fun lessonMetadata_destructuring() {
        val meta = LessonMetadata(LessonId("l1"), LanguageId("it"), "Title")
        val (id, languageId, title) = meta
        assertEquals("l1", id.value)
        assertEquals("it", languageId.value)
        assertEquals("Title", title)
    }

    // ========================================
    // LessonStore lazy API stub contract tests
    // These test default implementations on the interface.
    // Stubs return empty/null/0 — positive tests will be added
    // after implementation with proper Android test setup.
    // ========================================

    @Test
    fun stub_getLesson_unknown_returnsNull() {
        val store = object : LessonStore by stubLessonStore() {}
        val result = store.getLesson("UNKNOWN_PACK", "xx", "lesson_XX_nonexistent")
        assertNull("Stub getLesson should return null", result)
    }

    @Test
    fun stub_getLessonMetadata_returnsEmptyList() {
        val store = object : LessonStore by stubLessonStore() {}
        val result = store.getLessonMetadata("UNKNOWN_PACK", "xx")
        assertTrue("Stub getLessonMetadata should return empty list", result.isEmpty())
    }

    @Test
    fun stub_getLessonCount_returnsZero() {
        val store = object : LessonStore by stubLessonStore() {}
        val result = store.getLessonCount("UNKNOWN_PACK", "xx")
        assertEquals("Stub getLessonCount should return 0", 0, result)
    }

    @Test
    fun stub_getLessonIdAtIndex_returnsNull() {
        val store = object : LessonStore by stubLessonStore() {}
        val result = store.getLessonIdAtIndex("UNKNOWN_PACK", "xx", 0)
        assertNull("Stub getLessonIdAtIndex should return null", result)
    }

    @Test
    fun stub_getCardsForLesson_returnsEmptyList() {
        val store = object : LessonStore by stubLessonStore() {}
        val result = store.getCardsForLesson("UNKNOWN_PACK", "xx", "lesson_XX_nonexistent")
        assertTrue("Stub getCardsForLesson should return empty list", result.isEmpty())
    }

    /**
     * Provides a minimal stub implementation of LessonStore interface
     * so we can test the default method stubs without Android context.
     * Only the methods needed for lazy loading tests are implemented here.
     */
    private fun stubLessonStore(): LessonStore = object : LessonStore {
        override fun ensureSeedData() {}
        override fun seedDefaultPacksIfNeeded(): Boolean = false
        override fun updateDefaultPacksIfNeeded(): Boolean = false
        override fun forceReloadDefaultPacks(): Boolean = false
        override fun getLanguages(): List<Language> = emptyList()
        override fun getLanguagesWithPacks(): List<Language> = emptyList()
        override fun addLanguage(name: String): Language = throw UnsupportedOperationException()
        override fun getInstalledPacks(): List<LessonPack> = emptyList()
        override fun getPackIdForLesson(lessonId: String): String? = null
        override fun getLessonIdsForPack(packId: String): List<String> = emptyList()
        override fun getCumulativeTenses(packId: String, lessonLevel: Int): List<String> = emptyList()
        override fun importPackFromUri(uri: android.net.Uri, resolver: android.content.ContentResolver): LessonPack =
            throw UnsupportedOperationException()
        override fun importPackFromAssets(assetPath: String): LessonPack =
            throw UnsupportedOperationException()
        override fun removeInstalledPackData(packId: String): Boolean = false
        override fun importFromUri(languageId: String, uri: android.net.Uri, resolver: android.content.ContentResolver): Lesson =
            throw UnsupportedOperationException()
        override fun importFromUriWithErrors(languageId: String, uri: android.net.Uri, resolver: android.content.ContentResolver): Pair<Lesson, List<ParseError>> =
            throw UnsupportedOperationException()
        override fun getLessons(languageId: String): List<Lesson> = emptyList()
        override fun getLessons(packId: String, languageId: String): List<Lesson> = emptyList()
        override fun deleteAllLessons(languageId: String) {}
        override fun deleteLesson(languageId: String, lessonId: String) {}
        override fun createEmptyLesson(languageId: String, title: String): Lesson =
            throw UnsupportedOperationException()
        override fun getStoryQuizzes(lessonId: String, phase: StoryPhase, languageId: String): List<StoryQuiz> = emptyList()
        override fun getVocabEntries(lessonId: String, languageId: String): List<VocabEntry> = emptyList()
        override fun getVerbDrillFiles(packId: String, languageId: String): List<java.io.File> = emptyList()
        override fun getVerbDrillFilesForPack(packId: String): List<java.io.File> = emptyList()
        override fun getVocabDrillFiles(packId: String, languageId: String): List<java.io.File> = emptyList()
        override fun getVocabDrillFilesForPack(packId: String): List<java.io.File> = emptyList()
        override fun getVocabWordsByRankRange(packId: String, languageId: String, fromRank: Int, toRank: Int): List<VocabWord> = emptyList()
        override fun hasVerbDrill(packId: String, languageId: String): Boolean = false
        override fun hasVocabDrill(packId: String, languageId: String): Boolean = false
        override fun getChapters(packId: String): List<Chapter> = emptyList()
        override fun getChapterStory(packId: String, storyFile: String): String? = null
        override fun hasChapters(packId: String): Boolean = false
        override fun loadExternalLessons(languageId: String, externalDirPath: String): Int = 0
        override fun detectStoryLanguage(packId: String, chapterId: String, uiLanguage: String): String? = null
        override fun getVerbDrillFiles(languageId: String): List<java.io.File> = emptyList()
        override fun hasVerbDrillLessons(languageId: String): Boolean = false
    }
}

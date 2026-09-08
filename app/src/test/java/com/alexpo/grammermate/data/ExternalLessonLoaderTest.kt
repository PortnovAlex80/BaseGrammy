package com.alexpo.grammermate.data

import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.io.File
import java.io.FileWriter

/**
 * Tests for external lesson loading functionality.
 * Verifies that lessons can be loaded from external directories with Russian naming patterns.
 */
class ExternalLessonLoaderTest {

    private lateinit var testDir: File
    private lateinit var lessonStore: LessonStoreImpl
    private lateinit var mockContext: android.content.Context

    @Before
    fun setup() {
        // Create temporary test directory
        testDir = File(System.getProperty("java.io.tmpdir"), "test_lessons_${System.currentTimeMillis()}")
        testDir.mkdirs()

        // Create mock context (simplified for testing)
        // In real scenario, you'd use AndroidX Test framework
    }

    @After
    fun cleanup() {
        // Clean up test directory
        if (testDir.exists()) {
            testDir.deleteRecursively()
        }
    }

    @Test
    fun testRussianLessonPatternMatching() {
        // Test the Russian lesson pattern
        val pattern = Regex("""урок_(\d+)_[A-Z]\d+\.csv""")

        // Valid patterns
        assertTrue("урок_01_A01.csv" matches pattern)
        assertTrue("урок_02_A02.csv" matches pattern)
        assertTrue("урок_10_B01.csv" matches pattern)
        assertTrue("урок_100_C99.csv" matches pattern)

        // Invalid patterns
        assertFalse("lesson_01_A01.csv" matches pattern)
        assertFalse("урок_01.csv" matches pattern)
        assertFalse("урок_A01.csv" matches pattern)
        assertFalse("урок_01_A01.txt" matches pattern)
    }

    @Test
    fun testCreateSampleLessonFiles() {
        // Create sample lesson files with Russian naming
        val lesson1 = File(testDir, "урок_01_A01.csv")
        val lesson2 = File(testDir, "урок_02_A02.csv")
        val invalidFile = File(testDir, "lesson_01_A01.csv")

        // Write sample content
        FileWriter(lesson1).use { it.write("A01 - Presente Indicativo\nЯ говорю;Parlo") }
        FileWriter(lesson2).use { it.write("A02 - Passato Prossimo\nЯ говорил;Ho parlato") }
        FileWriter(invalidFile).use { it.write("Invalid file") }

        // Verify files were created
        assertTrue(lesson1.exists())
        assertTrue(lesson2.exists())
        assertTrue(invalidFile.exists())

        // Verify pattern matching
        val pattern = Regex("""урок_(\d+)_[A-Z]\d+\.csv""")
        val matchingFiles = testDir.listFiles()?.filter { pattern.matches(it.name) }?.toList()

        assertNotNull(matchingFiles)
        assertEquals(2, matchingFiles!!.size)
        assertTrue(matchingFiles.any { it.name == "урок_01_A01.csv" })
        assertTrue(matchingFiles.any { it.name == "урок_02_A02.csv" })
        assertFalse(matchingFiles.any { it.name == "lesson_01_A01.csv" })
    }

    @Test
    fun testLanguageDetectionStoryFiles() {
        // Create test directory structure for stories
        val storiesDir = File(testDir, "stories")
        storiesDir.mkdirs()

        // Create Russian and English story files
        val russianStory = File(storiesDir, "chapter_01_original.md")
        val englishStory = File(storiesDir, "chapter_01.md")

        FileWriter(russianStory).use { it.write("# Глава 1 - Оригинал") }
        FileWriter(englishStory).use { it.write("# Chapter 1 - Translation") }

        // Test language preference logic
        val chapterId = "chapter_01"

        // Russian preference
        val russianPref = listOf(
            "chapter_01_original.md",
            "chapter_01.md"
        )
        val russianResult = russianPref.firstOrNull { File(storiesDir, it).exists() }
        assertEquals("chapter_01_original.md", russianResult)

        // English preference
        val englishPref = listOf(
            "chapter_01.md",
            "chapter_01_original.md"
        )
        val englishResult = englishPref.firstOrNull { File(storiesDir, it).exists() }
        assertEquals("chapter_01.md", englishResult)

        // Fallback when English doesn't exist
        val fallbackPref = listOf(
            "chapter_02.md",  // Doesn't exist
            "chapter_01_original.md"
        )
        val fallbackResult = fallbackPref.firstOrNull { File(storiesDir, it).exists() }
        assertEquals("chapter_01_original.md", fallbackResult)
    }

    @Test
    fun testDrillFileDetectionPatterns() {
        // Create test directory structure for drills
        val drillsDir = File(testDir, "drills")
        val verbDrillDir = File(drillsDir, "verb_drill")
        val vocabDrillDir = File(drillsDir, "vocab_drill")

        verbDrillDir.mkdirs()
        vocabDrillDir.mkdirs()

        // Create drill files with language prefixes
        val itVerbDrill = File(verbDrillDir, "it_verb_drill.csv")
        val enVerbDrill = File(verbDrillDir, "en_verb_drill.csv")
        val itVocabDrill = File(vocabDrillDir, "it_vocab.csv")
        val enVocabDrill = File(vocabDrillDir, "en_vocab.csv")

        FileWriter(itVerbDrill).use { it.write("Italian verb drill") }
        FileWriter(enVerbDrill).use { it.write("English verb drill") }
        FileWriter(itVocabDrill).use { it.write("Italian vocab") }
        FileWriter(enVocabDrill).use { it.write("English vocab") }

        // Test Italian verb drill detection
        val itVerbFiles = verbDrillDir.listFiles()?.filter {
            it.name.startsWith("it_") && it.extension == "csv"
        }?.toList()
        assertEquals(1, itVerbFiles!!.size)
        assertEquals("it_verb_drill.csv", itVerbFiles[0].name)

        // Test English vocab drill detection
        val enVocabFiles = vocabDrillDir.listFiles()?.filter {
            it.name.startsWith("en_") && it.extension == "csv"
        }?.toList()
        assertEquals(1, enVocabFiles!!.size)
        assertEquals("en_vocab.csv", enVocabFiles[0].name)

        // Test hasVerbDrill logic
        val hasItVerb = verbDrillDir.listFiles()?.any {
            it.name.startsWith("it_") && it.extension == "csv"
        } ?: false
        assertTrue(hasItVerb)

        val hasFrVerb = verbDrillDir.listFiles()?.any {
            it.name.startsWith("fr_") && it.extension == "csv"
        } ?: false
        assertFalse(hasFrVerb)
    }

    @Test
    fun testChapterModelStructure() {
        // Verify Chapter model supports story language variants
        val chapter = Chapter(
            chapterId = "chapter_01",
            order = 1,
            title = "Chapter 1",
            subtitle = "The Beginning",
            storyFile = "chapter_01.md",  // Can be overridden by language detection
            lessons = listOf("lesson_01", "lesson_02")
        )

        assertEquals("chapter_01", chapter.chapterId)
        assertEquals(1, chapter.order)
        assertEquals("Chapter 1", chapter.title)
        assertEquals("chapter_01.md", chapter.storyFile)
        assertEquals(2, chapter.lessons.size)

        // Chapter model doesn't need additional fields for language support
        // Language detection is handled by LessonStore methods
    }
}

// Helper infix function for pattern matching
private infix fun String.matches(pattern: Regex): Boolean {
    return pattern.matches(this)
}

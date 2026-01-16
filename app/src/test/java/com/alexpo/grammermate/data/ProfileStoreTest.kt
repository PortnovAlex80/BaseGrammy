package com.alexpo.grammermate.data

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Unit tests for ProfileStore - защита сохранения профиля пользователя.
 *
 * Покрывает требования:
 * - FR-1.1.1: Сохранение имени пользователя
 * - FR-1.1.2: Загрузка имени пользователя
 * - FR-1.1.3: Формат YAML
 * - FR-1.1.4: Имя по умолчанию
 */
@RunWith(RobolectricTestRunner::class)
class ProfileStoreTest {

    private lateinit var context: Context
    private lateinit var store: ProfileStore
    private lateinit var testDir: File

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        store = ProfileStore(context)
        testDir = File(context.filesDir, "grammarmate")
    }

    @After
    fun cleanup() {
        // Очистка тестовых файлов
        store.clear()
        testDir.deleteRecursively()
    }

    // ========================================
    // 6.1 Сохранение и загрузка
    // ========================================

    @Test
    fun saveProfile_userName_persists() {
        // FR-1.1.1: Сохранение имени пользователя
        val profile = UserProfile(userName = "TestUser")
        store.save(profile)

        val loaded = store.load()
        assertEquals("TestUser", loaded.userName)
    }

    @Test
    fun loadProfile_existingFile_returnsCorrectName() {
        // FR-1.1.2: Загрузка существующего профиля
        val profile = UserProfile(userName = "Alice")
        store.save(profile)

        val loaded = store.load()
        assertEquals("Alice", loaded.userName)
    }

    @Test
    fun loadProfile_missingFile_returnsDefaultName() {
        // FR-1.1.4: Если файла нет → возвращается дефолтное имя
        store.clear() // Убедимся, что файла нет

        val loaded = store.load()
        assertEquals("GrammarMateUser", loaded.userName)
    }

    @Test
    fun saveProfile_overwritesPrevious() {
        // Перезапись существующего профиля
        store.save(UserProfile(userName = "FirstName"))
        store.save(UserProfile(userName = "SecondName"))

        val loaded = store.load()
        assertEquals("SecondName", loaded.userName)
    }

    // ========================================
    // 6.2 YAML формат
    // ========================================

    @Test
    fun saveProfile_createsYamlFile() {
        // FR-1.1.3: Файл создаётся в формате YAML
        val profile = UserProfile(userName = "TestUser")
        store.save(profile)

        val file = File(testDir, "profile.yaml")
        assertTrue("File should exist", file.exists())

        val content = file.readText()
        assertTrue("File should contain userName", content.contains("userName"))
        assertTrue("File should contain TestUser", content.contains("TestUser"))
    }

    @Test
    fun loadProfile_readsYamlFile() {
        // FR-1.1.3: Загрузка из YAML
        val profile = UserProfile(userName = "YamlUser")
        store.save(profile)

        // Проверяем, что загрузка работает
        val loaded = store.load()
        assertEquals("YamlUser", loaded.userName)
    }

    @Test
    fun saveProfile_createsDirectory() {
        // Проверка, что директория создаётся автоматически
        testDir.deleteRecursively()
        assertFalse("Directory should not exist", testDir.exists())

        store.save(UserProfile(userName = "Test"))

        assertTrue("Directory should be created", testDir.exists())
    }

    // ========================================
    // 6.3 Граничные случаи
    // ========================================

    @Test
    fun saveProfile_emptyName_handled() {
        // Граничный случай: пустое имя
        val profile = UserProfile(userName = "")
        store.save(profile)

        val loaded = store.load()
        assertEquals("", loaded.userName)
    }

    @Test
    fun saveProfile_specialCharacters_handled() {
        // Специальные символы в имени
        val profile = UserProfile(userName = "User@123!#")
        store.save(profile)

        val loaded = store.load()
        assertEquals("User@123!#", loaded.userName)
    }

    @Test
    fun saveProfile_unicodeCharacters_handled() {
        // Юникодные символы (кириллица, эмодзи)
        val profile = UserProfile(userName = "Иван")
        store.save(profile)

        val loaded = store.load()
        assertEquals("Иван", loaded.userName)
    }

    @Test
    fun saveProfile_longName_handled() {
        // Очень длинное имя
        val longName = "A".repeat(1000)
        val profile = UserProfile(userName = longName)
        store.save(profile)

        val loaded = store.load()
        assertEquals(longName, loaded.userName)
    }

    @Test
    fun loadProfile_corruptedFile_returnsDefault() {
        // FR-1.1.2: При ошибке чтения возвращается дефолтный профиль
        val file = File(testDir, "profile.yaml")
        testDir.mkdirs()
        file.writeText("invalid yaml content: [[[")

        val loaded = store.load()
        assertEquals("GrammarMateUser", loaded.userName)
    }

    @Test
    fun clear_removesFile() {
        // Проверка метода clear
        store.save(UserProfile(userName = "ToDelete"))
        val file = File(testDir, "profile.yaml")
        assertTrue("File should exist before clear", file.exists())

        store.clear()
        assertFalse("File should be deleted after clear", file.exists())
    }

    @Test
    fun clear_whenFileDoesNotExist_doesNotCrash() {
        // Вызов clear когда файла нет не должен падать
        store.clear()
        store.clear() // Двойной вызов
        // Тест проходит, если не было exception
    }

    @Test
    fun saveProfile_multipleSaves_lastWins() {
        // Множественные сохранения
        for (i in 1..10) {
            store.save(UserProfile(userName = "User$i"))
        }

        val loaded = store.load()
        assertEquals("User10", loaded.userName)
    }

    @Test
    fun loadProfile_emptyFile_returnsDefault() {
        // Пустой файл
        val file = File(testDir, "profile.yaml")
        testDir.mkdirs()
        file.writeText("")

        val loaded = store.load()
        assertEquals("GrammarMateUser", loaded.userName)
    }

    @Test
    fun saveProfile_withWhitespace_preserved() {
        // Имя с пробелами
        val profile = UserProfile(userName = "  John Doe  ")
        store.save(profile)

        val loaded = store.load()
        assertEquals("  John Doe  ", loaded.userName)
    }
}

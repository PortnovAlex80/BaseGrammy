package com.alexpo.grammermate.v2.core.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Миграция schema 1 → 2 (Фаза 2 плана стабилизации 2026-08-26):
 * `sessions.revision INTEGER NOT NULL DEFAULT 0`.
 *
 * DoD плана §6: schema change сопровождается forward-migration, schema fixture
 * и restore-тестом. Существующая v1-строка переживает миграцию: ревизия
 * детерминированно 0 (стартовое значение optimistic-concurrency токена).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GrammarMateMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GrammarMateDatabase::class.java,
    )

    private val dbName = "migration-test.db"

    @Test
    fun `migrate 1 to 2 keeps sessions and defaults revision to zero`() {
        helper.createDatabase(dbName, 1).use { v1 ->
            // Минимальная v1-строка сессии (без revision — колонки ещё нет).
            v1.execSQL(
                """
                INSERT INTO sessions (
                    id, packId, lessonId, mode, subLessonIndex, cursorIndex,
                    currentCardId, selectedTense, selectedGroup, selectedPerson,
                    status, state, correctCount, incorrectCount, hintCount,
                    incorrectAttemptsForCard, completedSubLessonCount,
                    activeTimeMs, voiceActiveMs, voiceWordCount,
                    startedAtMs, updatedAtMs
                ) VALUES (
                    'lesson:pack:lesson', 'pack', 'lesson', 'LESSON', 0, 0,
                    'card_1', NULL, NULL, NULL,
                    'ACTIVE', 'ACTIVE', 0, 0, 0,
                    0, 0,
                    0, 0, 0,
                    1, 1
                )
                """.trimIndent(),
            )
        }

        val v2 = helper.runMigrationsAndValidate(dbName, 2, true, GrammarMateDatabase.MIGRATION_1_2)

        v2.query("SELECT id, revision, currentCardId, status FROM sessions").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("lesson:pack:lesson")
            assertThat(cursor.getLong(1)).isEqualTo(0L)
            assertThat(cursor.getString(2)).isEqualTo("card_1")
            assertThat(cursor.getString(3)).isEqualTo("ACTIVE")
        }
        v2.close()
    }
}

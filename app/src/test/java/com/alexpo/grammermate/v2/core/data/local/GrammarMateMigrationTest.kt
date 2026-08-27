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

    /**
     * ADR-003: v2 → v3 — `word_mastery` становится pack-scoped (составной PK
     * packId+wordId). Строка с контентом получает packId из `vocab_words`;
     * сирота без контента удаляется.
     */
    @Test
    fun `migrate 2 to 3 scopes word mastery to pack and drops orphans`() {
        val scopedDb = "migration-test-v3.db"
        helper.createDatabase(scopedDb, 2).use { v2 ->
            v2.execSQL(
                "INSERT INTO vocab_words (id, packId, word, pos, rank, meaningRu, collocationsJson, formsJson) " +
                    "VALUES ('noun_1_casa', 'ITALIAN_SHORT', 'casa', 'noun', 1, 'дом', '[]', '{}')",
            )
            // Слово с контентом (получит packId) + сирота (без контента — в дроп).
            v2.execSQL(
                "INSERT INTO word_mastery (wordId, intervalStepIndex, correctCount, incorrectCount, " +
                    "lastReviewDateMs, nextReviewDateMs, isLearned) " +
                    "VALUES ('noun_1_casa', 2, 3, 1, 100, 200, 0)",
            )
            v2.execSQL(
                "INSERT INTO word_mastery (wordId, intervalStepIndex, correctCount, incorrectCount, " +
                    "lastReviewDateMs, nextReviewDateMs, isLearned) " +
                    "VALUES ('ghost_9_word', 1, 1, 0, 100, 200, 0)",
            )
        }

        val v3 = helper.runMigrationsAndValidate(scopedDb, 3, true, GrammarMateDatabase.MIGRATION_2_3)

        v3.query("SELECT packId, wordId, intervalStepIndex, correctCount FROM word_mastery ORDER BY wordId").use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("ITALIAN_SHORT")
            assertThat(cursor.getString(1)).isEqualTo("noun_1_casa")
            assertThat(cursor.getInt(2)).isEqualTo(2)
            assertThat(cursor.getInt(3)).isEqualTo(3)
        }
        v3.close()
    }


    /** D4: v4 → v5 — составные PK chapters/lessons/cards, данные сохраняются. */
    @Test
    fun `migrate 4 to 5 rebuilds composite PKs and keeps data`() {
        val db5 = "migration-test-v5.db"
        helper.createDatabase(db5, 4).use { v4 ->
            v4.execSQL(
                "INSERT INTO packs (id, languageId, displayName, version, importedAtMs) VALUES ('P1', 'it', 'Pack', '1', 0)"
            )
            v4.execSQL(
                "INSERT INTO chapters (id, packId, \"order\", title, subtitle, storyFile) " +
                    "VALUES ('ch1', 'P1', 0, 'Глава', NULL, NULL)"
            )
            v4.execSQL(
                "INSERT INTO lessons (id, packId, chapterId, \"order\", title, cefrLevel, grammarChipKey) " +
                    "VALUES ('lesson_01', 'P1', 'ch1', 0, 'Урок', NULL, NULL)"
            )
            v4.execSQL(
                "INSERT INTO cards (id, packId, lessonId, ord, type, promptRu, acceptedAnswersJson, tense, verb, verbGroup, person, frequencyRank) " +
                    "VALUES ('lesson_01_0', 'P1', 'lesson_01', 0, 'SENTENCE', 'привет', '[\"ciao\"]', NULL, NULL, NULL, NULL, NULL)"
            )
        }

        val v5 = helper.runMigrationsAndValidate(db5, 5, true, GrammarMateDatabase.MIGRATION_4_5)

        v5.query("SELECT packId, id FROM chapters").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("P1")
            assertThat(c.getString(1)).isEqualTo("ch1")
        }
        v5.query("SELECT packId, id FROM lessons").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(1)).isEqualTo("lesson_01")
        }
        v5.query("SELECT packId, id FROM cards").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("P1")
            assertThat(c.getString(1)).isEqualTo("lesson_01_0")
        }
        v5.close()
    }

    /** Срез 4 Фазы 4: v3 → v4 — `daily_cursors.vocabOffset` (additive, default 0). */
    @Test
    fun `migrate 3 to 4 adds vocabOffset with zero default`() {
        val db4 = "migration-test-v4.db"
        helper.createDatabase(db4, 3).use { v3 ->
            v3.execSQL(
                "INSERT INTO daily_cursors (packId, sentenceOffset, currentLessonIndex, verbOffset, " +
                    "firstSessionDate, firstSessionSentenceCardIdsJson, firstSessionVerbCardIdsJson, " +
                    "firstSessionLessonId, updatedAtMs) " +
                    "VALUES ('P1', 5, 1, 3, NULL, '[]', '[]', NULL, 1)",
            )
        }

        val v4 = helper.runMigrationsAndValidate(db4, 4, true, GrammarMateDatabase.MIGRATION_3_4)

        v4.query("SELECT sentenceOffset, verbOffset, vocabOffset FROM daily_cursors").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(5)
            assertThat(cursor.getInt(1)).isEqualTo(3)
            assertThat(cursor.getInt(2)).isEqualTo(0)
        }
        v4.close()
    }
}

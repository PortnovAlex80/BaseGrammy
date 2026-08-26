package com.alexpo.grammermate.v2.core.data.local

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * EXPLAIN QUERY PLAN-регрессия hot-запросов (Фаза 6 плана стабилизации:
 * «Проверить EXPLAIN QUERY PLAN для pack/lesson/cards, due, resume и drill
 * filters»).
 *
 * Фиксирует ИНДЕКСНОЕ покрытие: запросы тренировочного пути не должны
 * деградировать до полного SCAN больших таблиц (cards, word_mastery,
 * mastery_states, session_cards, session_shown_cards, verb_drill_cards).
 *
 * Попутно опровергнут аудит M-6: у session_shown_cards составной PK
 * (sessionId, cardId) уже покрывает выборки по sessionId — SCAN нет.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueryPlanRegressionTest {

    private lateinit var db: GrammarMateDatabase

    @Before
    fun setUp() {
        db = TrainingDbFixture.inMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun plan(sql: String): String =
        db.openHelper.readableDatabase
            .query("EXPLAIN QUERY PLAN $sql")
            .use { cursor ->
                buildString {
                    while (cursor.moveToNext()) {
                        append(cursor.getString(cursor.columnCount - 1)).append("\n")
                    }
                }
            }

    @Test
    fun cards_byLesson_usesIndex_notScan() {
        val p = plan("SELECT * FROM cards WHERE lessonId = 'l' ORDER BY ord")
        assertThat(p).contains("SEARCH TABLE cards USING INDEX")
        assertThat(p).doesNotContain("SCAN cards")
    }

    @Test
    fun session_resume_path_allThreeQueriesUseIndexes() {
        val session = plan("SELECT * FROM sessions WHERE id = 's' AND status = 'ACTIVE'")
        assertThat(session).contains("SEARCH TABLE sessions USING")
        assertThat(session).doesNotContain("SCAN sessions")

        // PK (sessionId, ord) покрывает и выборку, и ORDER BY ord.
        val pool = plan("SELECT * FROM session_cards WHERE sessionId = 's' ORDER BY ord")
        assertThat(pool).contains("SEARCH TABLE session_cards USING")
        assertThat(pool).doesNotContain("SCAN session_cards")

        // Аудит M-6 опровергнут: составной PK (sessionId, cardId) индексирует
        // sessionId — показанные карточки достаются без SCAN.
        val shown = plan("SELECT * FROM session_shown_cards WHERE sessionId = 's'")
        assertThat(shown).contains("SEARCH TABLE session_shown_cards USING")
        assertThat(shown).doesNotContain("SCAN session_shown_cards")
    }

    @Test
    fun wordMastery_due_byPack_usesIndex_notScan() {
        val p = plan(
            "SELECT * FROM word_mastery WHERE packId = 'p' AND nextReviewDateMs <= 1 " +
                "ORDER BY nextReviewDateMs LIMIT 10"
        )
        assertThat(p).doesNotContain("SCAN word_mastery")
        assertThat(p).contains("SEARCH TABLE word_mastery USING INDEX")
    }

    @Test
    fun packProgress_joinUsesCoveringIndexes_notScan() {
        val p = plan(
            """
            SELECT l.packId AS packId, COUNT(l.id) AS totalLessons,
                   COUNT(m.completedAtMs) AS completedLessons
            FROM lessons l LEFT JOIN mastery_states m
                ON m.packId = l.packId AND m.lessonId = l.id
            GROUP BY l.packId
            """.trimIndent()
        )
        // mastery_states: SEARCH по unique (packId, lessonId); lessons идут
        // index-ordered SCAN'ом по index_lessons_packId для GROUP BY — это не
        // full-scan (SQLite маркирует SCAN ... USING INDEX).
        assertThat(p).doesNotContain("SCAN mastery_states")
        assertThat(p).contains("SEARCH TABLE mastery_states AS m USING INDEX index_mastery_states_packId_lessonId")
        assertThat(p).contains("index_lessons_packId")
    }

    @Test
    fun verbDrill_byPack_usesIndex_notScan() {
        val p = plan("SELECT * FROM verb_drill_cards WHERE packId = 'p'")
        assertThat(p).contains("SEARCH TABLE verb_drill_cards USING INDEX")
        assertThat(p).doesNotContain("SCAN verb_drill_cards")
    }

    @Test
    fun mastery_dueCards_usesDueIndex_notScan() {
        val p = plan(
            "SELECT * FROM mastery_states WHERE dueAtMs <= 1 AND completedAtMs IS NULL " +
                "ORDER BY dueAtMs LIMIT 20"
        )
        assertThat(p).doesNotContain("SCAN mastery_states")
    }
}

package com.alexpo.grammermate.v2.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alexpo.grammermate.v2.core.data.local.dao.ContentDao
import com.alexpo.grammermate.v2.core.data.local.dao.DrillDao
import com.alexpo.grammermate.v2.core.data.local.dao.MasteryDao
import com.alexpo.grammermate.v2.core.data.local.dao.ProgressDao
import com.alexpo.grammermate.v2.core.data.local.dao.SessionDao
import com.alexpo.grammermate.v2.core.data.local.dao.UserContentDao
import com.alexpo.grammermate.v2.core.data.local.entity.AuxDrillCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.AuxDrillComboProgressEntity
import com.alexpo.grammermate.v2.core.data.local.entity.BadSentenceEntity
import com.alexpo.grammermate.v2.core.data.local.entity.BgVocabMarkEntity
import com.alexpo.grammermate.v2.core.data.local.entity.BossRewardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.BgVocabPositionEntity
import com.alexpo.grammermate.v2.core.data.local.entity.CardEncounterEntity
import com.alexpo.grammermate.v2.core.data.local.entity.CardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.ChapterEntity
import com.alexpo.grammermate.v2.core.data.local.entity.ChapterProgressEntity
import com.alexpo.grammermate.v2.core.data.local.entity.DailyCursorEntity
import com.alexpo.grammermate.v2.core.data.local.entity.DrillProgressEntity
import com.alexpo.grammermate.v2.core.data.local.entity.HiddenCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.VerbDrillCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.VerbDrillComboProgressEntity
import com.alexpo.grammermate.v2.core.data.local.entity.VerbDrillLastSessionEntity
import com.alexpo.grammermate.v2.core.data.local.entity.VocabWordEntity
import com.alexpo.grammermate.v2.core.data.local.entity.WordMasteryEntity
import com.alexpo.grammermate.v2.core.data.local.entity.LessonEntity
import com.alexpo.grammermate.v2.core.data.local.entity.MasteryStateEntity
import com.alexpo.grammermate.v2.core.data.local.entity.MigrationFlagEntity
import com.alexpo.grammermate.v2.core.data.local.entity.PackEntity
import com.alexpo.grammermate.v2.core.data.local.entity.PomodoroHistoryEntity
import com.alexpo.grammermate.v2.core.data.local.entity.SessionCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.SessionEntity
import com.alexpo.grammermate.v2.core.data.local.entity.SessionShownCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.ShownCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.StreakEntity
import com.alexpo.grammermate.v2.core.data.local.entity.StreakPracticeTodayEntity

/**
 * GrammarMate v2 — единая Room-база данных пользовательского состояния.
 *
 * Один [Database] = один transaction boundary. Это и есть архитектурный фикс
 * бага `card_15` (v1): завершение под-урока пишет mastery + progress + streak +
 * session внутри **одной @Transaction**, а не четырьмя отдельными YAML-файлами
 * под независимыми `ReentrantLock`. Либо все изменения применены, либо ни одного.
 *
 * Контент паков (CSV/YAML/аудио) живёт в файловой системе как read-only ассеты —
 * здесь только структурное пользовательское состояние.
 *
 * WAL-режим ([RoomDatabase.Builder.setJournalMode]) включён в DI-модуле: писатель
 * не блокирует читателей, заменяя 24 `ReentrantLock` из v1.
 *
 * Версия схемы: 2. История миграций:
 *  - v1 → v2 ([MIGRATION_1_2], Фаза 2 плана стабилизации 2026-08-26):
 *    `sessions.revision` (NOT NULL DEFAULT 0) — optimistic-concurrency токен
 *    снимка сессии.
 *
 * Миграции регистрируются через [Builder.addMigrations]; schema-экспорт — в
 * `app/schemas/` (регрессионные migration-тесты через room-testing).
 */
@Database(
    version = 3,
    exportSchema = true,
    entities = [
        // Контент
        PackEntity::class,
        ChapterEntity::class,
        LessonEntity::class,
        CardEntity::class,
        // Сессия (фикс card_15)
        SessionEntity::class,
        SessionCardEntity::class,
        SessionShownCardEntity::class,
        // Mastery / SRS
        MasteryStateEntity::class,
        ShownCardEntity::class,
        CardEncounterEntity::class,
        // Прогресс
        StreakEntity::class,
        StreakPracticeTodayEntity::class,
        DrillProgressEntity::class,
        ChapterProgressEntity::class,
        DailyCursorEntity::class,
        // Drill-тренировки (vocab SRS, verb/aux, boss-награды)
        VocabWordEntity::class,
        WordMasteryEntity::class,
        VerbDrillCardEntity::class,
        AuxDrillCardEntity::class,
        VerbDrillComboProgressEntity::class,
        AuxDrillComboProgressEntity::class,
        VerbDrillLastSessionEntity::class,
        BossRewardEntity::class,
        // Пользовательский контент
        HiddenCardEntity::class,
        BadSentenceEntity::class,
        BgVocabMarkEntity::class,
        BgVocabPositionEntity::class,
        PomodoroHistoryEntity::class,
        MigrationFlagEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class GrammarMateDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao
    abstract fun sessionDao(): SessionDao
    abstract fun masteryDao(): MasteryDao
    abstract fun progressDao(): ProgressDao
    abstract fun drillDao(): DrillDao
    abstract fun userContentDao(): UserContentDao

    companion object {
        const val DATABASE_NAME = "grammarmate_v2.db"

        /**
         * v1 → v2: колонка `sessions.revision` — монотонная ревизия снимка
         * (Фаза 2 плана стабилизации 2026-08-26). Additive: существующие строки
         * получают ревизию 0; destructive-изменений нет.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN revision INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v2 → v3 (ADR-003): `word_mastery` становится pack-scoped — составной
         * PK `(packId, wordId)`. PK меняется только пересборкой таблицы:
         * `packId` каждой строки подтягивается из `vocab_words` по `wordId`
         * (id слова глобально уникален в контенте); строки-сироты без
         * контента удаляются (зелёная field-БД их не содержит).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS word_mastery_v3 (
                        packId TEXT NOT NULL,
                        wordId TEXT NOT NULL,
                        intervalStepIndex INTEGER NOT NULL DEFAULT 0,
                        correctCount INTEGER NOT NULL DEFAULT 0,
                        incorrectCount INTEGER NOT NULL DEFAULT 0,
                        lastReviewDateMs INTEGER NOT NULL DEFAULT 0,
                        nextReviewDateMs INTEGER NOT NULL DEFAULT 0,
                        isLearned INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (packId, wordId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO word_mastery_v3 (packId, wordId, intervalStepIndex,
                        correctCount, incorrectCount, lastReviewDateMs, nextReviewDateMs, isLearned)
                    SELECT v.packId, m.wordId, m.intervalStepIndex, m.correctCount,
                           m.incorrectCount, m.lastReviewDateMs, m.nextReviewDateMs, m.isLearned
                    FROM word_mastery m
                    JOIN vocab_words v ON v.id = m.wordId
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE word_mastery")
                db.execSQL("ALTER TABLE word_mastery_v3 RENAME TO word_mastery")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_word_mastery_nextReviewDateMs " +
                        "ON word_mastery (nextReviewDateMs)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_word_mastery_packId ON word_mastery (packId)",
                )
            }
        }

        /**
         * Production builder: WAL включён, миграции регистрируются здесь.
         * Schema export — в `app/schemas/` (для регрессионных migration-тестов).
         */
        fun build(context: Context): GrammarMateDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                GrammarMateDatabase::class.java,
                DATABASE_NAME,
            )
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                // fallbackToDestructiveMigration НЕ используется — данные пользователя критичны.
                .build()
    }
}

package com.alexpo.grammermate.v2.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.alexpo.grammermate.v2.core.data.local.dao.ContentDao
import com.alexpo.grammermate.v2.core.data.local.dao.MasteryDao
import com.alexpo.grammermate.v2.core.data.local.dao.ProgressDao
import com.alexpo.grammermate.v2.core.data.local.dao.SessionDao
import com.alexpo.grammermate.v2.core.data.local.dao.UserContentDao
import com.alexpo.grammermate.v2.core.data.local.entity.BadSentenceEntity
import com.alexpo.grammermate.v2.core.data.local.entity.BgVocabMarkEntity
import com.alexpo.grammermate.v2.core.data.local.entity.BgVocabPositionEntity
import com.alexpo.grammermate.v2.core.data.local.entity.CardEncounterEntity
import com.alexpo.grammermate.v2.core.data.local.entity.CardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.ChapterEntity
import com.alexpo.grammermate.v2.core.data.local.entity.ChapterProgressEntity
import com.alexpo.grammermate.v2.core.data.local.entity.DailyCursorEntity
import com.alexpo.grammermate.v2.core.data.local.entity.DrillProgressEntity
import com.alexpo.grammermate.v2.core.data.local.entity.HiddenCardEntity
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
 * Версия схемы: 1 (initial). Миграции добавляются через [Builder.addMigrations].
 */
@Database(
    version = 1,
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
    abstract fun userContentDao(): UserContentDao

    companion object {
        const val DATABASE_NAME = "grammarmate_v2.db"

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
                // .addMigrations(*ALL_MIGRATIONS)  // добавляются по мере эволюции схемы
                // fallbackToDestructiveMigration НЕ используется — данные пользователя критичны.
                .build()
    }
}

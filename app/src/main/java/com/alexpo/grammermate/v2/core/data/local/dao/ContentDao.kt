package com.alexpo.grammermate.v2.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.alexpo.grammermate.v2.core.data.local.entity.CardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.ChapterEntity
import com.alexpo.grammermate.v2.core.data.local.entity.LessonEntity
import com.alexpo.grammermate.v2.core.data.local.entity.PackEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-слой для контента паков: языки, паки, главы, уроки, карточки.
 *
 * Контент иммутабелен и импортируется извне (pack-import), поэтому операции
 * записи — это в основном `REPLACE`-upsert'ы при реимпорте. Чтения для UI —
 * `suspend` point-in-time, кроме [observeLessons], на который подписывается UI.
 *
 * Импортируется одной [Transaction] через [replaceLessonCards] и bulk-insert
 * методы, чтобы реимпорт урока/пака был атомарным.
 *
 * `order` — зарезервированное слово SQL, экранируется backticks (`\`order\``).
 */
@Dao
interface ContentDao {

    // ── Packs ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPack(pack: PackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPacks(packs: List<PackEntity>)

    @Query("SELECT * FROM packs")
    suspend fun getPacks(): List<PackEntity>

    @Query("SELECT * FROM packs WHERE languageId = :langId")
    suspend fun getPacksForLanguage(langId: String): List<PackEntity>

    @Query("SELECT * FROM packs WHERE id = :packId")
    suspend fun getPack(packId: String): PackEntity?

    @Query("DELETE FROM packs WHERE id = :packId")
    suspend fun deletePack(packId: String)

    // ── Chapters ─────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Query("SELECT * FROM chapters WHERE packId = :packId ORDER BY `order`")
    suspend fun getChapters(packId: String): List<ChapterEntity>

    @Query("DELETE FROM chapters WHERE packId = :packId")
    suspend fun deleteChaptersForPack(packId: String)

    // ── Lessons ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLesson(lesson: LessonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(lessons: List<LessonEntity>)

    @Query("SELECT * FROM lessons WHERE packId = :packId ORDER BY `order`")
    suspend fun getLessons(packId: String): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE packId = :packId ORDER BY `order`")
    fun observeLessons(packId: String): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE packId = :packId AND chapterId = :chapterId ORDER BY `order`")
    suspend fun getLessonsForChapter(packId: String, chapterId: String): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE id = :lessonId")
    suspend fun getLesson(lessonId: String): LessonEntity?

    @Query("DELETE FROM lessons WHERE packId = :packId")
    suspend fun deleteLessonsForPack(packId: String)

    // ── Cards ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<CardEntity>)

    @Query("SELECT * FROM cards WHERE lessonId = :lessonId ORDER BY ord")
    suspend fun getCards(lessonId: String): List<CardEntity>

    @Query("SELECT * FROM cards WHERE packId = :packId")
    suspend fun getCardsForPack(packId: String): List<CardEntity>

    @Query("SELECT * FROM cards WHERE id = :cardId")
    suspend fun getCard(cardId: String): CardEntity?

    @Query("DELETE FROM cards WHERE lessonId = :lessonId")
    suspend fun deleteCardsForLesson(lessonId: String)

    @Query("DELETE FROM cards WHERE packId = :packId")
    suspend fun deleteCardsForPack(packId: String)

    /**
     * Атомарно заменить набор карточек урока: удалить старые и вставить [cards].
     *
     * Используется при реимпорте пака, чтобы пул карточек урока не рассогласовывался
     * с его [LessonEntity] (старые карточки не «зависают» в выдаче).
     */
    @Transaction
    suspend fun replaceLessonCards(lessonId: String, cards: List<CardEntity>) {
        deleteCardsForLesson(lessonId)
        insertCards(cards)
    }
}

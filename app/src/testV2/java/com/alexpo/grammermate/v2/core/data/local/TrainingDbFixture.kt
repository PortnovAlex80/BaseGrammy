package com.alexpo.grammermate.v2.core.data.local

import android.content.Context
import androidx.room.Room
import com.alexpo.grammermate.v2.core.data.local.entity.CardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.LessonEntity
import com.alexpo.grammermate.v2.core.data.local.entity.PackEntity

/**
 * Детерминированный fixture основного UX-пути (Фаза 0 плана стабилизации
 * 2026-08-26): один пак → один урок → три карточки SENTENCE.
 *
 * Один и тот же набор данных используется Room-контрактными тестами
 * (`SessionRepositoryContractTest`) и ViewModel-тестами
 * (`TrainingViewModelRegressionTest`) — domain-карточки строятся из тех же ID,
 * чтобы fake-слой и Room видели одинаковый контент.
 */
object TrainingDbFixture {

    const val PACK_ID = "FIXTURE_PACK"
    const val LESSON_ID = "lesson_fix_01"

    /** ID карточек урока (стабильные, НЕ позиционные — в отличие от card_N). */
    val CARD_IDS = listOf("card_fix_2", "card_fix_3", "card_fix_4")

    /** In-memory Room БД (Robolectric). Закрывается тестом в @After. */
    fun inMemory(context: Context): GrammarMateDatabase =
        Room.inMemoryDatabaseBuilder(context, GrammarMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    /**
     * Наполнить чистую БД минимальным обучающим контентом: пак + урок (без
     * главы — chapterId nullable, manifest v1 путь) + карточки в порядке ord.
     */
    suspend fun seedTrainingContent(db: GrammarMateDatabase) {
        val contentDao = db.contentDao()
        contentDao.insertPack(
            PackEntity(
                id = PACK_ID,
                languageId = "it",
                displayName = "Fixture Pack",
                version = "1",
                importedAtMs = 0L,
            )
        )
        contentDao.insertLesson(
            LessonEntity(
                id = LESSON_ID,
                packId = PACK_ID,
                chapterId = null,
                order = 0,
                title = "Fixture lesson",
                cefrLevel = "A1",
                grammarChipKey = null,
            )
        )
        contentDao.insertCards(
            CARD_IDS.mapIndexed { index, cardId ->
                CardEntity(
                    id = cardId,
                    packId = PACK_ID,
                    lessonId = LESSON_ID,
                    ord = index,
                    type = "SENTENCE",
                    promptRu = "Промпт $index",
                    acceptedAnswersJson = """["answer $index","alt $index"]""",
                    tense = null,
                    verb = null,
                    verbGroup = null,
                    person = null,
                    frequencyRank = null,
                )
            }
        )
    }
}

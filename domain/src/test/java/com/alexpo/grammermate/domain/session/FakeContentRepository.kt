package com.alexpo.grammermate.domain.session

import com.alexpo.grammermate.domain.model.Card
import com.alexpo.grammermate.domain.model.Chapter
import com.alexpo.grammermate.domain.model.ChapterId
import com.alexpo.grammermate.domain.model.Language
import com.alexpo.grammermate.domain.model.LanguageId
import com.alexpo.grammermate.domain.model.Lesson
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.Pack
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.VerbDrillCard
import com.alexpo.grammermate.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory реализация [ContentRepository] для чистых JVM-тестов.
 *
 * Хранит карточки по [LessonId]. Тесты настраивают выдачу через
 * [setCardsForLesson]; остальные методы возвращают пустые значения —
 * они доменному [SessionEngine] не нужны, но контракт интерфейса требует
 * их реализации.
 */
class FakeContentRepository : ContentRepository {

    private val cardsByLesson = mutableMapOf<LessonId, List<Card>>()
    private val lessonsById = mutableMapOf<LessonId, Lesson>()
    private val verbDrillCardsByPack = mutableMapOf<PackId, List<VerbDrillCard>>()

    /** Тестовый сетап: задать карточки verb drill для пака. */
    fun setVerbDrillCards(packId: PackId, cards: List<VerbDrillCard>) {
        verbDrillCardsByPack[packId] = cards
    }

    /** Тестовый сетап: задать карточки (и сам урок) для урока. */
    fun setCardsForLesson(lessonId: LessonId, cards: List<Card>) {
        cardsByLesson[lessonId] = cards
        if (lessonId !in lessonsById) {
            lessonsById[lessonId] = Lesson(
                id = lessonId,
                packId = cards.firstOrNull()?.packId ?: PackId("test-pack"),
                chapterId = null,
                order = 0,
                title = "test",
                cefrLevel = null,
                grammarChipKey = null,
                cards = cards,
            )
        }
    }

    override suspend fun getLanguages(): List<Language> = emptyList()
    override suspend fun getPacks(): List<Pack> = emptyList()
    override fun observePacks(): Flow<List<Pack>> = flowOf(emptyList())
    override suspend fun getPacksForLanguage(langId: LanguageId): List<Pack> = emptyList()
    override suspend fun getPack(packId: PackId): Pack? = null
    override suspend fun getChapters(packId: PackId): List<Chapter> = emptyList()
    override suspend fun getLessons(packId: PackId): List<Lesson> =
        lessonsById.values.filter { it.packId == packId }
    override suspend fun getLessonsForChapter(packId: PackId, chapterId: ChapterId): List<Lesson> =
        emptyList()
    override suspend fun getLesson(lessonId: LessonId): Lesson? = lessonsById[lessonId]
    override suspend fun getCards(lessonId: LessonId): List<Card> =
        cardsByLesson[lessonId] ?: emptyList()

    override suspend fun getVerbDrillCards(
        packId: PackId,
        tense: String?,
        group: String?,
        person: String?,
    ): List<VerbDrillCard> =
        verbDrillCardsByPack[packId].orEmpty()
            .filter { tense == null || it.tense == tense }
            .filter { group == null || it.group == group }
            .filter { person == null || it.person == person }
            .sortedWith(compareBy<VerbDrillCard> { it.rank ?: Int.MAX_VALUE }.thenBy { it.id })

    override fun observeLessons(packId: PackId): Flow<List<Lesson>> = flowOf(emptyList())

    override suspend fun getStoryText(packId: PackId, relativePath: String): String? = null
}

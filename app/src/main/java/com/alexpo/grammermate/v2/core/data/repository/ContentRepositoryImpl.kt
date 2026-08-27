package com.alexpo.grammermate.v2.core.data.repository

import com.alexpo.grammermate.v2.core.data.local.dao.ContentDao
import com.alexpo.grammermate.v2.core.data.local.dao.DrillDao
import dagger.hilt.android.qualifiers.ApplicationContext
import com.alexpo.grammermate.v2.core.data.local.entity.VerbDrillCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.CardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.ChapterEntity
import com.alexpo.grammermate.v2.core.data.local.entity.LessonEntity
import com.alexpo.grammermate.v2.core.data.local.entity.PackEntity
import com.alexpo.grammermate.domain.model.Card
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.CardType
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
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Room-реализация [ContentRepository] — read-only доступ к контенту паков.
 *
 * Маппит Room-entities ([PackEntity]/[LessonEntity]/[CardEntity]…) в чистые
 * доменные модели ([Pack]/[Lesson]/[Card]…), изолируя data-слой от домена.
 *
 * Особенности реализации:
 *  - **Языки** ([getLanguages]): в v2 нет отдельной таблицы `languages`, поэтому
 *    возвращается захардкоженный seed-список (en/it/de/zh/ru/el), идентичный
 *    v1 `LanguageManager.ensureSeedData`. Pack-import всегда использует один из
 *    этих кодов в `packs.languageId`.
 *  - **JSON-колонка** [CardEntity.acceptedAnswersJson]: десериализуется здесь
 *    (а не Room TypeConverter) через [kotlinx.serialization] в `List<String>`.
 *  - **enum↔String**: [CardType] хранится в БД как строка, маппится через
 *    [CardType.valueOf] с безопасным дефолтом ([parseCardType]).
 *  - **[Chapter.lessonIds]**: в [ChapterEntity] нет колонки для списка уроков,
 *    поэтому [getChapters] одним запросом тянет все уроки пака и группирует их
 *    по `chapterId` (без N+1).
 *
 * @property contentDao Room-DAO контента паков.
 * @property drillDao   Room-DAO drill-контента (verb drill, Фаза 4 срез 2).
 */
class ContentRepositoryImpl @Inject constructor(
    private val contentDao: ContentDao,
    private val drillDao: DrillDao,
    @ApplicationContext private val context: android.content.Context,
) : ContentRepository {

    // ── Языки ──────────────────────────────────────────────────────────────────

    /**
     * Возвращает захардкоженный список языков обучения.
     *
     * В v2 нет таблицы `languages` (см. KDoc класса): pack-import пишет код языка
     * прямо в `packs.languageId`. Этот список — стабильный seed из v1
     * `LanguageManager`, которого достаточно для UI-навигации. При появлении
     * таблицы `languages` здесь должен быть запрос в DAO.
     */
    override suspend fun getLanguages(): List<Language> = LANGUAGES_SEED

    // ── Паки ───────────────────────────────────────────────────────────────────

    override suspend fun getPacks(): List<Pack> =
        contentDao.getPacks().map(::packEntityToDomain)

    override suspend fun getPacksForLanguage(langId: LanguageId): List<Pack> =
        contentDao.getPacksForLanguage(langId.value).map(::packEntityToDomain)

    override suspend fun getPack(packId: PackId): Pack? =
        contentDao.getPack(packId.value)?.let(::packEntityToDomain)

    /** Реактивные паки: Home видит bundled-seed сразу после первого импорта. */
    override fun observePacks(): Flow<List<Pack>> =
        contentDao.observePacks().map { entities -> entities.map(::packEntityToDomain) }

    // ── Главы ──────────────────────────────────────────────────────────────────

    override suspend fun getChapters(packId: PackId): List<Chapter> {
        val chapters = contentDao.getChapters(packId.value)
        if (chapters.isEmpty()) return emptyList()

        // Один запрос на все уроки пака; группируем по chapterId, чтобы
        // собрать [Chapter.lessonIds] без N+1-обращений к БД.
        val lessonsByChapter: Map<String, List<LessonId>> =
            contentDao.getLessons(packId.value)
                .filter { it.chapterId != null }
                .groupBy { it.chapterId!! }
                .mapValues { (_, lessons) -> lessons.map { e -> LessonId(e.id) } }

        return chapters.map { e ->
            chapterEntityToDomain(e) { lessonsByChapter[e.id].orEmpty() }
        }
    }

    // ── Уроки ──────────────────────────────────────────────────────────────────

    override suspend fun getLessons(packId: PackId): List<Lesson> =
        contentDao.getLessons(packId.value).map { e ->
            lessonEntityToDomain(e, cards = emptyList())
        }

    override suspend fun getLessonsForChapter(packId: PackId, chapterId: ChapterId): List<Lesson> =
        contentDao.getLessonsForChapter(packId.value, chapterId.value).map { e ->
            lessonEntityToDomain(e, cards = emptyList())
        }

    /**
     * Один урок вместе с карточками.
     *
     * Сначала достаёт [LessonEntity] по id (в нём же лежит `packId`), затем —
     * карточки урока и десериализует у каждой [Card.acceptedAnswers] из JSON.
     * Возвращает null, если урока нет.
     */
    override suspend fun getLesson(lessonId: LessonId): Lesson? {
        val entity = contentDao.getLesson(lessonId.value) ?: return null
        val cards = contentDao.getCards(entity.packId, lessonId.value).map(::cardEntityToDomain)
        return lessonEntityToDomain(entity, cards)
    }

    /** Карточки урока (с десериализацией acceptedAnswers из JSON). */
    override suspend fun getCards(packId: PackId, lessonId: LessonId): List<Card> =
        contentDao.getCards(packId.value, lessonId.value).map(::cardEntityToDomain)

    /** Реактивный список уроков пака (без карточек — для подписки UI-списка). */
    override fun observeLessons(packId: PackId): Flow<List<Lesson>> =
        contentDao.observeLessons(packId.value).map { entities ->
            entities.map { e -> lessonEntityToDomain(e, cards = emptyList()) }
        }

    // ── Мапперы entity → domain ────────────────────────────────────────────────

    private fun packEntityToDomain(e: PackEntity): Pack = Pack(
        id = PackId(e.id),
        languageId = LanguageId(e.languageId),
        displayName = e.displayName,
        version = e.version,
        importedAtMs = e.importedAtMs,
    )

    /**
     * @param lessonIdsProvider вычисляет [Chapter.lessonIds] (достаются одним
     *   bulk-запросом в [getChapters], а не N запросами).
     */
    private fun chapterEntityToDomain(
        e: ChapterEntity,
        lessonIdsProvider: () -> List<LessonId>,
    ): Chapter = Chapter(
        id = ChapterId(e.id),
        packId = PackId(e.packId),
        order = e.order,
        title = e.title,
        subtitle = e.subtitle,
        storyFile = e.storyFile,
        lessonIds = lessonIdsProvider(),
    )

    private fun lessonEntityToDomain(e: LessonEntity, cards: List<Card>): Lesson = Lesson(
        id = LessonId(e.id),
        packId = PackId(e.packId),
        chapterId = e.chapterId?.let(::ChapterId),
        order = e.order,
        title = e.title,
        cefrLevel = e.cefrLevel,
        grammarChipKey = e.grammarChipKey,
        cards = cards,
    )

    private fun cardEntityToDomain(e: CardEntity): Card = Card(
        id = CardId(e.id),
        packId = PackId(e.packId),
        lessonId = LessonId(e.lessonId),
        ord = e.ord,
        type = parseCardType(e.type),
        promptRu = e.promptRu,
        acceptedAnswers = decodeAcceptedAnswers(e.acceptedAnswersJson),
        tense = e.tense,
        verb = e.verb,
        verbGroup = e.verbGroup,
        person = e.person,
        frequencyRank = e.frequencyRank,
    )

    // ── Verb drill (Фаза 4 срез 2) ─────────────────────────────────────────────

    /**
     * Карточки verb drill пака: один SELECT по `packId` (индекс) + фильтрация
     * combo в памяти (`tense`/`group`/`person`; null = не фильтровать) и
     * сортировка по частотности (`rank`, null — в конец), детерминированно.
     */
    override suspend fun getVerbDrillCards(
        packId: PackId,
        tense: String?,
        group: String?,
        person: String?,
    ): List<VerbDrillCard> =
        drillDao.getVerbDrillCardsForPack(packId.value)
            .asSequence()
            .filter { tense == null || it.tense == tense }
            .filter { group == null || it.group == group }
            .filter { person == null || it.person == person }
            .sortedWith(compareBy<VerbDrillCardEntity> { it.rank ?: Int.MAX_VALUE }.thenBy { it.id })
            .map { e ->
                VerbDrillCard(
                    id = e.id,
                    promptRu = e.promptRu,
                    answer = e.answer,
                    verb = e.verb,
                    tense = e.tense,
                    group = e.group,
                    person = e.person,
                    rank = e.rank,
                )
            }
            .toList()

    // ── Вспомогательное: JSON и enum-маппинг ───────────────────────────────────

    /**
     * Десериализует JSON-массив ответов из колонки [CardEntity.acceptedAnswersJson].
     *
     * Использует тот же [Json]-инстанс, что и [com.alexpo.grammermate.v2.core.data.local.Converters]
     * (`ignoreUnknownKeys`, `encodeDefaults`). Повреждённая/пустая строка → пустой список,
     * чтобы одна битая карточка не роняла весь урок.
     */
    private fun decodeAcceptedAnswers(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            JSON.decodeFromString(STRING_LIST_SERIALIZER, json)
        }.getOrDefault(emptyList())
    }

    /**
     * Story-текст главы из filesDir/stories/<packId>/<relativePath>
     * (файлы кладёт PackImporter.preserveStoryFiles — срез 6 Фазы 4).
     */
    override suspend fun getStoryText(packId: PackId, relativePath: String): String? {
        val file = java.io.File(java.io.File(context.filesDir, "stories/${packId.value}"), relativePath)
        return file.takeIf { it.isFile }?.readText()
    }

    /**
     * Безопасный enum↔String маппинг для [CardType].
     *
     * Неизвестное/битое значение логируется как SENTENCE (дефолт), чтобы не
     * ронять чтение урока. Менять дефолт на другой тип опасно — SENTENCE
     * является самым «нейтральным» типом карточки.
     */
    private fun parseCardType(raw: String?): CardType =
        raw?.takeUnless { it.isBlank() }
            ?.let { runCatching { CardType.valueOf(it) }.getOrNull() }
            ?: CardType.SENTENCE

    private companion object {
        /** Seed языков обучения (идентичен v1 `LanguageManager`). */
        private val LANGUAGES_SEED: List<Language> = listOf(
            Language(LanguageId("en"), "English"),
            Language(LanguageId("it"), "Italian"),
            Language(LanguageId("de"), "German"),
            Language(LanguageId("zh"), "Chinese"),
            Language(LanguageId("ru"), "Russian"),
            Language(LanguageId("el"), "Greek"),
        )

        private val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        private val STRING_LIST_SERIALIZER = ListSerializer(String.serializer())
    }
}

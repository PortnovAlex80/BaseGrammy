package com.alexpo.grammermate.domain.repository

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
import kotlinx.coroutines.flow.Flow

/**
 * Read-only доступ к контенту паков: языки, паки, главы, уроки, карточки.
 *
 * Контент иммутабелен (импортируется извне), поэтому все методы — одноразовые
 * `suspend`-чтения, кроме [observeLessons], на который подписывается UI для
 * реактивного обновления списка уроков пака.
 */
interface ContentRepository {

    /** Все доступные языки обучения. */
    suspend fun getLanguages(): List<Language>

    /** Все установленные паки. */
    suspend fun getPacks(): List<Pack>

    /**
     * Реактивный список установленных паков — Home подписывается и отражает
     * первый bundled-import без ручного re-query (Фаза 1 плана стабилизации).
     */
    fun observePacks(): Flow<List<Pack>>

    /** Паки конкретного языка. */
    suspend fun getPacksForLanguage(langId: LanguageId): List<Pack>

    /** Один пак по идентификатору или null, если не найден. */
    suspend fun getPack(packId: PackId): Pack?

    /** Главы пака в порядке отображения. */
    suspend fun getChapters(packId: PackId): List<Chapter>

    /** Все уроки пака. */
    suspend fun getLessons(packId: PackId): List<Lesson>

    /** Уроки конкретной главы пака. */
    suspend fun getLessonsForChapter(packId: PackId, chapterId: ChapterId): List<Lesson>

    /**
     * Один урок вместе с его карточками (включая [Lesson.cards]),
     * либо null, если урок не найден.
     */
    suspend fun getLesson(lessonId: LessonId): Lesson?

    /** Карточки урока в порядке отображения. */
    suspend fun getCards(lessonId: LessonId): List<Card>

    /**
     * Карточки verb drill пака с combo-фильтрами (Фаза 4 срез 2).
     *
     * `null`-фильтр = не фильтровать по этому измерению. Порядок — по
     * частотности (`VerbDrillCard.rank`, null — в конец), детерминированно.
     */
    suspend fun getVerbDrillCards(
        packId: PackId,
        tense: String? = null,
        group: String? = null,
        person: String? = null,
    ): List<VerbDrillCard>

    /**
     * Текст story-файла главы (срез 6 Фазы 4): importer сохраняет `.md`-файлы
     * пака; путь — как в `chapters[].storyFile` манифеста. null — файл не
     * найден (пак без стори / не импортирован).
     */
    suspend fun getStoryText(packId: PackId, relativePath: String): String?

    /** Реактивный список уроков пака — для подписки UI. */
    fun observeLessons(packId: PackId): Flow<List<Lesson>>
}

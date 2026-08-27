package com.alexpo.grammermate.v2.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entities drill-тренировок — глагольные (спряжение), вспомогательные (aux)
 * и словарные (vocab, Anki/FSRS-стиль SRS), а также прогресс combos и награды
 * за boss-битвы. Расширяет базовый [DrillProgressEntity] детализацией до combo
 * и отдельной SRS-схемой слова ([WordMasteryEntity]).
 *
 * Контент drill'ов (карточки, слова) — read-mostly, заполняется при pack-import.
 * Прогресс combos и SRS-состояния — изменяемое во времени пользовательское состояние.
 *
 * JSON-поля (`collocationsJson`, `formsJson`, `everShownCardIdsJson`,
 * `todayShownCardIdsJson`, `sessionCardIdsJson`) хранятся как готовые строки:
 * сериализация List/Set/Map через kotlinx.serialization выполняется в repository,
 * Room TypeConverter для entity-полей НЕ используется (см. ROOM_SCHEMA.md).
 *
 * Составные PK задаются через `primaryKeys = [...]` (а не `@PrimaryKey`), чтобы
 * объединять пак + ключ combo/сессии/награды.
 *
 * Enum-поля домена (BossType / BossReward) хранятся здесь как [String]
 * (`.name` enum'а); маппинг enum ↔ String — в repository-реализации.
 *
 * @see <a href="../../../../../../../../docs/v2-architecture/ROOM_SCHEMA.md">ROOM_SCHEMA.md</a>
 */

/**
 * Контент: лексическое слово для vocab drill.
 *
 * Идентификатор кодирует часть речи и ранг: `"${pos}_${rank}_${word}"`.
 * Уникальный индекс (packId, word) — одно слово на пак.
 *
 * @property id               PK слова ("${pos}_${rank}_${word}").
 * @property packId           пак слова.
 * @property word             целевое слово.
 * @property pos              часть речи (nouns/verbs/adjectives/...).
 * @property rank             ранг частотности (для сортировки и срезов).
 * @property meaningRu        перевод на русский, либо null.
 * @property collocationsJson устойчивые словосочетания (JSON List<String>).
 * @property formsJson        родовые/числовые формы (JSON Map<String,String>).
 */
@Entity(tableName = "vocab_words", primaryKeys = ["packId", "id"], indices = [Index("packId"), Index(value = ["packId", "word"], unique = true)])
data class VocabWordEntity(
    val id: String, // "${pos}_${rank}_${word}"
    val packId: String,
    val word: String,
    val pos: String,
    val rank: Int,
    val meaningRu: String?,
    val collocationsJson: String, // List<String>
    val formsJson: String,        // Map<String,String>
)

/**
 * SRS-состояние отдельного слова (vocab drill) — ОТДЕЛЬНО от lesson mastery.
 *
 * ADR-003 (gap #4 аудита 2026-08-26): PK составной `(packId, wordId)` — SRS
 * слова pack-scoped, как весь user-state; due-выборка фильтруется по паку.
 *
 * @property packId            пак слова ([VocabWordEntity.packId]).
 * @property wordId            идентификатор слова ([VocabWordEntity.id]).
 * @property intervalStepIndex индекс шага интервала (0-9).
 * @property correctCount      всего правильных ответов.
 * @property incorrectCount    всего неправильных ответов.
 * @property lastReviewDateMs  epoch-мс последнего повторения.
 * @property nextReviewDateMs  ★ epoch-мс, когда слово снова нужно повторить (0 — сразу); индекс SRS.
 * @property isLearned         достигнут ли порог изученности (intervalStepIndex >= 3).
 */
@Entity(
    tableName = "word_mastery",
    primaryKeys = ["packId", "wordId"],
    indices = [Index("nextReviewDateMs"), Index("packId")],
)
data class WordMasteryEntity(
    val packId: String,
    val wordId: String,
    val intervalStepIndex: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val lastReviewDateMs: Long = 0L,
    val nextReviewDateMs: Long = 0L,
    val isLearned: Boolean = false,
)

/**
 * Контент: карточка verb drill (спряжение).
 *
 * Идентификатор кодирует combo: `"${group}_${tense}_${dataRowIndex}"`.
 * Индексы на (packId, tense) и (packId, verb, tense) — для фильтров по времени/глаголу.
 *
 * @property id       PK карточки ("${group}_${tense}_${dataRowIndex}").
 * @property packId   пак карточки.
 * @property promptRu промпт на русском.
 * @property answer   единственный принимаемый ответ.
 * @property verb     инфинитив глагола, либо null.
 * @property tense    время глагола, либо null.
 * @property group    группа спряжения, либо null.
 * @property person   лицо/число (Io/Tu/...), либо null.
 * @property rank     ранг частотности (для sortByFrequency), либо null.
 */
@Entity(tableName = "verb_drill_cards", primaryKeys = ["packId", "id"], indices = [Index("packId"), Index(value = ["packId", "tense"]), Index(value = ["packId", "verb", "tense"])])
data class VerbDrillCardEntity(
    val id: String, // "${group}_${tense}_${dataRowIndex}"
    val packId: String,
    val promptRu: String,
    val answer: String,
    val verb: String?,
    val tense: String?,
    val group: String?,
    val person: String?,
    val rank: Int?,
)

/**
 * Контент: подводящая карточка aux drill (avere/essere/stare × время).
 *
 * Индекс (packId, verb, tense) — для выборки пары глагол+время aux drill.
 *
 * @property id       PK карточки.
 * @property packId   пак карточки.
 * @property promptRu промпт на русском.
 * @property answer   единственный принимаемый ответ.
 * @property verb     вспомогательный глагол (avere/essere/stare), либо null.
 * @property tense    время глагола, либо null.
 * @property group    группа спряжения, либо null.
 * @property person   лицо/число, либо null.
 * @property rank     ранг частотности, либо null.
 */
@Entity(tableName = "aux_drill_cards", primaryKeys = ["packId", "id"], indices = [Index("packId"), Index(value = ["packId", "verb", "tense"])])
data class AuxDrillCardEntity(
    val id: String,
    val packId: String,
    val promptRu: String,
    val answer: String,
    val verb: String?,
    val tense: String?,
    val group: String?,
    val person: String?,
    val rank: Int?,
)

/**
 * Прогресс verb drill по combo (group + tense).
 *
 * Составной PK (packId + comboKey); уникальный индекс (packId, group, tense)
 * — один прогресс на pak+combo. JSON-поля множеств показанных карточек
 * сериализуются в repository (Set<String>).
 *
 * @property packId               пак.
 * @property comboKey             ключ combo ("${group}_${tense}").
 * @property group                группа спряжения.
 * @property tense                время.
 * @property totalCards           всего карточек в combo.
 * @property everShownCardIdsJson показанные за всё время (JSON Set<String>).
 * @property todayShownCardIdsJson показанные сегодня (JSON Set<String>).
 * @property lastDate             дата последней тренировки (ISO yyyy-MM-dd), либо null.
 * @property updatedAtMs          epoch-мс последнего обновления.
 */
@Entity(tableName = "verb_drill_combo_progress", primaryKeys = ["packId", "comboKey"], indices = [Index(value = ["packId", "group", "tense"], unique = true)])
data class VerbDrillComboProgressEntity(
    val packId: String,
    val comboKey: String, // "${group}_${tense}"
    val group: String,
    val tense: String,
    val totalCards: Int,
    val everShownCardIdsJson: String,  // Set<String>
    val todayShownCardIdsJson: String, // Set<String>
    val lastDate: String?,
    val updatedAtMs: Long,
)

/**
 * Прогресс aux drill по combo (verb + tense).
 *
 * Составной PK (packId + comboKey); уникальный индекс (packId, verb, tense).
 * [everShownCardIdsJson] хранит Set<String> как JSON-строку.
 *
 * @property packId               пак.
 * @property comboKey             ключ combo ("aux|${verb}|${tense}").
 * @property verb                 вспомогательный глагол.
 * @property tense                время.
 * @property totalCards           всего карточек в combo.
 * @property everShownCardIdsJson показанные за всё время (JSON Set<String>).
 * @property lastDate             дата последней тренировки (ISO yyyy-MM-dd), либо null.
 * @property updatedAtMs          epoch-мс последнего обновления.
 */
@Entity(tableName = "aux_drill_combo_progress", primaryKeys = ["packId", "comboKey"], indices = [Index(value = ["packId", "verb", "tense"], unique = true)])
data class AuxDrillComboProgressEntity(
    val packId: String,
    val comboKey: String, // "aux|${verb}|${tense}"
    val verb: String,
    val tense: String,
    val totalCards: Int,
    val everShownCardIdsJson: String,
    val lastDate: String?,
    val updatedAtMs: Long,
)

/**
 * Resume-состояние последней verb drill сессии (pack-scoped).
 *
 * Сохраняется per-pack (уникальный индекс `packId`) и восстанавливается при
 * повторном входе на экран verb drill. JSON-поля (CardId-списки) сериализуются
 * в repository (Set/List<String>).
 *
 * @property packId                 PK — пак сессии.
 * @property selectedTense          выбранный фильтр времени, либо null.
 * @property selectedGroup          выбранный фильтр группы спряжения, либо null.
 * @property selectedPerson         выбранный фильтр лица, либо null.
 * @property sortByFrequency        признак сортировки по рангу частотности.
 * @property todayShownCardIdsJson  показанные сегодня карточки (JSON Set<String>).
 * @property sessionCardIdsJson     карточки последнего батча по порядку (JSON List<String>).
 * @property currentIndex           следующий индекс в sessionCardIds (для «Продолжить»).
 * @property updatedAtMs            epoch-мс последнего обновления.
 */
@Entity(tableName = "verb_drill_last_session", indices = [Index("packId", unique = true)])
data class VerbDrillLastSessionEntity(
    @PrimaryKey val packId: String,
    val selectedTense: String?,
    val selectedGroup: String?,
    val selectedPerson: String?,
    val sortByFrequency: Boolean,
    val todayShownCardIdsJson: String,
    val sessionCardIdsJson: String,
    val currentIndex: Int,
    val updatedAtMs: Long,
)

/**
 * Награда за boss-битву.
 *
 * Составной PK (packId + bossType + scopeKey) — одна награда на pak+тип+область.
 * Enum-поля домена хранятся как [String]: [bossType] = BossType.name (LESSON/MEGA/ELITE),
 * [reward] = BossReward.name (BRONZE/SILVER/GOLD); [scopeKey] — lessonId для LESSON,
 * "mega" для MEGA, шаг для ELITE.
 *
 * @property packId     пак битвы.
 * @property bossType   тип босса (BossType.name).
 * @property scopeKey   область: lessonId / "mega" / elite-step.
 * @property reward     награда (BossReward.name).
 * @property earnedAtMs epoch-мс получения награды.
 */
@Entity(tableName = "boss_rewards", primaryKeys = ["packId", "bossType", "scopeKey"])
data class BossRewardEntity(
    val packId: String,
    val bossType: String, // BossType.name (LESSON/MEGA/ELITE)
    val scopeKey: String, // lessonId для LESSON, "mega" для MEGA, step для ELITE
    val reward: String,   // BossReward.name (BRONZE/SILVER/GOLD)
    val earnedAtMs: Long,
)

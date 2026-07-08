package com.alexpo.grammermate.domain.model

/**
 * Модели drill-тренировок — глагольные (спряжение), вспомогательные (aux/lead-in)
 * и словарные (Anki-стиль) карты прогресса.
 *
 * Чистый Kotlin, без Room-аннотаций и Android-зависимостей. Drill'ы оперируют
 * лексикой/глаголами вне привязки к урокам [Lesson]: это отдельный режим практики
 * с собственной моделью прогресса, не сводимый к [LessonMastery].
 *
 * SRS-состояние отдельного словарного слова ([WordMasteryState]) живёт ОТДЕЛЬНО
 * от освоенности урока: урок завершается по показам карточек, а слово «выучено»
 * по лестнице интервалов (FSRS/Anki-стиль).
 */

/**
 * Карточка тренировки глаголов (спряжение).
 *
 * Перевод промпта [promptRu] на русском → целевая форма [answer] (один ответ;
 * альтернативы не поддерживаются на уровне drill-карточки).
 *
 * @property id        идентификатор карточки.
 * @property promptRu  что спрягать/перевести (русский).
 * @property answer    единственный принимаемый ответ (нормализованный).
 * @property verb      начальная форма глагола, либо null.
 * @property tense     время глагола (Presente, Imperfetto, …), либо null.
 * @property group     группа спряжения (regular_are, irregular, …), либо null.
 * @property person    лицо/число: Io/Tu/Lui/Lei/Noi/Voi/Loro, либо null.
 * @property rank      ранг частотности (для сортировки), либо null.
 */
data class VerbDrillCard(
    val id: String,
    val promptRu: String,
    val answer: String,
    val verb: String? = null,
    val tense: String? = null,
    val group: String? = null,
    val person: String? = null, // Io/Tu/Lui/Lei/Noi/Voi/Loro
    val rank: Int? = null,
) {
    /** Список принимаемых ответов — ровно один ([answer]). */
    val acceptedAnswers: List<String> get() = listOf(answer)
}

/**
 * Прогресс по combo (verb group + tense) в verb drill.
 *
 * @property group             группа спряжения.
 * @property tense             время.
 * @property totalCards        всего карточек в этой combo.
 * @property everShownCardIds  показанные за всё время карточки (для фильтра «новые»).
 * @property todayShownCardIds показанные сегодня карточки (для «всё на сегодня»).
 * @property lastDate          дата последней тренировки combo (ISO yyyy-MM-dd).
 */
data class VerbDrillComboProgress(
    val group: String,
    val tense: String,
    val totalCards: Int,
    val everShownCardIds: Set<String> = emptySet(),
    val todayShownCardIds: Set<String> = emptySet(),
    val lastDate: String = "", // ISO yyyy-MM-dd
)

/**
 * Resume-состояние последней незавершённой verb drill сессии.
 *
 * Сохраняется отдельным документом и восстанавливается при повторном входе на
 * экран verb drill: «Продолжить» грузит следующие карточки из пула, исключая
 * уже показанные сегодня ([todayShownCardIds]), а не точную прошлую сессию.
 *
 * @property selectedTense      выбранный фильтр времени (Presente, Imperfetto, …).
 * @property selectedGroup      выбранный фильтр группы спряжения.
 * @property selectedPerson     выбранный фильтр лица, либо null (без фильтра).
 * @property sortByFrequency    признак сортировки карточек по рангу частотности.
 * @property todayShownCardIds  показанные сегодня карточки (исключаются при resume).
 * @property sessionCardIds     идентификаторы карточек последнего батча по порядку (для «Повторить»).
 * @property currentIndex       следующий индекс в [sessionCardIds] (для «Продолжить»).
 */
data class VerbDrillLastSession(
    val selectedTense: String?,
    val selectedGroup: String?,
    val selectedPerson: String? = null,
    val sortByFrequency: Boolean,
    val todayShownCardIds: Set<String> = emptySet(),
    val sessionCardIds: List<String> = emptyList(),
    val currentIndex: Int = 0,
)

/**
 * Подводящая карточка (aux/lead-in drill) — те же поля, что у [VerbDrillCard].
 *
 * Карточка пула aux drill (avere/essere/stare × время). Пул разделяется с verb
 * drill, но модель изолирована, чтобы слой aux был независим.
 *
 * @property id        идентификатор карточки.
 * @property promptRu  что спрягать/перевести (русский).
 * @property answer    единственный принимаемый ответ (нормализованный).
 * @property verb      начальная форма глагола (avere/essere/stare), либо null.
 * @property tense     время глагола, либо null.
 * @property group     группа спряжения, либо null.
 * @property person    лицо/число, либо null.
 * @property rank      ранг частотности, либо null.
 */
data class AuxDrillCard(
    val id: String,
    val promptRu: String,
    val answer: String,
    val verb: String? = null,
    val tense: String? = null,
    val group: String? = null,
    val person: String? = null,
    val rank: Int? = null,
) {
    /** Список принимаемых ответов — ровно один ([answer]). */
    val acceptedAnswers: List<String> get() = listOf(answer)
}

/**
 * Прогресс aux drill по combo (verb + tense).
 *
 * @property verb             вспомогательный глагол (avere/essere/stare).
 * @property tense            время.
 * @property totalCards       всего карточек в этой combo.
 * @property everShownCardIds показанные за всё время карточки.
 * @property lastDate         дата последней тренировки combo (ISO yyyy-MM-dd).
 */
data class AuxDrillComboProgress(
    val verb: String,
    val tense: String,
    val totalCards: Int,
    val everShownCardIds: Set<String> = emptySet(),
    val lastDate: String = "",
)

/**
 * Пара для aux drill (avere/essere/stare × время) — единица меню выбора.
 *
 * Каждая пара готовит студента к уроку [lessonId] с темой [lessonTopic],
 * массово тренируя глагол [verb] во времени [tense].
 *
 * @property category    категория-группа в меню (Настоящее/Прошедшее/…).
 * @property verb        вспомогательный глагол.
 * @property tense       время глагола.
 * @property lessonId    урок, к которому готовит пара.
 * @property lessonTopic тема подготовительного урока.
 */
data class AuxDrillPair(
    val category: String,
    val verb: String,
    val tense: String,
    val lessonId: String,
    val lessonTopic: String,
)

/**
 * Лексическое слово для vocab drill.
 *
 * Строится из распарсенного CSV-словаря. Идентификатор кодирует часть речи
 * (например, «nouns_casa», «verbs_essere»).
 *
 * @property id           идентификатор слова (pos_word).
 * @property word         целевое слово.
 * @property pos          часть речи: nouns/verbs/adjectives/adverbs/numbers/pronouns.
 * @property rank         ранг частотности (для сортировки и срезов).
 * @property meaningRu    перевод на русский (опционально).
 * @property collocations устойчивые словосочетания со словом.
 * @property forms        родовые/числовые формы (например, {"msg":"pazzo","fsg":"pazza"}).
 */
data class VocabWord(
    val id: String,
    val word: String,
    val pos: String, // nouns/verbs/adjectives/adverbs/numbers/pronouns
    val rank: Int,
    val meaningRu: String? = null,
    val collocations: List<String> = emptyList(),
    val forms: Map<String, String> = emptyMap(), // gender forms
)

/**
 * SRS-состояние отдельного слова (Anki/FSRS-стиль) — ОТДЕЛЬНО от lesson mastery.
 *
 * [intervalStepIndex] индексирует лестницу дней интервала; [isLearned] становится
 * true при достижении порога изученности. Это самостоятельная схема повторения,
 * не зависящая от [LessonMastery].
 *
 * @property wordId            идентификатор слова ([VocabWord.id]).
 * @property intervalStepIndex индекс шага интервала (0-9) в лестнице дней.
 * @property correctCount      всего правильных ответов.
 * @property incorrectCount    всего неправильных ответов.
 * @property lastReviewDateMs  epoch-мс последнего повторения.
 * @property nextReviewDateMs  epoch-мс, когда слово снова нужно повторить (0 — сразу).
 * @property isLearned         достигнут ли порог изученности (intervalStepIndex >= 3).
 */
data class WordMasteryState(
    val wordId: String,
    val intervalStepIndex: Int = 0, // 0-9, индекс в INTERVAL_LADDER_DAYS
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val lastReviewDateMs: Long = 0L,
    val nextReviewDateMs: Long = 0L,
    val isLearned: Boolean = false, // intervalStepIndex >= LEARNED_THRESHOLD(3)
) {
    companion object {
        /** Миллисекунд в сутках — константа для расчёта дат интервального повторения. */
        const val DAY_MS: Long = 86_400_000L
    }
}

/**
 * Карточка vocab drill — слово + его SRS-состояние.
 *
 * @property word    лексическое слово.
 * @property mastery текущее SRS-состояние слова.
 */
data class VocabDrillCard(
    val word: VocabWord,
    val mastery: WordMasteryState,
)

/**
 * Vocab entry — простая запись словаря урока (lesson-vocab).
 *
 * В отличие от [VocabWord], это плоская пара «как есть» для конкретного урока
 * без ранга/форм/коллокаций.
 *
 * @property id          идентификатор записи.
 * @property lessonId    урок, к которому привязана запись.
 * @property languageId  язык обучения.
 * @property nativeText  текст на родном языке пользователя.
 * @property targetText  текст на изучаемом языке.
 * @property isHard      пометка «сложное» слово.
 */
data class VocabEntry(
    val id: String,
    val lessonId: LessonId,
    val languageId: LanguageId,
    val nativeText: String,
    val targetText: String,
    val isHard: Boolean = false,
)

/**
 * Состояние vocab drill сессии.
 *
 * @property cards               карточки сессии (слово + mastery).
 * @property currentIndex        индекс текущей карточки.
 * @property correctCount        счётчик правильных ответов.
 * @property incorrectCount      счётчик неправильных ответов.
 * @property isComplete          завершена ли сессия.
 * @property isFlipped           перевёрнута ли карточка (показан ответ).
 * @property direction           направление тренировки (см. [VocabDrillDirection]).
 * @property voiceAttempts       число голосовых попыток (0-3).
 * @property voiceRecognizedText распознанный голосом текст (для предпросмотра), либо null.
 * @property voiceResult         результат голосовой проверки (CORRECT/WRONG/SKIPPED), либо null.
 * @property voiceCompleted      завершён ли голосовой ввод (верно / 3 ошибки / пропуск).
 */
data class VocabDrillSessionState(
    val cards: List<VocabDrillCard>,
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val isComplete: Boolean = false,
    val isFlipped: Boolean = false,
    val direction: VocabDrillDirection = VocabDrillDirection.IT_TO_RU,
    val voiceAttempts: Int = 0,
    val voiceRecognizedText: String? = null,
    val voiceResult: VoiceResult? = null,
    val voiceCompleted: Boolean = false,
)

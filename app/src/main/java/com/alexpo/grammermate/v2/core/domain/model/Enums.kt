package com.alexpo.grammermate.v2.core.domain.model

/**
 * Перечисления и sealed-типы домена — чистый Kotlin, без Android-зависимостей.
 *
 * Сюда вынесены все замкнутые множества значений (статусы, режимы, типы), а
 * также дискретные состояния (TTS/ASR/Download жизненные циклы, ошибки парсинга),
 * используемые моделями контента, прогресса, сессий и аудио. Enum'ы и sealed
 * interface'ы — это стабильный контракт домена, на который опираются data- и
 * ui-слои. Синглтоны внутри sealed-иерархий объявлены как [data object],
 * варианты с данными — как [data class].
 */

/** Режим тренировки внутри пака. */
enum class TrainingMode { LESSON, ALL_SEQUENTIAL, ALL_MIXED }

/** Жизненный цикл сессии тренировки. */
enum class SessionStatus { ACTIVE, PAUSED, COMPLETED }

/** Текущее состояние шага сессии (активно / показана подсказка). */
enum class SessionState { ACTIVE, HINT_SHOWN }

/** Способ ввода ответа пользователем. */
enum class InputMode { VOICE, KEYBOARD, WORD_BANK }

/** Тип «босса» — контрольной точки в обучении. */
enum class BossType { LESSON, MEGA, ELITE }

/**
 * Награда за босса. [pct] — порог правильных ответов (в процентах),
 * необходимый для получения данного уровня.
 */
enum class BossReward(val pct: Int) { BRONZE(30), SILVER(60), GOLD(90) }

/** Уровень сложности подсказки. */
enum class HintLevel { EASY, MEDIUM, HARD }

/** Режим оформления приложения. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Тип практического упражнения. */
enum class PracticeType { TRANSLATION, VOCAB, VERB }

/** Оценка в алгоритме интервального повторения (SRS). */
enum class SrsRating { AGAIN, HARD, GOOD, EASY }

/** Оценка сложности конкретной карточки пользователем. */
enum class CardDifficultyRating { AGAIN, HARD, GOOD, EASY }

/** Тип блока в дневной норме. */
enum class DailyBlockType { TRANSLATE, VOCAB, VERBS }

/** Фаза истории (сторителлинга): начало / завершение урока. */
enum class StoryPhase { CHECK_IN, CHECK_OUT }

/**
 * Пресет помодоро-таймера. [minutes] — длительность фокуса в минутах.
 */
enum class PomodoroPreset(val minutes: Int) { QUICK(5), FOCUS(15), CLASSIC(20) }

/** Пометка фонового словарного слова. */
enum class BgVocabMark { NONE, GREEN, RED }

/** Направление drill-тренировки словаря. */
enum class VocabDrillDirection { IT_TO_RU, RU_TO_IT }

/** Тип карточки. */
enum class CardType { SENTENCE, VERB_DRILL, AUX_DRILL }

/**
 * Режим экрана тренировки — определяет состав пула и логику прохождения.
 *
 * Нормальные режимы, боссы, контрольные точки и виды дневных/специальных drill.
 * [AUX_DRILL] — тренировка вспомогательных (aux) глаголов.
 */
enum class TrainingScreenMode {
    NORMAL,
    BOSS,
    BOSS_MEGA,
    ELITE,
    VERB_DRILL,
    DAILY_TRANSLATE,
    DAILY_VERBS,
    AUX_DRILL,
}

/**
 * Действие перехода после завершения подурока/урока.
 *
 * Решает, что предложить пользователю по кнопке «далее» на экране завершения.
 */
enum class CompletionNextAction { NEXT_SUB_LESSON, NEXT_LESSON, NONE }

/**
 * Как рендерить блок дневной нормы. Вычисляется из [DailyBlockType]:
 *  - TRANSLATE/VERBS → [TRAINING_SCREEN] (отдельный экран тренировки);
 *  - VOCAB → [INLINE] (встроенная флешкарта без смены экрана).
 *
 * Хранится как enum, чтобы состояние было явным и сериализуемым.
 */
enum class BlockRenderVia { TRAINING_SCREEN, INLINE }

/**
 * Тип подурока: только новые карточки либо смесь новых и повторения.
 */
enum class SubLessonType { NEW_ONLY, MIXED }

/**
 * Результат проверки голосового ответа.
 *
 * SKIPPED — пользователь пропустил карточку без ответа.
 */
enum class VoiceResult { CORRECT, WRONG, SKIPPED }

/**
 * Состояние жизненного цикла ASR (распознавание речи).
 */
enum class AsrState { IDLE, INITIALIZING, READY, RECORDING, RECOGNIZING, ERROR }

/**
 * Единая severity ошибок домена.
 *
 * Объединяет дублированные в v1 перечисления (WARN/ERROR/FATAL) в один контракт.
 */
enum class ErrorSeverity { WARNING, ERROR, CRITICAL }

/**
 * Тип модели распознавания речи (ASR). В v1 использовался только Whisper.
 */
enum class AsrModelType { WHISPER }

/**
 * Тип модели синтеза речи (TTS).
 *
 * KOKORO — основной движок; VITS_PIPER — альтернатива (включая pack-модели).
 */
enum class TtsModelType { KOKORO, VITS_PIPER }

/**
 * Состояние TTS-движка как sealed interface: синглтоны — [data object],
 * ошибка — [data class] с причиной.
 *
 * UI коллектит это состояние, чтобы показывать индикатор/сообщение об ошибке.
 */
sealed interface TtsState {
    /** Движок не инициализирован / выгружен. */
    data object Idle : TtsState

    /** Идёт загрузка/инициализация нативной модели. */
    data object Initializing : TtsState

    /** Модель загружена, готов к озвучиванию. */
    data object Ready : TtsState

    /** Идёт озвучивание текста. */
    data object Speaking : TtsState

    /** Озвучивание приостановлено (пауза). */
    data object Paused : TtsState

    /**
     * Ошибка TTS.
     *
     * @property reason человекочитаемое описание причины (для лога/UI), либо null.
     */
    data class Error(val reason: String? = null) : TtsState
}

/**
 * Фаза инициализации аудио-модели — детализация прогресса внутри [DownloadState.Initializing].
 */
enum class InitPhase { CHECKING_FILES, LOADING_MODEL, PREPARING_ENGINE }

/**
 * Состояние скачивания/подготовки модели как sealed interface.
 *
 * Описывает полный конвейер: скачивание → распаковка → инициализация движка → готово.
 */
sealed interface DownloadState {
    /** Ничего не скачивается / модель отсутствует. */
    data object Idle : DownloadState

    /**
     * Идёт скачивание.
     *
     * @property percent прогресс 0..100.
     * @property bytesDownloaded сколько байт скачано.
     * @property totalBytes полный размер (0 — если неизвестен).
     */
    data class Downloading(
        val percent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : DownloadState

    /**
     * Идёт распаковка скачанного архива.
     *
     * @property percent прогресс 0..100.
     */
    data class Extracting(val percent: Int) : DownloadState

    /**
     * Идёт инициализация движка по фазам ([InitPhase]).
     *
     * @property phase текущая фаза инициализации.
     * @property percent общий прогресс инициализации 0..100.
     */
    data class Initializing(
        val phase: InitPhase,
        val percent: Int,
    ) : DownloadState

    /** Модель скачана и готова к использованию. */
    data object Done : DownloadState

    /**
     * Ошибка на любом этапе конвейера.
     *
     * @property message человекочитаемое сообщение об ошибке.
     */
    data class Error(val message: String) : DownloadState
}

/**
 * Слот озвучивания — «что именно сказать» в составном TTS-промпте.
 *
 * Используется планировщиком озвучки для последовательного воспроизведения
 * нескольких элементов (слово IT, перевод RU, коллокации, предложения).
 */
sealed interface SpeakSlot {
    /** Итальянское (целевое) слово. */
    data object WordIt : SpeakSlot

    /** Русский перевод слова. */
    data object WordRu : SpeakSlot

    /** Коллокация на целевом языке. */
    data object ColloIt : SpeakSlot

    /** Перевод коллокации. */
    data object ColloRu : SpeakSlot

    /**
     * Предложение на целевом языке по индексу в списке.
     *
     * @property index индекс предложения (0-based).
     */
    data class SentenceIt(val index: Int) : SpeakSlot

    /**
     * Перевод предложения по индексу.
     *
     * @property index индекс предложения (0-based).
     */
    data class SentenceRu(val index: Int) : SpeakSlot
}

/**
 * Сегмент композиционной TTS-очереди — атомарная единица воспроизведения.
 *
 * Чистый домен: путь к аудиофайлу хранится как [String], а НЕ как `java.io.File`
 * (domain не зависит от платформенных IO-типов). Слой данных/resolves путь в
 * `java.io.File` при необходимости.
 */
sealed interface Segment {
    /**
     * Текстовый сегмент — синтезируется через TTS.
     *
     * @property text текст для озвучивания.
     * @property languageId язык синтеза.
     */
    data class Text(val text: String, val languageId: String) : Segment

    /**
     * Пауза между сегментами.
     *
     * @property ms длительность паузы в миллисекундах.
     */
    data class Pause(val ms: Long) : Segment

    /**
     * Готовый аудиофрагмент (предсинтезированный/SFX).
     *
     * @property file путь к аудиофайлу (не `java.io.File` — domain чистый).
     * @property languageId язык фрагмента (для маршрутизации модели).
     */
    data class Audio(val file: String, val languageId: String) : Segment
}

/**
 * Ошибка разбора контента пака как sealed interface.
 *
 * Каждая ошибка несёт общие атрибуты ([lineNumber], [severity], [message]);
 * конкретные варианты уточняют контекст. [WithFileContext] оборачивает любую
 * ошибку, добавляя имя файла, в котором она возникла.
 */
sealed interface ParseError {
    /** Номер строки (1-based), где возникла ошибка; 0 — если строк нет (файл и т. п.). */
    val lineNumber: Int

    /** Серьёзность ошибки. */
    val severity: ErrorSeverity

    /** Человекочитаемое сообщение. */
    val message: String

    /**
     * Строка не соответствует ожидаемому формату.
     *
     * @property lineNumber номер строки.
     * @property expected что ожидалось.
     * @property actual что найдено по факту.
     */
    data class MalformedLine(
        override val lineNumber: Int,
        val expected: String,
        val actual: String,
    ) : ParseError {
        override val severity: ErrorSeverity = ErrorSeverity.ERROR
        override val message: String = "Malformed line $lineNumber: expected '$expected', got '$actual'"
    }

    /**
     * Пустой файл / файл без распознаваемого контента.
     */
    data object EmptyFile : ParseError {
        override val lineNumber: Int = 0
        override val severity: ErrorSeverity = ErrorSeverity.WARNING
        override val message: String = "File is empty"
    }

    /**
     * Неверный формат записи на строке.
     *
     * @property lineNumber номер строки.
     * @property reason пояснение, почему формат некорректен.
     */
    data class InvalidFormat(
        override val lineNumber: Int,
        val reason: String,
    ) : ParseError {
        override val severity: ErrorSeverity = ErrorSeverity.ERROR
        override val message: String = "Invalid format at line $lineNumber: $reason"
    }

    /**
     * Декоратор: добавляет имя файла к любой [ParseError].
     *
     * @property fileName имя файла, в котором возникла [wrappedError].
     * @property wrappedError исходная ошибка.
     */
    data class WithFileContext(
        val fileName: String,
        val wrappedError: ParseError,
    ) : ParseError {
        override val lineNumber: Int = wrappedError.lineNumber
        override val severity: ErrorSeverity = wrappedError.severity
        override val message: String = "${wrappedError.message} (in '$fileName')"
    }
}

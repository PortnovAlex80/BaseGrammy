package com.alexpo.grammermate.v2.core.data.audio

import java.io.File

/**
 * Упорядоченный сегмент потока воспроизведения для [SegmentPlayer].
 *
 * Канонический контракт **SRS-003 §5.1** (shared-mutation-risk: E03 хост,
 * E08 bg-vocab, E09 story narration). Сигнатура зафиксирована; изменение после
 * старта E08/E09 = drift-задача (см. SRS-003 §5).
 *
 * Перенос из legacy `MultilingualStoryParser.Segment` (v1) в data-слой v2.
 * E09 (story-domain) может позже вынести этот тип в домен — на момент E03 он
 * живёт рядом с [SegmentPlayer], единственным потребителем в этом эпике.
 *
 * Добавление нового типа сегмента = новая ветка в `when (segment)` в
 * [SegmentPlayer.playSegments]. Не плодить dispatcher'ы поверх.
 *
 * @see SegmentPlayer.playSegments единственное место потребления.
 */
sealed interface Segment {
    /** ISO 639-1 код языка сегмента (для маршрутизации в TTS-движок). */
    val languageId: String

    /**
     * Текстовый сегмент — озвучивается через [TtsEngineWrapper.speak] после
     * markdown-очистки; на паузе (после завершения) переозвучивается с начала.
     */
    data class Text(override val languageId: String, val text: String) : Segment

    /**
     * Пауза между сегментами — кооперативная `delay(ms)`, cancellable.
     * На паузе (после завершения) ожидает resume и **advance** (не переозвучивается).
     */
    data class Pause(val ms: Long) : Segment {
        override val languageId: String get() = ""
    }

    /**
     * Предрендеренный аудио-клип (`.wav`) — играется через `MediaPlayer`.
     * Если файл отсутствует — log + advance (без TTS-fallback: Audio не несёт текста).
     * На паузе (после завершения) переигрывается с начала.
     */
    data class Audio(override val languageId: String, val file: File) : Segment
}

package com.alexpo.grammermate.v2.core.data.audio

import android.media.MediaPlayer
import android.util.Log
import com.alexpo.grammermate.v2.core.domain.model.TtsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.coroutines.resume

/**
 * Реusable sequential playback engine для потоков [Segment] (text + pauses +
 * pre-rendered audio). Сериализует concurrent callers через внутренний [Mutex];
 * own pause/resume flag — НЕ владеет (он втекает через `isPaused` callback в
 * [playSegments]).
 *
 * **Канонический контракт SRS-003 §5 (shared-mutation-risk).** Shared dependency
 * E03 (хост), E08 (bg-vocab), E09 (story narration). Все три эпика **только
 * вызывают** [playSegments]; они НЕ мутируют сигнатуру и НЕ владеют внутренним
 * состоянием. Контракт зафиксирован в SRS-003 §5; изменение после старта E08/E09
 * = drift-задача, НЕ правка здесь.
 *
 * Перенос из legacy `shared/audio/SegmentPlayer.kt` (v1). Body-задача AC-15+AC-16
 * закрывает оставшиеся TODO (переозвучивание текущего сегмента на паузе уже в
 * логике ниже; Mutex serialization уже реализована).
 *
 * @param ttsEngine singleton Sherpa-ONNX TTS-обёртка (resident LRU cache).
 */
class SegmentPlayer(private val ttsEngine: TtsEngineWrapper) {

    /**
     * Сериализует concurrent callers. Player владеет TTS-доступом для своих
     * consumers, поэтому self-protect: даже если две корутины вызовут
     * [playSegments] одновременно, они выстроятся в очередь, а не будут
     * соревноваться за native model state.
     */
    private val mutex = Mutex()

    /**
     * Итерировать [segments] последовательно, озвучивая каждый [Segment.Text]
     * через TTS-движок и выжидая [Segment.Pause.ms] для каждой паузы.
     *
     * Per-segment контракт (перенос из legacy AudioCoordinator loop, идентично v1):
     *
     * - [Segment.Pause]: кооперативно `delay(ms)` (cancellable). Если [isPaused]
     *   стал true после паузы — ждать resume, затем ADVANCE (паузы никогда не
     *   переозвучиваются).
     * - [Segment.Text]: ensure TTS Ready для языка сегмента (initialize —
     *   cheap fast-path на резидентной модели), озвучить markdown-cleaned text
     *   на [speed], poll пока выйдет из `TtsState.Speaking`. Если [isPaused]
     *   стал true после сегмента — ждать resume и **переозвучить ТОТ ЖЕ сегмент**
     *   (continue, БЕЗ advance индекса).
     * - [Segment.Audio]: play referenced `.wav` через [MediaPlayer]. Если файл
     *   отсутствует — log + advance (без TTS-fallback: Audio не несёт текста).
     *   Тот же re-play-on-resume UX как у Text-ветки.
     *
     * Cancellation-safe: все ожидания используют [delay] (cooperative with
     * structured concurrency). Concurrent callers сериализуются через Mutex.
     *
     * @param segments       ordered segment stream (text + pauses + audio).
     * @param speed          TTS playback speed multiplier, forwarded to ttsEngine.
     * @param isPaused       возвращает true когда caller хочет suspend playback.
     * @param onSegmentStart optional per-segment callback (index, segment) для UI hooks.
     */
    suspend fun playSegments(
        segments: List<Segment>,
        speed: Float,
        isPaused: () -> Boolean,
        onSegmentStart: (Int, Segment) -> Unit = { _, _ -> },
    ) = mutex.withLock {
        var currentSegmentIdx = 0
        while (currentSegmentIdx < segments.size) {
            val segment = segments[currentSegmentIdx]
            onSegmentStart(currentSegmentIdx, segment)

            when (segment) {
                is Segment.Pause -> {
                    Log.d(TAG, "Segment $currentSegmentIdx/${segments.size} | Pause ${segment.ms}ms")
                    delay(segment.ms)

                    // После паузы: если playback был paused — ждать resume и
                    // продолжить. Паузы не переозвучиваются (нет аудио).
                    if (isPaused()) {
                        Log.d(TAG, "Paused after pause-segment $currentSegmentIdx — waiting for resume")
                        while (isPaused()) {
                            delay(100)
                        }
                        Log.d(TAG, "Resuming after pause-segment $currentSegmentIdx")
                    }
                    currentSegmentIdx++
                }

                is Segment.Text -> {
                    val cleanText = cleanMarkdown(segment.text)
                    val previewText = cleanText.take(50).replace("\n", "\\n")

                    Log.d(TAG, "Segment $currentSegmentIdx/${segments.size} | Language: ${segment.languageId.uppercase()}")
                    Log.d(TAG, "Text preview: \"$previewText...\"")

                    // Инициализировать TTS под язык сегмента если нужно.
                    // С resident LRU cache initialize() fast-path'ит резидентный
                    // язык — дёшево на повторах.
                    if (ttsEngine.state.value != TtsState.Ready
                        || ttsEngine.activeLanguageId != segment.languageId
                    ) {
                        Log.d(TAG, "-> Initializing TTS for: ${segment.languageId}")
                        ttsEngine.initialize(segment.languageId)
                    }

                    // Ждать готовности TTS.
                    var retries = 0
                    while (ttsEngine.state.value != TtsState.Ready && retries < 50) {
                        delay(100)
                        retries++
                    }

                    if (ttsEngine.state.value == TtsState.Ready) {
                        Log.d(TAG, "-> Speaking segment $currentSegmentIdx (len=${cleanText.length})")
                        ttsEngine.speak(cleanText, languageId = segment.languageId, speed = speed)

                        // Ждать пока сегмент проиграется.
                        var waitRetries = 0
                        while (ttsEngine.state.value == TtsState.Speaking && waitRetries < 3000) {
                            delay(100)
                            waitRetries++
                        }
                        Log.d(TAG, "Segment $currentSegmentIdx finished (${segment.languageId.uppercase()})")
                    } else {
                        Log.w(TAG, "TTS not ready for segment $currentSegmentIdx, skipping")
                    }

                    // После сегмента: проверка паузы. Если paused во время/после —
                    // ждать resume и переозвучить ТОТ ЖЕ сегмент (continue, без advance).
                    if (isPaused()) {
                        Log.d(TAG, "Paused after segment $currentSegmentIdx — waiting for resume to re-speak")
                        while (isPaused()) {
                            delay(100)
                        }
                        Log.d(TAG, "Resuming — re-speaking segment $currentSegmentIdx")
                        continue
                    }

                    currentSegmentIdx++
                }

                is Segment.Audio -> {
                    // Pre-rendered clip. Если файл есть — играть через MediaPlayer,
                    // зеркалируя Text-ветку pause/resume UX. Если отсутствует —
                    // log + advance (без TTS-fallback: Audio не несёт текста).
                    val file = segment.file
                    if (!file.exists() || !file.canRead()) {
                        Log.w(TAG, "Audio file missing, skipping: $file")
                        currentSegmentIdx++
                    } else {
                        Log.d(TAG, "Segment $currentSegmentIdx/${segments.size} | Audio: ${file.name} (${segment.languageId.uppercase()})")
                        playAudioFile(file)

                        // Тот же re-play-on-resume UX как у Text-ветки.
                        if (isPaused()) {
                            Log.d(TAG, "Paused after audio-segment $currentSegmentIdx — waiting for resume to replay")
                            while (isPaused()) {
                                delay(100)
                            }
                            Log.d(TAG, "Resuming — replaying audio-segment $currentSegmentIdx")
                            continue
                        }
                        currentSegmentIdx++
                    }
                }
            }
        }
    }

    /**
     * Play [file] через [MediaPlayer], suspending пока клип не завершится/не упадёт.
     *
     * Player создаётся, готовится, стартует и освобождается целиком внутри вызова —
     * ни один MediaPlayer не выходит за пределы функции (нет утечки). На cancellation
     * корутины in-flight [MediaPlayer] освобождается best-effort (`release()` идемпотентен).
     *
     * Перенос из legacy `shared/audio/SegmentPlayer.playAudioFile`. NFR-5
     * (cancellation safety).
     */
    private suspend fun playAudioFile(file: File) = suspendCancellableCoroutine { cont ->
        val player = MediaPlayer()
        // Safety net: при cancellation корутины teardown native state.
        cont.invokeOnCancellation { runCatching { player.release() } }

        try {
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                player.release()
                if (cont.isActive) cont.resume(Unit)
            }
            player.setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra on $file")
                runCatching { mp.release() }
                if (cont.isActive) cont.resume(Unit)
                true
            }
            player.prepare()
            player.start()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to play audio clip $file", t)
            runCatching { player.release() }
            if (cont.isActive) cont.resume(Unit)
        }
    }

    /**
     * Markdown-cleaning: убрать разметку перед озвучиванием текста.
     *
     * Перенос из legacy `MultilingualStoryParser.cleanMarkdown`. TODO(body): при
     * миграции parser'а в домен (E09) — вызывать доменную версию. Пока дублируем
     * минимальную очистку inline.
     */
    private fun cleanMarkdown(text: String): String =
        text
            .replace(Regex("([*_~`]{1,3})(.+?)\\1"), "$2")  // **bold**, *em*, _em_, ~strike~, `code`
            .replace(Regex("^#{1,6}\\s+"), "")               // ATX headings
            .replace(Regex("^\\s{0,3}[-*+]\\s+", RegexOption.MULTILINE), "") // bullet lists
            .trim()

    companion object {
        private const val TAG = "SegmentPlayer"
    }
}

package com.alexpo.grammermate.v2.core.data.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.alexpo.grammermate.domain.audio.RecognitionEvent
import com.alexpo.grammermate.domain.model.AsrState
import com.alexpo.grammermate.domain.model.LanguageId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit-тесты на [SherpaAudioRepository.recognizeSpeech] — AC-8 (CRITICAL
 * regression-anchor): `Flow<RecognitionEvent>` эмитит полную цепочку
 * `ListeningStarted → (Partial ≥ 0) → EndpointDetected → Final(text)`,
 * **НЕ** `Started → Failed`.
 *
 * Тестируется адаптер (`SherpaAudioRepository`) с **fake AsrEngine** (AC-8 DoD:
 * "adapter unit test with fake AsrEngine emits full chain up to Final"). Нативный
 * Sherpa-ONNX/AudioRecord в unit-тестах недоступен — поэтому движок подменяется
 * фейком через шов [AsrEnginePort] (SRS-003 §2.4). Реальная capture/decode-логика
 * покрывается AC-9/AC-10 (native).
 *
 * @see <a href="../../../../../../../../../../docs/requirements/REQ-003-audio/03-acceptance-criteria.md#AC-8">AC-8</a>
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.13 supports up to API 34; compileSdk=35 не имеет android-all jar.
class SherpaAudioRepositoryRecognizeSpeechTest {

    /**
     * Fake движок для AC-8: эмитит полную цепочку событий (Partial → Endpoint →
     * Final) в заданном порядке. Записывает язык, переданный в `setLanguage`,
     * чтобы тест мог проверить, что репозиторий синхронизирует язык до стрима.
     */
    private class FakeAsrEngine(
        private val events: List<RecognitionEvent>,
        initialState: AsrState = AsrState.READY,
    ) : AsrEnginePort {
        val setLanguageCalls = mutableListOf<String>()
        override val state: StateFlow<AsrState> = MutableStateFlow(initialState)
        override val isReady: Boolean get() = state.value == AsrState.READY

        override suspend fun initialize(language: String) {}
        override fun setLanguage(language: String) {
            setLanguageCalls += language
        }

        override fun streamRecognition(maxDurationMs: Long): Flow<RecognitionEvent> = flow {
            events.forEach { emit(it) }
        }

        override fun stopRecording() {}
        override fun release() {}
    }

    /**
     * Минимальный SherpaAudioRepository с подменённым fake-движком. Реальный
     * конструктор требует Context/SoundPool (Android); для AC-8 мы изолируем
     * только `recognizeSpeech`, поэтому создаём репозиторий напрямую. Под
     * Robolectric `ApplicationProvider` даёт рабочий `Context`, а зависимые
     * обёртки (ttsEngine/memoryChecker/segmentPlayer) в этих тестах не
     * вызываются — `recognizeSpeech` трогает только `asrEngine`.
     */
    private fun repository(fake: AsrEnginePort): SherpaAudioRepository {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val memoryChecker = MemoryChecker(ctx)
        val ttsEngine = TtsEngineWrapper(ctx, memoryChecker)
        val segmentPlayer = SegmentPlayer(ttsEngine)
        return SherpaAudioRepository(
            context = ctx,
            ttsEngine = ttsEngine,
            asrEngine = fake,
            memoryChecker = memoryChecker,
            segmentPlayer = segmentPlayer,
        )
    }

    @Test
    fun `recognizeSpeech emits full chain ListeningStarted then Partial EndpointDetected Final`() = runTest {
        // AC-8 etalon: произнесена фраза "cat" → ListeningStarted → Partial →
        // EndpointDetected → Final("cat"). Fake эмитит ровно цепочку движка;
        // репозиторий добавляет вперед ListeningStarted.
        val fake = FakeAsrEngine(
            events = listOf(
                RecognitionEvent.Partial("ca"),
                RecognitionEvent.Partial("cat"),
                RecognitionEvent.EndpointDetected,
                RecognitionEvent.Final("cat"),
            ),
        )
        val repo = repository(fake)

        repo.recognizeSpeech(LanguageId("en")).test {
            val first = awaitItem()
            assertThat(first).isEqualTo(RecognitionEvent.ListeningStarted)

            assertThat(awaitItem()).isEqualTo(RecognitionEvent.Partial("ca"))
            assertThat(awaitItem()).isEqualTo(RecognitionEvent.Partial("cat"))
            assertThat(awaitItem()).isEqualTo(RecognitionEvent.EndpointDetected)
            assertThat(awaitItem()).isEqualTo(RecognitionEvent.Final("cat"))

            awaitComplete()
        }

        // Репозиторий должен синхронизировать язык до старта стрима (FR-6).
        assertThat(fake.setLanguageCalls).containsExactly("en")
    }

    @Test
    fun `recognizeSpeech with zero Partials still reaches EndpointDetected then Final`() = runTest {
        // AC-8 допускает Partial ≥ 0: движок без streaming-гипотез (legacy batch
        // decode) всё равно проходит EndpointDetected → Final. Это NOT Started→Failed.
        val fake = FakeAsrEngine(
            events = listOf(
                RecognitionEvent.EndpointDetected,
                RecognitionEvent.Final("cat"),
            ),
        )
        val repo = repository(fake)

        repo.recognizeSpeech(LanguageId("en")).test {
            assertThat(awaitItem()).isEqualTo(RecognitionEvent.ListeningStarted)
            assertThat(awaitItem()).isEqualTo(RecognitionEvent.EndpointDetected)
            assertThat(awaitItem()).isEqualTo(RecognitionEvent.Final("cat"))
            awaitComplete()
        }
    }

    @Test
    fun `recognizeSpeech event order is ListeningStarted before any engine event`() = runTest {
        val fake = FakeAsrEngine(
            events = listOf(RecognitionEvent.Final("hi")),
        )
        val repo = repository(fake)

        repo.recognizeSpeech(LanguageId("it")).test {
            val first = awaitItem()
            assertThat(first).isInstanceOf(RecognitionEvent.ListeningStarted::class.java)
            assertThat(awaitItem()).isEqualTo(RecognitionEvent.Final("hi"))
            awaitComplete()
        }
    }

    @Test
    fun `recognizeSpeech propagates engine Failed after ListeningStarted when model missing`() = runTest {
        // AC-9 boundary: репозиторий всё равно эмитит ListeningStarted, затем
        // движок сообщает Failed (model_missing). Не Started→Failed — а
        // ListeningStarted→Failed (сессия началась, движок не смог).
        val failure = RecognitionEvent.Failed(IllegalStateException("model_missing"))
        val fake = FakeAsrEngine(events = listOf(failure))
        val repo = repository(fake)

        repo.recognizeSpeech(LanguageId("en")).test {
            assertThat(awaitItem()).isEqualTo(RecognitionEvent.ListeningStarted)
            val failed = awaitItem()
            assertThat(failed).isInstanceOf(RecognitionEvent.Failed::class.java)
            awaitComplete()
        }
    }

    @Test
    fun `recognizeSpeech propagates language to engine setLanguage`() = runTest {
        val fake = FakeAsrEngine(events = listOf(RecognitionEvent.Final("ciao")))
        val repo = repository(fake)

        repo.recognizeSpeech(LanguageId("it")).test {
            awaitItem() // ListeningStarted
            awaitItem() // Final
            awaitComplete()
        }
        assertThat(fake.setLanguageCalls).containsExactly("it")
    }
}

package com.alexpo.grammermate.v2.core.data.audio

import android.app.Application
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit-тест AC-13 (CRITICAL): MemoryChecker — hard block при нехватке памяти,
 * процесс НЕ падает с OOM.
 *
 * **AC-13 etalon (regression-lock):**
 * - Given `MemoryChecker.hasEnoughMemory(...)` вернёт `false`
 *   (mocked: `availableTotalMb < requiredMb × 1.5`);
 * - When инициируется load TTS/ASR;
 * - Then native-load **не вызывается**, возвращается предсказуемый блокирующий
 *   статус ([MemoryCheckResult.Blocked] с каноническим сообщением), процесс жив
 *   (никакого OOM-crash).
 *
 * Канонические пороги (SRS-003 §FR-10, AC-13): TTS≈150 МБ, ASR≈800 МБ,
 * safetyMargin=1.5. Проверяем ровно эти значения, а не «достаточно/недостаточно».
 *
 * **Подход к hard-block симуляции.** Нативный load живёт в wrapper'ах
 * (TtsEngineWrapper/AsrEngineWrapper, separate ACs); здесь тестируется
 * **диспетчер блокировки** — [MemoryChecker] — который wrapper вызывает перед
 * нативным constructor'ом. Симуляция низкого `availMem` идёт через internal
 * test-seam [MemoryChecker.nativeAvailableMbProvider]: он по умолчанию читает
 * реальный `ActivityManager.MemoryInfo.availMem`, а тест подменяет его
 * детерминированным значением (МБ) — это reproduces production-формулу
 * `availableTotalMb = native(availMem) + heap(maxMemory − used)` без запуска
 * нативного кода и без хрупкой привязки к private ShadowActivityManager API.
 *
 * **AC-13 «native-load НЕ вызывается».** Сам нативный Sherpa-ONNX constructor
 * в unit-тест не подключается — wrapper'ы (TODO-body) вызывают его только при
 * `checkForLoad(...) == Enough`. Мы верифицируем, что при low-memory проверка
 * возвращает [MemoryCheckResult.Blocked] (а значит wrapper по контракту не
 * дойдёт до native constructor) — это и есть assertion «no native call» на
 * уровне gate. Поведенческий spy на конструкторе — в интеграционном тесте
 * wrapper'а (AC-2/AC-8).
 *
 * @see <a href="../../../../../../../../../../docs/requirements/REQ-003-audio/03-acceptance-criteria.md#AC-13">AC-13</a>
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.13 поддерживает до API 34; проект на compileSdk=35.
class MemoryCheckerHardBlockTest {

    private lateinit var checker: MemoryChecker

    @Before
    fun setUp() {
        checker = MemoryChecker(RuntimeEnvironment.getApplication() as Application)
    }

    @After
    fun tearDown() {
        // Снимаем переопределение seams, чтобы не протекло в другие тесты.
        checker.nativeAvailableMbProvider = { null }
        checker.heapAvailableMbProvider = realHeap
    }

    /** Реальный heap тестовой JVM — значение по умолчанию для seam'а. */
    private val realHeap: () -> Long = {
        val rt = Runtime.getRuntime()
        rt.maxMemory() / MemoryChecker.MB - (rt.totalMemory() - rt.freeMemory()) / MemoryChecker.MB
    }

    /**
     * Симуляция памяти устройства: выставляет доступную native (`availMem`) и
     * heap память (МБ), которые [MemoryChecker] читает через test-seams. Формула
     * проверки та же, что и в production: `availableTotalMb = native + heap`.
     * Полная детерминированность — без зависимости от heap-размера тестовой JVM.
     */
    private fun setMemory(availMb: Long, heapMb: Long = 0L) {
        checker.nativeAvailableMbProvider = { availMb }
        checker.heapAvailableMbProvider = { heapMb }
    }

    // ── AC-13: канонические пороги (FR-10 etalon) ────────────────────────

    /** TTS-порог — ровно 150 МБ (FR-10 / AC-13 etalon). */
    @Test
    fun `AC-13 canonical TTS required threshold is 150 MB`() {
        assertThat(MemoryChecker.REQUIRED_MB_TTS).isEqualTo(150L)
    }

    /** ASR-порог — ровно 800 МБ (FR-10 / AC-13 etalon). */
    @Test
    fun `AC-13 canonical ASR required threshold is 800 MB`() {
        assertThat(MemoryChecker.REQUIRED_MB_ASR).isEqualTo(800L)
    }

    /** safetyMargin по умолчанию — ровно ×1.5 (AC-13: `< requiredMb × 1.5`). */
    @Test
    fun `AC-13 canonical safety margin is 1_5`() {
        assertThat(MemoryChecker.DEFAULT_SAFETY_MARGIN).isEqualTo(1.5f)
    }

    /** Канонические сообщения-причины закреплены в API (AC-13 etalon). */
    @Test
    fun `AC-13 canonical block messages are stable`() {
        assertThat(MemoryChecker.TTS_BLOCK_MESSAGE)
            .isEqualTo("Not enough memory to load voice model")
        assertThat(MemoryChecker.ASR_BLOCK_MESSAGE)
            .isEqualTo("Not enough memory to load speech recognition model")
        assertThat(MemoryChecker.ModelKind.TTS.blockMessage).isEqualTo(MemoryChecker.TTS_BLOCK_MESSAGE)
        assertThat(MemoryChecker.ModelKind.ASR.blockMessage).isEqualTo(MemoryChecker.ASR_BLOCK_MESSAGE)
        assertThat(MemoryChecker.ModelKind.TTS.requiredMb).isEqualTo(MemoryChecker.REQUIRED_MB_TTS)
        assertThat(MemoryChecker.ModelKind.ASR.requiredMb).isEqualTo(MemoryChecker.REQUIRED_MB_ASR)
    }

    // ── AC-13: pure-граница hard-block (no Android) ──────────────────────

    /**
     * AC-13 boundary (pure, детерминированно): available == required×1.5 → enough
     * (граница включается); available на 1 МБ меньше → hard block.
     */
    @Test
    fun `AC-13 boundary at required times margin is enough`() {
        // requiredMb × 1.5 ровно → enough (>=).
        assertThat(checker.isEnough(availableTotalMb = 225L, requiredMb = 150L, safetyMargin = 1.5f)).isTrue()
        assertThat(checker.isEnough(availableTotalMb = 1200L, requiredMb = 800L, safetyMargin = 1.5f)).isTrue()
    }

    @Test
    fun `AC-13 one_mb_below_margin is hard block`() {
        // На 1 МБ меньше requiredMb × 1.5 → hard block.
        assertThat(checker.isEnough(availableTotalMb = 224L, requiredMb = 150L, safetyMargin = 1.5f)).isFalse()
        assertThat(checker.isEnough(availableTotalMb = 1199L, requiredMb = 800L, safetyMargin = 1.5f)).isFalse()
    }

    // ── AC-13: hard block через checkForLoad (typed signal) ──────────────

    /**
     * AC-13 TTS hard block: native availMem = 0 МБ (heap добавит мало) →
     * availableTotalMb < 150×1.5=225 → [Blocked] с каноническим TTS-сообщением.
     * Процесс не упал (тест дошёл до assert'а).
     */
    @Test
    fun `AC-13 TTS low memory returns Blocked with canonical message`() {
        setMemory(availMb = 0L) // ноль native — гарантированно < 225.

        val result = checker.checkForLoad(MemoryChecker.ModelKind.TTS)

        assertThat(result).isInstanceOf(MemoryCheckResult.Blocked::class.java)
        val blocked = result as MemoryCheckResult.Blocked
        assertThat(blocked.message).isEqualTo(MemoryChecker.TTS_BLOCK_MESSAGE)
        assertThat(blocked.modelKind).isEqualTo(MemoryChecker.ModelKind.TTS)
        assertThat(blocked.requiredMb).isEqualTo(150L)
        assertThat(blocked.safetyMargin).isEqualTo(1.5f)
        // availableMb = native(0) + heap(>0) — точно ниже порога 225.
        assertThat(blocked.availableMb).isLessThan(225L)
    }

    /**
     * AC-13 ASR hard block: native availMem = 0 МБ → availableTotalMb < 800×1.5=
     * 1200 → [Blocked] с каноническим ASR-сообщением. ASR тяжелее — нужен больший availMem.
     */
    @Test
    fun `AC-13 ASR low memory returns Blocked with canonical message`() {
        setMemory(availMb = 0L) // ноль native — гарантированно < 1200.

        val result = checker.checkForLoad(MemoryChecker.ModelKind.ASR)

        assertThat(result).isInstanceOf(MemoryCheckResult.Blocked::class.java)
        val blocked = result as MemoryCheckResult.Blocked
        assertThat(blocked.message).isEqualTo(MemoryChecker.ASR_BLOCK_MESSAGE)
        assertThat(blocked.modelKind).isEqualTo(MemoryChecker.ModelKind.ASR)
        assertThat(blocked.requiredMb).isEqualTo(800L)
        assertThat(blocked.safetyMargin).isEqualTo(1.5f)
        assertThat(blocked.availableMb).isLessThan(1200L)
    }

    /**
     * AC-13 позитивный путь: native availMem достаточно → [Enough], native load
     * разрешён (hard block НЕ срабатывает). Подтверждает, что gate не блокирует
     * нормальный load (false-positive regression).
     */
    @Test
    fun `AC-13 enough memory for TTS returns Enough`() {
        setMemory(availMb = 4096L) // 4 ГБ native → точно достаточно.

        val result = checker.checkForLoad(MemoryChecker.ModelKind.TTS)

        assertThat(result).isEqualTo(MemoryCheckResult.Enough)
    }

    @Test
    fun `AC-13 enough memory for ASR returns Enough`() {
        setMemory(availMb = 8192L) // 8 ГБ native → точно достаточно для ASR.

        val result = checker.checkForLoad(MemoryChecker.ModelKind.ASR)

        assertThat(result).isEqualTo(MemoryCheckResult.Enough)
    }

    /**
     * AC-13 `hasEnoughMemory` (legacy Boolean API) также блокирует при low-mem —
     * wrapper'ы, использующие булеву форму, получают тот же gate.
     */
    @Test
    fun `AC-13 hasEnoughMemory returns false below margin and true above`() {
        setMemory(availMb = 0L)
        assertThat(checker.hasEnoughMemory(requiredMb = 150L)).isFalse() // TTS, < 225.

        setMemory(availMb = 4096L)
        assertThat(checker.hasEnoughMemory(requiredMb = 150L)).isTrue() // TTS, plenty.
    }

    @Test
    fun `AC-13 hasEnoughMemoryForTts and ForAsr convenience use canonical thresholds`() {
        setMemory(availMb = 0L)
        assertThat(checker.hasEnoughMemoryForTts()).isFalse() // 0 < 225.
        assertThat(checker.hasEnoughMemoryForAsr()).isFalse() // 0 < 1200.

        setMemory(availMb = 300L)
        assertThat(checker.hasEnoughMemoryForTts()).isTrue() // 300 >= 225.
        assertThat(checker.hasEnoughMemoryForAsr()).isFalse() // 300 < 1200.

        setMemory(availMb = 8192L)
        assertThat(checker.hasEnoughMemoryForAsr()).isTrue() // 8192 >= 1200.
    }

    // ── AC-13: heap-only fallback (ActivityManager недоступен) ───────────

    /**
     * AC-13 heap-only fallback: если `ActivityManager` недоступен (native
     * provider вернул `null`), решение принимается только по heap. Жёсткая
     * симуляция через test-seam `nativeAvailableMbProvider` — детерминированно.
     * Heap в unit-тесте ограничен JVM (~256–512 МБ), поэтому ASR-порог (×1.5 =
     * 1200) стабильно блокируется — assert на блокируемый ASR (heap < 1200 МБ).
     */
    @Test
    fun `AC-13 heap-only fallback blocks ASR when ActivityManager unavailable`() {
        checker.nativeAvailableMbProvider = { null } // имитация: AM недоступен.
        // Детерминированно (было негерметично: полагалось на реальный heap
        // JVM < 1200 МБ — сломалось при maxHeapSize=2g в build.gradle).
        checker.heapAvailableMbProvider = { 512L }

        // ASR требует 800×1.5 = 1200 МБ — heap 512 МБ блокируется.
        val result = checker.checkForLoad(MemoryChecker.ModelKind.ASR)
        assertThat(result).isInstanceOf(MemoryCheckResult.Blocked::class.java)
        assertThat((result as MemoryCheckResult.Blocked).message).isEqualTo(MemoryChecker.ASR_BLOCK_MESSAGE)
        // Процесс жив — тест дошёл сюда без OOM.
    }

    // ── AC-13: процесс не падает (regression на OOM-crash) ───────────────

    /**
     * AC-13 «процесс жив»: повторный запрос после hard block не бросает, не
     * крашит — gate остаётся предсказуемым сигналом, а не исключением. Это
     * ключевая защита от OOM-crash: вместо падения — стабильный Blocked.
     */
    @Test
    fun `AC-13 repeated checks after hard block do not throw or crash`() {
        setMemory(availMb = 0L)

        val results = (1..5).map { checker.checkForLoad(MemoryChecker.ModelKind.TTS) }

        results.forEach { r ->
            assertThat(r).isInstanceOf(MemoryCheckResult.Blocked::class.java)
            assertThat((r as MemoryCheckResult.Blocked).message).isEqualTo(MemoryChecker.TTS_BLOCK_MESSAGE)
        }
        // Процесс не упал — тестовая JVM жива, assert дошёл до конца.
    }
}

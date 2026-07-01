package com.alexpo.grammermate.v2.core.domain.srs

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import kotlin.math.pow

/**
 * Регрессионные тесты [SrsParams] — статического контракта параметров FSRS v6.
 *
 * Гарантирует, что «база» алгоритма (веса, retention, интервал, decay, factor)
 * соответствует спецификации и не «уплывает» при рефакторинге. Любое изменение
 * этих констант должно быть осознанным и пойманным этим тестом.
 */
class SrsParamsTest {

    @Test
    fun `DEFAULT_WEIGHTS has exactly 21 parameters (FSRS v6)`() {
        assertThat(SrsParams.DEFAULT_WEIGHTS).hasSize(SrsParams.WEIGHT_COUNT)
        assertThat(SrsParams.WEIGHT_COUNT).isEqualTo(21)
    }

    @Test
    fun `DEFAULT_WEIGHTS has no NaN or Infinity`() {
        SrsParams.DEFAULT_WEIGHTS.forEachIndexed { idx, value ->
            assertWithMessage("w[$idx] must not be NaN").that(value.isNaN()).isFalse()
            assertWithMessage("w[$idx] must be finite").that(value.isInfinite()).isFalse()
        }
    }

    @Test
    fun `default requestRetention is 0_9`() {
        assertThat(SrsParams().requestRetention).isEqualTo(SrsParams.DEFAULT_REQUEST_RETENTION)
        assertThat(SrsParams.DEFAULT_REQUEST_RETENTION).isEqualTo(0.9)
    }

    @Test
    fun `default maximumInterval is 36500 days`() {
        assertThat(SrsParams().maximumInterval).isEqualTo(SrsParams.DEFAULT_MAXIMUM_INTERVAL)
        assertThat(SrsParams.DEFAULT_MAXIMUM_INTERVAL).isEqualTo(36500)
    }

    @Test
    fun `decay equals negative w20`() {
        val params = SrsParams()
        assertThat(params.decay).isEqualTo(-params.w[20])
        // в дефолтных весах w[20] = DEFAULT_DECAY = 0.1542
        assertThat(params.decay).isEqualTo(-SrsParams.DEFAULT_DECAY)
        assertThat(SrsParams.DEFAULT_DECAY).isEqualTo(0.1542)
    }

    /**
     * factor = 0.9^(1/decay) − 1 — опорная константа формы кривой.
     * При decay = −0.1542 эталон ≈ 0.98034649. Проверка гарантирует, что
     * определение factor не поменялось (именно оно даёт R(S)=0.9).
     */
    @Test
    fun `factor matches 0_9 power formula`() {
        val params = SrsParams()
        val expected = 0.9.pow(1.0 / params.decay) - 1.0
        assertThat(params.factor).isWithin(1e-12).of(expected)
        assertThat(params.factor).isWithin(1e-6).of(0.98034649)
    }

    @Test
    fun `constructor rejects wrong weight count`() {
        val tooFew = (1..20).map { it.toDouble() }
        try {
            SrsParams(w = tooFew)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("21")
        }
    }

    @Test
    fun `constructor rejects requestRetention out of 0_1`() {
        try {
            SrsParams(requestRetention = 1.5)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("requestRetention")
        }
        try {
            SrsParams(requestRetention = -0.1)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("requestRetention")
        }
    }

    @Test
    fun `constructor rejects maximumInterval less than 1`() {
        try {
            SrsParams(maximumInterval = 0)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("maximumInterval")
        }
    }

    /** Начальная стабильность S0 (веса w[0..3]) монотонна AGAIN<HARD<GOOD<EASY. */
    @Test
    fun `initial stability weights w0_w3 are monotonically increasing`() {
        val w = SrsParams.DEFAULT_WEIGHTS
        assertThat(w[0]).isLessThan(w[1]) // AGAIN < HARD
        assertThat(w[1]).isLessThan(w[2]) // HARD < GOOD
        assertThat(w[2]).isLessThan(w[3]) // GOOD < EASY
    }
}

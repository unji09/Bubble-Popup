package com.ssafy.S14P21A205.game.day.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CaptureRatePolicyTests {

    private final CaptureRatePolicy captureRatePolicy = new CaptureRatePolicy();

    @Test
    void applyMultiplierClampsCaptureRateAtOne() {
        BigDecimal result = captureRatePolicy.applyMultiplier(
                new BigDecimal("0.95"),
                new BigDecimal("1.20")
        );

        assertThat(result).isEqualByComparingTo("1.0000");
    }

    @Test
    void resolveStartingCaptureRateAlsoClampsAtOne() {
        BigDecimal result = captureRatePolicy.resolveStartingCaptureRate(
                new BigDecimal("2.00"),
                new BigDecimal("1.50")
        );

        assertThat(result).isEqualByComparingTo("1.0000");
    }
}

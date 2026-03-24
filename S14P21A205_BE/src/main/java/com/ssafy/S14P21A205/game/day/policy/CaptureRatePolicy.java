package com.ssafy.S14P21A205.game.day.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class CaptureRatePolicy {

    private static final BigDecimal BASE_CAPTURE_RATE = new BigDecimal("0.50");
    private static final BigDecimal DECIMAL_ZERO = new BigDecimal("0.00");
    private static final BigDecimal DECIMAL_ONE = new BigDecimal("1.00");

    public BigDecimal resolveStartingCaptureRate(BigDecimal... multipliers) {
        BigDecimal captureRate = BASE_CAPTURE_RATE;
        if (multipliers == null) {
            return normalizeCaptureRate(captureRate);
        }
        for (BigDecimal multiplier : multipliers) {
            captureRate = applyMultiplier(captureRate, multiplier);
        }
        return normalizeCaptureRate(captureRate);
    }

    public BigDecimal applyMultiplier(BigDecimal currentCaptureRate, BigDecimal effectMultiplier) {
        BigDecimal baseRate = normalizeCaptureRate(currentCaptureRate);
        BigDecimal multiplier = normalizePositiveMultiplier(effectMultiplier);
        return normalizeCaptureRate(baseRate.multiply(multiplier));
    }

    public BigDecimal normalizeCaptureRate(BigDecimal value) {
        if (value == null) {
            return DECIMAL_ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return value.max(DECIMAL_ZERO).min(DECIMAL_ONE).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizePositiveMultiplier(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return DECIMAL_ONE.setScale(4, RoundingMode.HALF_UP);
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}

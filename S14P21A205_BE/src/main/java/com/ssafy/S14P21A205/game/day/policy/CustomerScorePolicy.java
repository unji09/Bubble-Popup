package com.ssafy.S14P21A205.game.day.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class CustomerScorePolicy {

    private static final int MAX_REGION_STORE_DIVISOR = 5;
    private static final BigDecimal SCORE_MIN = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
    private static final BigDecimal SCORE_MAX = new BigDecimal("20.000000");
    private static final BigDecimal DECIMAL_ZERO = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);

    public CustomerScoreResult calculate(
            PopulationPolicy.PopulationSnapshot populationSnapshot,
            Integer regionStoreCount,
            BigDecimal captureRate
    ) {
        if (populationSnapshot == null || regionStoreCount == null || regionStoreCount <= 0) {
            return CustomerScoreResult.empty();
        }

        int currentFloatingPopulation = populationSnapshot.currentFloatingPopulation() == null
                ? 0
                : populationSnapshot.currentFloatingPopulation();
        if (currentFloatingPopulation <= 0) {
            return new CustomerScoreResult(0, DECIMAL_ZERO, 0);
        }

        int divisorStoreCount = Math.min(regionStoreCount, MAX_REGION_STORE_DIVISOR);
        BigDecimal rValue = BigDecimal.valueOf(currentFloatingPopulation)
                .divide(BigDecimal.valueOf(divisorStoreCount), 6, RoundingMode.HALF_UP);
        BigDecimal populationScore = normalizePopulationScore(rValue);
        int populationPerStore = populationScore.setScale(0, RoundingMode.HALF_UP).intValue();
        int customerCount = populationScore
                .multiply(normalizeCaptureRate(captureRate))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        return new CustomerScoreResult(populationPerStore, populationScore, customerCount);
    }

    private BigDecimal normalizeCaptureRate(BigDecimal captureRate) {
        if (captureRate == null || captureRate.signum() <= 0) {
            return DECIMAL_ZERO;
        }
        return captureRate.setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizePopulationScore(BigDecimal populationScore) {
        if (populationScore == null || populationScore.signum() <= 0) {
            return SCORE_MIN;
        }
        return populationScore.min(SCORE_MAX).setScale(6, RoundingMode.HALF_UP);
    }

    public record CustomerScoreResult(
            Integer populationPerStore,
            BigDecimal rValue,
            Integer customerCount
    ) {
        public static CustomerScoreResult empty() {
            return new CustomerScoreResult(0, DECIMAL_ZERO, 0);
        }
    }
}

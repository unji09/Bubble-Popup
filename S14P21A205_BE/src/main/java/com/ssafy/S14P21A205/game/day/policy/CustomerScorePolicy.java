package com.ssafy.S14P21A205.game.day.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class CustomerScorePolicy {

    private static final BigDecimal SCORE_MULTIPLIER = new BigDecimal("20");
    private static final BigDecimal SCORE_OFFSET = new BigDecimal("600");
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

        BigDecimal rValue = BigDecimal.valueOf(currentFloatingPopulation)
                .divide(BigDecimal.valueOf(regionStoreCount), 6, RoundingMode.HALF_UP);
        int populationPerStore = rValue.setScale(0, RoundingMode.HALF_UP).intValue();
        BigDecimal score = SCORE_MULTIPLIER
                .multiply(rValue)
                .divide(rValue.add(SCORE_OFFSET), 6, RoundingMode.HALF_UP)
                .multiply(normalizeCaptureRate(captureRate));
        int customerCount = score
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        return new CustomerScoreResult(populationPerStore, rValue, customerCount);
    }

    private BigDecimal normalizeCaptureRate(BigDecimal captureRate) {
        if (captureRate == null || captureRate.signum() <= 0) {
            return DECIMAL_ZERO;
        }
        return captureRate.setScale(6, RoundingMode.HALF_UP);
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

package com.ssafy.S14P21A205.game.day.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CustomerScorePolicyTests {

    private final CustomerScorePolicy customerScorePolicy = new CustomerScorePolicy();

    @Test
    void calculateAppliesCaptureRateToCustomerScore() {
        PopulationPolicy.PopulationSnapshot populationSnapshot = new PopulationPolicy.PopulationSnapshot(
                0,
                BigDecimal.ONE,
                900
        );

        CustomerScorePolicy.CustomerScoreResult result = customerScorePolicy.calculate(
                populationSnapshot,
                1,
                new BigDecimal("0.50")
        );

        assertThat(result.populationPerStore()).isEqualTo(900);
        assertThat(result.rValue()).isEqualByComparingTo("900.000000");
        assertThat(result.customerCount()).isEqualTo(6);
    }

    @Test
    void calculateReturnsZeroCustomersWhenCaptureRateIsMissing() {
        PopulationPolicy.PopulationSnapshot populationSnapshot = new PopulationPolicy.PopulationSnapshot(
                0,
                BigDecimal.ONE,
                900
        );

        CustomerScorePolicy.CustomerScoreResult result = customerScorePolicy.calculate(
                populationSnapshot,
                1,
                null
        );

        assertThat(result.populationPerStore()).isEqualTo(900);
        assertThat(result.rValue()).isEqualByComparingTo("900.000000");
        assertThat(result.customerCount()).isZero();
    }
}

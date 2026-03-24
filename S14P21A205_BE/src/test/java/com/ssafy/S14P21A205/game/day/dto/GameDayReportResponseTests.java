package com.ssafy.S14P21A205.game.day.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class GameDayReportResponseTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesCaptureRateAsSnakeCase() throws Exception {
        GameDayReportResponse response = new GameDayReportResponse(
                12L,
                3,
                "Yoonjin Cookie",
                "Seongsu",
                "Cookie",
                1_200_000L,
                320_000L,
                150,
                142,
                45,
                0,
                new BigDecimal("1.50"),
                new BigDecimal("0.90"),
                new GameDayReportResponse.DailyRevenue(1000L, 1000L, 1000L, 1000L, 1000L, 1000L, null),
                new GameDayReportResponse.TomorrowWeather("CLEAR"),
                true,
                0,
                false
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.has("capture_rate")).isTrue();
        assertThat(json.has("captureRate")).isFalse();
        assertThat(json.get("capture_rate").decimalValue()).isEqualByComparingTo("1.50");
        assertThat(json.has("change_capture_rate")).isTrue();
        assertThat(json.has("changeCaptureRate")).isFalse();
        assertThat(json.get("change_capture_rate").decimalValue()).isEqualByComparingTo("0.90");
        assertThat(json.get("storeName").asText()).isEqualTo("Yoonjin Cookie");
        assertThat(json.get("dailyRevenue").get("first").longValue()).isEqualTo(1000L);
        assertThat(json.get("dailyRevenue").get("seventh").isNull()).isTrue();
        assertThat(json.get("isBankrupt").booleanValue()).isFalse();
    }
}

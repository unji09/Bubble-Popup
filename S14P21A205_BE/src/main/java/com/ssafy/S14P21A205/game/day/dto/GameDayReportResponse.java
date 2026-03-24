package com.ssafy.S14P21A205.game.day.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record GameDayReportResponse(
        Long seasonId,
        Integer day,
        String storeName,
        String locationName,
        String menuName,
        Long revenue,
        Long totalCost,
        Integer visitors,
        Integer salesCount,
        Integer stockRemaining,
        Integer stockDisposedCount,
        @JsonProperty("capture_rate") BigDecimal captureRate,
        @JsonProperty("change_capture_rate") BigDecimal changeCaptureRate,
        DailyRevenue dailyRevenue,
        TomorrowWeather tomorrowWeather,
        Boolean isNextDayOrderDay,
        Integer consecutiveDeficitDays,
        Boolean isBankrupt
) {
    public record DailyRevenue(
            Long first,
            Long second,
            Long third,
            Long fourth,
            Long fifth,
            Long sixth,
            Long seventh
    ) {
    }

    public record TomorrowWeather(
            String condition
    ) {
    }
}

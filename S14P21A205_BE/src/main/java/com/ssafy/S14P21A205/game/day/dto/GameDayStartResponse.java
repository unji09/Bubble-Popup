package com.ssafy.S14P21A205.game.day.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameDayStartResponse(
        String startTime,
        String endTime,
        Map<String, HourlySchedule> hourlySchedule,
        String weatherType,
        BigDecimal weatherMultiplier,
        BigDecimal trafficSchedule,
        BigDecimal captureRate,
        List<EventSchedule> eventSchedule,
        Integer initialBalance,
        Integer initialStock,
        OpeningSummary openingSummary,
        MarketSnapshot marketSnapshot
) {
    public record HourlySchedule(
            Integer population,
            BigDecimal trafficMultiplier,
            BigDecimal eventMultiplier,
            Integer effectivePopulation
        ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EventSchedule(
            String time,
            String type,
            Scope scope,
            String newsTitle,
            BigDecimal populationMultiplier,
            Integer balanceChange,
            String eventCategory
    ) {
    }

    public record Scope(
            Long region,
            Long menu
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OpeningSummary(
            Integer previousClosingBalance,
            Integer previousClosingStock,
            Integer regularOrderQuantity,
            Integer regularOrderCost,
            Integer dailyRentApplied,
            Integer interiorCost,
            Integer disposalQuantity,
            Integer disposalLoss,
            Integer governmentSupportCash,
            Integer appliedUnitCost,
            Integer openingFreshStock,
            Integer openingAgedStock,
            Integer fixedCostTotal,
            BigDecimal rentMultiplier,
            BigDecimal rentCouponMultiplier,
            BigDecimal ingredientDiscountMultiplier,
            BigDecimal persistentCostEventMultiplier,
            BigDecimal todayCostEventMultiplier,
            BigDecimal trendCostMultiplier
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MarketSnapshot(
            Integer avgMenuPrice,
            Integer regionStoreCount,
            Integer totalFloatingPopulation,
            Integer totalPopulationPerStore,
            Integer locationPopularityRank,
            Integer menuTrendRank,
            BigDecimal priceRatio,
            String priceBand,
            BigDecimal priceBandMultiplier,
            BigDecimal trendMultiplier,
            String festivalName,
            BigDecimal festivalMultiplier
    ) {
    }
}

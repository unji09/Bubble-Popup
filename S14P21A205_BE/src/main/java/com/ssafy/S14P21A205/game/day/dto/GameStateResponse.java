package com.ssafy.S14P21A205.game.day.dto;

import com.ssafy.S14P21A205.game.environment.entity.TrafficStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record GameStateResponse(
        LocalDateTime serverTime,
        Long seasonId,
        Integer day,
        String population,
        Traffic traffic,
        LocalDateTime lastCalculatedAt,
        Long cash,
        Integer customerCount,
        CustomerTick customerTick,
        List<GameDayStartResponse.EventSchedule> todayEventSchedule,
        Inventory inventory,
        ActionStatus actionStatus,
        List<AppliedEvent> appliedEvents
) {
    public record CustomerTick(
            Integer tick,
            Integer customerCount,
            Integer unitPrice,
            List<Integer> soldUnits,
            Integer baseFloatingPopulation,
            BigDecimal populationGrowthRate,
            Integer currentFloatingPopulation,
            Integer regionStoreCount,
            BigDecimal rValue
    ) {
    }

    public record Inventory(
            Integer totalStock
    ) {
    }

    public record Traffic(
            TrafficStatus status,
            Integer value,
            Integer gameHour,
            Integer delaySeconds
    ) {
    }

    public record ActionStatus(
            Boolean discountUsed,
            Boolean donationUsed,
            Boolean promotionUsed,
            Boolean emergencyUsed,
            Boolean emergencyOrderPending,
            LocalDateTime emergencyOrderArriveAt
    ) {
    }

    public record AppliedEvent(
            String eventType,
            String eventName,
            String newsTitle,
            LocalDateTime appliedAt
    ) {
    }
}

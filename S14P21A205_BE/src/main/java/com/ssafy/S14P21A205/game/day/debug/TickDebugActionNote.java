package com.ssafy.S14P21A205.game.day.debug;

public record TickDebugActionNote(
        String actionLabel,
        Long promotionCost,
        Long discountCost,
        Long emergencyOrderCost,
        Long donationCost,
        Long moveCost,
        Integer donationStockDelta
) {
}

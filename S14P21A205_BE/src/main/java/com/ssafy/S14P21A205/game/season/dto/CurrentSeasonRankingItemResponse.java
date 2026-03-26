package com.ssafy.S14P21A205.game.season.dto;

import java.math.BigDecimal;

public record CurrentSeasonRankingItemResponse(
        Integer rank,
        Integer userId,
        String nickname,
        String storeName,
        String locationName,
        String menuName,
        BigDecimal roi,
        Long totalRevenue,
        Integer rewardPoints,
        Boolean isBankrupt,
        Boolean isMine
) {
}

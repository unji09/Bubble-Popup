package com.ssafy.S14P21A205.game.season.dto;

import java.math.BigDecimal;

public record CurrentSeasonTopRankingItemResponse(
        Integer rank,
        Integer userId,
        String nickname,
        String storeName,
        BigDecimal roi,
        Long totalRevenue,
        Integer rewardPoints,
        Boolean isMine
) {
}

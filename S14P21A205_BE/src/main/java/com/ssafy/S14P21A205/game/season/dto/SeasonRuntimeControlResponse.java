package com.ssafy.S14P21A205.game.season.dto;

import java.time.LocalDateTime;

public record SeasonRuntimeControlResponse(
        boolean paused,
        LocalDateTime pausedAt,
        LocalDateTime effectiveNow,
        Long currentSeasonId,
        String seasonStatus,
        Integer currentDay,
        String phase,
        Integer remainingPhaseSeconds
) {
}

package com.ssafy.S14P21A205.game.season.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Admin demo skip reservation response")
public record SeasonDemoSkipResponse(
        @Schema(description = "Season ID", example = "12")
        Long seasonId,
        @Schema(description = "Demo skip status", example = "RESERVED")
        String status,
        @Schema(description = "Playable days during demo season", example = "3")
        Integer demoPlayableDays,
        @Schema(description = "Result message", example = "This season will run as a 3-day demo season when it starts.")
        String message
) {
}

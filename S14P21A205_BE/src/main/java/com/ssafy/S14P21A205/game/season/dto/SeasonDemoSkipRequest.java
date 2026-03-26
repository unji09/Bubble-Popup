package com.ssafy.S14P21A205.game.season.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Admin demo skip reservation request")
public record SeasonDemoSkipRequest(
        @Schema(description = "Target season ID", example = "12")
        Long seasonId
) {
}

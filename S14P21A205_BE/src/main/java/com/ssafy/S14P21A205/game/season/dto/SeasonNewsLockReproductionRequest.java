package com.ssafy.S14P21A205.game.season.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Admin request to trigger deterministic news lock reproduction")
public record SeasonNewsLockReproductionRequest(
        @Schema(description = "Target season ID", example = "238")
        Long seasonId,
        @Schema(description = "Delay before second concurrent trigger in milliseconds", example = "1000")
        Long secondTriggerDelayMs
) {
}

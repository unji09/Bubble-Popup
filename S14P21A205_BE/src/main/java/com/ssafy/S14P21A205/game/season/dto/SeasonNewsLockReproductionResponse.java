package com.ssafy.S14P21A205.game.season.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Admin response for deterministic news lock reproduction trigger")
public record SeasonNewsLockReproductionResponse(
        @Schema(description = "Target season ID", example = "238")
        Long seasonId,
        @Schema(description = "Delay before second concurrent trigger in milliseconds", example = "1000")
        Long secondTriggerDelayMs,
        @Schema(description = "Whether reproduction env flags are enabled", example = "true")
        boolean reproductionEnabled,
        @Schema(description = "Trigger accepted time", example = "2026-04-01T14:15:00")
        LocalDateTime triggeredAt,
        @Schema(description = "Result message", example = "Triggered two concurrent news generation jobs for deterministic lock reproduction.")
        String message
) {
}

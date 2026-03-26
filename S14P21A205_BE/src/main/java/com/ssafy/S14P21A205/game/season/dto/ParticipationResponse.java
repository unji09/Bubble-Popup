package com.ssafy.S14P21A205.game.season.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Current season participation state for the authenticated user")
public record ParticipationResponse(
        @Schema(description = "Whether the user has joined the current season", example = "true")
        boolean joinedCurrentSeason,
        @Schema(description = "Whether the store screen is accessible right now", example = "true")
        boolean storeAccessible,
        @Schema(description = "Current season store ID", example = "15")
        Long storeId,
        @Schema(description = "Current season store name", example = "PulsePop Kitchen")
        String storeName,
        @Schema(description = "Playable start day for the current season store", example = "3")
        Integer playableFromDay
) {
    public static ParticipationResponse notJoined() {
        return new ParticipationResponse(false, false, null, null, null);
    }
}

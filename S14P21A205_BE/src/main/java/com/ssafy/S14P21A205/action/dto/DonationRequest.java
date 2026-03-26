package com.ssafy.S14P21A205.action.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "나눔 이벤트 요청")
public record DonationRequest(
        @Schema(description = "나눔 수량 (1~50개)", example = "25")
        @NotNull @Min(1) Integer quantity
) {
}

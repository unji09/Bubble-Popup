package com.ssafy.S14P21A205.action.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "긴급 발주 응답")
public record EmergencyOrderResponse(
        @Schema(description = "주문 ID") Long orderId,
        @Schema(description = "수량") int quantity,
        @Schema(description = "총 비용") int totalCost,
        @Schema(description = "배송 지연 시간(초)") int deliverySeconds,
        @Schema(description = "도착 예정 시각") LocalDateTime arrivedTime,
        @Schema(description = "결과 메시지") String message
) {
}

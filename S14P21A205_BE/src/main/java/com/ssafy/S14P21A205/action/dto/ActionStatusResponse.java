package com.ssafy.S14P21A205.action.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "액션 사용 현황 응답")
public record ActionStatusResponse(
        @Schema(description = "할인 사용 여부") boolean discountUsed,
        @Schema(description = "긴급발주 사용 여부") boolean emergencyUsed,
        @Schema(description = "나눔 사용 여부") boolean donationUsed,
        @Schema(description = "홍보 사용 여부") boolean promotionUsed
) {
    public static ActionStatusResponse from(Map<String, Boolean> actions) {
        return new ActionStatusResponse(
                Boolean.TRUE.equals(actions.get("discount")),
                Boolean.TRUE.equals(actions.get("emergency")),
                Boolean.TRUE.equals(actions.get("donation")),
                isPromotionUsed(actions)
        );
    }

    public static boolean isPromotionUsed(Map<String, Boolean> actions) {
        return Boolean.TRUE.equals(actions.get("promotion"))
                || Boolean.TRUE.equals(actions.get("influencer"))
                || Boolean.TRUE.equals(actions.get("sns"))
                || Boolean.TRUE.equals(actions.get("leaflet"))
                || Boolean.TRUE.equals(actions.get("friend"));
    }
}

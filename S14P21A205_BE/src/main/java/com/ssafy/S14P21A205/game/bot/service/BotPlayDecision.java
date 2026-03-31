package com.ssafy.S14P21A205.game.bot.service;

import com.ssafy.S14P21A205.action.entity.PromotionType;

public record BotPlayDecision(
        BotActionType actionType,
        Integer menuId,
        Integer quantity,
        Integer price,
        Integer discountValue,
        Integer donationQuantity,
        PromotionType promotionType
) {
    public static BotPlayDecision none() {
        return new BotPlayDecision(BotActionType.NONE, null, null, null, null, null, null);
    }
}

package com.ssafy.S14P21A205.game.bot.service;

public record BotPreparationDecision(
        Integer menuId,
        Integer quantity,
        Integer price,
        Long nextLocationId
) {
}

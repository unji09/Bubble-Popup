package com.ssafy.S14P21A205.order.dto;

import lombok.Builder;

@Builder
public record CurrentOrderResponse(
        Integer menuId,
        String menuName,
        Integer costPrice,
        Integer minimumSellingPrice,
        Integer recommendedPrice,
        Integer maxSellingPrice,
        Integer sellingPrice,
        Integer stock
) {
}

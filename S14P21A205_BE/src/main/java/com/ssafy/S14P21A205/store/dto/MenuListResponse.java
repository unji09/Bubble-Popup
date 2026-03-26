package com.ssafy.S14P21A205.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class MenuListResponse {

    private List<MenuInfo> menus;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class MenuInfo {
        private Integer menuId;
        private String menuName;
        private Integer ingredientPrice;
        private Float discount;
        private Integer recommendedPrice;
        private Integer maxSellingPrice;
    }
}
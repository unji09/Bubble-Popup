package com.ssafy.S14P21A205.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-001", "Server error occurred."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON-002", "Invalid input value."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-003", "Requested resource was not found."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "COMMON-004", "Service is temporarily unavailable."),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH-001", "Authentication is required."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH-002", "Access is denied."),

    // Game
    INVALID_DAY(HttpStatus.BAD_REQUEST, "GAME-001", "Invalid day value."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "GAME-002", "Requested day report was not found."),
    NOT_PARTICIPATING(HttpStatus.FORBIDDEN, "GAME-003", "No participating season is available."),
    GAME_STATE_NOT_FOUND(HttpStatus.NOT_FOUND, "GAME-004", "Game state was not found."),
    SEASON_NOT_FOUND(HttpStatus.NOT_FOUND, "GAME-005", "No active season is available."),
    MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "GAME-006", "Menu was not found."),
    ALREADY_JOINED_CURRENT_SEASON(HttpStatus.CONFLICT, "GAME-007", "Current season is already joined."),
    NEWS_NOT_FOUND(HttpStatus.NOT_FOUND, "GAME-008", "News not found for the requested day."),
    FINAL_RANKING_NOT_READY(HttpStatus.CONFLICT, "GAME-009", "Current final ranking is not ready."),
    SEASON_STATE_CONFLICT(HttpStatus.CONFLICT, "GAME-010", "Season state conflict."),

    // Store
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE-001", "Store was not found."),

    // Order
    ORDER_NOT_AVAILABLE_DAY(HttpStatus.BAD_REQUEST, "ORDER-001", "Regular orders are not available today."),
    ORDER_DAY_ALREADY_STARTED(HttpStatus.CONFLICT, "ORDER-002", "Regular orders are unavailable after the day has started."),
    ORDER_ALREADY_EXISTS(HttpStatus.CONFLICT, "ORDER-003", "A regular order already exists for this day."),
    ORDER_INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "ORDER-004", "Order quantity must be between 50 and 500."),
    ORDER_INVALID_SELLING_PRICE(HttpStatus.BAD_REQUEST, "ORDER-005", "Selling price is out of the allowed range."),
    ORDER_INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "ORDER-006", "Insufficient balance for this regular order."),

    // Shop
    SHOP_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "SHOP-001", "Shop item was not found."),
    SHOP_ITEM_ALREADY_PURCHASED(HttpStatus.CONFLICT, "SHOP-002", "Shop item is already purchased."),
    SHOP_INSUFFICIENT_POINTS(HttpStatus.BAD_REQUEST, "SHOP-003", "Insufficient points."),
    SHOP_STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "SHOP-004", "Store was not found."),

    // Action
    ACTION_ALREADY_USED(HttpStatus.CONFLICT, "ACTION-001", "Action already used today."),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "ACTION-002", "Insufficient balance."),
    INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "ACTION-003", "Insufficient stock.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

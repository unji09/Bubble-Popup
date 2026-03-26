package com.ssafy.S14P21A205.order.controller;

import com.ssafy.S14P21A205.exception.ErrorResponse;
import com.ssafy.S14P21A205.order.dto.CurrentOrderResponse;
import com.ssafy.S14P21A205.order.dto.RegularOrderRequest;
import com.ssafy.S14P21A205.order.dto.RegularOrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "Order", description = "판매가 API")
public interface OrderControllerDoc {

    @Operation(
            summary = "현재 판매가 조회",
            description = "인증된 사용자의 매장에 대한 현재 판매가 정보를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "현재 판매가 조회 성공",
                    content = @Content(schema = @Schema(implementation = CurrentOrderResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "현재 판매가 조회 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<CurrentOrderResponse> getCurrentOrder(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "가격 정보를 조회할 메뉴 ID. 생략 시 현재 가게 메뉴 기준으로 계산됩니다.")
            Integer menuId
    );

    @Operation(
            summary = "정규 발주 생성",
            description = "인증된 사용자의 매장에 대해 정규 발주를 생성합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "정규 발주 생성 성공",
                    content = @Content(schema = @Schema(implementation = RegularOrderResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "정규 발주 생성 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<RegularOrderResponse> createRegularOrder(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "정규 발주 요청")
            RegularOrderRequest request
    );
}

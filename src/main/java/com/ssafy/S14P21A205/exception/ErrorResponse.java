package com.ssafy.S14P21A205.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** 용도: API 공통 에러 응답 바디 정의. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        @JsonFormat(shape = JsonFormat.Shape.STRING,
                    pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                    timezone = "Asia/Seoul")
        Instant timestamp,
        String path
) {
    /** 용도: ErrorCode 기반 에러 응답 생성. */
    public ErrorResponse(ErrorCode errorCode, String path) {
        this(errorCode.getCode(), errorCode.getMessage(), Instant.now(), path);
    }
}

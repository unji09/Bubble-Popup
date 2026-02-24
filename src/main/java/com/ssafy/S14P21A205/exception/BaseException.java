package com.ssafy.S14P21A205.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 용도: ErrorCode를 담아 전달하는 공통 비즈니스 예외. */
@Getter
public class BaseException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 용도: 원인 예외 없이 생성. */
    public BaseException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 용도: 원인 예외 포함 생성. */
    public BaseException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    /** 용도: ErrorCode의 HTTP 상태 반환. */
    public HttpStatus getHttpStatus() {
        return errorCode.getHttpStatus();
    }
}

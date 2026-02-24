package com.ssafy.S14P21A205.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 용도: 전역 예외를 공통 JSON 응답으로 변환.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String REQUEST_LOG_FORMAT = "[{}] {} {} - {}";

    /** 용도: BaseException 처리. */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException e, HttpServletRequest request) {
        HttpStatus status = e.getHttpStatus();
        if (status.is5xxServerError()) {
            log.error(REQUEST_LOG_FORMAT, e.getErrorCode().getCode(), request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        } else {
            log.warn(REQUEST_LOG_FORMAT, e.getErrorCode().getCode(), request.getMethod(), request.getRequestURI(), e.getMessage());
        }
        return ResponseEntity.status(status).body(new ErrorResponse(e.getErrorCode(), request.getRequestURI()));
    }

    /** 용도: 입력/바인딩/파싱 예외를 400으로 처리. */
    @ExceptionHandler({
            BindException.class,
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidInput(Exception e, HttpServletRequest request) {
        log.info(REQUEST_LOG_FORMAT, ErrorCode.INVALID_INPUT_VALUE.getCode(), request.getMethod(), request.getRequestURI(), summarize(e));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ErrorCode.INVALID_INPUT_VALUE, request.getRequestURI()));
    }

    /** 용도: 인가 실패 예외를 403으로 처리. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn(REQUEST_LOG_FORMAT, ErrorCode.ACCESS_DENIED.getCode(), request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity
                .status(ErrorCode.ACCESS_DENIED.getHttpStatus())
                .body(new ErrorResponse(ErrorCode.ACCESS_DENIED, request.getRequestURI()));
    }

    /** 용도: 미처리 예외를 500으로 처리. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        log.error(REQUEST_LOG_FORMAT, ErrorCode.INTERNAL_SERVER_ERROR.getCode(), request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR, request.getRequestURI()));
    }

    private static String summarize(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "잘못된 요청 값입니다.";
        }
        return message;
    }
}

package com.ssafy.S14P21A205.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String REQUEST_LOG_FORMAT = "[{}] {} {} - {}";

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException e, HttpServletRequest request) {
        HttpStatus status = e.getHttpStatus();
        if (status.is5xxServerError()) {
            log.error(REQUEST_LOG_FORMAT, e.getErrorCode().getCode(), request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        } else {
            log.warn(REQUEST_LOG_FORMAT, e.getErrorCode().getCode(), request.getMethod(), request.getRequestURI(), e.getMessage());
        }
        return ResponseEntity
                .status(status)
                .body(new ErrorResponse(e.getErrorCode(), e.getMessage(), request.getRequestURI()));
    }

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
        String message = summarize(e);
        log.info(REQUEST_LOG_FORMAT, ErrorCode.INVALID_INPUT_VALUE.getCode(), request.getMethod(), request.getRequestURI(), message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ErrorCode.INVALID_INPUT_VALUE, message, request.getRequestURI()));
    }

    @ExceptionHandler({
            NoHandlerFoundException.class,
            NoResourceFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(Exception e, HttpServletRequest request) {
        log.info(REQUEST_LOG_FORMAT, ErrorCode.RESOURCE_NOT_FOUND.getCode(), request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ErrorCode.RESOURCE_NOT_FOUND, request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn(REQUEST_LOG_FORMAT, ErrorCode.ACCESS_DENIED.getCode(), request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity
                .status(ErrorCode.ACCESS_DENIED.getHttpStatus())
                .body(new ErrorResponse(ErrorCode.ACCESS_DENIED, request.getRequestURI()));
    }

    @ExceptionHandler({
            CannotCreateTransactionException.class,
            DataAccessResourceFailureException.class,
            QueryTimeoutException.class,
            RedisConnectionFailureException.class
    })
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(Exception e, HttpServletRequest request) {
        log.error(REQUEST_LOG_FORMAT, ErrorCode.SERVICE_UNAVAILABLE.getCode(), request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(ErrorCode.SERVICE_UNAVAILABLE, request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        log.error(REQUEST_LOG_FORMAT, ErrorCode.INTERNAL_SERVER_ERROR.getCode(), request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR, request.getRequestURI()));
    }

    private static String summarize(Exception e) {
        if (e instanceof MethodArgumentNotValidException ex) {
            return summarizeBindingResult(ex.getBindingResult());
        }
        if (e instanceof BindException ex) {
            return summarizeBindingResult(ex.getBindingResult());
        }
        if (e instanceof ConstraintViolationException ex && !ex.getConstraintViolations().isEmpty()) {
            return ex.getConstraintViolations().iterator().next().getMessage();
        }
        if (e instanceof MissingServletRequestParameterException ex) {
            return ex.getParameterName() + " 요청 파라미터는 필수입니다.";
        }
        if (e instanceof MethodArgumentTypeMismatchException ex) {
            return ex.getName() + " 값의 형식이 올바르지 않습니다.";
        }
        if (e instanceof HttpMessageNotReadableException) {
            return "요청 본문 JSON 형식이 올바르지 않습니다.";
        }

        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return ErrorCode.INVALID_INPUT_VALUE.getMessage();
        }
        return message;
    }

    private static String summarizeBindingResult(BindingResult bindingResult) {
        FieldError fieldError = bindingResult.getFieldErrors().stream().findFirst().orElse(null);
        if (fieldError != null) {
            String message = fieldError.getDefaultMessage();
            if (message == null || message.isBlank()) {
                return fieldError.getField() + " 값이 올바르지 않습니다.";
            }
            return message;
        }

        ObjectError objectError = bindingResult.getGlobalErrors().stream().findFirst().orElse(null);
        if (objectError != null) {
            String message = objectError.getDefaultMessage();
            if (message == null || message.isBlank()) {
                return ErrorCode.INVALID_INPUT_VALUE.getMessage();
            }
            return message;
        }

        return ErrorCode.INVALID_INPUT_VALUE.getMessage();
    }
}

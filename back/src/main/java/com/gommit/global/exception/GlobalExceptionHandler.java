package com.gommit.global.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException: {} - {}", errorCode.getCode(), e.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public void rethrowAccessDenied(AccessDeniedException e) throws AccessDeniedException {
        throw e;
    }

    @ExceptionHandler(AuthenticationException.class)
    public void rethrowAuthentication(AuthenticationException e) throws AuthenticationException {
        throw e;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        List<ErrorResponse.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {

        List<ErrorResponse.FieldError> errors = e.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.FieldError(lastNodeOf(v.getPropertyPath()), v.getMessage()))
                .toList();

        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, errors));
    }

    private String lastNodeOf(Path propertyPath) {
        String full = propertyPath.toString();
        int lastDot = full.lastIndexOf('.');
        return (lastDot < 0) ? full : full.substring(lastDot + 1);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        log.warn("Spring internal exception: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());

        HttpStatus status = HttpStatus.valueOf(statusCode.value());

        ErrorResponse response = new ErrorResponse(status.name(), messageOf(status), List.of());

        return ResponseEntity.status(statusCode).headers(headers).body(response);
    }

    private String messageOf(HttpStatus status) {
        return switch (status) {
            case METHOD_NOT_ALLOWED -> "지원하지 않는 요청 방식입니다.";
            case UNSUPPORTED_MEDIA_TYPE -> "지원하지 않는 형식의 요청입니다.";
            case NOT_FOUND -> "요청한 경로를 찾을 수 없습니다.";
            case BAD_REQUEST -> "요청 형식이 올바르지 않습니다.";
            default -> "요청을 처리할 수 없습니다.";
        };
    }
}

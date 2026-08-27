package com.gommit.global.exception;

import java.util.List;

public record ErrorResponse(String code, String message, List<FieldError> errors) {

    public ErrorResponse {
        errors = (errors == null) ? List.of() : List.copyOf(errors);
    }

    public record FieldError(String field, String reason) {}

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), errors);
    }
}

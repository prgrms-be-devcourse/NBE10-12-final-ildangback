package com.gommit.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ===== 전역 — global 이 정의한다. 도메인에서 사용은 자유, 정의 변경은 금지 =====
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // ===== challenge =====

    // ===== checkin =====

    // ===== group =====

    // ===== item =====

    // ===== point =====
    INSUFFICIENT_POINTS(HttpStatus.CONFLICT, "포인트가 부족합니다."),
    POINT_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 포인트 이력입니다."),

    // ===== record =====

    // ===== user =====
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    ACCOUNT_INFO_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일 또는 닉네임입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    PASSWORD_UNCHANGED(HttpStatus.BAD_REQUEST, "현재 비밀번호와 다른 비밀번호를 입력해 주세요."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "만료되었거나 유효하지 않은 토큰입니다. 다시 로그인해 주세요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    ;

    private final HttpStatus httpStatus;
    private final String message;

    public String getCode() {
        return name();
    }
}

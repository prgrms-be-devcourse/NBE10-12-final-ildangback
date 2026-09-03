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
    // CHALLENGE_NOT_FOUND 는 challenge 도메인(#6)이 소유 예정. 미머지라 checkin 이 임시 정의 — 머지 시 이관.
    CHALLENGE_NOT_FOUND(HttpStatus.NOT_FOUND, "챌린지를 찾을 수 없습니다."),
    CHECK_IN_NOT_FOUND(HttpStatus.NOT_FOUND, "인증을 찾을 수 없습니다."),
    NOT_CHALLENGE_MEMBER(HttpStatus.FORBIDDEN, "챌린지 참여자만 접근할 수 있습니다."),
    MEMO_TOO_LONG(HttpStatus.BAD_REQUEST, "메모는 100자를 넘을 수 없습니다."),
    CHECK_IN_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "허용되지 않은 인증 방식입니다."),
    DAILY_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "오늘 인증 횟수를 모두 채웠습니다."),
    NOT_CHECK_IN_DAY(HttpStatus.CONFLICT, "오늘은 인증 대상일이 아닙니다."),
    CHALLENGE_NOT_ACTIVE(HttpStatus.CONFLICT, "진행 중인 챌린지가 아닙니다."),

    // media(#8) 의 EMPTY_FILE / FILE_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE / MEDIA_NOT_FOUND / MEDIA_STORAGE_FAILED 를
    // 미디어 저장/서빙에 사용한다 (feat/8 media 섹션에 정의됨).

    // ===== group =====

    // ===== item =====

    // ===== media =====
    EMPTY_FILE(HttpStatus.BAD_REQUEST, "파일이 비어 있습니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "허용된 파일 크기를 초과했습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 파일 형식입니다."),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "미디어를 찾을 수 없습니다."),
    MEDIA_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장에 실패했습니다."),

    // ===== point =====
    POINT_INSUFFICIENT(HttpStatus.CONFLICT, "포인트가 부족합니다."),
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

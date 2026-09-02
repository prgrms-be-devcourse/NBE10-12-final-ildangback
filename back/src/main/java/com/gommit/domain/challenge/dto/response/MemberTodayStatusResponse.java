package com.gommit.domain.challenge.dto.response;

public record MemberTodayStatusResponse(
    Long userId,
    String nickname,
//    CharacterResponse character, TODO: 캐릭터 응답 추가시 주석제거
    int todayCheckInCount
) {
}

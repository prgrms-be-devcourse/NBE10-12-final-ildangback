package com.gommit.domain.challenge.dto.response;

import com.gommit.domain.challenge.entity.ExtensionChoice;

public record MemberTodayStatusResponse(
        Long userId,
        String nickname,
        //    CharacterResponse character, TODO: 캐릭터 응답 추가시 주석제거
        int todayCheckInCount,
        ExtensionChoice extensionChoice) {}

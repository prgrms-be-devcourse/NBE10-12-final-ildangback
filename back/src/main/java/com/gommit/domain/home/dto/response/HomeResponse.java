package com.gommit.domain.home.dto.response;

import com.gommit.domain.item.dto.response.CharacterResponse;
import java.time.LocalDate;
import java.util.List;

public record HomeResponse(
        String nickname,
        int pointBalance,
        String statusMessage,
        CharacterResponse character,
        HomeSummaryResponse summary,
        List<TodayChallengeResponse> todayChallenges,
        int todayTotalCount,
        int todayCompletedCount,
        boolean hasUnreadNotification,
        LocalDate businessDate) {}

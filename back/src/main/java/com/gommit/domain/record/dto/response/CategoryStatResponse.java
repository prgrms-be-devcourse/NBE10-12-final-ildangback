package com.gommit.domain.record.dto.response;

// "개인 전체 통계" 카테고리 탭 1건. category는 ChallengeGroup.category(GroupCategory)
// 이름을 그대로 쓴다. missedDayCount는 "카테고리별 놓친 인증 비율" 도넛 차트용.
public record CategoryStatResponse(
        String category, int challengeCount, int totalCheckInCount, int averageCompletionRate, int missedDayCount) {}

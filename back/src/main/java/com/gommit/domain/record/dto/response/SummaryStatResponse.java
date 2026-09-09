package com.gommit.domain.record.dto.response;

// "개인 전체 통계" 요약 탭. inProgressChallengeCount는 최종 머지가 아직 없는
// 챌린지 수 - Record는 중도 이탈 여부를 몰라서 "미완주"를 이렇게 근사한다.
public record SummaryStatResponse(
        int totalCheckInCount,
        int completedDayCount,
        int missedDayCount,
        int bestStreakEver,
        int averageCompletionRate,
        int completedChallengeCount,
        int inProgressChallengeCount) {}

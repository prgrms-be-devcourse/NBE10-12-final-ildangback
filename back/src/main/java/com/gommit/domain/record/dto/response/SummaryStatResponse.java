package com.gommit.domain.record.dto.response;

// "개인 전체 통계" 요약 탭. completedDayCount/missedDayCount는 "인증 대상일 중 며칠을
// 채웠는지" 기준이라 totalCheckInCount(하루 여러 번 인증 가능)와는 다른 숫자다.
// completedChallengeCount는 최종 머지까지 발행된(끝까지 참여한) 챌린지 수,
// inProgressChallengeCount는 아직 최종 머지가 없는(진행 중인) 챌린지 수다 - Record는
// "중도 이탈" 여부를 모르므로 "미완주"를 "아직 안 끝남"으로 근사한다.
public record SummaryStatResponse(
        int totalCheckInCount,
        int completedDayCount,
        int missedDayCount,
        int bestStreakEver,
        int averageCompletionRate,
        int completedChallengeCount,
        int inProgressChallengeCount) {}

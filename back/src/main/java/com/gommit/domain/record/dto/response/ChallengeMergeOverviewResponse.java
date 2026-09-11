package com.gommit.domain.record.dto.response;

import java.time.LocalDate;

// 챌린지 하나의 머지 진행 현황 요약. "월간 머지 아카이브" 목록/상세 헤더에서 쓴다.
public record ChallengeMergeOverviewResponse(
        Long challengeId,
        // Challenge가 속한 ChallengeGroup에서 가져온다 - 챌린지 자체엔 제목 필드가
        // 없어서 그룹 이름을 화면에 챌린지 이름처럼 보여준다.
        String groupName,
        String category,
        LocalDate periodStart,
        LocalDate periodEnd,
        int totalDays,
        int cycleLengthDays,
        // "월간 머지" 개수만 센다 - 마지막 사이클은 최종 머지로 발행되므로 포함하지
        // 않는다(180일 챌린지 = 월간 5개 + 최종 1개).
        int totalMergeCount,
        int completedMergeCount,
        boolean hasFinalMerge,
        // 아직 발행 안 된(=MonthlyMerge 행이 없는) 진행 중인 월간 사이클의 위치를
        // challenge.startDate 기준 달력 계산만으로 구한 값 - 체크인 완료 여부와 무관하게
        // "오늘이 그 30일 구간의 며칠째인지"만 알려준다(완료율 아님).
        Integer currentSeqNo,
        Integer currentCycleDay) {}

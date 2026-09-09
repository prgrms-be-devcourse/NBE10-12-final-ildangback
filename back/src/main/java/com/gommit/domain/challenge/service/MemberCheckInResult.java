package com.gommit.domain.challenge.service;

// checkin 이 하루 목표 완료 인증을 넘겼을 때 challenge 가 돌려주는 결과.
// checkin 은 이 값을 CheckInResultResponse 의 스트릭/그룹 진행 필드에 그대로 매핑한다.
public record MemberCheckInResult(
        int memberCurrentStreak, int groupCompletedCount, int groupTotalCount, boolean groupJustCompleted) {}

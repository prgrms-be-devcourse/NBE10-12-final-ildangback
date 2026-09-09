package com.gommit.domain.challenge.service;

// checkin 이 하루 목표 완료 인증을 넘겼을 때 challenge 가 돌려주는 그룹 진행 결과.
// 개인 스트릭은 checkin 이 직접 유도하므로 여기 없다.
public record MemberCheckInResult(int groupCompletedCount, int groupTotalCount, boolean groupJustCompleted) {}

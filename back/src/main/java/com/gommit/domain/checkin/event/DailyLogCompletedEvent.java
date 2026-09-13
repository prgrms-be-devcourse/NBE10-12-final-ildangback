package com.gommit.domain.checkin.event;

import java.time.LocalDate;

// 해당 challenge-day 인증이 전원 완료된 시점에 발행된다(제출 트랜잭션 커밋 후 몽타주 생성 트리거).
public record DailyLogCompletedEvent(Long challengeId, LocalDate businessDate) {}

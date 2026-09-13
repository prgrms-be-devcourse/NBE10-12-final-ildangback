package com.gommit.domain.challenge.event;

// 챌린지가 ENDED로 전환된 시점에 발행된다(상태 전환 트랜잭션 커밋 후 최종 머지 생성 트리거).
public record ChallengeEndedEvent(Long challengeId) {}

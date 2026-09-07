package com.gommit.domain.record.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 월간/최종 머지 "생성" 담당. 완료율·연속일수 계산에 CheckIn 도메인의 집계 쿼리가
// 필요한데 아직 main에 병합되지 않아서, 그 쪽이 준비되면 이어서 구현한다.
@Service
@RequiredArgsConstructor
public class RecordBatchService {

    // TODO(Record): 매일 스케줄러가 호출. ACTIVE 챌린지 중 시작일 기준 30일 배수 시점이
    // 지난 것을 찾아 월간 머지를 생성한다. CheckIn 도메인의 완료율/연속일수 집계 쿼리 필요.
    public void generateDueMonthlyMerges() {}

    // TODO(Record): Challenge 도메인이 챌린지를 ENDED로 바꾸는 트랜잭션 안에서 직접 호출.
    // 완료율 기반 차등 보너스 포인트 지급(PersonalPointService.reward, UserPointReason.
    // CHALLENGE_BONUS)까지 이 메서드 안에서 처리한다.
    public void generateFinalMerge(Long challengeId) {}
}

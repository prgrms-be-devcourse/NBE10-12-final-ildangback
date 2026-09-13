package com.gommit.domain.record.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 월간/최종 머지 "생성" 담당. 계산 로직은 RecordCompletionCalculator에 미리 짜뒀고,
// CheckIn 도메인 병합 후 체크인 날짜만 뽑아 넘기면 된다.
@Service
@RequiredArgsConstructor
public class RecordBatchService {

    // TODO(Record): 스케줄러가 매일 호출. ACTIVE 챌린지 중 30일 주기가 지난 걸 찾아
    // 월간 머지를 생성한다.
    public void generateDueMonthlyMerges() {}

    // TODO(Record): 챌린지를 ENDED로 바꾸는 트랜잭션 안에서 Challenge 도메인이 호출.
    // 완료율 기반 보너스 포인트 지급까지 여기서 처리한다.
    public void generateFinalMerge(Long challengeId) {}
}

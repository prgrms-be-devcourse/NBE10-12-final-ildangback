package com.gommit.domain.record.event;

import com.gommit.domain.challenge.event.ChallengeEndedEvent;
import com.gommit.domain.record.service.RecordBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 챌린지 ENDED 전환 트랜잭션 커밋 후 최종 머지 생성을 트리거한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengeEndedEventListener {

    private final RecordBatchService recordBatchService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChallengeEnded(ChallengeEndedEvent event) {
        // @Async라 예외가 호출 스레드로 전파되지 않는다 - 여기서 안 잡으면 실패가
        // 조용히 묻혀서 최종 머지가 영영 안 만들어진 걸 아무도 모르게 된다.
        try {
            recordBatchService.generateFinalMerge(event.challengeId());
        } catch (Exception e) {
            log.error("최종 머지 생성 실패 - challengeId={}", event.challengeId(), e);
        }
    }
}

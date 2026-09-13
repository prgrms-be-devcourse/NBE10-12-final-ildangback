package com.gommit.domain.record.event;

import com.gommit.domain.challenge.event.ChallengeEndedEvent;
import com.gommit.domain.record.service.RecordBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 챌린지 ENDED 전환 트랜잭션 커밋 후 최종 머지 생성을 트리거한다.
@Component
@RequiredArgsConstructor
public class ChallengeEndedEventListener {

    private final RecordBatchService recordBatchService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChallengeEnded(ChallengeEndedEvent event) {
        recordBatchService.generateFinalMerge(event.challengeId());
    }
}

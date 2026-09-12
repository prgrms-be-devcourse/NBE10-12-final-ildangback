package com.gommit.domain.checkin.event;

import com.gommit.domain.checkin.config.MontageAsyncConfig;
import com.gommit.domain.checkin.service.DailyLogMontageService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 전원 완료 시점(제출 트랜잭션 커밋 후)에 몽타주 생성을 트리거
@Component
@RequiredArgsConstructor
public class DailyLogMontageEventListener {

    private final DailyLogMontageService montageService;

    @Async(MontageAsyncConfig.EXECUTOR) // 몽타주 전용 풀 사용, ffmpeg 동시 실행 수를 제한 등.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDailyLogCompleted(DailyLogCompletedEvent event) {
        montageService.generateMontage(event.challengeId(), event.businessDate());
    }
}

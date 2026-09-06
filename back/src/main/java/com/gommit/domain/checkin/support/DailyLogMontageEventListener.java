package com.gommit.domain.checkin.support;

import com.gommit.domain.checkin.config.MontageAsyncConfig;
import com.gommit.domain.checkin.event.DailyLogCompletedEvent;
import com.gommit.domain.checkin.service.DailyLogMontageService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 그 날 전원 완료 시점(제출 트랜잭션 커밋 후) 몽타주 생성을 트리거한다.
// AFTER_COMMIT: 커밋 전에 실행되면 방금 완료된 인증이 아직 안 보일 수 있다.
// @Async: 인증 제출 응답이 ffmpeg 실행을 기다리지 않게 한다. 별도 빈(DailyLogMontageService) 호출 — 셀프 인보케이션 아님.
// 몽타주 전용 풀(montageExecutor)로 돌려 공용 풀·다른 @Async 작업과 격리하고 ffmpeg 동시 실행 수를 제한한다.
@Component
@RequiredArgsConstructor
public class DailyLogMontageEventListener {

    private final DailyLogMontageService montageService;

    @Async(MontageAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDailyLogCompleted(DailyLogCompletedEvent event) {
        montageService.generateMontage(event.challengeId(), event.businessDate());
    }
}

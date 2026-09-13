package com.gommit.domain.record.scheduler;

import com.gommit.domain.record.service.RecordBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecordScheduler {
    private final RecordBatchService recordBatchService;

    // 매일 오전 4시(KST) - ChallengeScheduler와 같은 시각. 30일 주기가 지난 ACTIVE
    // 챌린지의 월간 머지를 생성한다. (최종 머지는 챌린지 ENDED 전환 시점에 바로 생성되어
    // 여기서 다루지 않는다 - ChallengeLifecycleService.endChallengesDueToday 참고)
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void generateMonthlyMerges() {
        recordBatchService.generateDueMonthlyMerges();
    }
}

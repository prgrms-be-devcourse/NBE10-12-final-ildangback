package com.gommit.domain.challenge.scheduler;
import com.gommit.domain.challenge.service.ChallengeService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChallengeScheduler {
    private final ChallengeService challengeService;

    // 매일 오전 4시(KST)
    // 종료 2일 전 챌린지 연장 투표를 마감하고 다음 시즌 생성
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void finalizeExtensions() {
        challengeService.finalizeExtensionsDueToday();
    }

    // 오늘 시작하는 READY 챌린지를 ACTIVE로 변경
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void activateChallenges() {
        challengeService.activateChallengesDueToday();
    }

    // 어제까지가 마지막 날이었던 ACTIVE 챌린지를 ENDED로 변경
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void endChallenges() {
        challengeService.endChallengesDueToday();
    }
}

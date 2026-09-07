package com.gommit.domain.checkin.support;

import com.gommit.domain.checkin.entity.DailyLog;
import com.gommit.domain.checkin.repository.DailyLogRepository;
import com.gommit.domain.checkin.service.DailyLogMontageService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 몽타주 마감 배치. 영상 없는 지난 날(오늘 이전, 최근 sweep-lookback-days 일 창) row 를 훑어 몽타주를 만든다.
// 04:00 단발이 아니라 인스턴스가 깨어 있는 시간대(cron, 기본 04:00~17:45 15분 주기)에 나눠 돌려, 미완료 challenge-day 가
// 한 시각에 몰려 ffmpeg CPU 버스트를 내는 걸 피한다. 한 틱은 sweep-budget-seconds 시간 예산 안에서만 처리하고
// 남은 백로그는 다음 틱으로 넘긴다(건수 상한 sweep-max-per-run 은 재기동 직후 대량 백로그용 안전장치).

// TODO(feat/20): businessDate 경계가 04:00 으로 바뀌면 "오늘" 계산을 공용 BusinessDate 컴포넌트로 교체하고
//   sweep cron 을 경계 뒤(예: 05:00)로 옮긴다. 지금은 sweep 의 LocalDate.now(00:00 경계)와 businessDate(00:00 경계)가
//   일치해 진행 중인 날이 대상에 안 들어오지만, 경계가 어긋나면 00:00~04:00 창에서 아직 인증받는 날을 몽타주할 수 있다.
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyLogMontageScheduler {

    private final DailyLogRepository dailyLogRepository;
    private final DailyLogMontageService montageService;
    private final Clock clock;

    @Value("${app.dailylog.montage-sweep-lookback-days:14}")
    private int sweepLookbackDays; // 마감 배치가 확인하는 최대 일수.

    @Value("${app.dailylog.montage-sweep-max-per-run:60}")
    private int sweepMaxPerRun; // 1회(틱) 처리 상한 — 재기동 직후 대량 백로그일 때만 작동하는 안전장치. 초과분은 다음 틱.

    @Value("${app.dailylog.montage-sweep-budget-seconds:300}")
    private long sweepBudgetSeconds; // 1회(틱) 시간 예산 — ffmpeg 직렬 처리가 이 시간을 넘으면 남은 건 다음 틱으로.

    @EventListener(ApplicationReadyEvent.class)
    public void sweepOnStartup() {
        sweepPendingMontages();
    }

    @Scheduled(cron = "${app.dailylog.montage-sweep-cron:0 0/15 4-17 * * *}", zone = "${app.time-zone:Asia/Seoul}")
    public void sweepPendingMontages() {
        // TODO(feat/20): 공용 BusinessDate 로 교체 + cron 을 경계 뒤로 (클래스 상단 주석 참고).
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(sweepLookbackDays);
        List<DailyLog> matched = dailyLogRepository.findByVideoKeyIsNullAndLogDateBetween(from, today.minusDays(1));
        if (matched.isEmpty()) {
            return;
        }

        long deadlineNanos = System.nanoTime() + sweepBudgetSeconds * 1_000_000_000L;
        int limit = Math.min(matched.size(), sweepMaxPerRun);
        int done = 0;
        int failed = 0;
        int i = 0;
        for (; i < limit; i++) {
            if (System.nanoTime() >= deadlineNanos) {
                break; // 시간 예산 소진 — 남은 건 다음 틱
            }
            DailyLog dailyLog = matched.get(i);
            try {
                montageService.generateMontage(dailyLog.getChallengeId(), dailyLog.getLogDate());
                done++;
            } catch (Exception e) {
                failed++;
                log.warn(
                        "DailyLog 몽타주 생성 실패 — 다음 틱에서 재시도 (challengeId={}, logDate={})",
                        dailyLog.getChallengeId(),
                        dailyLog.getLogDate(),
                        e);
            }
        }

        int remaining = matched.size() - i;
        if (failed > 0 || remaining > 0) {
            log.warn(
                    "DailyLog 몽타주 마감 배치 틱 종료 — 성공 {}건, 실패 {}건, 이월 {}건 (미완료 총 {}건)",
                    done,
                    failed,
                    remaining,
                    matched.size());
        } else {
            log.info("DailyLog 몽타주 마감 배치 틱 종료 — 성공 {}건", done);
        }
    }
}

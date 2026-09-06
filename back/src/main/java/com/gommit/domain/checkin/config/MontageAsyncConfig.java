package com.gommit.domain.checkin.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// DailyLog 몽타주 생성 전용 스레드풀.
// ffmpeg 는 CPU·메모리를 크게 쓴다. 하루 마감 무렵 여러 챌린지의 '전원 완료' 이벤트가 몰리면
// 기본 공용 풀(core 8, 무제한 큐)에서는 ffmpeg 가 최대 8개 병렬로 떠 인스턴스가 포화될 수 있다.
// 전용 풀로 동시 실행 수를 좁히고, 초과분은 큐에 쌓거나(그마저 넘치면) 폴백 배치(09:00)가 훑는다.
@Slf4j
@Configuration
public class MontageAsyncConfig {

    public static final String EXECUTOR = "montageExecutor";

    @Bean(EXECUTOR)
    public Executor montageExecutor(
            @Value("${app.dailylog.montage-executor.core-size:1}") int coreSize,
            @Value("${app.dailylog.montage-executor.max-size:2}") int maxSize,
            @Value("${app.dailylog.montage-executor.queue-capacity:50}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("montage-");
        // 큐까지 가득 차면 호출 스레드에서 직접 실행하지 않고 버린다 — 유실분은 폴백 배치가 재시도한다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}

package com.gommit.domain.checkin.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// DailyLog 몽타주 생성 전용 스레드풀.
// 전용 풀로 동시 실행 수를 좁히고, 초과분은 큐(50)에 쌓거나 넘치면 다음 마감 배치(04:00)가 재시도.
@Slf4j
@Configuration
public class MontageAsyncConfig {

    public static final String EXECUTOR = "montageExecutor";

    @Bean(EXECUTOR)
    public Executor montageExecutor(
            @Value("${app.dailylog.montage-executor.core-size:1}") int coreSize,
            @Value("${app.dailylog.montage-executor.max-size:1}") int maxSize,
            @Value("${app.dailylog.montage-executor.queue-capacity:50}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("montage-");
        // 큐까지 가득 차면 호출 스레드에서 직접 실행하지 않고 버린다 — 유실분은 마감 배치가 재시도한다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}

package com.gommit.domain.checkin.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gommit.domain.checkin.entity.DailyLog;
import com.gommit.domain.checkin.repository.DailyLogRepository;
import com.gommit.domain.checkin.service.DailyLogMontageService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyLogMontageScheduler")
class DailyLogMontageSchedulerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 7);

    @Mock
    private DailyLogRepository dailyLogRepository;

    @Mock
    private DailyLogMontageService montageService;

    private DailyLogMontageScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        scheduler = new DailyLogMontageScheduler(dailyLogRepository, montageService, clock);
        ReflectionTestUtils.setField(scheduler, "sweepLookbackDays", 14);
        ReflectionTestUtils.setField(scheduler, "sweepMaxPerRun", 60);
        ReflectionTestUtils.setField(scheduler, "sweepBudgetSeconds", 300L);
    }

    private DailyLog log(int dayOffset) {
        return DailyLog.create((long) dayOffset, TODAY.minusDays(dayOffset + 1L));
    }

    private void givenPending(int count) {
        List<DailyLog> rows = IntStream.range(0, count).mapToObj(this::log).toList();
        when(dailyLogRepository.findByVideoKeyIsNullAndLogDateBetween(any(), any()))
                .thenReturn(rows);
    }

    @Test
    @DisplayName("미완료 row 마다 몽타주 생성을 호출한다")
    void generatesForEachPending() {
        givenPending(3);

        scheduler.sweepPendingMontages();

        verify(montageService, times(3)).generateMontage(any(), any());
    }

    @Test
    @DisplayName("한 건 실패해도 나머지는 계속 처리한다")
    void failureIsolated() {
        givenPending(3);
        doThrow(new RuntimeException("ffmpeg")).when(montageService).generateMontage(eq(1L), any());

        scheduler.sweepPendingMontages();

        verify(montageService, times(3)).generateMontage(any(), any());
    }

    @Test
    @DisplayName("건수 상한을 넘으면 상한까지만 처리한다")
    void capsAtMaxPerRun() {
        ReflectionTestUtils.setField(scheduler, "sweepMaxPerRun", 2);
        givenPending(5);

        scheduler.sweepPendingMontages();

        verify(montageService, times(2)).generateMontage(any(), any());
    }

    @Test
    @DisplayName("시간 예산이 0이면 한 건도 처리하지 않고 다음 틱으로 넘긴다")
    void stopsWhenBudgetExhausted() {
        ReflectionTestUtils.setField(scheduler, "sweepBudgetSeconds", 0L);
        givenPending(3);

        scheduler.sweepPendingMontages();

        verify(montageService, never()).generateMontage(any(), any());
    }

    @Test
    @DisplayName("대상이 없으면 아무것도 하지 않는다")
    void noopWhenEmpty() {
        when(dailyLogRepository.findByVideoKeyIsNullAndLogDateBetween(any(), any()))
                .thenReturn(List.of());

        scheduler.sweepPendingMontages();

        verify(montageService, never()).generateMontage(any(), any());
    }
}

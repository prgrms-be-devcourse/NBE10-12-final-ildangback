package com.gommit.domain.record.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gommit.domain.record.service.RecordCompletionCalculator.TrendData;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RecordCompletionCalculator - 완료율/연속일수/기여도/인증 추이 계산")
class RecordCompletionCalculatorTest {

    private final RecordCompletionCalculator calculator = new RecordCompletionCalculator();

    @Test
    @DisplayName("같은 날 여러 번 체크인해도 완료일수는 하루로 센다")
    void completedDayCountDedupesSameDay() {
        List<LocalDate> dates =
                List.of(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 21));

        int result = calculator.completedDayCount(dates);

        assertThat(result).isEqualTo(2);
    }

    @Test
    @DisplayName("30일 중 27일 완료면 완주율은 90%다")
    void completionRateRounds() {
        int result = calculator.completionRate(27, 30);

        assertThat(result).isEqualTo(90);
    }

    @Test
    @DisplayName("totalDays가 0 이하면 완주율은 0이다")
    void completionRateZeroWhenNoDays() {
        assertThat(calculator.completionRate(5, 0)).isZero();
    }

    @Test
    @DisplayName("그룹 전체 인증 대비 내 인증 비중을 반올림해서 계산한다")
    void contributionRateRounds() {
        int result = calculator.contributionRate(26, 135);

        // 26/135 = 19.26%
        assertThat(result).isEqualTo(19);
    }

    @Test
    @DisplayName("그룹 전체 인증이 0이면 기여도는 0이다")
    void contributionRateZeroWhenGroupTotalZero() {
        assertThat(calculator.contributionRate(0, 0)).isZero();
    }

    @Test
    @DisplayName("연속된 날짜가 있으면 최장 연속일수를 구한다")
    void bestStreakInPeriodFindsLongestRun() {
        // 8/1~8/3 연속(3일), 하루 건너뛰고 8/5~8/6 연속(2일) -> 최장 3일
        List<LocalDate> dates = List.of(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 2),
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 6));

        int result = calculator.bestStreakInPeriod(dates);

        assertThat(result).isEqualTo(3);
    }

    @Test
    @DisplayName("체크인이 하나도 없으면 최장 연속일수는 0이다")
    void bestStreakInPeriodZeroWhenEmpty() {
        assertThat(calculator.bestStreakInPeriod(List.of())).isZero();
    }

    @Test
    @DisplayName("중복 날짜가 있어도 연속일수 계산에 영향을 주지 않는다")
    void bestStreakInPeriodDedupesSameDay() {
        List<LocalDate> dates = List.of(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2));

        assertThat(calculator.bestStreakInPeriod(dates)).isEqualTo(2);
    }

    @Test
    @DisplayName("30일 기간을 7일 단위 5구간(7,7,7,7,2일)으로 나눠 각 구간의 인증 수를 센다")
    void weeklyTrendBucketsByWeek() {
        LocalDate periodStart = LocalDate.of(2026, 8, 20);
        // 1주차(8/20~8/26)에 2번, 2주차(8/27~9/2)에 1번, 마지막 2일 구간(9/17~9/18)에 1번
        List<LocalDate> dates = List.of(
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 21),
                LocalDate.of(2026, 8, 27),
                LocalDate.of(2026, 9, 18));

        TrendData result = calculator.weeklyTrend(dates, periodStart, 30);

        assertThat(result.labels()).containsExactly("1주", "2주", "3주", "4주", "5주");
        assertThat(result.counts()).containsExactly(2, 1, 0, 0, 1);
    }

    @Test
    @DisplayName("기간을 달력 월 단위로 나눠 각 달의 인증 수를 센다")
    void monthlyTrendBucketsByCalendarMonth() {
        LocalDate periodStart = LocalDate.of(2026, 8, 20);
        LocalDate periodEnd = LocalDate.of(2026, 10, 19);
        List<LocalDate> dates = List.of(
                LocalDate.of(2026, 8, 25),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 10, 19));

        TrendData result = calculator.monthlyTrend(dates, periodStart, periodEnd);

        assertThat(result.labels()).containsExactly("8월", "9월", "10월");
        assertThat(result.counts()).containsExactly(1, 2, 1);
    }
}

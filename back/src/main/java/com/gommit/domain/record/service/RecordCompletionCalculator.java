package com.gommit.domain.record.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

// 완료율/연속일수/기여도/인증 추이 계산. "체크인한 날짜 목록"만 있으면 계산할 수 있는
// 순수 통계 로직이라, CheckIn 도메인의 실제 리포지토리 시그니처를 몰라도 미리 짤 수 있다.
// CheckIn이 main에 merge되면 RecordBatchService가 체크인 날짜만 뽑아서 여기 넘기면 된다.
// ChallengeMergeCycleCalculator와 마찬가지로 DB 접근 없는 순수 계산 컴포넌트다.
@Component
public class RecordCompletionCalculator {

    private static final int WEEK_LENGTH_DAYS = 7;

    // "며칠을 인증했는지" - 하루 여러 번 인증해도 날짜 기준으로 한 번만 센다.
    public int completedDayCount(List<LocalDate> checkInDates) {
        return (int) checkInDates.stream().distinct().count();
    }

    // 완주율(%), 반올림. totalDays가 0 이하면 나눌 수 없어서 0으로 처리한다.
    public int completionRate(int completedDayCount, int totalDays) {
        if (totalDays <= 0) {
            return 0;
        }
        return Math.round(completedDayCount * 100f / totalDays);
    }

    // 그룹 전체 인증 대비 이 참여자가 채운 비중(%). groupTotalCheckInCount가 0 이하면 0.
    public int contributionRate(int participantCheckInCount, int groupTotalCheckInCount) {
        if (groupTotalCheckInCount <= 0) {
            return 0;
        }
        return Math.round(participantCheckInCount * 100f / groupTotalCheckInCount);
    }

    // 기간 내 최장 연속 인증일수. 같은 날 중복 체크인은 하루로 취급하고, 날짜가
    // 하루 차이로 이어질 때만 연속으로 센다.
    public int bestStreakInPeriod(List<LocalDate> checkInDates) {
        List<LocalDate> sortedDistinctDates =
                checkInDates.stream().distinct().sorted().toList();
        if (sortedDistinctDates.isEmpty()) {
            return 0;
        }
        int best = 1;
        int current = 1;
        for (int i = 1; i < sortedDistinctDates.size(); i++) {
            boolean consecutive =
                    ChronoUnit.DAYS.between(sortedDistinctDates.get(i - 1), sortedDistinctDates.get(i)) == 1;
            current = consecutive ? current + 1 : 1;
            best = Math.max(best, current);
        }
        return best;
    }

    // "주간 인증 추이"(월간 머지용) - periodStart부터 7일 단위로 묶는다. totalDays가
    // 7의 배수가 아니면 마지막 구간은 그만큼 짧다(예: 30일 -> 5구간: 7,7,7,7,2일).
    public TrendData weeklyTrend(List<LocalDate> checkInDates, LocalDate periodStart, int totalDays) {
        int bucketCount = (int) Math.ceilDiv(totalDays, WEEK_LENGTH_DAYS);
        List<String> labels = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (int i = 0; i < bucketCount; i++) {
            LocalDate bucketStart = periodStart.plusDays((long) i * WEEK_LENGTH_DAYS);
            LocalDate bucketEndExclusive = periodStart.plusDays(Math.min((long) (i + 1) * WEEK_LENGTH_DAYS, totalDays));
            long count = checkInDates.stream()
                    .distinct()
                    .filter(date -> !date.isBefore(bucketStart) && date.isBefore(bucketEndExclusive))
                    .count();
            labels.add((i + 1) + "주");
            counts.add((int) count);
        }
        return new TrendData(labels, counts);
    }

    // "월별 인증 추이"(최종 머지용) - 챌린지 전체 기간을 달력 월 단위로 묶는다.
    public TrendData monthlyTrend(List<LocalDate> checkInDates, LocalDate periodStart, LocalDate periodEnd) {
        List<String> labels = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        YearMonth startMonth = YearMonth.from(periodStart);
        YearMonth endMonth = YearMonth.from(periodEnd);
        for (YearMonth month = startMonth; !month.isAfter(endMonth); month = month.plusMonths(1)) {
            YearMonth bucketMonth = month;
            long count = checkInDates.stream()
                    .distinct()
                    .filter(date -> YearMonth.from(date).equals(bucketMonth))
                    .count();
            labels.add(bucketMonth.getMonthValue() + "월");
            counts.add((int) count);
        }
        return new TrendData(labels, counts);
    }

    /** weeklyTrend/monthlyTrend 결과 - CSV로 합쳐서 MonthlyMergeResult/FinalMergeResult의
     * checkInTrendLabels/checkInTrendCounts 컬럼에 그대로 저장하면 된다. */
    public record TrendData(List<String> labels, List<Integer> counts) {}
}

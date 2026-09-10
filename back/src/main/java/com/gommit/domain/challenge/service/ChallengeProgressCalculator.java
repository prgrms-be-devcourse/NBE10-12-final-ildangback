package com.gommit.domain.challenge.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.global.time.DaysOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ChallengeProgressCalculator {
    // 현재까지 진행된 인증 예정일 수 계산
    public int calculateCurrentDay(Challenge challenge, LocalDate today) {
        if (challenge.getStatus() == ChallengeStatus.READY) {
            return 0;
        }
        if (challenge.getStatus() == ChallengeStatus.ENDED) {
            return challenge.getRequiredDayCount();
        }
        return switch (challenge.getFrequencyType()) {
            case DAILY -> calculateDailyCurrentDay(challenge, today);
            case DAYS_OF_WEEK -> calculateDaysOfWeekCurrentDay(challenge, today);
            case EVERY_N_DAYS -> calculateEveryNDaysCurrentDay(challenge, today);
        };
    }

    // businessDate 직전의 인증 대상일. 시작일 이전이면 null. 종료일 뒤 businessDate 는 종료일까지로 제한한다.
    public LocalDate previousCheckInDay(Challenge challenge, LocalDate businessDate) {
        LocalDate start = challenge.getStartDate();
        LocalDate prev = businessDate.minusDays(1);
        if (prev.isBefore(start)) {
            return null;
        }
        LocalDate ref = prev.isAfter(challenge.getEndDate()) ? challenge.getEndDate() : prev;
        return switch (challenge.getFrequencyType()) {
            case DAILY -> ref;
            case EVERY_N_DAYS -> {
                Integer n = challenge.getFrequencyValue();
                if (n == null || n <= 0) {
                    yield null;
                }
                long elapsed = ChronoUnit.DAYS.between(start, ref);
                yield start.plusDays(Math.floorDiv(elapsed, n) * (long) n);
            }
            case DAYS_OF_WEEK -> {
                Set<DaysOfWeek> scheduled = parseDaysOfWeek(challenge.getDaysOfWeek());
                LocalDate date = ref;
                for (int i = 0; i < 7 && !date.isBefore(start); i++, date = date.minusDays(1)) {
                    if (scheduled.contains(DaysOfWeek.getDaysOfWeek(date.getDayOfWeek()))) {
                        yield date;
                    }
                }
                yield null;
            }
        };
    }

    public double calculatePeriodProgressRate(int currentDay, int totalDays) {
        if (totalDays == 0) {
            return 0.0;
        }
        return Math.round(((double) currentDay / totalDays) * 1000) / 10.0;
    }

    private int calculateDailyCurrentDay(Challenge challenge, LocalDate today) {
        // 스케줄러 꼬일경우 방지
        if (today.isBefore(challenge.getStartDate())) {
            return 0;
        }
        int currentDay = (int) ChronoUnit.DAYS.between(challenge.getStartDate(), today) + 1;
        return Math.min(currentDay, challenge.getRequiredDayCount());
    }

    private int calculateDaysOfWeekCurrentDay(Challenge challenge, LocalDate today) {
        if (today.isBefore(challenge.getStartDate())) {
            return 0;
        }
        if (challenge.getDaysOfWeek() == null || challenge.getDaysOfWeek().isBlank()) {
            return 0;
        }
        List<DaysOfWeek> scheduledDays = Arrays.stream(challenge.getDaysOfWeek().split(","))
                .map(DaysOfWeek::valueOf)
                .toList();
        LocalDate endDate = today.isAfter(challenge.getEndDate()) ? challenge.getEndDate() : today;
        int count = 0;
        for (LocalDate date = challenge.getStartDate(); !date.isAfter(endDate); date = date.plusDays(1)) {
            DaysOfWeek currentDay = DaysOfWeek.getDaysOfWeek(date.getDayOfWeek());
            if (scheduledDays.contains(currentDay)) {
                count++;
            }
        }
        return count;
    }

    private int calculateEveryNDaysCurrentDay(Challenge challenge, LocalDate today) {
        if (today.isBefore(challenge.getStartDate())) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(challenge.getStartDate(), today);
        int currentDay = (int) (days / challenge.getFrequencyValue()) + 1;
        return Math.min(currentDay, challenge.getRequiredDayCount());
    }

    public int calculateRequiredDayCount(
            LocalDate startDate,
            LocalDate endDate,
            FrequencyType frequencyType,
            Integer frequencyValue,
            List<DaysOfWeek> daysOfWeek) {
        return switch (frequencyType) {
            case DAILY -> (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
            case DAYS_OF_WEEK -> {
                if (daysOfWeek == null || daysOfWeek.isEmpty()) {
                    yield 0;
                }
                int count = 0;
                LocalDate date = startDate;
                while (!date.isAfter(endDate)) {
                    DaysOfWeek currentDay = DaysOfWeek.getDaysOfWeek(date.getDayOfWeek());
                    if (daysOfWeek.contains(currentDay)) {
                        count++;
                    }
                    date = date.plusDays(1);
                }
                yield count;
            }
            case EVERY_N_DAYS -> {
                long days = ChronoUnit.DAYS.between(startDate, endDate);
                yield (int) (days / frequencyValue) + 1;
            }
        };
    }

    // date 가 이 챌린지의 인증 예정일인가 — 순수 달력 규칙(기간 + frequency). 챌린지 status 는 보지 않는다.
    // status 는 "지금" 속성이고 date 는 임의 값으로 분리. status 가 필요시 canCheckInOn 사용.
    public boolean isCheckInDay(Challenge challenge, LocalDate date) {
        if (date.isBefore(challenge.getStartDate()) || date.isAfter(challenge.getEndDate())) {
            return false;
        }
        return switch (challenge.getFrequencyType()) {
            case DAILY -> true;
            case DAYS_OF_WEEK -> matchesDayOfWeek(challenge, date);
            case EVERY_N_DAYS -> matchesEveryNDays(challenge, date);
        };
    }

    // date 시점에 실제로 인증 가능한 상태인가 — 예정일이면서 챌린지가 ACTIVE. 표시/게이트용.
    public boolean canCheckInOn(Challenge challenge, LocalDate date) {
        return challenge.getStatus() == ChallengeStatus.ACTIVE && isCheckInDay(challenge, date);
    }

    private boolean matchesDayOfWeek(Challenge challenge, LocalDate date) {
        return parseDaysOfWeek(challenge.getDaysOfWeek()).contains(DaysOfWeek.getDaysOfWeek(date.getDayOfWeek()));
    }

    // daysOfWeek CSV(저장 포맷은 항상 MON..SUN) 를 파싱한다. null/blank 면 빈 집합.
    private Set<DaysOfWeek> parseDaysOfWeek(String csv) {
        EnumSet<DaysOfWeek> days = EnumSet.noneOf(DaysOfWeek.class);
        if (csv == null || csv.isBlank()) {
            return days;
        }
        Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(DaysOfWeek::valueOf)
                .forEach(days::add);
        return days;
    }

    // 시작일로부터 frequencyValue 일 간격의 날(0일차 포함)만 대상일.
    private boolean matchesEveryNDays(Challenge challenge, LocalDate date) {
        Integer n = challenge.getFrequencyValue();
        if (n == null || n <= 0) {
            return false;
        }
        long elapsed = ChronoUnit.DAYS.between(challenge.getStartDate(), date);
        return elapsed >= 0 && elapsed % n == 0;
    }
}

package com.gommit.domain.challenge.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.entity.DaysOfWeek;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

@Component
public class ChallengeProgressCalculator {
    // 현재까지 진행된 인증 예정일 수 계산
    public int calculateCurrentDay(Challenge challenge, LocalDate today) {
        // 아직 시작 전인 챌린지
        if(challenge.getStatus() == ChallengeStatus.READY) {
            return 0;
        }

        // 종료된 챌린지
        if(challenge.getStatus() == ChallengeStatus.ENDED) {
            return challenge.getRequiredDayCount();
        }

        return switch (challenge.getFrequencyType()) {
            case DAILY -> calculateDailyCurrentDay(challenge, today);
            case DAYS_OF_WEEK -> calculateDaysOfWeekCurrentDay(challenge, today);
            case EVERY_N_DAYS -> calculateEveryNDaysCurrentDay(challenge, today);
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
        if(today.isBefore(challenge.getStartDate())) {
            return 0;
        }

        int currentDay = (int) ChronoUnit.DAYS.between(challenge.getStartDate(), today) + 1;

        return Math.min(currentDay, challenge.getRequiredDayCount());
    }

    private int calculateDaysOfWeekCurrentDay(Challenge challenge, LocalDate today) {

        if(today.isBefore(challenge.getStartDate())) {
            return 0;
        }

        List<DaysOfWeek> scheduledDays = Arrays.stream(challenge.getDaysOfWeek().split(",")).map(DaysOfWeek::valueOf).toList();

        LocalDate endDate = today.isAfter(challenge.getEndDate()) ? challenge.getEndDate() : today;

        int count = 0;

        for (
            LocalDate date = challenge.getStartDate();
            !date.isAfter(endDate);
            date = date.plusDays(1)
        ) {

            DaysOfWeek currentDay = DaysOfWeek.getDaysOfWeek(date.getDayOfWeek());

            if (scheduledDays.contains(currentDay)) {
                count++;
            }
        }

        return count;
    }

    private int calculateEveryNDaysCurrentDay(
        Challenge challenge,
        LocalDate today
    ) {
        if(today.isBefore(challenge.getStartDate())) {
            return 0;
        }

        long days = ChronoUnit.DAYS.between(challenge.getStartDate(), today);

        int currentDay = (int) (days / challenge.getFrequencyValue()) + 1;

        return Math.min(currentDay, challenge.getRequiredDayCount());
    }


}

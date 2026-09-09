package com.gommit.domain.challenge.service;

import com.gommit.domain.challenge.entity.Challenge;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 개인 스트릭 = businessDate 부터 직전 인증 대상일로 하루씩 되짚으며, 목표를 채운 날이 이어지는 길이.
// 완료한 날 집합(completedDays)은 호출자가 check_ins 에서 뽑아 넘긴다 — 이 컴포넌트는 달력 규칙만 안다.
@Component
@RequiredArgsConstructor
public class MemberStreakCalculator {

    private final ChallengeProgressCalculator challengeProgressCalculator;

    public int currentStreak(Challenge challenge, LocalDate businessDate, Set<LocalDate> completedDays) {
        int streak = 0;
        for (LocalDate day = businessDate;
                day != null && completedDays.contains(day);
                day = challengeProgressCalculator.previousCheckInDay(challenge, day)) {
            streak++;
        }
        return streak;
    }
}

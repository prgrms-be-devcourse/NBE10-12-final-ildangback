package com.gommit.domain.checkin.policy;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.checkin.entity.CheckInType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

// 챌린지의 빈도 설정(frequencyType)으로 특정 날짜가 인증 대상일인지, 어떤 인증 방식이 허용되는지 판정한다.
// challenge 도메인(#6)에 인증 정책 로직이 생기면 그쪽으로 옮긴다.
@Component
public class CheckInPolicy {

    // 인증 성공 1건당 지급 포인트. [임시값] — 기획 확정 필요.
    // 여러 도메인의 포인트 금액을 한 곳에서 관리하려면 중앙 PointPolicy / @ConfigurationProperties 도입 검토.
    private static final int POINT_PER_CHECK_IN = 10;

    // 인증 1건에 지급할 포인트. challenge 별/스트릭별로 가변이 되면 여기에 파라미터를 추가한다.
    public int checkInReward() {
        return POINT_PER_CHECK_IN;
    }

    // date 가 challenge 의 인증 대상일인가.
    public boolean isCheckInDay(Challenge challenge, LocalDate date) {
        if (date.isBefore(challenge.getStartDate()) || date.isAfter(challenge.getEndDate())) {
            return false;
        }
        return switch (challenge.getFrequencyType()) {
            case DAILY -> true;
            case DAYS_OF_WEEK -> matchesDayOfWeek(challenge.getDaysOfWeek(), date.getDayOfWeek());
            case EVERY_N_DAYS -> matchesEveryNDays(challenge, date);
        };
    }

    // 이 챌린지에서 허용되는 인증 방식 목록.
    public List<CheckInType> allowedTypes(Challenge challenge) {
        return challenge.isAllowPhoto() ? List.of(CheckInType.PHOTO) : List.of();
    }

    public boolean allows(Challenge challenge, CheckInType type) {
        return allowedTypes(challenge).contains(type);
    }

    // "MON,WED,FRI" 또는 "MONDAY,WEDNESDAY" 형태 모두 허용.
    private boolean matchesDayOfWeek(String csv, DayOfWeek target) {
        if (csv == null || csv.isBlank()) {
            return false;
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(token -> token.toUpperCase(java.util.Locale.ROOT))
                .anyMatch(token -> target.name().startsWith(token) || token.startsWith(target.name()));
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

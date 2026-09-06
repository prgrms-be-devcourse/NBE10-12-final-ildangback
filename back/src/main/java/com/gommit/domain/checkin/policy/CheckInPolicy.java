package com.gommit.domain.checkin.policy;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

// challenge 도메인(#6)에 인증 정책 로직이 생기면 그쪽으로 옮긴다. 옮긴 후 policy에 맞지 않는 것만 남으면 재배치.
// 챌린지 설정으로부터 특정 날짜가 인증 대상일인지, 어떤 인증 방식이 허용되는지 판정.
@Component
public class CheckInPolicy {

    // 인증 성공 1건당 지급 포인트. [임시값] — 기획 확정 필요.
    // 여러 도메인의 포인트 금액을 한 곳에서 관리하려면 중앙 PointPolicy / @ConfigurationProperties 도입 검토.
    private static final int POINT_PER_CHECK_IN = 10;

    public int checkInReward() {
        return POINT_PER_CHECK_IN; // challenge 별/스트릭별로 가변이 되면 파라미터를 추가 필요
    }

    // TODO: Challenge 도메인과 요일/주기 판정 로직 중복, 머지 순서에 따라 챌린지 것으로 교체
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

    // TODO: Challenge 도메인의 엔티티에서 제공시 교체
    public List<CheckInType> allowedTypes(Challenge challenge) {
        return challenge.isAllowPhoto() ? List.of(CheckInType.PHOTO) : List.of();
    }

    public void validateCheckInDay(Challenge challenge, LocalDate date) {
        if (!isCheckInDay(challenge, date)) {
            throw new BusinessException(ErrorCode.NOT_CHECK_IN_DAY);
        }
    }

    public void validateAllowedType(Challenge challenge, CheckInType type) {
        if (!allowedTypes(challenge).contains(type)) {
            throw new BusinessException(ErrorCode.CHECK_IN_TYPE_NOT_ALLOWED);
        }
    }

    private boolean matchesDayOfWeek(String csv, DayOfWeek target) {
        if (csv == null || csv.isBlank()) {
            return false;
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(token -> token.toUpperCase(java.util.Locale.ROOT))
                .anyMatch(token -> target.name().startsWith(token));
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

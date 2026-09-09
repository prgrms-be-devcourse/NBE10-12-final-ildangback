package com.gommit.domain.checkin.policy;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.service.ChallengeProgressCalculator;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 지급 포인트, 허용 인증방식 — 각각 프로퍼티화, challenge 엔티티 제공이 되면 클래스 소멸. 일부 남을시 명칭 변경 예정
@Component
@RequiredArgsConstructor
public class CheckInPolicy {

    private final ChallengeProgressCalculator progressCalculator;

    // 인증 성공 1건당 지급 포인트. [임시값] — 기획 확정 필요.
    // 여러 도메인의 포인트 금액을 한 곳에서 관리하려면 중앙 PointPolicy / @ConfigurationProperties 도입 검토.
    private static final int POINT_PER_CHECK_IN = 10;

    public int checkInReward() {
        return POINT_PER_CHECK_IN; // challenge 별/스트릭별로 가변이 되면 파라미터를 추가 필요
    }

    // TODO: Challenge 도메인의 엔티티에서 제공시 교체
    public List<CheckInType> allowedTypes(Challenge challenge) {
        return challenge.isAllowPhoto() ? List.of(CheckInType.PHOTO) : List.of();
    }

    public void validateCheckInDay(Challenge challenge, LocalDate date) {
        if (!progressCalculator.isCheckInDay(challenge, date)) {
            throw new BusinessException(ErrorCode.NOT_CHECK_IN_DAY);
        }
    }

    public void validateAllowedType(Challenge challenge, CheckInType type) {
        if (!allowedTypes(challenge).contains(type)) {
            throw new BusinessException(ErrorCode.CHECK_IN_TYPE_NOT_ALLOWED);
        }
    }
}

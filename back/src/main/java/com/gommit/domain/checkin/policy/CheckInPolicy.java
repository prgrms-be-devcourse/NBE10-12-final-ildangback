package com.gommit.domain.checkin.policy;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.checkin.entity.CheckInType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

// 스켈레톤 — 구현은 "feat: 인증 대상일/허용 방식 판정 정책" 커밋에서 채운다.
@Component
public class CheckInPolicy {

    public int checkInReward() {
        throw new UnsupportedOperationException("미구현");
    }

    public boolean isCheckInDay(Challenge challenge, LocalDate date) {
        throw new UnsupportedOperationException("미구현");
    }

    public List<CheckInType> allowedTypes(Challenge challenge) {
        throw new UnsupportedOperationException("미구현");
    }

    public void validateCheckInDay(Challenge challenge, LocalDate date) {
        throw new UnsupportedOperationException("미구현");
    }

    public void validateAllowedType(Challenge challenge, CheckInType type) {
        throw new UnsupportedOperationException("미구현");
    }
}

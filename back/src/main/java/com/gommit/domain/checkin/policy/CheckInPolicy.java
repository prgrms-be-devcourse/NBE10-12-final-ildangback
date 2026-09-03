package com.gommit.domain.checkin.policy;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.checkin.entity.CheckInType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

// 챌린지의 빈도 설정(frequencyType)으로 특정 날짜가 인증 대상일인지, 어떤 인증 방식이 허용되는지 판정한다.
// challenge 도메인(#6)에 인증 정책 로직이 생기면 그쪽으로 옮긴다.
// TODO(commit): 대상일/허용 방식 판정 구현. 지금은 스켈레톤.
@Component
public class CheckInPolicy {

    public boolean isCheckInDay(Challenge challenge, LocalDate date) {
        throw new UnsupportedOperationException("not implemented");
    }

    public List<CheckInType> allowedTypes(Challenge challenge) {
        throw new UnsupportedOperationException("not implemented");
    }

    public boolean allows(Challenge challenge, CheckInType type) {
        throw new UnsupportedOperationException("not implemented");
    }
}

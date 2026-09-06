package com.gommit.domain.checkin.support;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 스켈레톤 — 구현은 "feat: 인증 서비스 + 정책 + 접근 판정" 커밋에서 채운다.
@Component
@RequiredArgsConstructor
public class CheckInPreconditions {

    private final ChallengeRepository challengeRepository;
    private final ChallengeMemberRepository challengeMemberRepository;

    public Challenge getChallenge(Long challengeId) {
        throw new UnsupportedOperationException("미구현");
    }

    public Challenge getChallengeForActiveMember(Long challengeId, Long userId) {
        throw new UnsupportedOperationException("미구현");
    }

    public Challenge getActiveChallengeForActiveMember(Long challengeId, Long userId) {
        throw new UnsupportedOperationException("미구현");
    }

    public ReadDateAccess resolveReadDateAccess(Long challengeId, Long userId) {
        throw new UnsupportedOperationException("미구현");
    }

    // 조회 접근 범위. maxBusinessDate == null 이면 현 멤버(제한 없음), 값이 있으면 이탈 멤버(해당 날짜 이하만).
    public record ReadDateAccess(Challenge challenge, LocalDate maxBusinessDate) {
        public boolean allows(LocalDate businessDate) {
            return maxBusinessDate == null || !businessDate.isAfter(maxBusinessDate);
        }
    }
}

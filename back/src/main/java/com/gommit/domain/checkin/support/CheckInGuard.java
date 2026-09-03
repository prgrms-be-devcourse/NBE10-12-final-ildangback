package com.gommit.domain.checkin.support;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 인증 API 공통 전제조건 — 챌린지 존재(404) / 참여 자격(403) 확인.
// challenge 도메인(#6)에 접근 제어가 생기면 그쪽으로 옮긴다.
// TODO(commit): 접근 판정 구현. 지금은 스켈레톤.
@Component
@RequiredArgsConstructor
public class CheckInGuard {

    private final ChallengeRepository challengeRepository;
    private final ChallengeMemberRepository challengeMemberRepository;

    public Challenge getChallenge(Long challengeId) {
        throw new UnsupportedOperationException("not implemented");
    }

    public Challenge getChallengeForActiveMember(Long challengeId, Long userId) {
        throw new UnsupportedOperationException("not implemented");
    }

    public ReadAccess resolveReadAccess(Long challengeId, Long userId) {
        throw new UnsupportedOperationException("not implemented");
    }

    // 조회 접근 범위. maxBusinessDate == null 이면 ACTIVE 멤버 — 제한 없음.
    // 값이 있으면 이탈 멤버 — 해당 날짜(이탈일) 이하의 기록만 볼 수 있다.
    public record ReadAccess(Challenge challenge, LocalDate maxBusinessDate) {

        public boolean allows(LocalDate businessDate) {
            return maxBusinessDate == null || !businessDate.isAfter(maxBusinessDate);
        }
    }
}

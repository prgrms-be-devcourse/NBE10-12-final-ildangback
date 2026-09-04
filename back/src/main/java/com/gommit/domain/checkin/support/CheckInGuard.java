package com.gommit.domain.checkin.support;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 인증 API 공통 전제조건 — 챌린지 존재(404) / 참여 자격(403) 확인.
// challenge 도메인(#6)에 접근 제어가 생기면 그쪽으로 옮긴다.
@Component
@RequiredArgsConstructor
public class CheckInGuard {

    private final ChallengeRepository challengeRepository;
    private final ChallengeMemberRepository challengeMemberRepository;

    public Challenge getChallenge(Long challengeId) {
        return challengeRepository
                .findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
    }

    // 챌린지 존재 + 현재 ACTIVE 참여자임을 확인하고 챌린지를 돌려준다. (today / submit 계열)
    public Challenge getChallengeForActiveMember(Long challengeId, Long userId) {
        Challenge challenge = getChallenge(challengeId);
        ChallengeMember member = findMember(challengeId, userId);
        if (member.getStatus() != ChallengeMemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER);
        }
        return challenge;
    }

    // 조회 접근 범위를 판정한다. (gallery / recent / single / media)
    // ACTIVE 참여자 → 제한 없음. 이탈(LEFT/KICKED) → 참여 기간(이탈일) 이하의 기록만.
    public ReadAccess resolveReadAccess(Long challengeId, Long userId) {
        Challenge challenge = getChallenge(challengeId);
        ChallengeMember member = findMember(challengeId, userId);
        if (member.getStatus() == ChallengeMemberStatus.ACTIVE) {
            return new ReadAccess(challenge, null);
        }
        LocalDate leftOn =
                member.getLeftAt() == null ? LocalDate.MIN : member.getLeftAt().toLocalDate();
        return new ReadAccess(challenge, leftOn);
    }

    private ChallengeMember findMember(Long challengeId, Long userId) {
        return challengeMemberRepository
                .findByChallenge_IdAndUserId(challengeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER));
    }

    // 조회 접근 범위. maxBusinessDate == null 이면 ACTIVE 멤버 — 제한 없음.
    // 값이 있으면 이탈 멤버 — 해당 날짜(이탈일) 이하의 기록만 볼 수 있다.
    public record ReadAccess(Challenge challenge, LocalDate maxBusinessDate) {

        public boolean allows(LocalDate businessDate) {
            return maxBusinessDate == null || !businessDate.isAfter(maxBusinessDate);
        }
    }
}

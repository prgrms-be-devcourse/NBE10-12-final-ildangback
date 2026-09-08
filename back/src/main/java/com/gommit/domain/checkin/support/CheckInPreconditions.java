package com.gommit.domain.checkin.support;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.time.BusinessDateUtil;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// TODO: challenge 도메인에 접근 제어가 생기면 이관
// 인증 API 공통 전제조건 — 챌린지 존재(404) / 참여 자격(403) 확인.
@Component
@RequiredArgsConstructor
public class CheckInPreconditions {

    private final ChallengeRepository challengeRepository;
    private final ChallengeMemberRepository challengeMemberRepository;

    public Challenge getChallenge(Long challengeId) {
        return challengeRepository
                .findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
    }

    public Challenge getChallengeForActiveMember(Long challengeId, Long userId) {
        Challenge challenge = getChallenge(challengeId);
        ChallengeMember member = findMember(challengeId, userId);
        if (member.getStatus() != ChallengeMemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER);
        }
        return challenge;
    }

    public Challenge getActiveChallengeForActiveMember(Long challengeId, Long userId) {
        Challenge challenge = getChallengeForActiveMember(challengeId, userId);
        if (challenge.getStatus() != ChallengeStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_ACTIVE);
        }
        return challenge;
    }

    // 조회 접근 기간 범위: ACTIVE 참여자 → 제한 없음. 이탈(LEFT/KICKED) → 참여 기간(이탈일) 이전의 기록만.
    public ReadDateAccess resolveReadDateAccess(Long challengeId, Long userId) {
        Challenge challenge = getChallenge(challengeId);
        ChallengeMember member = findMember(challengeId, userId);
        if (member.getStatus() == ChallengeMemberStatus.ACTIVE) {
            return new ReadDateAccess(challenge, null);
        }
        LocalDate leftOn = member.getLeftAt() == null ? LocalDate.MIN : BusinessDateUtil.of(member.getLeftAt());
        return new ReadDateAccess(challenge, leftOn);
    }

    private ChallengeMember findMember(Long challengeId, Long userId) {
        return challengeMemberRepository
                .findByChallengeIdAndUserId(challengeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER));
    }

    // 조회 접근 범위. maxBusinessDate == null 이면 현 멤버, 제한 없음. 값이 있으면 이탈 멤버, 해당 날짜(이탈일) 이하의 기록만 접근 가능.
    public record ReadDateAccess(Challenge challenge, LocalDate maxBusinessDate) {
        public boolean allows(LocalDate businessDate) {
            return maxBusinessDate == null || !businessDate.isAfter(maxBusinessDate);
        }
    }
}

package com.gommit.domain.group.service;

import com.gommit.domain.challenge.entity.*;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.group.dto.response.KickVoteStatusResponse;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupMember;
import com.gommit.domain.group.entity.GroupMemberStatus;
import com.gommit.domain.group.entity.KickVoteChoice;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.group.repository.GroupMemberRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnerKickVoteService {

    @Value("${group.kick-vote.expiry-hours:24}")
    private int kickVoteExpiryHours;

    private final ChallengeGroupRepository challengeGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ChallengeRepository challengeRepository;
    private final ChallengeMemberRepository challengeMemberRepository;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ── 투표 개시 ─────────────────────────────────────────────────────────────
    @Transactional
    public KickVoteStatusResponse initiateVote(Long groupId, Long initiatorId) {
        ChallengeGroup group = challengeGroupRepository
                .findByIdWithLock(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        GroupMember initiator = getActiveMember(groupId, initiatorId);

        if (group.getOwnerId().equals(initiatorId)) {
            throw new BusinessException(ErrorCode.KICK_VOTE_OWNER_CANNOT_INITIATE);
        }

        challengeRepository
                .findFirstByGroupIdAndStatus(groupId, ChallengeStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_KICK_NOT_ALLOWED));

        if (group.hasActiveKickVote()) {
            throw new BusinessException(ErrorCode.KICK_VOTE_ALREADY_IN_PROGRESS);
        }

        long voteMember = groupMemberRepository.countByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE) - 1;
        if (voteMember <= 0) {
            throw new BusinessException(ErrorCode.KICK_VOTE_NOT_ENOUGH_MEMBERS);
        }

        group.startKickVote();
        initiator.castKickVote(KickVoteChoice.AGREE);
        Long targetOwnerId = group.getOwnerId();
        checkAndConclude(group, groupId);
        return buildResponse(group, groupId, targetOwnerId, initiatorId);
    }

    // ── 투표 참여 ─────────────────────────────────────────────────────────────
    @Transactional
    public KickVoteStatusResponse castVote(Long groupId, Long voterId, KickVoteChoice choice) {
        ChallengeGroup group = challengeGroupRepository
                .findByIdWithLock(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        GroupMember voter = getActiveMember(groupId, voterId);

        if (group.getOwnerId().equals(voterId)) {
            throw new BusinessException(ErrorCode.KICK_VOTE_OWNER_CANNOT_VOTE);
        }

        if (!group.hasActiveKickVote()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (group.isKickVoteExpired(kickVoteExpiryHours)) {
            resetVoteData(group, groupId);
            throw new BusinessException(ErrorCode.KICK_VOTE_EXPIRED);
        }

        if (voter.hasVoted()) {
            throw new BusinessException(ErrorCode.KICK_VOTE_ALREADY_VOTED);
        }

        if (choice == KickVoteChoice.NONE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        voter.castKickVote(choice);
        Long targetOwnerId = group.getOwnerId();
        checkAndConclude(group, groupId);
        return buildResponse(group, groupId, targetOwnerId, voterId);
    }

    // ── 투표 현황 조회 ────────────────────────────────────────────────────────
    @Transactional
    public KickVoteStatusResponse getVoteStatus(Long groupId, Long requesterId) {
        ChallengeGroup group = challengeGroupRepository
                .findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        getActiveMember(groupId, requesterId);

        if (!group.hasActiveKickVote()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (group.isKickVoteExpired(kickVoteExpiryHours)) {
            resetVoteData(group, groupId);
            throw new BusinessException(ErrorCode.KICK_VOTE_EXPIRED);
        }

        return buildResponse(group, groupId, group.getOwnerId(), requesterId);
    }

    // ── 스케줄러 전용 — 만료된 투표 일괄 초기화 ──────────────────────────────
    @Transactional
    public void expireOutdatedVotes() {
        LocalDateTime expiredBefore = LocalDateTime.now().minusHours(kickVoteExpiryHours);
        groupMemberRepository.bulkResetExpiredKickVoteChoice(expiredBefore);
        challengeGroupRepository.bulkExpiredKickVotes(expiredBefore);
    }

    // ── 내부 메서드 ───────────────────────────────────────────────────────────

    // 과반수 판정. n = ACTIVE 멤버 수 - 1 (방장 제외)
    // agree * 2 > n      → 가결: 방장 즉시 강퇴
    // disagree * 2 >= n  → 부결: 투표 종료
    private void checkAndConclude(ChallengeGroup group, Long groupId) {
        long totalActive = groupMemberRepository.countByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE);
        long n = totalActive - 1;

        long agree = groupMemberRepository.countByGroupIdAndStatusAndKickVoteChoice(
                groupId, GroupMemberStatus.ACTIVE, KickVoteChoice.AGREE);
        long disagree = groupMemberRepository.countByGroupIdAndStatusAndKickVoteChoice(
                groupId, GroupMemberStatus.ACTIVE, KickVoteChoice.DISAGREE);

        if (agree * 2 > n) {
            executeOwnerKick(group, groupId);
            resetVoteData(group, groupId);
        } else if (disagree * 2 >= n) {
            resetVoteData(group, groupId);
        }
        // 결론 미달 → 투표 계속
    }

    // 방장 GroupMember KICKED → ChallengeMember 역할 강등 후 KICKED → 새 방장 선정
    private void executeOwnerKick(ChallengeGroup group, Long groupId) {
        Long ownerId = group.getOwnerId();

        groupMemberRepository
                .findByGroupIdAndUserId(groupId, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_MEMBER))
                .kick();

        Challenge activeChallenge = challengeRepository
                .findFirstByGroupIdAndStatus(groupId, ChallengeStatus.ACTIVE)
                .orElse(null);
        Challenge readyChallenge = challengeRepository
                .findFirstByGroupIdAndStatus(groupId, ChallengeStatus.READY)
                .orElse(null);

        if (activeChallenge != null) {
            kickOwnerFromChallenge(activeChallenge, ownerId);
        }
        if (readyChallenge != null) {
            kickOwnerFromChallenge(readyChallenge, ownerId);
        }

        List<GroupMember> remaining =
                groupMemberRepository.findAllByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE);
        if (remaining.isEmpty()) {
            group.end();
            if (activeChallenge != null) activeChallenge.end();
            if (readyChallenge != null) readyChallenge.end();
            return;
        }

        List<GroupMember> candidates;
        if (readyChallenge != null) {
            Set<Long> readyMemberIds =
                    challengeMemberRepository
                            .findAllByChallengeIdAndStatus(readyChallenge.getId(), ChallengeMemberStatus.ACTIVE)
                            .stream()
                            .map(ChallengeMember::getUserId)
                            .collect(Collectors.toSet());
            candidates = remaining.stream()
                    .filter(member -> readyMemberIds.contains(member.getUserId()))
                    .toList();
            if (candidates.isEmpty()) {
                candidates = remaining;
            }
        } else {
            candidates = remaining;
        }

        Long newOwnerId =
                candidates.get(SECURE_RANDOM.nextInt(candidates.size())).getUserId();
        group.changeOwner(newOwnerId);

        if (activeChallenge != null) {
            promoteToOwner(activeChallenge, newOwnerId);
        }
        if (readyChallenge != null) {
            promoteToOwner(readyChallenge, newOwnerId);
        }
    }

    // 방장 ChallengeMember: 역할을 OWNER → MEMBER로 먼저 낮춘 뒤 KICKED 처리
    private void kickOwnerFromChallenge(Challenge challenge, Long ownerId) {
        challengeMemberRepository
                .findByChallengeIdAndUserId(challenge.getId(), ownerId)
                .filter(m -> m.getStatus() == ChallengeMemberStatus.ACTIVE)
                .ifPresent(m -> {
                    m.changeRole(ChallengeMemberRole.MEMBER);
                    m.kick();
                });
    }

    // 새 방장 ChallengeMember role → OWNER
    private void promoteToOwner(Challenge challenge, Long userId) {
        challengeMemberRepository
                .findByChallengeIdAndUserId(challenge.getId(), userId)
                .filter(m -> m.getStatus() == ChallengeMemberStatus.ACTIVE)
                .ifPresent(m -> m.changeRole(ChallengeMemberRole.OWNER));
    }

    // 투표 종료 시 그룹·멤버 모든 투표 데이터 초기화 (완료/부결/만료 공통)
    private void resetVoteData(ChallengeGroup group, Long groupId) {
        group.endKickVote();
        groupMemberRepository.resetAllKickVoteChoices(groupId, KickVoteChoice.NONE);
    }

    // ACTIVE 그룹 멤버 조회 헬퍼
    private GroupMember getActiveMember(Long groupId, Long userId) {
        GroupMember member = groupMemberRepository
                .findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_MEMBER));
        if (member.getStatus() != GroupMemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_GROUP_MEMBER);
        }
        return member;
    }

    // 응답 DTO 조립
    private KickVoteStatusResponse buildResponse(ChallengeGroup group, Long groupId, Long targetOwnerId, Long userId) {
        long totalActive = groupMemberRepository.countByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE);
        int agree = (int) groupMemberRepository.countByGroupIdAndStatusAndKickVoteChoice(
                groupId, GroupMemberStatus.ACTIVE, KickVoteChoice.AGREE);
        int disagree = (int) groupMemberRepository.countByGroupIdAndStatusAndKickVoteChoice(
                groupId, GroupMemberStatus.ACTIVE, KickVoteChoice.DISAGREE);
        KickVoteChoice myChoice = groupMemberRepository
                .findByGroupIdAndUserId(groupId, userId)
                .map(GroupMember::getKickVoteChoice)
                .orElse(KickVoteChoice.NONE);
        LocalDateTime expiresAt =
                group.hasActiveKickVote() ? group.getKickVoteStartedAt().plusHours(kickVoteExpiryHours) : null;

        return new KickVoteStatusResponse(
                groupId,
                targetOwnerId,
                group.hasActiveKickVote(),
                agree,
                disagree,
                (int) totalActive - 1,
                myChoice,
                group.getKickVoteStartedAt(),
                expiresAt);
    }
}

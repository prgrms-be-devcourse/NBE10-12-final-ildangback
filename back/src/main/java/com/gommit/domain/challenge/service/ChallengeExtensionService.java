package com.gommit.domain.challenge.service;

import com.gommit.domain.challenge.dto.request.ExtensionChoiceRequest;
import com.gommit.domain.challenge.dto.response.ExtensionChoiceResponse;
import com.gommit.domain.challenge.entity.*;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.group.entity.GroupMember;
import com.gommit.domain.group.entity.GroupMemberStatus;
import com.gommit.domain.group.repository.GroupMemberRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChallengeExtensionService {
    private final ChallengeRepository challengeRepository;
    private final ChallengeMemberRepository challengeMemberRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ChallengeMemberService challengeMemberService;
    private final ChallengeProgressCalculator challengeProgressCalculator;

    @Transactional
    public ExtensionChoiceResponse updateExtensionChoice(
            Long challengeId, Long userId, ExtensionChoiceRequest request) {
        Challenge challenge = challengeRepository
                .findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        if (challenge.getStatus() != ChallengeStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.EXTENSION_CHOICE_NOT_AVAILABLE);
        }
        ChallengeMember challengeMember = challengeMemberRepository
                .findByChallengeIdAndUserId(challengeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER));
        if (challengeMember.getStatus() != ChallengeMemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER);
        }
        validateExtensionChoicePeriod(challenge);
        validateExtensionChoice(request.choice());
        challengeMember.changeExtensionChoice(request.choice());
        // 현재 ACTIVE 멤버들의 선택 현황 집계
        int pendingCount = (int) challengeMemberRepository.countByChallengeIdAndStatusAndExtensionChoice(
                challengeId, ChallengeMemberStatus.ACTIVE, ExtensionChoice.PENDING);
        int extendCount = (int) challengeMemberRepository.countByChallengeIdAndStatusAndExtensionChoice(
                challengeId, ChallengeMemberStatus.ACTIVE, ExtensionChoice.EXTEND);
        int declineCount = (int) challengeMemberRepository.countByChallengeIdAndStatusAndExtensionChoice(
                challengeId, ChallengeMemberStatus.ACTIVE, ExtensionChoice.DECLINE);
        return new ExtensionChoiceResponse(
                challengeId, userId, challengeMember.getExtensionChoice(), pendingCount, extendCount, declineCount);
    }

    @Transactional
    public void finalizeExtension(Long challengeId) {
        Challenge currentChallenge = challengeRepository
                .findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        closeExtensionChoice(challengeId);
        List<ChallengeMember> extendMembers = getExtendMembers(challengeId);
        if (extendMembers.isEmpty()) {
            leaveDeclineMembers(currentChallenge.getGroupId(), challengeId);
            return;
        }
        ChallengeMember nextOwner = selectNextOwner(challengeId, extendMembers);
        Challenge nextChallenge = createNextChallenge(currentChallenge);
        createNextChallengeMembers(nextChallenge, extendMembers, nextOwner);
        leaveDeclineMembers(currentChallenge.getGroupId(), challengeId);
    }

    private void validateExtensionChoice(ExtensionChoice choice) {
        if (choice != ExtensionChoice.EXTEND && choice != ExtensionChoice.DECLINE) {
            throw new BusinessException(ErrorCode.INVALID_EXTENSION_CHOICE);
        }
    }

    private void validateExtensionChoicePeriod(Challenge challenge) {
        LocalDate deadline = challenge.getEndDate().minusDays(2);
        LocalDate today = LocalDate.now();
        if (today.isAfter(deadline)) {
            throw new BusinessException(ErrorCode.EXTENSION_CHOICE_CLOSED);
        }
    }

    private void closeExtensionChoice(Long challengeId) {
        List<ChallengeMember> pendingMembers =
                challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(
                        challengeId, ChallengeMemberStatus.ACTIVE, ExtensionChoice.PENDING);
        for (ChallengeMember challengeMember : pendingMembers) {
            challengeMember.changeExtensionChoice(ExtensionChoice.DECLINE);
        }
    }

    private List<ChallengeMember> getExtendMembers(Long challengeId) {
        return challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(
                challengeId, ChallengeMemberStatus.ACTIVE, ExtensionChoice.EXTEND);
    }

    private ChallengeMember selectNextOwner(Long challengeId, List<ChallengeMember> extendMembers) {
        ChallengeMember currentOwner = challengeMemberRepository
                .findByChallengeIdAndRole(challengeId, ChallengeMemberRole.OWNER)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_OWNER));
        // 기존 OWNER가 연장을 선택했다면 그대로 OWNER
        if (currentOwner.getExtensionChoice() == ExtensionChoice.EXTEND) {
            return currentOwner;
        }
        // 기존 OWNER가 연장하지 않으면 EXTEND 멤버 중 랜덤 선정
        return extendMembers.get((int) (Math.random() * extendMembers.size()));
    }

    private Challenge createNextChallenge(Challenge currentChallenge) {
        long periodDays = ChronoUnit.DAYS.between(currentChallenge.getStartDate(), currentChallenge.getEndDate());
        // 다음 시즌은 현재 시즌 종료 다음 날 시작
        LocalDate nextStartDate = currentChallenge.getEndDate().plusDays(1);
        // 기본 기간은 현재 시즌과 동일
        LocalDate nextEndDate = nextStartDate.plusDays(periodDays);
        List<DaysOfWeek> daysOfWeek = currentChallenge.getDaysOfWeek() == null ? null : Arrays.stream(currentChallenge.getDaysOfWeek().split(",")).map(String::trim).map(DaysOfWeek::valueOf).toList();
        int requiredDayCount = challengeProgressCalculator.calculateRequiredDayCount(nextStartDate, nextEndDate, currentChallenge.getFrequencyType(), currentChallenge.getFrequencyValue(), daysOfWeek);

        Challenge nextChallenge = Challenge.builder()
                .groupId(currentChallenge.getGroupId())
                .seqNo(currentChallenge.getSeqNo() + 1)
                .startDate(nextStartDate)
                .endDate(nextEndDate)
                // 이전 시즌 설정을 기본값으로 복사
                .frequencyType(currentChallenge.getFrequencyType())
                .frequencyValue(currentChallenge.getFrequencyValue())
                .daysOfWeek(currentChallenge.getDaysOfWeek())
                .dailyCheckInCount(currentChallenge.getDailyCheckInCount())
                .requiredDayCount(requiredDayCount)
                .allowPhoto(currentChallenge.isAllowPhoto())
                // 새 시즌이므로 streak 초기화
                .groupCurrentStreak(0)
                .groupBestStreak(0)
                .build();

        return challengeRepository.save(nextChallenge);
    }

    private void createNextChallengeMembers(
            Challenge nextChallenge, List<ChallengeMember> extendMembers, ChallengeMember nextOwner) {
        for (ChallengeMember member : extendMembers) {
            ChallengeMemberRole role = member.getUserId().equals(nextOwner.getUserId())
                    ? ChallengeMemberRole.OWNER
                    : ChallengeMemberRole.MEMBER;
            challengeMemberService.createChallengeMember(nextChallenge, member.getUserId(), role);
        }
    }

    private void leaveDeclineMembers(Long groupId, Long challengeId) {
        List<ChallengeMember> declinedMembers =
                challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(
                        challengeId, ChallengeMemberStatus.ACTIVE, ExtensionChoice.DECLINE);
        for (ChallengeMember member : declinedMembers) {
            groupMemberRepository
                    .findByGroupIdAndUserId(groupId, member.getUserId())
                    .filter(groupMember -> groupMember.getStatus() == GroupMemberStatus.ACTIVE)
                    .ifPresent(GroupMember::leave);
        }
    }

    @Transactional
    public void finalizeExtensionsDueToday() {
        LocalDate today = LocalDate.now();
        List<Challenge> challenges = challengeRepository.findAllByStatus(ChallengeStatus.ACTIVE);
        for (Challenge challenge : challenges) {
            LocalDate deadline = challenge.getEndDate().minusDays(2);
            if (deadline.equals(today)) {
                finalizeExtension(challenge.getId());
            }
        }
    }
}

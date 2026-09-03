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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChallengeExtensionService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ChallengeRepository challengeRepository;
    private final ChallengeMemberRepository challengeMemberRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ChallengeMemberService challengeMemberService;

    // 다음 시즌 연장 참여 의사 검사
    @Transactional
    public ExtensionChoiceResponse updateExtensionChoice(Long challengeId, Long userId, ExtensionChoiceRequest request) {
        // 챌린지 조회
        Challenge challenge = challengeRepository.findById(challengeId).orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

        // 진해 중인 시즌에서만 연장 의사 선택 가능
        if(challenge.getStatus() != ChallengeStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.EXTENSION_CHOICE_NOT_AVAILABLE);
        }

        // 해당 챌린지의 내 참여 정보 조회
        ChallengeMember challengeMember = challengeMemberRepository.findByChallengeIdAndUserId(challengeId, userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER));

        // 현재 참여 중인 멤버만 연장 의사 선택 가능
        if(challengeMember.getStatus() != ChallengeMemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER);
        }

        // 연장 투표 마감 여부 확인
        validateExtensionChoicePeriod(challenge);

        // EXTEND / DECLINE 선택
        validateExtensionChoice(request.choice());

        // 내 연장 의사 변경
        challengeMember.changeExtensionChoice(request.choice());

        // 현재 ACTIVE 멤버들의 선택 현황 집계
        int pendingCount = (int) challengeMemberRepository.countByChallengeIdAndStatusAndExtensionChoice(challengeId, ChallengeMemberStatus.ACTIVE, ExtensionChoice.PENDING);
        int extendCount = (int) challengeMemberRepository.countByChallengeIdAndStatusAndExtensionChoice(challengeId, ChallengeMemberStatus.ACTIVE, ExtensionChoice.EXTEND);
        int declineCount = (int) challengeMemberRepository.countByChallengeIdAndStatusAndExtensionChoice(challengeId, ChallengeMemberStatus.ACTIVE, ExtensionChoice.DECLINE);

        return new ExtensionChoiceResponse(
            challengeId,
            userId,
            challengeMember.getExtensionChoice(),
            pendingCount,
            extendCount,
            declineCount
        );
    }

    @Transactional
    public void finalizeExtension(Long challengeId) {
        // 현재 시즌 조회
        Challenge currentChallenge = challengeRepository.findById(challengeId).orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

        // 마감 시 PENDING -> DECLINE 처리
        closeExtensionChoice(challengeId);

        // 다음 시즌 참여할 EXTEND 멤버 조회
        List<ChallengeMember> extendMembers = getExtendMembers(challengeId);

        // EXTEND 아무도 없으면 다음 시즌 생성하지 않음
        if(extendMembers.isEmpty()) {
            leaveDeclineMembers(currentChallenge.getGroupId(), challengeId);
            return;
        }

        // 다음 시즌 OWNER 선정
        ChallengeMember nextOwner = selectNextOwner(challengeId, extendMembers);

        // 다음 READY Challenge 생성
        Challenge nextChallenge = createNextChallenge(currentChallenge);

        // EXTEND 멤버들을 다음 시즌 멤버로 생성
        createNextChallengeMembers(nextChallenge, extendMembers, nextOwner);

        // 연장하지 않는 멤버는 그룹에서 LEFT 처리
        leaveDeclineMembers(currentChallenge.getGroupId(), challengeId);
    }



    private void validateExtensionChoice(ExtensionChoice choice) {
        if(choice != ExtensionChoice.EXTEND && choice != ExtensionChoice.DECLINE) {
            throw new BusinessException(ErrorCode.INVALID_EXTENSION_CHOICE);
        }
    }

    private void validateExtensionChoicePeriod(Challenge challenge) {
        LocalDate deadline = challenge.getEndDate().minusDays(2);

        if(LocalDate.now().isAfter(deadline)) {
            throw new BusinessException(ErrorCode.EXTENSION_CHOICE_CLOSED);
        }
    }

    private void closeExtensionChoice(Long challengeId) {
        // 아직 연장 의사를 선택하지 않은 현재 참여 멤버 조회
        List<ChallengeMember> pendingMembers = challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(challengeId, ChallengeMemberStatus.ACTIVE, ExtensionChoice.PENDING);

        // 마감 시 미응답자는 연장 거절로 처리
        for(ChallengeMember challengeMember : pendingMembers) {
            challengeMember.changeExtensionChoice(ExtensionChoice.DECLINE);
        }
    }

    // EXTEND 멤버 조회
    private List<ChallengeMember> getExtendMembers(Long challengeId) {
        return challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(challengeId, ChallengeMemberStatus.ACTIVE, ExtensionChoice.EXTEND);
    }

    private ChallengeMember selectNextOwner(Long challengeId, List<ChallengeMember> extendMembers) {
        // 현 시즌 OWNER 조회
        ChallengeMember currentOwner = challengeMemberRepository.findByChallengeIdAndRole(challengeId, ChallengeMemberRole.OWNER).orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHALLENGE_OWNER));

        // 기존 OWNER가 연장을 선택했다면 그대로 OWNER
        if(currentOwner.getExtensionChoice() == ExtensionChoice.EXTEND) {
            return currentOwner;
        }

        // 기존 OWNER가 연장하지 않으면 EXTEND 멤버 중 랜덤 선정
        return extendMembers.get((int) (Math.random() * extendMembers.size()));
    }

    private Challenge createNextChallenge(Challenge currentChallenge) {
        // 현재 시즌과 동일한 기간 계산
        long periodDays = ChronoUnit.DAYS.between(currentChallenge.getStartDate(), currentChallenge.getEndDate());

        // 다음 시즌은 현재 시즌 종료 다음 날 시작
        LocalDate nextStartDate = currentChallenge.getEndDate().plusDays(1);

        // 기본 기간은 현재 시즌과 동일
        LocalDate nextEndDate = nextStartDate.plusDays(periodDays);

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
            .requiredDayCount(currentChallenge.getRequiredDayCount())
            .allowPhoto(currentChallenge.isAllowPhoto())
            // 새 시즌이므로 streak 초기화
            .groupCurrentStreak(0)
            .groupBestStreak(0)
            .build();

        return challengeRepository.save(nextChallenge);
    }

    private void createNextChallengeMembers(Challenge nextChallenge, List<ChallengeMember> extendMembers, ChallengeMember nextOwner) {
        for(ChallengeMember member : extendMembers) {
            ChallengeMemberRole role = member.getUserId().equals(nextOwner.getUserId()) ? ChallengeMemberRole.OWNER : ChallengeMemberRole.MEMBER;

            challengeMemberService.createChallengeMember(nextChallenge, member.getUserId(), role);
        }
    }

    private void leaveDeclineMembers(Long groupId, Long challengeId) {
        // 연장하지 않는 멤버 조회
        List<ChallengeMember> declinedMembers = challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(challengeId, ChallengeMemberStatus.ACTIVE, ExtensionChoice.DECLINE);

        for(ChallengeMember member : declinedMembers) {
            groupMemberRepository.findByGroupIdAndUserId(groupId, member.getUserId())
                .filter(groupMember -> groupMember.getStatus() == GroupMemberStatus.ACTIVE)
                .ifPresent(GroupMember::leave);
        }
    }

    @Transactional
    public void finalizeExtensionsDueToday() {
        LocalDate today = LocalDate.now(KST);

        List<Challenge> challenges = challengeRepository.findAllByStatus(ChallengeStatus.ACTIVE);

        for (Challenge challenge : challenges) {
            LocalDate deadline = challenge.getEndDate().minusDays(2);

            if (deadline.equals(today)) {
                finalizeExtension(challenge.getId());
            }
        }
    }
}

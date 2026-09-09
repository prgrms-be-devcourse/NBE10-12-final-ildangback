package com.gommit.domain.challenge.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.point.config.PointProperties;
import com.gommit.domain.point.entity.GroupPointReason;
import com.gommit.domain.point.service.GroupPointService;
import com.gommit.domain.user.service.UserService;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 하루 인증 목표 완료 시점의 스트릭 갱신 + 그룹 전원 완료 시 그룹 포인트 적립.
// checkin submit() 트랜잭션 안에서 동기 호출된다.
// - challenge_members : 개인이 그날 목표를 채우면 개인 스트릭
// - users            : 소속 챌린지 중 하나라도 채우면 유저 전역 스트릭
// - challenges        : 이 인증으로 ACTIVE 멤버 전원이 그날 목표를 채우면 그룹 스트릭 + 그룹 포인트
@Service
@RequiredArgsConstructor
public class ChallengeStreakService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeMemberRepository challengeMemberRepository;
    private final ChallengeProgressCalculator challengeProgressCalculator;
    private final UserService userService;
    private final GroupPointService groupPointService;
    private final PointProperties pointProperties;

    @Transactional
    public MemberCheckInResult onMemberDailyComplete(Long challengeId, Long userId, LocalDate businessDate) {
        Challenge challenge = challengeRepository
                .findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        LocalDate previousCheckInDay = challengeProgressCalculator.previousCheckInDay(challenge, businessDate);

        ChallengeMember member = challengeMemberRepository
                .findByChallengeIdAndUserId(challengeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER));
        member.completeDay(businessDate, previousCheckInDay);

        // 유저 전역 스트릭(users.personal_streak) — 이 챌린지의 직전 대상일 기준으로 연속성 판정.
        userService.recordDailyCompletion(userId, businessDate, previousCheckInDay);

        List<ChallengeMember> activeMembers =
                challengeMemberRepository.findAllByChallengeIdAndStatus(challengeId, ChallengeMemberStatus.ACTIVE);
        int groupTotalCount = activeMembers.size();
        int groupCompletedCount = (int)
                activeMembers.stream().filter(m -> m.hasCompleted(businessDate)).count();

        boolean groupJustCompleted = false;
        if (groupTotalCount > 0
                && groupCompletedCount == groupTotalCount
                && !businessDate.equals(challenge.getGroupLastCompletedDate())) {
            challenge.completeGroupDay(businessDate, previousCheckInDay);
            // 그룹 포인트 적립 — 같은 트랜잭션. 그룹당 하루 1회(위 가드), 마지막 완료자 1명만 도달.
            groupPointService.reward(
                    challenge.getGroupId(),
                    pointProperties.groupDailyAllComplete(),
                    GroupPointReason.DAILY_ALL_COMPLETE,
                    "전원 하루 인증 완료");
            groupJustCompleted = true;
        }

        return new MemberCheckInResult(
                member.getCurrentStreak(), groupCompletedCount, groupTotalCount, groupJustCompleted);
    }
}

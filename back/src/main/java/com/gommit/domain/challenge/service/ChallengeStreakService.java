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
        // 그룹 하루 완료 판정을 group_points 행 잠금으로 직렬화한다. 같은 그룹의 마지막 인증이 동시에 들어와도
        // 잠금 뒤 재조회 시 앞선 인증의 완료가 반영돼, 그룹 스트릭/포인트가 정확히 1회만 처리된다.
        // (challenge 행을 잠그면 check_ins → challenges FK 의 공유잠금과 엇갈려 데드락)
        // 이 호출은 group_points 행을 만들며 영속성 컨텍스트를 비울 수 있으므로, 뒤에서 수정할 엔티티는 그 다음에 로드한다.
        Long groupId = challengeRepository
                .findById(challengeId)
                .map(Challenge::getGroupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        groupPointService.lockForGroupCompletion(groupId);

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

        // 잠금 읽기 — REPEATABLE READ 스냅샷을 우회해 앞선 인증들이 커밋한 완료 상태까지 반영해 전원 완료를 판정한다.
        List<ChallengeMember> activeMembers = challengeMemberRepository.findAllForUpdateByChallengeIdAndStatus(
                challengeId, ChallengeMemberStatus.ACTIVE);
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

package com.gommit.domain.record.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.checkin.repository.CheckInRepository.UserBusinessDate;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.point.config.PointProperties;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.repository.UserPointHistoryRepository;
import com.gommit.domain.point.repository.UserPointHistoryRepository.UserEarnedAmount;
import com.gommit.domain.point.service.PersonalPointService;
import com.gommit.domain.record.entity.FinalMerge;
import com.gommit.domain.record.entity.FinalMergeResult;
import com.gommit.domain.record.entity.MonthlyMerge;
import com.gommit.domain.record.entity.MonthlyMergeResult;
import com.gommit.domain.record.repository.FinalMergeRepository;
import com.gommit.domain.record.repository.FinalMergeResultRepository;
import com.gommit.domain.record.repository.MonthlyMergeRepository;
import com.gommit.domain.record.repository.MonthlyMergeResultRepository;
import com.gommit.domain.record.service.RecordCompletionCalculator.TrendData;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.time.BusinessClock;
import com.gommit.global.time.BusinessDayCutoff;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 월간/최종 머지 "생성" 담당. 회차 계산은 ChallengeMergeCycleCalculator, 통계 계산은
// RecordCompletionCalculator에 이미 짜여 있어서, 여기서는 대상을 찾고 체크인/포인트
// 데이터를 뽑아 넘긴 뒤 결과를 저장하는 역할만 한다. (Should) 중도 탈퇴자는 그 시점 이미
// 포인트가 회수되므로(PersonalPointService.recoverChallengePoints), 머지 집계 대상에서도 뺀다.
@Service
@RequiredArgsConstructor
public class RecordBatchService {

    // 최종 완주로 인정하는 최소 완료율(%). 이 미만이면 완주 보너스를 못 받는다.
    private static final int CHALLENGE_BONUS_MIN_COMPLETION_RATE = 50;

    private final ChallengeRepository challengeRepository;
    private final ChallengeMemberRepository challengeMemberRepository;
    private final ChallengeGroupRepository challengeGroupRepository;
    private final CheckInRepository checkInRepository;
    private final UserPointHistoryRepository userPointHistoryRepository;
    private final MonthlyMergeRepository monthlyMergeRepository;
    private final MonthlyMergeResultRepository monthlyMergeResultRepository;
    private final FinalMergeRepository finalMergeRepository;
    private final FinalMergeResultRepository finalMergeResultRepository;
    private final PersonalPointService personalPointService;
    private final PointProperties pointProperties;
    private final ChallengeMergeCycleCalculator cycleCalculator;
    private final RecordCompletionCalculator completionCalculator;
    private final BusinessClock businessClock;

    // 스케줄러가 매일 호출. ACTIVE 챌린지 중 30일 주기가 지난 걸 찾아 월간 머지를 생성한다.
    @Transactional
    public void generateDueMonthlyMerges() {
        LocalDate today = businessClock.today();
        for (Challenge challenge : challengeRepository.findAllByStatus(ChallengeStatus.ACTIVE)) {
            generateDueMonthlyMerges(challenge, today);
        }
    }

    private void generateDueMonthlyMerges(Challenge challenge, LocalDate today) {
        int totalMonthlyMergeCount =
                cycleCalculator.totalMonthlyMergeCount(challenge.getStartDate(), challenge.getEndDate());
        int seqNo = cycleCalculator.nextSeqNo(monthlyMergeRepository.countByChallengeId(challenge.getId()));
        while (seqNo <= totalMonthlyMergeCount) {
            LocalDate periodStart = cycleCalculator.cycleStartDate(challenge.getStartDate(), seqNo);
            LocalDate periodEnd = periodStart.plusDays(cycleCalculator.cycleLengthDays() - 1L);
            if (!today.isAfter(periodEnd)) {
                // 이 회차 기간이 아직 안 끝났다(오늘이 periodEnd 당일이면 그 businessDate는
                // 다음날 04:00 컷오프 전까지 체크인이 계속 들어올 수 있어 아직 마감 전이다).
                // 다음 배치 실행 때 다시 확인.
                return;
            }
            if (!monthlyMergeRepository.existsByChallengeIdAndSeqNo(challenge.getId(), seqNo)) {
                publishMonthlyMerge(challenge, seqNo, periodStart, periodEnd);
            }
            seqNo++;
        }
    }

    private void publishMonthlyMerge(Challenge challenge, int seqNo, LocalDate periodStart, LocalDate periodEnd) {
        List<ChallengeMember> members = activeMembers(challenge.getId());
        if (members.isEmpty()) {
            return;
        }
        MergeInputs inputs = collectMergeInputs(challenge, members, periodStart, periodEnd);
        List<MemberStat> stats = buildMemberStats(members, inputs, periodStart, inputs.totalDays);

        MonthlyMerge merge = MonthlyMerge.create(
                challenge.getId(),
                seqNo,
                periodStart,
                periodEnd,
                inputs.totalDays,
                inputs.totalCheckInCount,
                averageCompletionRate(stats),
                LocalDateTime.now());
        monthlyMergeRepository.save(merge);

        for (MemberStat stat : stats) {
            TrendData trend = completionCalculator.weeklyTrend(stat.checkInDates, periodStart, inputs.totalDays);
            monthlyMergeResultRepository.save(MonthlyMergeResult.of(
                    merge.getId(),
                    stat.userId,
                    stat.ranking,
                    stat.completionRate,
                    stat.completedDayCount,
                    stat.totalCheckInCount,
                    stat.bestStreakInPeriod,
                    stat.earnedPoints,
                    stat.contributionRate,
                    joinLabels(trend),
                    joinCounts(trend)));
        }
    }

    // 챌린지를 ENDED로 바꾸는 트랜잭션 안에서 Challenge 도메인이 호출한다.
    // 완료율 기반 완주 보너스 포인트 지급까지 여기서 처리한다.
    @Transactional
    public void generateFinalMerge(Long challengeId) {
        if (finalMergeRepository.existsByChallengeId(challengeId)) {
            return;
        }
        Challenge challenge = challengeRepository
                .findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        List<ChallengeMember> members = activeMembers(challengeId);
        if (members.isEmpty()) {
            return;
        }
        LocalDate periodStart = challenge.getStartDate();
        LocalDate periodEnd = challenge.getEndDate();
        MergeInputs inputs = collectMergeInputs(challenge, members, periodStart, periodEnd);
        List<MemberStat> stats = buildMemberStats(members, inputs, periodStart, inputs.totalDays);

        FinalMerge merge = FinalMerge.create(
                challengeId,
                periodStart,
                periodEnd,
                inputs.totalDays,
                inputs.totalCheckInCount,
                averageCompletionRate(stats),
                LocalDateTime.now());
        finalMergeRepository.save(merge);

        String groupName = challengeGroupRepository.findNameById(challenge.getGroupId()).orElse("챌린지");
        int bonus = pointProperties.mergeBonus();
        for (MemberStat stat : stats) {
            TrendData trend = completionCalculator.monthlyTrend(stat.checkInDates, periodStart, periodEnd);
            finalMergeResultRepository.save(FinalMergeResult.of(
                    merge.getId(),
                    stat.userId,
                    stat.ranking,
                    stat.completionRate,
                    stat.completedDayCount,
                    stat.totalCheckInCount,
                    stat.bestStreakInPeriod,
                    stat.earnedPoints,
                    stat.contributionRate,
                    joinLabels(trend),
                    joinCounts(trend)));
            if (bonus > 0 && stat.completionRate >= CHALLENGE_BONUS_MIN_COMPLETION_RATE) {
                personalPointService.reward(stat.userId, challengeId, bonus, UserPointReason.CHALLENGE_BONUS, groupName);
            }
        }
    }

    private List<ChallengeMember> activeMembers(Long challengeId) {
        return challengeMemberRepository.findAllByChallengeIdAndStatus(challengeId, ChallengeMemberStatus.ACTIVE);
    }

    // 체크인 날짜/적립 포인트를 참여자 전원분 한 번에 뽑는다(N+1 방지).
    private MergeInputs collectMergeInputs(
            Challenge challenge, List<ChallengeMember> members, LocalDate periodStart, LocalDate periodEnd) {
        List<Long> userIds = members.stream().map(ChallengeMember::getUserId).toList();

        Map<Long, List<LocalDate>> checkInDatesByUserId =
                checkInRepository
                        .findBusinessDatesByChallengeIdAndUserIdInAndBusinessDateBetween(
                                challenge.getId(), userIds, periodStart, periodEnd)
                        .stream()
                        .collect(Collectors.groupingBy(
                                UserBusinessDate::getUserId,
                                Collectors.mapping(UserBusinessDate::getBusinessDate, Collectors.toList())));

        Map<Long, Integer> earnedPointsByUserId = userPointHistoryRepository
                .sumEarnedByChallengeIdAndUserIdInBetween(
                        challenge.getId(),
                        userIds,
                        BusinessDayCutoff.startTimeOfBusinessDate(periodStart),
                        BusinessDayCutoff.startTimeOfBusinessDate(periodEnd.plusDays(1)))
                .stream()
                .collect(Collectors.toMap(UserEarnedAmount::getUserId, UserEarnedAmount::getAmount));

        int totalDays = totalDays(periodStart, periodEnd);
        int totalCheckInCount =
                checkInDatesByUserId.values().stream().mapToInt(List::size).sum();
        return new MergeInputs(checkInDatesByUserId, earnedPointsByUserId, totalDays, totalCheckInCount);
    }

    private List<MemberStat> buildMemberStats(
            List<ChallengeMember> members, MergeInputs inputs, LocalDate periodStart, int totalDays) {
        List<MemberStat> stats = new ArrayList<>();
        for (ChallengeMember member : members) {
            Long userId = member.getUserId();
            List<LocalDate> checkInDates = inputs.checkInDatesByUserId.getOrDefault(userId, List.of());
            int completedDayCount = completionCalculator.completedDayCount(checkInDates);
            int completionRate = completionCalculator.completionRate(completedDayCount, totalDays);
            int totalCheckInCount = checkInDates.size();
            stats.add(new MemberStat(
                    userId,
                    checkInDates,
                    completedDayCount,
                    completionRate,
                    totalCheckInCount,
                    completionCalculator.bestStreakInPeriod(checkInDates),
                    inputs.earnedPointsByUserId.getOrDefault(userId, 0),
                    completionCalculator.contributionRate(totalCheckInCount, inputs.totalCheckInCount)));
        }
        assignRankings(stats);
        return stats;
    }

    // 완료율 내림차순 랭킹(동률은 같은 순위, 다음 순위는 그만큼 건너뜀 - 1,1,3 방식).
    // 동률이면 총 인증횟수 내림차순, 그래도 같으면 userId 오름차순으로 정렬해 결과를 결정적으로 만든다.
    private void assignRankings(List<MemberStat> stats) {
        stats.sort(Comparator.comparingInt((MemberStat s) -> s.completionRate)
                .reversed()
                .thenComparing(Comparator.comparingInt((MemberStat s) -> s.totalCheckInCount)
                        .reversed())
                .thenComparingLong(s -> s.userId));
        int rank = 1;
        for (int i = 0; i < stats.size(); i++) {
            if (i > 0 && stats.get(i).completionRate != stats.get(i - 1).completionRate) {
                rank = i + 1;
            }
            stats.get(i).ranking = rank;
        }
    }

    private int averageCompletionRate(List<MemberStat> stats) {
        if (stats.isEmpty()) {
            return 0;
        }
        return (int) Math.round(
                stats.stream().mapToInt(s -> s.completionRate).average().orElse(0));
    }

    private int totalDays(LocalDate periodStart, LocalDate periodEnd) {
        return (int) cycleCalculator.totalDays(periodStart, periodEnd);
    }

    private String joinLabels(TrendData trend) {
        return String.join(",", trend.labels());
    }

    private String joinCounts(TrendData trend) {
        return trend.counts().stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    // 챌린지 전체 참여자 대상 체크인/포인트 조회 결과를 한데 묶어 넘기는 내부 전달용 값.
    private record MergeInputs(
            Map<Long, List<LocalDate>> checkInDatesByUserId,
            Map<Long, Integer> earnedPointsByUserId,
            int totalDays,
            int totalCheckInCount) {}

    // 참여자 한 명의 계산된 통계. ranking은 assignRankings에서 정렬 후 채워 넣어야 해서 가변이다.
    private static final class MemberStat {
        private final Long userId;
        private final List<LocalDate> checkInDates;
        private final int completedDayCount;
        private final int completionRate;
        private final int totalCheckInCount;
        private final int bestStreakInPeriod;
        private final int earnedPoints;
        private final int contributionRate;
        private int ranking;

        private MemberStat(
                Long userId,
                List<LocalDate> checkInDates,
                int completedDayCount,
                int completionRate,
                int totalCheckInCount,
                int bestStreakInPeriod,
                int earnedPoints,
                int contributionRate) {
            this.userId = userId;
            this.checkInDates = checkInDates;
            this.completedDayCount = completedDayCount;
            this.completionRate = completionRate;
            this.totalCheckInCount = totalCheckInCount;
            this.bestStreakInPeriod = bestStreakInPeriod;
            this.earnedPoints = earnedPoints;
            this.contributionRate = contributionRate;
        }
    }
}

package com.gommit.domain.record.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.record.dto.response.ChallengeMergeOverviewResponse;
import com.gommit.domain.record.dto.response.FinalMergeDetailResponse;
import com.gommit.domain.record.dto.response.MergeParticipantResponse;
import com.gommit.domain.record.dto.response.MergeSummaryResponse;
import com.gommit.domain.record.dto.response.MonthlyMergeDetailResponse;
import com.gommit.domain.record.dto.response.MyMonthlyMergeResponse;
import com.gommit.domain.record.entity.FinalMerge;
import com.gommit.domain.record.entity.FinalMergeResult;
import com.gommit.domain.record.entity.MonthlyMerge;
import com.gommit.domain.record.entity.MonthlyMergeResult;
import com.gommit.domain.record.repository.FinalMergeRepository;
import com.gommit.domain.record.repository.FinalMergeResultRepository;
import com.gommit.domain.record.repository.MonthlyMergeRepository;
import com.gommit.domain.record.repository.MonthlyMergeResultRepository;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 조회 전용 - 머지 생성(배치)은 RecordBatchService가 따로 담당한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecordQueryService {

    private final MonthlyMergeRepository monthlyMergeRepository;
    private final MonthlyMergeResultRepository monthlyMergeResultRepository;
    private final FinalMergeRepository finalMergeRepository;
    private final FinalMergeResultRepository finalMergeResultRepository;
    private final UserRepository userRepository;
    private final ChallengeRepository challengeRepository;
    private final ChallengeMemberRepository challengeMemberRepository;
    private final ChallengeGroupRepository challengeGroupRepository;
    private final ChallengeMergeCycleCalculator cycleCalculator;

    // 챌린지 화면의 "월간 머지 목록"에 최종 머지가 있으면 맨 위에 함께 보여준다
    // (발행 시점상 최종 머지가 항상 가장 나중이라 이 순서로 충분하다).
    public List<MergeSummaryResponse> getMergeList(Long challengeId) {
        List<MergeSummaryResponse> summaries = new ArrayList<>();
        finalMergeRepository
                .findByChallengeId(challengeId)
                .map(MergeSummaryResponse::from)
                .ifPresent(summaries::add);
        monthlyMergeRepository.findByChallengeIdOrderBySeqNoDesc(challengeId).stream()
                .map(MergeSummaryResponse::from)
                .forEach(summaries::add);
        return summaries;
    }

    public MonthlyMergeDetailResponse getMonthlyMergeDetail(Long challengeId, int seqNo) {
        MonthlyMerge merge = monthlyMergeRepository
                .findByChallengeIdAndSeqNo(challengeId, seqNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.MONTHLY_MERGE_NOT_FOUND));
        List<MonthlyMergeResult> results =
                monthlyMergeResultRepository.findByMonthlyMergeIdOrderByRanking(merge.getId());
        Map<Long, String> nicknamesById =
                nicknamesOf(results.stream().map(MonthlyMergeResult::getUserId).toList());
        List<MergeParticipantResponse> participants = results.stream()
                .map(result -> MergeParticipantResponse.from(result, nicknamesById.get(result.getUserId())))
                .toList();
        return MonthlyMergeDetailResponse.of(merge, participants);
    }

    public FinalMergeDetailResponse getFinalMergeDetail(Long challengeId) {
        FinalMerge merge = finalMergeRepository
                .findByChallengeId(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FINAL_MERGE_NOT_FOUND));
        List<FinalMergeResult> results = finalMergeResultRepository.findByFinalMergeIdOrderByRanking(merge.getId());
        Map<Long, String> nicknamesById =
                nicknamesOf(results.stream().map(FinalMergeResult::getUserId).toList());
        List<MergeParticipantResponse> participants = results.stream()
                .map(result -> MergeParticipantResponse.from(result, nicknamesById.get(result.getUserId())))
                .toList();
        return FinalMergeDetailResponse.of(merge, participants);
    }

    // 참여자 목록에 표시할 닉네임을 한 번에 조회한다(참여자 수만큼 User를 따로 조회하지 않도록).
    private Map<Long, String> nicknamesOf(List<Long> userIds) {
        return userRepository.findAllById(userIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
    }

    // 결과별로 부모 MonthlyMerge를 따로 조회하면 N+1이 나서, ID 모아서 한 번에 조회한다.
    public SliceResponse<MyMonthlyMergeResponse> getMyMonthlyMergeArchive(Long userId, Long cursor, int size) {
        List<MonthlyMergeResult> rows =
                monthlyMergeResultRepository.findHistoriesByUserId(userId, cursor, PageRequest.of(0, size + 1));
        List<Long> mergeIds = rows.stream()
                .map(MonthlyMergeResult::getMonthlyMergeId)
                .distinct()
                .toList();
        Map<Long, MonthlyMerge> mergesById = monthlyMergeRepository.findAllById(mergeIds).stream()
                .collect(Collectors.toMap(MonthlyMerge::getId, Function.identity()));

        List<MyMonthlyMergeResponse> content = rows.stream()
                .map(result -> MyMonthlyMergeResponse.of(result, mergesById.get(result.getMonthlyMergeId())))
                .toList();
        return SliceResponse.ofCursor(content, size, MyMonthlyMergeResponse::id);
    }

    // 챌린지 하나의 머지 진행 현황. "월간 머지 목록" 화면 상단 요약 카드에서 쓴다.
    public ChallengeMergeOverviewResponse getChallengeMergeOverview(Long challengeId) {
        Challenge challenge = challengeRepository
                .findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        ChallengeGroup group = challengeGroupRepository
                .findById(challenge.getGroupId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        int completedMergeCount = monthlyMergeRepository.countByChallengeId(challengeId);
        boolean hasFinalMerge = finalMergeRepository.existsByChallengeId(challengeId);
        return toOverview(challenge, group, completedMergeCount, hasFinalMerge);
    }

    // 월간 머지 아카이브(프로필) 최상위 화면 - 내가 속한 챌린지마다 하나씩 요약을 만든다.
    public List<ChallengeMergeOverviewResponse> getMyChallengeMergeOverviews(Long userId) {
        List<Long> challengeIds = challengeMemberRepository.findAllByUserId(userId).stream()
                .map(ChallengeMember::getChallenge)
                .map(Challenge::getId)
                .distinct()
                .toList();
        List<Challenge> challenges = challengeRepository.findAllById(challengeIds);
        List<Long> groupIds =
                challenges.stream().map(Challenge::getGroupId).distinct().toList();
        Map<Long, ChallengeGroup> groupsById = challengeGroupRepository.findAllById(groupIds).stream()
                .collect(Collectors.toMap(ChallengeGroup::getId, Function.identity()));

        // 챌린지마다 countByChallengeId/existsByChallengeId를 따로 부르면 N+1이 나서,
        // 전체를 한 번에 조회해 챌린지별로 묶는다.
        Map<Long, Long> monthlyMergeCountByChallengeId =
                monthlyMergeRepository.findAllByChallengeIdIn(challengeIds).stream()
                        .collect(Collectors.groupingBy(MonthlyMerge::getChallengeId, Collectors.counting()));
        Set<Long> challengeIdsWithFinalMerge = finalMergeRepository.findAllByChallengeIdIn(challengeIds).stream()
                .map(FinalMerge::getChallengeId)
                .collect(Collectors.toSet());

        return challenges.stream()
                .map(challenge -> toOverview(
                        challenge,
                        groupsById.get(challenge.getGroupId()),
                        monthlyMergeCountByChallengeId
                                .getOrDefault(challenge.getId(), 0L)
                                .intValue(),
                        challengeIdsWithFinalMerge.contains(challenge.getId())))
                .toList();
    }

    private ChallengeMergeOverviewResponse toOverview(
            Challenge challenge, ChallengeGroup group, int completedMergeCount, boolean hasFinalMerge) {
        LocalDate startDate = challenge.getStartDate();
        LocalDate endDate = challenge.getEndDate();
        int totalDays = (int) cycleCalculator.totalDays(startDate, endDate);
        int totalMergeCount = cycleCalculator.totalMonthlyMergeCount(startDate, endDate);

        Integer currentSeqNo = null;
        Integer currentCycleDay = null;
        LocalDate today = LocalDate.now();
        if (!hasFinalMerge && completedMergeCount < totalMergeCount && !today.isAfter(endDate)) {
            currentSeqNo = cycleCalculator.nextSeqNo(completedMergeCount);
            LocalDate cycleStart = cycleCalculator.cycleStartDate(startDate, currentSeqNo);
            currentCycleDay = cycleCalculator.cycleDayOf(cycleStart, today);
        }

        return new ChallengeMergeOverviewResponse(
                challenge.getId(),
                group.getName(),
                group.getCategory().name(),
                startDate,
                endDate,
                totalDays,
                cycleCalculator.cycleLengthDays(),
                totalMergeCount,
                completedMergeCount,
                hasFinalMerge,
                currentSeqNo,
                currentCycleDay);
    }
}

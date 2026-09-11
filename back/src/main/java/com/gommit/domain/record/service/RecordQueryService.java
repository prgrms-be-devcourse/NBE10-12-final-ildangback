package com.gommit.domain.record.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupCategory;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.record.dto.response.CategoryStatResponse;
import com.gommit.domain.record.dto.response.ChallengeMergeOverviewResponse;
import com.gommit.domain.record.dto.response.FinalMergeDetailResponse;
import com.gommit.domain.record.dto.response.HeatmapCellResponse;
import com.gommit.domain.record.dto.response.MergeParticipantResponse;
import com.gommit.domain.record.dto.response.MergeSummaryResponse;
import com.gommit.domain.record.dto.response.MonthlyMergeDetailResponse;
import com.gommit.domain.record.dto.response.MonthlyTrendItemResponse;
import com.gommit.domain.record.dto.response.MyMonthlyMergeResponse;
import com.gommit.domain.record.dto.response.PersonalStatsResponse;
import com.gommit.domain.record.dto.response.SummaryStatResponse;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
    // (발행 시점상 최종 머지가 항상 가장 나중이라 publishedAt 내림차순 = 이 순서다).
    // 한 챌린지의 머지 총 개수는 상한이 있어(30일 주기라 아무리 길어도 몇십 개) 두 테이블을
    // 합친 뒤 메모리에서 커서로 자른다 - 모바일 화면은 다 무한스크롤로 통일하기로 했다.
    public SliceResponse<MergeSummaryResponse> getMergeList(Long challengeId, Long requesterId, Long cursor, int size) {
        requireActiveMember(challengeId, requesterId);
        List<MergeSummaryResponse> summaries = new ArrayList<>();
        finalMergeRepository
                .findByChallengeId(challengeId)
                .map(MergeSummaryResponse::from)
                .ifPresent(summaries::add);
        monthlyMergeRepository.findByChallengeIdOrderBySeqNoDesc(challengeId).stream()
                .map(MergeSummaryResponse::from)
                .forEach(summaries::add);

        List<MergeSummaryResponse> page = summaries.stream()
                .filter(m -> cursor == null || mergeCursorOf(m) < cursor)
                .limit(size + 1L)
                .toList();
        return SliceResponse.ofCursor(page, size, this::mergeCursorOf);
    }

    // publishedAt을 커서로 쓴다 - 위 목록 순서(최종 머지가 항상 가장 나중에 발행됨)와
    // 그대로 일치해서, 두 테이블의 독립적인 id 대신 이걸 정렬/커서 기준으로 삼을 수 있다.
    private long mergeCursorOf(MergeSummaryResponse merge) {
        return merge.publishedAt().toEpochSecond(ZoneOffset.UTC);
    }

    public MonthlyMergeDetailResponse getMonthlyMergeDetail(Long challengeId, int seqNo, Long requesterId) {
        requireActiveMember(challengeId, requesterId);
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

    public FinalMergeDetailResponse getFinalMergeDetail(Long challengeId, Long requesterId) {
        requireActiveMember(challengeId, requesterId);
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

    // 그룹 머지 결과는 "그룹 한정 공개"라 그 챌린지의 ACTIVE 멤버만 볼 수 있다 -
    // challengeId만 알면 아무나 열람 가능한 BOLA를 막는다.
    private void requireActiveMember(Long challengeId, Long requesterId) {
        boolean isActiveMember =
                challengeMemberRepository.existsActiveMember(challengeId, requesterId, ChallengeMemberStatus.ACTIVE);
        if (!isActiveMember) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
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
    public ChallengeMergeOverviewResponse getChallengeMergeOverview(Long challengeId, Long requesterId) {
        requireActiveMember(challengeId, requesterId);
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
    // 한 유저가 동시에 속한 챌린지 수도 상한이 있어(그룹 여러 개를 동시에 하진 않는다)
    // 필터링/정렬을 메모리에서 하고 무한스크롤 규약(SliceResponse)에 맞춰 커서로 자른다.
    public SliceResponse<ChallengeMergeOverviewResponse> getMyChallengeMergeOverviews(
            Long userId, String keyword, GroupCategory category, Long cursor, int size) {
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

        List<ChallengeMergeOverviewResponse> overviews = challenges.stream()
                .sorted(Comparator.comparing(Challenge::getId).reversed())
                .map(challenge -> toOverview(
                        challenge,
                        groupsById.get(challenge.getGroupId()),
                        monthlyMergeCountByChallengeId
                                .getOrDefault(challenge.getId(), 0L)
                                .intValue(),
                        challengeIdsWithFinalMerge.contains(challenge.getId())))
                .filter(o -> category == null || category.name().equals(o.category()))
                .filter(o ->
                        keyword == null || keyword.isBlank() || o.groupName().contains(keyword))
                .filter(o -> cursor == null || o.challengeId() < cursor)
                .toList();

        List<ChallengeMergeOverviewResponse> page =
                overviews.stream().limit(size + 1L).toList();
        return SliceResponse.ofCursor(page, size, ChallengeMergeOverviewResponse::challengeId);
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

    // "개인 전체 통계" 화면(GET /users/me/stats). 발행된 머지 결과(스냅샷)만 집계한다.
    // from/to는 회차 period와 겹치는지로 필터, 둘 다 없으면 전체 기간.
    public PersonalStatsResponse getMyStats(Long userId, LocalDate from, LocalDate to) {
        List<MonthlyMergeResult> monthlyResults = monthlyMergeResultRepository.findAllByUserId(userId);
        List<FinalMergeResult> finalResults = finalMergeResultRepository.findAllByUserId(userId);
        Map<Long, MonthlyMerge> monthlyMergesById = monthlyMergeRepository
                .findAllById(monthlyResults.stream()
                        .map(MonthlyMergeResult::getMonthlyMergeId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(MonthlyMerge::getId, Function.identity()));
        Map<Long, FinalMerge> finalMergesById =
                finalMergeRepository
                        .findAllById(finalResults.stream()
                                .map(FinalMergeResult::getFinalMergeId)
                                .distinct()
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(FinalMerge::getId, Function.identity()));

        List<StatRow> allRows = new ArrayList<>();
        for (MonthlyMergeResult result : monthlyResults) {
            MonthlyMerge merge = monthlyMergesById.get(result.getMonthlyMergeId());
            if (merge != null) {
                allRows.add(StatRow.of(merge.getChallengeId(), merge, result));
            }
        }
        for (FinalMergeResult result : finalResults) {
            FinalMerge merge = finalMergesById.get(result.getFinalMergeId());
            if (merge != null) {
                allRows.add(StatRow.of(merge.getChallengeId(), merge, result));
            }
        }

        Map<Long, ChallengeInfo> infoByChallengeId = challengeInfoByChallengeId(
                allRows.stream().map(StatRow::challengeId).distinct().toList());
        // 그룹/챌린지가 삭제되는 등 정합성이 깨져 정보를 못 찾은 회차는 통계에서 제외한다.
        List<StatRow> rows = allRows.stream()
                .filter(row -> infoByChallengeId.containsKey(row.challengeId()))
                .filter(row -> overlaps(row, from, to))
                .toList();

        return new PersonalStatsResponse(
                buildSummary(rows),
                buildMonthlyTrend(rows),
                buildCategoryBreakdown(rows, infoByChallengeId),
                buildHeatmap(rows));
    }

    private SummaryStatResponse buildSummary(List<StatRow> rows) {
        Set<Long> completedChallengeIds =
                rows.stream().filter(StatRow::isFinal).map(StatRow::challengeId).collect(Collectors.toSet());
        Set<Long> allChallengeIds = rows.stream().map(StatRow::challengeId).collect(Collectors.toSet());

        return new SummaryStatResponse(
                rows.stream().mapToInt(StatRow::totalCheckInCount).sum(),
                rows.stream().mapToInt(StatRow::completedDayCount).sum(),
                rows.stream().mapToInt(StatRow::missedDayCount).sum(),
                rows.stream().mapToInt(StatRow::bestStreakInPeriod).max().orElse(0),
                averageCompletionRate(rows),
                completedChallengeIds.size(),
                allChallengeIds.size() - completedChallengeIds.size());
    }

    // 달력 월(periodStart가 속한 달) 기준으로 묶어서 시간순으로 정렬한다.
    private List<MonthlyTrendItemResponse> buildMonthlyTrend(List<StatRow> rows) {
        return rows.stream().collect(Collectors.groupingBy(StatRow::monthBucket)).entrySet().stream()
                .map(entry -> new MonthlyTrendItemResponse(
                        entry.getKey(),
                        entry.getValue().stream()
                                .mapToInt(StatRow::totalCheckInCount)
                                .sum(),
                        averageCompletionRate(entry.getValue())))
                .sorted(Comparator.comparing(MonthlyTrendItemResponse::month))
                .toList();
    }

    private List<CategoryStatResponse> buildCategoryBreakdown(
            List<StatRow> rows, Map<Long, ChallengeInfo> infoByChallengeId) {
        return rows.stream()
                .collect(Collectors.groupingBy(
                        row -> infoByChallengeId.get(row.challengeId()).category()))
                .entrySet()
                .stream()
                .map(entry -> new CategoryStatResponse(
                        entry.getKey(),
                        (int) entry.getValue().stream()
                                .map(StatRow::challengeId)
                                .distinct()
                                .count(),
                        entry.getValue().stream()
                                .mapToInt(StatRow::totalCheckInCount)
                                .sum(),
                        averageCompletionRate(entry.getValue()),
                        entry.getValue().stream()
                                .mapToInt(StatRow::missedDayCount)
                                .sum()))
                .sorted(Comparator.comparing(CategoryStatResponse::averageCompletionRate)
                        .reversed())
                .toList();
    }

    private static final int HEATMAP_LEVELS = 4;

    // 하루 단위 체크인 로그가 없어서 실제 잔디처럼 일 단위는 못 만들고, 그 달의 평균
    // 완주율을 0~4단계 진하기로 근사한다.
    private List<HeatmapCellResponse> buildHeatmap(List<StatRow> rows) {
        return rows.stream().collect(Collectors.groupingBy(StatRow::monthBucket)).entrySet().stream()
                .map(entry -> new HeatmapCellResponse(
                        entry.getKey(),
                        Math.min(HEATMAP_LEVELS, averageCompletionRate(entry.getValue()) * HEATMAP_LEVELS / 100)))
                .sorted(Comparator.comparing(HeatmapCellResponse::month))
                .toList();
    }

    // 회차 기간 [periodStart, periodEnd]가 조회 범위 [from, to]와 겹치는지. from/to가
    // null이면 그쪽은 제한이 없는 것으로 취급한다.
    private boolean overlaps(StatRow row, LocalDate from, LocalDate to) {
        if (from != null && row.periodEnd().isBefore(from)) {
            return false;
        }
        return to == null || !row.periodStart().isAfter(to);
    }

    private int averageCompletionRate(List<StatRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        return (int) Math.round(
                rows.stream().mapToInt(StatRow::completionRate).average().orElse(0));
    }

    // challengeId -> (그룹명, 카테고리) - 그룹/챌린지 정보를 못 찾은 challengeId는
    // 맵에서 빠지고, 호출부가 그 회차를 통계에서 제외한다.
    private Map<Long, ChallengeInfo> challengeInfoByChallengeId(List<Long> challengeIds) {
        Map<Long, Challenge> challengesById = challengeRepository.findAllById(challengeIds).stream()
                .collect(Collectors.toMap(Challenge::getId, Function.identity()));
        List<Long> groupIds = challengesById.values().stream()
                .map(Challenge::getGroupId)
                .distinct()
                .toList();
        Map<Long, ChallengeGroup> groupsById = challengeGroupRepository.findAllById(groupIds).stream()
                .collect(Collectors.toMap(ChallengeGroup::getId, Function.identity()));

        Map<Long, ChallengeInfo> result = new HashMap<>();
        for (Long challengeId : challengeIds) {
            Challenge challenge = challengesById.get(challengeId);
            ChallengeGroup group = challenge == null ? null : groupsById.get(challenge.getGroupId());
            if (group != null) {
                result.put(
                        challengeId,
                        new ChallengeInfo(group.getName(), group.getCategory().name()));
            }
        }
        return result;
    }

    // getMyStats 계산용 중간 값 - MonthlyMergeResult/FinalMergeResult 두 출처를
    // 같은 모양으로 합치기 위한 내부 전용 타입이라 public DTO로 노출하지 않는다.
    private record StatRow(
            Long challengeId,
            boolean isFinal,
            LocalDate periodStart,
            LocalDate periodEnd,
            int totalDays,
            int completionRate,
            int completedDayCount,
            int totalCheckInCount,
            int earnedPoints,
            int bestStreakInPeriod) {

        static StatRow of(Long challengeId, MonthlyMerge merge, MonthlyMergeResult result) {
            return new StatRow(
                    challengeId,
                    false,
                    merge.getPeriodStart(),
                    merge.getPeriodEnd(),
                    merge.getTotalDays(),
                    result.getCompletionRate(),
                    result.getCompletedDayCount(),
                    result.getTotalCheckInCount(),
                    result.getEarnedPoints(),
                    result.getBestStreakInPeriod());
        }

        static StatRow of(Long challengeId, FinalMerge merge, FinalMergeResult result) {
            return new StatRow(
                    challengeId,
                    true,
                    merge.getPeriodStart(),
                    merge.getPeriodEnd(),
                    merge.getTotalDays(),
                    result.getCompletionRate(),
                    result.getCompletedDayCount(),
                    result.getTotalCheckInCount(),
                    result.getEarnedPoints(),
                    result.getBestStreakInPeriod());
        }

        int missedDayCount() {
            return Math.max(0, totalDays - completedDayCount);
        }

        /** 달력 월 버킷 키("yyyy-MM") - periodStart가 속한 달로 묶는다. */
        String monthBucket() {
            return periodStart.getYear() + "-" + String.format("%02d", periodStart.getMonthValue());
        }
    }

    private record ChallengeInfo(String groupName, String category) {}
}

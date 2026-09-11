package com.gommit.domain.home.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.checkin.repository.CheckInRepository.CheckInCountByDate;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupCategory;
import com.gommit.domain.group.entity.GroupStatus;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.group.service.GroupService;
import com.gommit.domain.home.dto.response.*;
import com.gommit.domain.item.dto.response.CharacterResponse;
import com.gommit.domain.item.service.UserItemService;
import com.gommit.domain.point.entity.UserPointHistory;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.service.PersonalPointService;
import com.gommit.domain.user.dto.response.UserProfileResponse;
import com.gommit.domain.user.service.UserService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.time.BusinessClock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {
    private final UserService userService;
    private final UserItemService userItemService;
    private final PersonalPointService pointService;
    private final GroupService groupService;
    private final ChallengeRepository challengeRepository;
    private final ChallengeGroupRepository challengeGroupRepository;
    private final CheckInRepository checkInRepository;
    private final BusinessClock businessClock;

    // 홈 화면 조회
    public HomeResponse getHome(Long userId) {
        UserProfileResponse userProfile = userService.getMyProfile(userId);
        CharacterResponse character = userItemService.getMyCharacter(userId);
        String statusMessage = userProfile.introduction();

        LocalDate firstDayOfMonth = businessClock.firstDayOfBusinessMonth();
        LocalDate businessDate = businessClock.today();

        long monthlyCheckInCount = checkInRepository.countMine(userId, null, null, firstDayOfMonth, businessDate);
        long activeDays = checkInRepository.countDistinctDatesByUserIdBetween(userId, firstDayOfMonth, businessDate);
        long elapsedDays = ChronoUnit.DAYS.between(firstDayOfMonth, businessDate) + 1;

        int monthlyCompletionRate = elapsedDays == 0 ? 0 : (int) (activeDays * 100 / elapsedDays);
        HomeSummaryResponse summary =
                new HomeSummaryResponse(userProfile.personalStreak(), (int) monthlyCheckInCount, monthlyCompletionRate);

        List<TodayChallengeResponse> todayChallenges =
                groupService.getMyGroups(userId, GroupStatus.ACTIVE, null, 100).content().stream()
                        .filter(g -> g.challengeStatus() == ChallengeStatus.ACTIVE)
                        .map(g -> new TodayChallengeResponse(
                                g.challengeId(),
                                g.groupId(),
                                g.name(),
                                g.category(),
                                g.todayCheckInCount(),
                                g.dailyCheckInCount(),
                                g.todayCompleted()))
                        .toList();
        int todayTotalCount = todayChallenges.size();
        int todayCompletedCount = (int) todayChallenges.stream()
                .filter(TodayChallengeResponse::completed)
                .count();

        boolean hasUnreadNotification = false;
        int pointBalance = pointService.getMyBalance(userId).balance();

        return new HomeResponse(
                userProfile.nickname(),
                pointBalance,
                statusMessage,
                character,
                summary,
                todayChallenges,
                todayTotalCount,
                todayCompletedCount,
                hasUnreadNotification,
                businessDate);
    }

    // 꼬밋 잔디 조회
    public SliceResponse<GrassResponse> getGrass(Long userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        if (ChronoUnit.DAYS.between(from, to) > 366) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);

        Map<LocalDate, Long> countByDate = checkInRepository.countByUserIdGroupByDateBetween(userId, from, to).stream()
                .collect(Collectors.toMap(CheckInCountByDate::getBusinessDate, CheckInCountByDate::getCount));

        List<GrassResponse> result = new ArrayList<>();
        LocalDate date = from;
        while (!date.isAfter(to)) {
            int count = countByDate.getOrDefault(date, 0L).intValue();
            result.add(new GrassResponse(date, count, toGrassLevel(count)));
            date = date.plusDays(1);
        }
        return new SliceResponse<>(result, false, null);
    }

    // 인증 횟수 -> 잔디 레벨 변환 규칙
    private int toGrassLevel(int count) {
        return Math.min(count, 4);
    }

    // 최근 활동 조회
    public SliceResponse<ActivityResponse> getActivities(Long userId) {
        List<UserPointHistory> histories = pointService.getRecentHistories(userId, 3);

        // CHECK_IN인 항목의 challengeId만 수집
        List<Long> challengeIds = histories.stream()
                .filter(h -> h.getReason() == UserPointReason.CHECK_IN && h.getChallengeId() != null)
                .map(UserPointHistory::getChallengeId)
                .distinct()
                .toList();

        // challengeId -> GroupCategory
        Map<Long, GroupCategory> challengeCategoryMap = new HashMap<>();
        if (!challengeIds.isEmpty()) {
            Map<Long, Long> challengeToGroupId = challengeRepository.findAllById(challengeIds).stream()
                    .collect(Collectors.toMap(Challenge::getId, Challenge::getGroupId));

            Map<Long, GroupCategory> groupIdToCategory =
                    challengeGroupRepository.findAllById(challengeToGroupId.values()).stream()
                            .collect(Collectors.toMap(ChallengeGroup::getId, ChallengeGroup::getCategory));

            challengeToGroupId.forEach((cId, gId) -> challengeCategoryMap.put(cId, groupIdToCategory.get(gId)));
        }

        List<ActivityResponse> content = histories.stream()
                .map(h -> toActivityResponse(h, challengeCategoryMap))
                .toList();

        return new SliceResponse<>(content, false, null);
    }

    private ActivityResponse toActivityResponse(UserPointHistory history, Map<Long, GroupCategory> categoryMap) {
        GroupCategory category = history.getChallengeId() != null ? categoryMap.get(history.getChallengeId()) : null;
        return new ActivityResponse(
                history.getReason(),
                toCommitPrefix(history.getReason(), category),
                history.getSourceName(),
                history.getAmount(),
                history.getCreatedAt());
    }

    private String toCommitPrefix(UserPointReason reason, GroupCategory category) {
        return switch (reason) {
            case CHECK_IN ->
                category == null
                        ? "feat:"
                        : switch (category) {
                            case DEV, STUDY, JOB -> "feat:";
                            case READING, LIFE -> "docs:";
                            case EXERCISE, HEALTH -> "workout:";
                            case ETC -> "chore:";
                        };
            case CHALLENGE_BONUS, MONTHLY_MERGE_BONUS -> "feat:";
            case ITEM_PURCHASE -> "chore:";
            case WITHDRAWAL_PENALTY -> "fix:";
        };
    }
}

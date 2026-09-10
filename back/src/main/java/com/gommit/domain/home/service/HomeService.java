package com.gommit.domain.home.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupCategory;
import com.gommit.domain.group.entity.GroupStatus;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.group.service.GroupService;
import com.gommit.domain.home.dto.response.*;
import com.gommit.domain.item.dto.response.CharacterResponse;
import com.gommit.domain.item.service.UserItemService;
import com.gommit.domain.point.dto.response.UserPointHistoryResponse;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.service.PersonalPointService;
import com.gommit.domain.user.dto.response.UserProfileResponse;
import com.gommit.domain.user.service.UserService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    // TODO Phase1: private final CheckInService checkInService;

    // 홈 화면 조회
    public HomeResponse getHome(Long userId) {
        UserProfileResponse userProfile = userService.getMyProfile(userId);
        // 아이템 도메인 머지 후 getMyCharacter 수정
        CharacterResponse character = userItemService.getMyCharacter(userId);
        String statusMessage = userProfile.introduction();

        // TODO Phase1: CheckIn 연동 후 교체
        int monthlyCheckInCount = 0;
        int monthlyCompletionRate = 0;
        HomeSummaryResponse summary =
                new HomeSummaryResponse(userProfile.personalStreak(), monthlyCheckInCount, monthlyCompletionRate);

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
        LocalDate businessDate =
                LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(4).toLocalDate();
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
    public List<GrassResponse> getGrass(Long userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        if (ChronoUnit.DAYS.between(from, to) > 366) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);

        // TODO Phase1: CheckIn 연동 후 교체 - 날짜별 체크인 횟수
        Map<LocalDate, Long> countByDate = Map.of();

        List<GrassResponse> result = new ArrayList<>();
        LocalDate date = from;
        while (!date.isAfter(to)) {
            int count = countByDate.getOrDefault(date, 0L).intValue();
            result.add(new GrassResponse(date, count, toGrassLevel(count)));
            date = date.plusDays(1);
        }
        return result;
    }

    // 인증 횟수 -> 잔디 레벨 변환 규칙
    private int toGrassLevel(int count) {
        return Math.min(count, 4);
    }

    // 최근 활동 조회
    public ActivityListResponse getActivities(Long userId) {
        SliceResponse<UserPointHistoryResponse> histories =
                pointService.getMyHistories(userId, null, null, null, null, null, null, 3);

        // CHECK_IN인 항목의 challengeId만 수집
        List<Long> challengeIds = histories.content().stream()
                .filter(h -> h.reason() == UserPointReason.CHECK_IN && h.challengeId() != null)
                .map(UserPointHistoryResponse::challengeId)
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

        List<ActivityResponse> content = histories.content().stream()
                .map(h -> toActivityResponse(h, challengeCategoryMap))
                .toList();

        return new ActivityListResponse(content);
    }

    private ActivityResponse toActivityResponse(
            UserPointHistoryResponse history, Map<Long, GroupCategory> categoryMap) {
        GroupCategory category = history.challengeId() != null ? categoryMap.get(history.challengeId()) : null;
        return new ActivityResponse(
                history.reason(),
                toCommitPrefix(history.reason(), category),
                history.sourceName(),
                history.amount(),
                history.createdAt());
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

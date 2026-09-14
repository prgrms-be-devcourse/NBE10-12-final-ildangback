package com.gommit.domain.record.dto.response;

import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.record.entity.FinalMergeResult;
import com.gommit.domain.record.entity.MonthlyMergeResult;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record MergeParticipantResponse(
        Long userId,
        String nickname,
        int ranking,
        int completionRate,
        int completedDayCount,
        int totalCheckInCount,
        int bestStreakInPeriod,
        int earnedPoints,
        int contributionRate,
        List<String> checkInTrendLabels,
        List<Integer> checkInTrendCounts,
        Map<ItemSlot, String> characterSlots) {

    public static MergeParticipantResponse from(MonthlyMergeResult result, String nickname) {
        return new MergeParticipantResponse(
                result.getUserId(),
                nickname,
                result.getRanking(),
                result.getCompletionRate(),
                result.getCompletedDayCount(),
                result.getTotalCheckInCount(),
                result.getBestStreakInPeriod(),
                result.getEarnedPoints(),
                result.getContributionRate(),
                splitLabels(result.getCheckInTrendLabels()),
                splitCounts(result.getCheckInTrendCounts()),
                characterSlots(
                        result.getHeadImageUrl(),
                        result.getTopImageUrl(),
                        result.getBottomImageUrl(),
                        result.getShoesImageUrl()));
    }

    public static MergeParticipantResponse from(FinalMergeResult result, String nickname) {
        return new MergeParticipantResponse(
                result.getUserId(),
                nickname,
                result.getRanking(),
                result.getCompletionRate(),
                result.getCompletedDayCount(),
                result.getTotalCheckInCount(),
                result.getBestStreakInPeriod(),
                result.getEarnedPoints(),
                result.getContributionRate(),
                splitLabels(result.getCheckInTrendLabels()),
                splitCounts(result.getCheckInTrendCounts()),
                characterSlots(
                        result.getHeadImageUrl(),
                        result.getTopImageUrl(),
                        result.getBottomImageUrl(),
                        result.getShoesImageUrl()));
    }

    private static Map<ItemSlot, String> characterSlots(String head, String top, String bottom, String shoes) {
        Map<ItemSlot, String> slots = new EnumMap<>(ItemSlot.class);
        slots.put(ItemSlot.HEAD, head);
        slots.put(ItemSlot.TOP, top);
        slots.put(ItemSlot.BOTTOM, bottom);
        slots.put(ItemSlot.SHOES, shoes);
        return slots;
    }

    private static List<String> splitLabels(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).toList();
    }

    private static List<Integer> splitCounts(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(MergeParticipantResponse::parseCountOrZero)
                .toList();
    }

    // 배치가 채우는 값이라 항상 숫자 형식이어야 하지만, 데이터가 손상됐을 때 조회
    // API 전체가 500으로 죽는 대신 0으로 방어하고 로그만 남긴다.
    private static int parseCountOrZero(String token) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            log.warn("checkInTrendCounts 파싱 실패 - 0으로 대체합니다: token={}", token);
            return 0;
        }
    }
}

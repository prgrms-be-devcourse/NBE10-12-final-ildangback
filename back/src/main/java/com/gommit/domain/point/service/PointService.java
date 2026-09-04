package com.gommit.domain.point.service;

import com.gommit.domain.point.dto.request.PeriodFilter;
import com.gommit.domain.point.dto.request.PointChangeType;
import com.gommit.domain.point.dto.response.GroupPointBalanceResponse;
import com.gommit.domain.point.dto.response.GroupPointHistoryResponse;
import com.gommit.domain.point.dto.response.PointBalanceResponse;
import com.gommit.domain.point.dto.response.UserPointHistoryResponse;
import com.gommit.domain.point.entity.GroupPoint;
import com.gommit.domain.point.entity.GroupPointHistory;
import com.gommit.domain.point.entity.GroupPointReason;
import com.gommit.domain.point.entity.UserPoint;
import com.gommit.domain.point.entity.UserPointHistory;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.repository.GroupPointHistoryRepository;
import com.gommit.domain.point.repository.GroupPointRepository;
import com.gommit.domain.point.repository.UserPointHistoryRepository;
import com.gommit.domain.point.repository.UserPointRepository;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// reward/deduct/rewardGroup/deductGroup은 REST API가 아니라 CheckIn, Item 등 다른 도메인이
// 자기 트랜잭션 안에서 직접 호출하는 내부 계약이다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

    private final UserPointHistoryRepository userPointHistoryRepository;
    private final GroupPointHistoryRepository groupPointHistoryRepository;
    private final UserPointRepository userPointRepository;
    private final GroupPointRepository groupPointRepository;

    // ===== 내부 계약: 지급/차감 =====
    // 잔액 행(UserPoint/GroupPoint)을 SELECT ... FOR UPDATE로 잠근 뒤 갱신한다.
    // 같은 유저/그룹에 대한 동시 호출이 순서대로 처리되도록 보장하기 위함이다.

    @Transactional
    public void reward(Long userId, Long challengeId, int amount, UserPointReason reason, String sourceName) {
        UserPoint point = lockOrCreateUserPoint(userId);
        point.add(amount);
        userPointHistoryRepository.save(
                UserPointHistory.of(userId, challengeId, sourceName, amount, reason, point.getBalance()));
    }

    @Transactional
    public void deduct(Long userId, int amount, UserPointReason reason, String sourceName) {
        UserPoint point = lockOrCreateUserPoint(userId);
        if (point.getBalance() < amount) {
            throw new BusinessException(ErrorCode.POINT_INSUFFICIENT);
        }
        point.add(-amount);
        userPointHistoryRepository.save(
                UserPointHistory.of(userId, null, sourceName, -amount, reason, point.getBalance()));
    }

    @Transactional
    public void rewardGroup(Long groupId, int amount, GroupPointReason reason, String sourceName) {
        GroupPoint point = lockOrCreateGroupPoint(groupId);
        point.add(amount);
        groupPointHistoryRepository.save(GroupPointHistory.of(groupId, sourceName, amount, reason, point.getBalance()));
    }

    @Transactional
    public void deductGroup(Long groupId, int amount, GroupPointReason reason, String sourceName) {
        GroupPoint point = lockOrCreateGroupPoint(groupId);
        if (point.getBalance() < amount) {
            throw new BusinessException(ErrorCode.POINT_INSUFFICIENT);
        }
        point.add(-amount);
        groupPointHistoryRepository.save(
                GroupPointHistory.of(groupId, sourceName, -amount, reason, point.getBalance()));
    }

    // ===== 조회: 개인 포인트 =====

    public PointBalanceResponse getMyBalance(Long userId) {
        LocalDateTime startOfThisMonth = startOfBusinessMonth(currentBusinessMonthFirstDay());
        int balance = userPointRepository
                .findByUserId(userId)
                .map(UserPoint::getBalance)
                .orElse(0);
        int monthlyEarned = userPointHistoryRepository.sumEarnedFrom(userId, startOfThisMonth);
        int monthlySpent = userPointHistoryRepository.sumSpentFrom(userId, startOfThisMonth);
        int totalEarned = userPointHistoryRepository.sumEarnedAll(userId);
        return new PointBalanceResponse(balance, monthlyEarned, monthlySpent, totalEarned);
    }

    public SliceResponse<UserPointHistoryResponse> getMyHistories(
            Long userId,
            PeriodFilter period,
            PointChangeType type,
            UserPointReason reason,
            LocalDate from,
            LocalDate to,
            Long cursor,
            int size) {
        LocalDateTime[] range = toDateRange(period, from, to);
        var rows = userPointHistoryRepository.findHistories(
                userId, cursor, range[0], range[1], reason, toEarnFlag(type), PageRequest.of(0, size + 1));
        var content = rows.stream().map(UserPointHistoryResponse::from).toList();
        return SliceResponse.ofCursor(content, size, UserPointHistoryResponse::id);
    }

    public UserPointHistoryResponse getMyHistoryDetail(Long userId, Long historyId) {
        UserPointHistory history = userPointHistoryRepository
                .findById(historyId)
                .filter(h -> h.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.POINT_HISTORY_NOT_FOUND));
        return UserPointHistoryResponse.from(history);
    }

    // ===== 조회: 그룹 포인트 =====
    // TODO(Point): 그룹 존재 여부(GROUP_NOT_FOUND)와 멤버십(NOT_GROUP_MEMBER) 검증은
    // Group 도메인의 ChallengeGroupRepository/GroupMemberRepository가 필요하다.
    // Group 도메인 구현 완료 후 검증 로직을 채워 넣을 것.

    public GroupPointBalanceResponse getGroupBalance(Long groupId) {
        int balance = groupPointRepository
                .findByGroupId(groupId)
                .map(GroupPoint::getBalance)
                .orElse(0);
        return new GroupPointBalanceResponse(groupId, balance);
    }

    public SliceResponse<GroupPointHistoryResponse> getGroupHistories(
            Long groupId,
            PeriodFilter period,
            PointChangeType type,
            GroupPointReason reason,
            LocalDate from,
            LocalDate to,
            Long cursor,
            int size) {
        LocalDateTime[] range = toDateRange(period, from, to);
        var rows = groupPointHistoryRepository.findHistories(
                groupId, cursor, range[0], range[1], reason, toEarnFlag(type), PageRequest.of(0, size + 1));
        var content = rows.stream().map(GroupPointHistoryResponse::from).toList();
        return SliceResponse.ofCursor(content, size, GroupPointHistoryResponse::id);
    }

    public GroupPointHistoryResponse getGroupHistoryDetail(Long groupId, Long historyId) {
        GroupPointHistory history = groupPointHistoryRepository
                .findById(historyId)
                .filter(h -> h.getGroupId().equals(groupId))
                .orElseThrow(() -> new BusinessException(ErrorCode.POINT_HISTORY_NOT_FOUND));
        return GroupPointHistoryResponse.from(history);
    }

    // ===== 내부 헬퍼 =====

    // 갭 락 데드락을 피하려고 없으면 만들고 나서 잠근다(순서 중요).
    // 생성은 ON DUPLICATE KEY UPDATE로 멱등하게 한다 - save()는 커밋 시점에 INSERT가
    // 나가서 유니크 제약 위반을 catch로 못 막는다.
    private UserPoint lockOrCreateUserPoint(Long userId) {
        if (!userPointRepository.existsByUserId(userId)) {
            userPointRepository.insertZeroBalanceIfAbsent(userId, LocalDateTime.now());
        }
        return userPointRepository
                .findWithLockByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("UserPoint 생성에 실패했다. userId=" + userId));
    }

    private GroupPoint lockOrCreateGroupPoint(Long groupId) {
        if (!groupPointRepository.existsByGroupId(groupId)) {
            groupPointRepository.insertZeroBalanceIfAbsent(groupId, LocalDateTime.now());
        }
        return groupPointRepository
                .findWithLockByGroupId(groupId)
                .orElseThrow(() -> new IllegalStateException("GroupPoint 생성에 실패했다. groupId=" + groupId));
    }

    private static Boolean toEarnFlag(PointChangeType type) {
        if (type == null || type == PointChangeType.ALL) {
            return null;
        }
        return type == PointChangeType.EARN;
    }

    // from/to(직접설정)가 오면 period 대신 04:00 기준으로 그 범위를 쓴다. 둘 다 있거나
    // 둘 다 없어야 한다 - 하나만 주면 "무제한"인지 "빠뜨린 것"인지 구분이 안 된다.
    private static LocalDateTime[] toDateRange(PeriodFilter period, LocalDate from, LocalDate to) {
        if ((from == null) != (to == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (from != null && to.isBefore(from)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (from != null) {
            LocalDateTime start = from.atTime(BUSINESS_DAY_CUTOFF_HOUR, 0);
            LocalDateTime end = to.plusDays(1).atTime(BUSINESS_DAY_CUTOFF_HOUR, 0);
            return new LocalDateTime[] {start, end};
        }

        if (period == null || period == PeriodFilter.ALL) {
            return new LocalDateTime[] {null, null};
        }
        LocalDate thisMonthFirstDay = currentBusinessMonthFirstDay();
        LocalDateTime startOfThisMonth = startOfBusinessMonth(thisMonthFirstDay);
        if (period == PeriodFilter.THIS_MONTH) {
            return new LocalDateTime[] {startOfThisMonth, null};
        }
        // LAST_MONTH
        LocalDateTime startOfLastMonth = startOfBusinessMonth(thisMonthFirstDay.minusMonths(1));
        return new LocalDateTime[] {startOfLastMonth, startOfThisMonth};
    }

    // 하루 경계 04:00 기준. 매달 1일 00:00~03:59는 지난달로 집계한다.
    private static final int BUSINESS_DAY_CUTOFF_HOUR = 4;

    private static LocalDate currentBusinessMonthFirstDay() {
        return businessMonthFirstDay(LocalDateTime.now());
    }

    static LocalDate businessMonthFirstDay(LocalDateTime now) {
        return now.minusHours(BUSINESS_DAY_CUTOFF_HOUR).toLocalDate().withDayOfMonth(1);
    }

    private static LocalDateTime startOfBusinessMonth(LocalDate monthFirstDay) {
        return monthFirstDay.atTime(BUSINESS_DAY_CUTOFF_HOUR, 0);
    }
}

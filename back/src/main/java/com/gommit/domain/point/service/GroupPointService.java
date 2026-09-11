package com.gommit.domain.point.service;

import com.gommit.domain.point.dto.request.PeriodFilter;
import com.gommit.domain.point.dto.request.PointChangeType;
import com.gommit.domain.point.dto.response.GroupPointBalanceResponse;
import com.gommit.domain.point.dto.response.GroupPointHistoryResponse;
import com.gommit.domain.point.entity.GroupPoint;
import com.gommit.domain.point.entity.GroupPointHistory;
import com.gommit.domain.point.entity.GroupPointReason;
import com.gommit.domain.point.repository.GroupPointHistoryRepository;
import com.gommit.domain.point.repository.GroupPointRepository;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// TODO(Point): 그룹 존재/멤버십 검증은 Group 도메인 구현 후 추가
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupPointService {

    private final GroupPointHistoryRepository groupPointHistoryRepository;
    private final GroupPointRepository groupPointRepository;
    private final PointPeriodCalculator periodCalculator;

    @Transactional
    public void reward(Long groupId, int amount, GroupPointReason reason, String sourceName) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        GroupPoint point = lockOrCreatePoint(groupId);
        point.add(amount);
        groupPointHistoryRepository.save(GroupPointHistory.of(groupId, sourceName, amount, reason, point.getBalance()));
    }

    @Transactional
    public void deduct(Long groupId, int amount, GroupPointReason reason, String sourceName) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        GroupPoint point = lockOrCreatePoint(groupId);
        if (point.getBalance() < amount) {
            throw new BusinessException(ErrorCode.POINT_INSUFFICIENT);
        }
        point.add(-amount);
        groupPointHistoryRepository.save(
                GroupPointHistory.of(groupId, sourceName, -amount, reason, point.getBalance()));
    }

    public GroupPointBalanceResponse getBalance(Long groupId) {
        int balance = groupPointRepository
                .findByGroupId(groupId)
                .map(GroupPoint::getBalance)
                .orElse(0);
        return new GroupPointBalanceResponse(groupId, balance);
    }

    public SliceResponse<GroupPointHistoryResponse> getHistories(
            Long groupId,
            PeriodFilter period,
            PointChangeType type,
            GroupPointReason reason,
            LocalDate from,
            LocalDate to,
            Long cursor,
            int size) {
        LocalDateTime[] range = periodCalculator.toDateRange(period, from, to);
        List<GroupPointHistory> rows = groupPointHistoryRepository.findHistories(
                groupId,
                cursor,
                range[0],
                range[1],
                reason,
                periodCalculator.toEarnFlag(type),
                PageRequest.of(0, size + 1));
        List<GroupPointHistoryResponse> content =
                rows.stream().map(GroupPointHistoryResponse::from).toList();
        return SliceResponse.ofCursor(content, size, GroupPointHistoryResponse::id);
    }

    public GroupPointHistoryResponse getHistoryDetail(Long groupId, Long historyId) {
        GroupPointHistory history = groupPointHistoryRepository
                .findById(historyId)
                .filter(h -> h.getGroupId().equals(groupId))
                .orElseThrow(() -> new BusinessException(ErrorCode.POINT_HISTORY_NOT_FOUND));
        return GroupPointHistoryResponse.from(history);
    }

    // 그룹 하루 완료 판정(멤버 카운트 + 중복 가드)을 호출자 트랜잭션에서 직렬화하기 위해
    // group_points 행을 미리 잠근다(없으면 0원으로 생성). 이후 같은 tx 의 reward 는 이 잠금을 재사용.
    @Transactional
    public void lockForGroupCompletion(Long groupId) {
        lockOrCreatePoint(groupId);
    }

    private GroupPoint lockOrCreatePoint(Long groupId) {
        if (!groupPointRepository.existsByGroupId(groupId)) {
            groupPointRepository.insertZeroBalanceIfAbsent(groupId, LocalDateTime.now());
        }
        return groupPointRepository
                .findWithLockByGroupId(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}

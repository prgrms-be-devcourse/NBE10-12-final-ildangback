package com.gommit.domain.point.service;

import com.gommit.domain.point.dto.request.PeriodFilter;
import com.gommit.domain.point.dto.request.PointChangeType;
import com.gommit.domain.point.dto.response.PointBalanceResponse;
import com.gommit.domain.point.dto.response.UserPointHistoryResponse;
import com.gommit.domain.point.entity.UserPoint;
import com.gommit.domain.point.entity.UserPointHistory;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.repository.UserPointHistoryRepository;
import com.gommit.domain.point.repository.UserPointRepository;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonalPointService {

    private final UserPointHistoryRepository userPointHistoryRepository;
    private final UserPointRepository userPointRepository;
    private final PointPeriodCalculator periodCalculator;

    @Transactional
    public void reward(Long userId, Long challengeId, int amount, UserPointReason reason, String sourceName) {
        UserPoint point = lockOrCreatePoint(userId);
        point.add(amount);
        userPointHistoryRepository.save(
                UserPointHistory.of(userId, challengeId, sourceName, amount, reason, point.getBalance()));
    }

    @Transactional
    public void deduct(Long userId, int amount, UserPointReason reason, String sourceName) {
        UserPoint point = lockOrCreatePoint(userId);
        if (point.getBalance() < amount) {
            throw new BusinessException(ErrorCode.POINT_INSUFFICIENT);
        }
        point.add(-amount);
        userPointHistoryRepository.save(
                UserPointHistory.ofWithoutChallenge(userId, sourceName, -amount, reason, point.getBalance()));
    }

    public PointBalanceResponse getMyBalance(Long userId) {
        LocalDateTime startOfThisMonth = periodCalculator.toDateRange(PeriodFilter.THIS_MONTH, null, null)[0];
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
        LocalDateTime[] range = periodCalculator.toDateRange(period, from, to);
        List<UserPointHistory> rows = userPointHistoryRepository.findHistories(
                userId,
                cursor,
                range[0],
                range[1],
                reason,
                periodCalculator.toEarnFlag(type),
                PageRequest.of(0, size + 1));
        List<UserPointHistoryResponse> content =
                rows.stream().map(UserPointHistoryResponse::from).toList();
        return SliceResponse.ofCursor(content, size, UserPointHistoryResponse::id);
    }

    public UserPointHistoryResponse getMyHistoryDetail(Long userId, Long historyId) {
        UserPointHistory history = userPointHistoryRepository
                .findById(historyId)
                .filter(h -> h.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.POINT_HISTORY_NOT_FOUND));
        return UserPointHistoryResponse.from(history);
    }

    // 존재 확인 -> (없으면) 생성 -> 락 조회 순서. 처음부터 락 조회만 하면 대상 행이
    // 없을 때 갭 락 데드락이 나서(PointApiIntegrationTest에서 CannotAcquireLockException로 확인됨) 이 순서로 피한다.
    private UserPoint lockOrCreatePoint(Long userId) {
        if (!userPointRepository.existsByUserId(userId)) {
            userPointRepository.insertZeroBalanceIfAbsent(userId, LocalDateTime.now());
        }
        return userPointRepository
                .findWithLockByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("UserPoint 생성에 실패했다. userId=" + userId));
    }
}

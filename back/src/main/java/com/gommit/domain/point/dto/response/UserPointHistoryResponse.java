package com.gommit.domain.point.dto.response;

import com.gommit.domain.point.entity.UserPointHistory;
import com.gommit.domain.point.entity.UserPointReason;
import java.time.LocalDateTime;

public record UserPointHistoryResponse(
        Long id,
        Long userId,
        String sourceName,
        int amount,
        UserPointReason reason,
        int balanceAfter,
        LocalDateTime createdAt) {

    public static UserPointHistoryResponse from(UserPointHistory history) {
        return new UserPointHistoryResponse(
                history.getId(),
                history.getUserId(),
                history.getSourceName(),
                history.getAmount(),
                history.getReason(),
                history.getBalanceAfter(),
                history.getCreatedAt());
    }
}

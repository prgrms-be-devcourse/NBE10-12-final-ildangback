package com.gommit.domain.point.dto.response;

import com.gommit.domain.point.entity.GroupPointHistory;
import com.gommit.domain.point.entity.GroupPointReason;
import java.time.LocalDateTime;

public record GroupPointHistoryResponse(
        Long id,
        Long groupId,
        String sourceName,
        int amount,
        GroupPointReason reason,
        int balanceAfter,
        LocalDateTime createdAt) {

    public static GroupPointHistoryResponse from(GroupPointHistory history) {
        return new GroupPointHistoryResponse(
                history.getId(),
                history.getGroupId(),
                history.getSourceName(),
                history.getAmount(),
                history.getReason(),
                history.getBalanceAfter(),
                history.getCreatedAt());
    }
}

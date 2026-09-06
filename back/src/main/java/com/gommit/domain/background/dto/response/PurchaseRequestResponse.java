package com.gommit.domain.background.dto.response;

import com.gommit.domain.background.entity.BackgroundPurchaseRequest;
import com.gommit.domain.background.entity.PurchaseRequestStatus;
import java.time.LocalDateTime;

public record PurchaseRequestResponse(
        Long requestId,
        Long backgroundId,
        String backgroundName,
        String imageUrl,
        int price,
        Long requestedBy,
        String requestedByNickname,
        PurchaseRequestStatus status,
        int agreeCount,
        int disagreeCount,
        int totalMembers,
        int requiredCount,
        boolean voted,
        LocalDateTime expiresAt) {

    public PurchaseRequestResponse(
            BackgroundPurchaseRequest request,
            String imageUrl,
            String requestedByNickname,
            int agreeCount,
            int disagreeCount,
            int totalMembers,
            int requiredCount,
            boolean voted) {
        this(
                request.getId(),
                request.getBackground().getId(),
                request.getBackground().getName(),
                imageUrl,
                request.getBackground().getPrice(),
                request.getRequestedBy(),
                requestedByNickname,
                request.getStatus(),
                agreeCount,
                disagreeCount,
                totalMembers,
                requiredCount,
                voted,
                request.getExpiresAt());
    }
}

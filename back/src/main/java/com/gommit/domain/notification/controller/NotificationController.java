package com.gommit.domain.notification.controller;

import com.gommit.domain.notification.dto.response.NotificationResponse;
import com.gommit.domain.notification.service.NotificationService;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "내 알림 목록 조회")
    public ResponseEntity<List<NotificationResponse>> getNotifications(@CurrentUser SecurityUser actor) {
        return ResponseEntity.ok(notificationService.getNotifications(actor.getId()));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "내 알림 읽음 처리")
    public ResponseEntity<Void> readNotification(@PathVariable Long notificationId, @CurrentUser SecurityUser actor) {
        notificationService.readNotification(actor.getId(), notificationId);
        return ResponseEntity.noContent().build();
    }
}

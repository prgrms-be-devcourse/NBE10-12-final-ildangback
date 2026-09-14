package com.gommit.domain.notification.scheduler;

import com.gommit.domain.notification.service.CheckInReminderService;
import com.gommit.domain.notification.service.ExtensionReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {
    private final CheckInReminderService reminderService;

    private final ExtensionReminderService extensionReminderService;

    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Seoul")
    public void sendExtensionReminders() {
        extensionReminderService.sendReminders();
    }

    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Seoul")
    public void sendCheckInReminders() {
        reminderService.sendReminders();
    }
}

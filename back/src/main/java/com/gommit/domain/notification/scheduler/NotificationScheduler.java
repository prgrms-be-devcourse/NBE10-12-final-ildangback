package com.gommit.domain.notification.scheduler;

import com.gommit.domain.notification.service.CheckInReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {
    private final CheckInReminderService reminderService;

    @Scheduled(cron = "0 0 21 * * *", zone = "Asia/Seoul")
    public void sendCheckInReminders() {
        reminderService.sendReminders();
    }
}

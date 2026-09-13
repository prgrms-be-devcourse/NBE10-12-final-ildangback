package com.gommit.domain.notification.service;

import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.challenge.service.ChallengeProgressCalculator;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.notification.entity.Notification;
import com.gommit.domain.notification.entity.NotificationType;
import com.gommit.domain.notification.repository.NotificationRepository;
import com.gommit.global.time.BusinessClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckInReminderService {
    private final ChallengeRepository challenges;
    private final ChallengeMemberRepository members;
    private final ChallengeProgressCalculator progressCalculator;
    private final CheckInRepository checkIns;
    private final NotificationRepository notifications;
    private final BusinessClock businessClock;

    @Transactional
    public void sendReminders() {
        var businessDate = businessClock.today();
        var startAt = businessDate.atTime(4, 0);
        var endAt = startAt.plusDays(1);
        // 여러 서버에서 동시에 실행해도 중복 검사 전에 챌린지 잠금을 획득한다.
        for (var challenge : challenges.findActiveForReminder()) {
            if (!progressCalculator.isCheckInDay(challenge, businessDate)) continue;
            for (var member : members.findAllByChallengeIdAndStatus(challenge.getId(), ChallengeMemberStatus.ACTIVE)) {
                if (checkIns.countByChallengeIdAndUserIdAndBusinessDate(
                                challenge.getId(), member.getUserId(), businessDate)
                        >= challenge.getDailyCheckInCount()) continue;
                if (notifications.existsByUserIdAndTypeAndRefIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        member.getUserId(), NotificationType.CHECK_IN_REMINDER, challenge.getId(), startAt, endAt))
                    continue;
                notifications.save(new Notification(
                        member.getUserId(),
                        NotificationType.CHECK_IN_REMINDER,
                        "오늘 인증이 아직 남아있어요!",
                        "오늘 인증이 아직 남아있어요! 잊기 전에 인증해 주세요 🔥",
                        challenge.getId()));
            }
        }
    }
}

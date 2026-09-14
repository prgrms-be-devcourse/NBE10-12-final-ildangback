package com.gommit.domain.notification.service;

import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ExtensionChoice;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.challenge.service.ChallengeExtensionService;
import com.gommit.domain.notification.entity.Notification;
import com.gommit.domain.notification.entity.NotificationType;
import com.gommit.domain.notification.repository.NotificationRepository;
import com.gommit.global.time.BusinessClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExtensionReminderService {
    private final ChallengeRepository challenges;
    private final ChallengeMemberRepository members;
    private final NotificationRepository notifications;
    private final BusinessClock businessClock;

    @Transactional
    public void sendReminders() {
        var tomorrow = businessClock.today().plusDays(1);
        // 기존 리마인드의 잠금 조회를 재사용해 동시 실행도 직렬화한다.
        for (var challenge : challenges.findActiveForReminder()) {
            if (!ChallengeExtensionService.extensionDeadline(challenge).equals(tomorrow)) continue;
            for (var member : members.findAllByChallengeIdAndStatusAndExtensionChoice(
                    challenge.getId(), ChallengeMemberStatus.ACTIVE, ExtensionChoice.PENDING)) {
                // 읽음 여부와 날짜에 관계없이 같은 시즌에 한 번만 보낸다.
                if (notifications.existsByUserIdAndTypeAndRefId(
                        member.getUserId(), NotificationType.EXTENSION_REMINDER, challenge.getId())) continue;
                notifications.save(new Notification(
                        member.getUserId(),
                        NotificationType.EXTENSION_REMINDER,
                        "연장 투표 마감이 다가오고 있어요!",
                        "다음 시즌 참여 여부를 아직 선택하지 않았어요! 마감 전에 선택해주세요 🔔",
                        challenge.getId()));
            }
        }
    }
}

package com.gommit.domain.notification.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.challenge.service.ChallengeProgressCalculator;
import com.gommit.domain.notification.dto.response.NotificationResponse;
import com.gommit.domain.notification.entity.Notification;
import com.gommit.domain.notification.entity.NotificationType;
import com.gommit.domain.notification.repository.NotificationRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.time.BusinessClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final ChallengeRepository challengeRepository;
    private final ChallengeMemberRepository challengeMemberRepository;
    private final ChallengeProgressCalculator progressCalculator;
    private final BusinessClock businessClock;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long userId) {
        return notificationRepository.findAllByUserIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public void readNotification(Long userId, Long notificationId) {
        Notification notification = notificationRepository
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.read();
    }

    @Transactional
    public void sendCheckInNudge(Long receiverId, String senderNickname, Long challengeId) {
        LocalDate businessDate = businessClock.today();
        Challenge challenge = challengeRepository
                .findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        if (challenge.getStatus() != ChallengeStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_ACTIVE);
        }
        boolean isActiveMember =
                challengeMemberRepository.existsActiveMember(challengeId, receiverId, ChallengeMemberStatus.ACTIVE);
        if (!isActiveMember) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER);
        }
        if (!progressCalculator.isCheckInDay(challenge, businessDate)) {
            throw new BusinessException(ErrorCode.NOT_CHECK_IN_DAY);
        }
        LocalDateTime startAt = businessDate.atTime(4, 0);
        LocalDateTime endAt = startAt.plusDays(1);
        boolean alreadyNudged =
                notificationRepository.existsByUserIdAndTypeAndRefIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        receiverId, NotificationType.CHECK_IN_NUDGE, challengeId, startAt, endAt);
        if (alreadyNudged) {
            throw new BusinessException(ErrorCode.ALREADY_NUDGED);
        }
        createNotification(
                receiverId,
                NotificationType.CHECK_IN_NUDGE,
                "콕 찌르기가 도착했어요!",
                senderNickname + "님이 오늘 인증을 기다리고 있어요.",
                challengeId);
    }

    private void createNotification(Long userId, NotificationType type, String title, String body, Long refId) {
        Notification notification = new Notification(userId, type, title, body, refId);
        notificationRepository.save(notification);
    }
}

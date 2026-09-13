package com.gommit.domain.challenge.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.notification.service.NotificationService;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.time.BusinessClock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChallengeMemberService {
    private final ChallengeMemberRepository challengeMemberRepository;
    private final ChallengeProgressCalculator challengeProgressCalculator;
    private final ChallengeRepository challengeRepository;
    private final CheckInRepository checkInRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final BusinessClock businessClock;

    public ChallengeMember createChallengeMember(Challenge challenge, Long userId, ChallengeMemberRole role) {
        ChallengeMember challengeMember = ChallengeMember.builder()
                .challenge(challenge)
                .userId(userId)
                .role(role)
                .build();
        return challengeMemberRepository.save(challengeMember);
    }

    public LocalDate findLastRequiredCheckInDay(Long userId, LocalDate businessDate) {
        return challengeMemberRepository.findAllByUserIdAndStatus(userId, ChallengeMemberStatus.ACTIVE).stream()
                .filter(member -> member.getChallenge().getStatus() == ChallengeStatus.ACTIVE)
                .map(member -> challengeProgressCalculator.previousCheckInDay(member.getChallenge(), businessDate))
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    @Transactional
    public void nudgeMember(Long challengeId, Long senderId, Long receiverId) {
        Challenge challenge = challengeRepository
                .findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        if (challenge.getStatus() != ChallengeStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_ACTIVE);
        }
        if (senderId.equals(receiverId)) {
            throw new BusinessException(ErrorCode.CANNOT_NUDGE_SELF);
        }
        boolean senderIsMember =
                challengeMemberRepository.existsActiveMember(challengeId, senderId, ChallengeMemberStatus.ACTIVE);
        if (!senderIsMember) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER);
        }
        boolean receiverIsMember =
                challengeMemberRepository.existsActiveMember(challengeId, receiverId, ChallengeMemberStatus.ACTIVE);
        if (!receiverIsMember) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER);
        }
        LocalDate businessDate = businessClock.today();
        int checkInCount =
                checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(challengeId, receiverId, businessDate);
        // receiver가 오늘 인증을 이미 완료했다면 콕 찌르기 불가
        boolean completedToday = checkInCount >= challenge.getDailyCheckInCount();
        if (completedToday) {
            throw new BusinessException(ErrorCode.ALREADY_CHECKED_IN);
        }
        User senderUser =
                userRepository.findById(senderId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        notificationService.sendCheckInNudge(receiverId, senderUser.getNickname(), challengeId);
    }
}

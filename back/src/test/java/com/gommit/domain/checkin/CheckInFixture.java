package com.gommit.domain.checkin;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.entity.ExtensionChoice;
import com.gommit.domain.challenge.entity.FrequencyType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

// challenge 도메인에 팩토리/빌더가 없어 테스트에서 리플렉션으로 엔티티를 만든다.
// 기본 기간: 2026-09-01 ~ 2026-12-31.
public final class CheckInFixture {

    public static final LocalDate START = LocalDate.of(2026, 9, 1);
    public static final LocalDate END = LocalDate.of(2026, 12, 31);

    private CheckInFixture() {}

    private static <T> T newInstance(Class<T> type) {
        try {
            var ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("테스트 엔티티 생성 실패: " + type, e);
        }
    }

    public static Challenge dailyChallenge(Long id, int dailyCheckInCount) {
        return challenge(id, FrequencyType.DAILY, null, null, dailyCheckInCount, true);
    }

    public static Challenge challenge(
            Long id,
            FrequencyType frequencyType,
            Integer frequencyValue,
            String daysOfWeek,
            int dailyCheckInCount,
            boolean allowPhoto) {
        Challenge challenge = newInstance(Challenge.class);
        ReflectionTestUtils.setField(challenge, "id", id);
        ReflectionTestUtils.setField(challenge, "groupId", 1L);
        ReflectionTestUtils.setField(challenge, "seqNo", 1);
        ReflectionTestUtils.setField(challenge, "startDate", START);
        ReflectionTestUtils.setField(challenge, "endDate", END);
        ReflectionTestUtils.setField(challenge, "status", ChallengeStatus.ACTIVE);
        ReflectionTestUtils.setField(challenge, "frequencyType", frequencyType);
        ReflectionTestUtils.setField(challenge, "frequencyValue", frequencyValue);
        ReflectionTestUtils.setField(challenge, "daysOfWeek", daysOfWeek);
        ReflectionTestUtils.setField(challenge, "dailyCheckInCount", dailyCheckInCount);
        ReflectionTestUtils.setField(challenge, "requiredDayCount", 30);
        ReflectionTestUtils.setField(challenge, "groupCurrentStreak", 0);
        ReflectionTestUtils.setField(challenge, "groupBestStreak", 0);
        ReflectionTestUtils.setField(challenge, "allowPhoto", allowPhoto);
        return challenge;
    }

    public static ChallengeMember activeMember(Long id, Challenge challenge, Long userId) {
        return member(id, challenge, userId, ChallengeMemberStatus.ACTIVE, null);
    }

    public static ChallengeMember leftMember(Long id, Challenge challenge, Long userId, LocalDate leftOn) {
        return member(id, challenge, userId, ChallengeMemberStatus.LEFT, leftOn.atStartOfDay());
    }

    public static ChallengeMember member(
            Long id, Challenge challenge, Long userId, ChallengeMemberStatus status, LocalDateTime leftAt) {
        ChallengeMember member = newInstance(ChallengeMember.class);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "challenge", challenge);
        ReflectionTestUtils.setField(member, "userId", userId);
        ReflectionTestUtils.setField(member, "role", ChallengeMemberRole.MEMBER);
        ReflectionTestUtils.setField(member, "status", status);
        ReflectionTestUtils.setField(member, "currentStreak", 0);
        ReflectionTestUtils.setField(member, "bestStreak", 0);
        ReflectionTestUtils.setField(member, "leftAt", leftAt);
        ReflectionTestUtils.setField(member, "extensionChoice", ExtensionChoice.PENDING);
        return member;
    }
}

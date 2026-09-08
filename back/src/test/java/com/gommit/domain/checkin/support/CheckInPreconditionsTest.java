package com.gommit.domain.checkin.support;

import static com.gommit.domain.checkin.CheckInFixture.dailyChallenge;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.checkin.CheckInFixture;
import com.gommit.domain.checkin.support.CheckInPreconditions.ReadDateAccess;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckInPreconditions")
class CheckInPreconditionsTest {

    private static final long CHALLENGE_ID = 1L;
    private static final long USER_ID = 42L;

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeMemberRepository challengeMemberRepository;

    private CheckInPreconditions preconditions;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        preconditions = new CheckInPreconditions(challengeRepository, challengeMemberRepository);
    }

    @Test
    @DisplayName("getChallenge — 없으면 CHALLENGE_NOT_FOUND")
    void getChallengeNotFound() {
        when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> preconditions.getChallenge(CHALLENGE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHALLENGE_NOT_FOUND);
    }

    @Nested
    @DisplayName("getActiveChallengeForActiveMember")
    class ActiveChallengeForActiveMember {

        @Test
        @DisplayName("챌린지 ACTIVE + 멤버 ACTIVE 면 챌린지를 돌려준다")
        void succeeds() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(CHALLENGE_ID, USER_ID))
                    .thenReturn(Optional.of(CheckInFixture.activeMember(9L, challenge, USER_ID)));

            assertThat(preconditions.getActiveChallengeForActiveMember(CHALLENGE_ID, USER_ID))
                    .isEqualTo(challenge);
        }

        @Test
        @DisplayName("챌린지가 ACTIVE 가 아니면 CHALLENGE_NOT_ACTIVE")
        void rejectsInactiveChallenge() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            ReflectionTestUtils.setField(challenge, "status", ChallengeStatus.ENDED);
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(CHALLENGE_ID, USER_ID))
                    .thenReturn(Optional.of(CheckInFixture.activeMember(9L, challenge, USER_ID)));

            assertThatThrownBy(() -> preconditions.getActiveChallengeForActiveMember(CHALLENGE_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CHALLENGE_NOT_ACTIVE);
        }

        @Test
        @DisplayName("참여자 행이 없으면 NOT_CHALLENGE_MEMBER")
        void rejectsNonMember() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(CHALLENGE_ID, USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> preconditions.getActiveChallengeForActiveMember(CHALLENGE_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_CHALLENGE_MEMBER);
        }

        @Test
        @DisplayName("이탈한 참여자면 NOT_CHALLENGE_MEMBER")
        void rejectsLeftMember() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(CHALLENGE_ID, USER_ID))
                    .thenReturn(
                            Optional.of(CheckInFixture.leftMember(9L, challenge, USER_ID, LocalDate.of(2026, 9, 5))));

            assertThatThrownBy(() -> preconditions.getActiveChallengeForActiveMember(CHALLENGE_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_CHALLENGE_MEMBER);
        }
    }

    @Nested
    @DisplayName("resolveReadDateAccess")
    class ResolveReadDateAccess {

        @Test
        @DisplayName("ACTIVE 멤버는 날짜 제한이 없다(maxBusinessDate=null)")
        void activeMemberNoLimit() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(CHALLENGE_ID, USER_ID))
                    .thenReturn(Optional.of(CheckInFixture.activeMember(9L, challenge, USER_ID)));

            ReadDateAccess access = preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID);

            assertThat(access.maxBusinessDate()).isNull();
        }

        @Test
        @DisplayName("이탈 멤버는 이탈일까지만 조회 가능하다")
        void leftMemberCappedAtLeftDate() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            LocalDate leftOn = LocalDate.of(2026, 9, 10);
            // 04:00 컷오프에 걸리지 않도록 정오로 고정 (자정이면 전날로 셈해진다).
            ChallengeMember member =
                    CheckInFixture.member(9L, challenge, USER_ID, ChallengeMemberStatus.LEFT, leftOn.atTime(12, 0));
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(CHALLENGE_ID, USER_ID))
                    .thenReturn(Optional.of(member));

            ReadDateAccess access = preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID);

            assertThat(access.maxBusinessDate()).isEqualTo(leftOn);
            assertThat(access.allows(leftOn)).isTrue();
            assertThat(access.allows(leftOn.plusDays(1))).isFalse();
        }
    }
}
